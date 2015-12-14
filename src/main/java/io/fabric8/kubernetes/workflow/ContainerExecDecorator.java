package io.fabric8.kubernetes.workflow;

import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.LogWatch;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fabric8.kubernetes.workflow.Utils.isPodCompleted;
import static io.fabric8.kubernetes.workflow.Utils.isPodRunning;

public class ContainerExecDecorator extends LauncherDecorator implements Serializable {

    private transient final KubernetesClient client;
    private final String podName;
    private final String image;
    private final String workspace;
    private transient final List<EnvVar> env;

    private transient final List<Closeable> closeables = new ArrayList<>();

    public ContainerExecDecorator(KubernetesClient client, String podName, String image, String workspace, List<EnvVar> env) {
        this.client = client;
        this.podName = podName;
        this.image = image;
        this.workspace = workspace;
        this.env = env;
    }

    @Override
    public Launcher decorate(final Launcher launcher, Node node) {
        return new Launcher.DecoratedLauncher(launcher) {
            @Override
            public Proc launch(Launcher.ProcStarter starter) throws IOException {
                final AtomicBoolean isAlive = new AtomicBoolean(false);
                final CountDownLatch finished = new CountDownLatch(1);

                final Pod pod = client.pods().createNew()
                        .withNewMetadata()
                        .withName(podName)
                        .addToLabels("owner", "jenkins")
                        .endMetadata()
                        .withNewSpec()
                        .addNewVolume()
                            .withName("workspace")
                            .withNewHostPath(workspace)
                        .endVolume()
                        .withServiceAccount("fabric8")
                        .addNewContainer()
                        .addNewVolumeMount()
                        .withName("workspace")
                        .withMountPath(workspace)
                        .endVolumeMount()
                        .withName("podstep")
                        .withImage(image)
                        .withEnv(env)
                        .withWorkingDir(workspace)
                        .withCommand("/bin/sh", "-c")
                        .withArgs(starter.cmds().get(starter.cmds().size() - 1)) // Always get the last part
                        .withTty(false) //It screws up getLog() if tty = true
                        .endContainer()
                        .withRestartPolicy("Never")
                        .endSpec()
                        .done();

                waitForStarted(pod);

               closeables.add(client.pods().withName(podName).watch(new Watcher<Pod>() {
                   @Override
                   public void eventReceived(Action action, Pod pod) {
                       isAlive.set(isPodRunning(pod));

                       switch (action) {
                           case ADDED:
                           case MODIFIED:
                           case ERROR:
                               if (isPodCompleted(pod)) {
                                   finished.countDown();
                               }
                               break;
                           case DELETED:
                               finished.countDown();
                       }
                   }

                   @Override
                   public void onClose(KubernetesClientException e) {
                       finished.countDown();
                   }
               }));

                final LogWatch logWatch = client.pods().withName(podName).watchLog();
                closeables.add(logWatch);

                return new Proc() {
                    @Override
                    public boolean isAlive() throws IOException, InterruptedException {
                        return isAlive.get();
                    }

                    @Override
                    public void kill() throws IOException, InterruptedException {
                        closeAll();
                        client.pods().withName(podName).delete();
                    }

                    @Override
                    public int join() throws IOException, InterruptedException {
                        finished.await();
                        closeAll();
                        return 1;
                    }

                    @Override
                    public InputStream getStdout() {
                        return logWatch.getOutput();
                    }

                    @Override
                    public InputStream getStderr() {
                        return logWatch.getOutput();
                    }

                    @Override
                    public OutputStream getStdin() {
                        return null;
                    }
                };
            }

            @Override
            public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
                client.pods().withName(podName).delete();
            }
        };
    }

    private boolean waitForStarted(Pod pod) {
        final CountDownLatch latch = new CountDownLatch(1);
        try (Watch watch = client.pods().withName(pod.getMetadata().getName()).watch(new PodWatcher(latch))) {
            if (isPodRunning(pod)) {
                latch.countDown();
            }
            return latch.await(1, TimeUnit.MINUTES);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private void closeAll() {
        for (Closeable c : closeables) {
            try {
                c.close();
            } catch (Throwable t) {
                //ignore
            }
        }
        closeables.clear();
    }

    private static class PodWatcher implements Watcher<Pod> {

        private final CountDownLatch latch;

        private PodWatcher(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void eventReceived(Watcher.Action action, Pod pod) {
            if (isPodRunning(pod)) {
                latch.countDown();
            }
        }
        @Override
        public void onClose(KubernetesClientException e) {
            latch.countDown();
        }
    }
}
