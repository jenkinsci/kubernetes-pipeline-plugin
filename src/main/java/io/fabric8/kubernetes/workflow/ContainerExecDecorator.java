package io.fabric8.kubernetes.workflow;

import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.dsl.LogWatch;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fabric8.kubernetes.workflow.KubernetesFacade.createPod;
import static io.fabric8.kubernetes.workflow.KubernetesFacade.deletePod;
import static io.fabric8.kubernetes.workflow.KubernetesFacade.watch;
import static io.fabric8.kubernetes.workflow.KubernetesFacade.watchLogs;

public class ContainerExecDecorator extends LauncherDecorator implements Serializable {


    private final String podName;
    private final String image;
    private final String workspace;
    private transient final List<EnvVar> env;

    public ContainerExecDecorator(String podName, String image, String workspace, List<EnvVar> env) {
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
                final CountDownLatch started = new CountDownLatch(1);
                final CountDownLatch finished = new CountDownLatch(1);
                final Pod pod = createPod(podName, image, workspace, env, starter.cmds().get(starter.cmds().size() - 1));
                final Watch watch = watch(podName, isAlive, started, finished, true);
                try {
                    started.await();
                    final LogWatch logWatch = watchLogs(podName);
                    return new PodProc(podName, isAlive, finished, logWatch);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
                deletePod(podName);
            }
        };
    }
}
