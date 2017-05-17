/*
 * Copyright (C) 2015 Original Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fabric8.kubernetes.pipeline;

import org.csanchez.jenkins.plugins.kubernetes.ContainerEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.PodEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;

import io.fabric8.docker.client.utils.Utils;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import okhttp3.Response;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.fabric8.workflow.core.Constants.EXIT;
import static io.fabric8.workflow.core.Constants.FAILED_PHASE;
import static io.fabric8.workflow.core.Constants.NEWLINE;
import static io.fabric8.workflow.core.Constants.RUNNING_PHASE;
import static io.fabric8.workflow.core.Constants.SPACE;
import static io.fabric8.workflow.core.Constants.SUCCEEDED_PHASE;
import static io.fabric8.workflow.core.Constants.UTF_8;

public final class KubernetesFacade implements Closeable {

    private static final transient Logger LOGGER = Logger.getLogger(KubernetesFacade.class.getName());
    private static final String VOLUME_FORMAT = "volume-%d";
    private static final Pattern SPLIT_IN_SPACES = Pattern.compile("([^\"]\\S*|\".+?\")\\s*");

    private final Set<Closeable> closeables = new HashSet<>();
    private final KubernetesClient client = new DefaultKubernetesClient();


    public Pod createPod(String hostname, String jobname, PodTemplate podTemplate, String buildWorkspace, Map<String, String> labels) {
        LOGGER.info("Creating pod with name:" + podTemplate.getName());
        List<Volume> volumes = new ArrayList<>();
        List<VolumeMount> mounts = new ArrayList<>();

        int volumeIndex = 1;
        String rootWorkspace = Paths.get(buildWorkspace).getParent().toAbsolutePath().toString();
        if (hasWorkspaceMount(rootWorkspace, podTemplate.getVolumes())) {
            LOGGER.info("Found volume mount for workspace:[" + buildWorkspace + "].");
        } else if (hasWorkspaceMount(buildWorkspace, podTemplate.getVolumes())) {
            LOGGER.info("Found volume mount for build workspace:[" + buildWorkspace + "].");
        } else {
            // mount jenkins-workspace pvc if available
            String rootPvcName = "jenkins-workspace";
            String buildPvcName = "jenkins-workspace-" + jobname;
            String volumeName = String.format(VOLUME_FORMAT, volumeIndex);
            if (client.persistentVolumeClaims().withName(buildPvcName).get() != null) {
                LOGGER.info("Using build pvc: ["+buildPvcName+"] for build workspace:[" + buildWorkspace + "].");
                volumes.add(new VolumeBuilder().withName(volumeName).withNewPersistentVolumeClaim(buildPvcName, false).build());
                mounts.add(new VolumeMountBuilder().withName(volumeName).withMountPath(buildWorkspace).build());
                volumeIndex++;
            } else if (client.persistentVolumeClaims().withName(rootPvcName).get() != null) {
                LOGGER.info("Using workspace pvc: ["+rootPvcName+"] for workspace:[" + rootWorkspace + "].");
                volumes.add(new VolumeBuilder().withName(volumeName).withNewPersistentVolumeClaim(rootPvcName, false).build());
                //mounts.add(new VolumeMountBuilder().withName(volumeName).withMountPath(rootWorkspace).build());
                mounts.add(new VolumeMountBuilder().withName(volumeName).withMountPath("/home/jenkins/workspace").build());
                volumeIndex++;
            }
            else {
                LOGGER.warning("No volume mount for workspace. And no pvc named: [jenkins-workspace] found. Falling back to hostPath volumes.");
                volumes.add(new VolumeBuilder().withName(volumeName).withNewHostPath(buildWorkspace).build());
                mounts.add(new VolumeMountBuilder().withName(volumeName).withMountPath(buildWorkspace).build());
                volumeIndex++;
            }
        }

        for (PodVolume volume : podTemplate.getVolumes()) {
            String volumeName = String.format(VOLUME_FORMAT, volumeIndex);
            volumes.add(volume.buildVolume(volumeName));
            mounts.add(new VolumeMountBuilder().withName(volumeName).withMountPath(volume.getMountPath()).build());
            volumeIndex++;
        }

        Node node = getNodeOfPod(hostname);

        List<Container> containers = new ArrayList<>();
        for (ContainerTemplate c : podTemplate.getContainers()) {
            List<EnvVar> env = new ArrayList<EnvVar>();

            if (podTemplate.getEnvVars() != null) {
                for (PodEnvVar podEnvVar : podTemplate.getEnvVars()) {
                    env.add(new EnvVarBuilder().withName(podEnvVar.getKey()).withValue(podEnvVar.getValue()).build());
                }
            }

            if (c.getEnvVars() != null) {
                for (ContainerEnvVar containerEnvVar : c.getEnvVars()) {
                    env.add(new EnvVarBuilder().withName(containerEnvVar.getKey()).withValue(containerEnvVar.getValue()).build());
                }
            }

            containers.add(new ContainerBuilder()
                    .withName(c.getName())
                    .withImage(c.getImage())
                    .withEnv(env)
                    .withWorkingDir(buildWorkspace)
                    .withCommand(split(c.getCommand()))
                    .withArgs(split(c.getArgs()))
                    .withTty(c.isTtyEnabled())
                    .withNewSecurityContext()
                        .withPrivileged(c.isPrivileged())
                    .endSecurityContext()
                    .withVolumeMounts(mounts)
                    .build()
            );
        }

        Pod p = client.pods().createNew()
                .withNewMetadata()
                    .withName(podTemplate.getName())
                    .addToLabels(labels)
                    .addToLabels("owner", "jenkins")
                    .addToLabels("name", podTemplate.getName())
                    .addToLabels("image", containers.get(0).getName())
                .endMetadata()
                .withNewSpec()
                    .withNodeSelector(node != null ? node.getMetadata().getLabels() : new HashMap<String, String>())
                    .withVolumes(volumes)
                    .withContainers(containers)
                    .withRestartPolicy("Never")
                    .withServiceAccount(podTemplate.getServiceAccount())
                    .endSpec()
                .done();
        return p;
    }

    private Node getNodeOfPod(String podName) {
        Node node;
        if (Utils.isNullOrEmpty(podName)) {
            LOGGER.warning("Failed to find the current pod name.");
            return null;
        }

        Pod pod = client.pods().withName(podName).get();
        if (pod == null) {
            LOGGER.warning("Failed to find pod with name:" + podName + " in namespace:" + client.getNamespace() + ".");
            node = null;
        } else {
            String nodeName = pod.getSpec().getNodeName();
            node = client.nodes().withName(nodeName).get();
        }
        if (node == null) {
            LOGGER.warning("Failed to find pod with name:" + podName + ".");
            return null;
        } else {
            return node;
        }
    }

    public Boolean deletePod(String name) {
        LOGGER.info("Deleting pod with name:" + name);
        if (client.pods().withName(name).delete()) {
            synchronized (closeables) {
                for (Closeable c : closeables) {
                    closeQuietly(c);
                }
                closeables.clear();
            }
            return true;
        } else {
            return false;
        }
    }

    public LogWatch watchLogs(String podName) {
        LogWatch watch = client.pods().withName(podName).watchLog();
        synchronized (closeables) {
            closeables.add(watch);
        }
        return watch;
    }


    public ExecWatch exec(String podName, String containerName, final AtomicBoolean alive, final CountDownLatch started, final CountDownLatch finished, final PrintStream out, final String... statements) {
        ExecWatch watch = client.pods().withName(podName)
                .inContainer(containerName)
                .redirectingInput()
                .writingOutput(out)
                .writingError(out)
                .withTTY()
                .usingListener(new ExecListener() {
                    @Override
                    public void onOpen(Response response) {
                        alive.set(true);
                        started.countDown();
                    }

                    @Override

                    public void onFailure(Throwable t, Response response) {
                        alive.set(false);
                        t.printStackTrace(out);
                        started.countDown();
                        finished.countDown();
                    }

                    @Override
                    public void onClose(int i, String s) {
                        alive.set(false);
                        started.countDown();
                        finished.countDown();

                    }
                }).exec();

        synchronized (closeables) {
            closeables.add(watch);
        }

        waitQuietly(started);

        try {
            for (String stmt : statements) {
                watch.getInput().write((stmt).getBytes(UTF_8));
                watch.getInput().write((SPACE).getBytes(UTF_8));
            }
            //We need to exit so that we know when the command has finished.
            watch.getInput().write(NEWLINE.getBytes(UTF_8));
            watch.getInput().write(EXIT.getBytes(UTF_8));
            watch.getInput().write(NEWLINE.getBytes(UTF_8));
            watch.getInput().flush();
        } catch (Exception e) {
            //e.printStackTrace(out);
        }

        return watch;
    }

    public Watch watch(final String podName, AtomicBoolean alive, CountDownLatch started, CountDownLatch finished, Boolean cleanUpOnFinish) {
        Callable<Void> onCompletion = cleanUpOnFinish ? new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                cleanUp();
                return null;
            }
        } : null;

        Watch watch = client.pods().withName(podName).watch(new PodWatcher(alive, started, finished, onCompletion));
        synchronized (closeables) {
            closeables.add(watch);
        }
        return watch;
    }


    @Override
    public void close() throws IOException {
        closeQuietly(client);
        cleanUp();
    }

    private void cleanUp() {
        synchronized (closeables) {
            for (Closeable c : closeables) {
                closeQuietly(c);
            }
            closeables.clear();
        }
    }

    private static List<String> split(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        // handle quoted arguments
        Matcher m = SPLIT_IN_SPACES.matcher(str);
        List<String> commands = new ArrayList<String>();
        while (m.find()) {
            commands.add(m.group(1).replace("\"", ""));
        }
        return commands;
    }

    public static boolean hasWorkspaceMount(String workspace, List<PodVolume> volumes) {
            for (PodVolume volume : volumes) {
                if (volume.getMountPath().equals(workspace)) {
                    return true;
                }
            }
        return false;
    }


    public static final boolean isPodRunning(Pod pod) {
        return pod != null && pod.getStatus() != null && RUNNING_PHASE.equals(pod.getStatus().getPhase());
    }

    public static final boolean isPodCompleted(Pod pod) {
        return pod != null && pod.getStatus() != null &&
                (SUCCEEDED_PHASE.equals(pod.getStatus().getPhase()) || FAILED_PHASE.equals(pod.getStatus().getPhase()));
    }

    private static void waitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            //ignore
        }
    }

    private static void closeQuietly(Closeable c) {
        try {
            c.close();
        } catch (IOException e) {
            //ignore
        }
    }
}
