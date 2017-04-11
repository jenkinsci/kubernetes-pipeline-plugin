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

package io.fabric8.kubernetes.pipeline

import com.cloudbees.groovy.cps.NonCPS
import org.csanchez.jenkins.plugins.kubernetes.ContainerEnvVar
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate
import org.csanchez.jenkins.plugins.kubernetes.PodEnvVar
import org.csanchez.jenkins.plugins.kubernetes.volumes.ConfigMapVolume
import org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume
import org.csanchez.jenkins.plugins.kubernetes.volumes.HostPathVolume
import org.csanchez.jenkins.plugins.kubernetes.volumes.NfsVolume
import org.csanchez.jenkins.plugins.kubernetes.volumes.PersistentVolumeClaim
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume
import org.csanchez.jenkins.plugins.kubernetes.volumes.SecretVolume

class Kubernetes implements Serializable {

    private org.jenkinsci.plugins.workflow.cps.CpsScript script

    public final image

    public Kubernetes(org.jenkinsci.plugins.workflow.cps.CpsScript script) {
        this.script = script
        this.image = new Image(this)
    }

    public Pod pod(String name = "jenkins-pod", String image = "", String serviceAccount = "", String workingDir = "/home/jenkins/workspace") {
        Pod pod = new Pod(this)
        pod.name = name
        return pod
    }

    public Image image() {
        return new Image(this)
    }

    public Image image(String name) {
        return new NamedImage(this, name)
    }

    public static class Pod implements Serializable {

        private final Kubernetes kubernetes
        private String name;

        private List<ContainerTemplate> containers = new ArrayList<>()
        private Map<String, String> envVars = new HashMap<>()
        private List<PodVolume> volumes = new ArrayList<>()

        private String serviceAccount;
        private String nodeSelector;
        private String workingDir = "/home/jenkins";

        Pod(Kubernetes kubernetes) {
            this.kubernetes = kubernetes
        }

        public Pod withName(String name) {
            this.name = name
            return this
        }

        @NonCPS
        public Container withNewContainer() {
            return new Container(this);
        }


        @NonCPS
        private Container getFirstContainer() {
            if  (containers == null || containers.isEmpty()) {
                return this.withNewContainer()
            } else {
                return new Container(this, containers.remove(0))
            }
        }

        @NonCPS
        public Pod withImage(String image) {
            return getFirstContainer().withImage(image).done()
        }

        public Pod withEnvar(String key, String value) {
            this.envVars.put(key, value)
            return this
        }

        public Pod withSecret(String mountPath, String secretName) {
            volumes.add(new SecretVolume(mountPath, secretName))
            return this
        }

        public Pod withConfigMap(String mountPath, String configMapName) {
            volumes.add(new ConfigMapVolume(mountPath, configMapName))
            return this
        }

        public Pod withHostPath(String mountPath, String hostPath) {
            volumes.add(new HostPathVolume(hostPath, mountPath))
            return this
        }

        public Pod withVolumeClaim(String mountPath, String claimName, Boolean readOnly) {
            volumes.add(new PersistentVolumeClaim(mountPath, claimName, readOnly))
            return this
        }

        public Pod withNfs(String mountPath, String serverAddress, String serverPath, Boolean readOnly) {
            volumes.add(new NfsVolume(serverAddress, serverPath, readOnly, mountPath))
            return this
        }

        public Pod withEmptyDir(String mountPath, Boolean memory) {
            volumes.add(new EmptyDirVolume(mountPath, memory))
            return this
        }


        public Pod withServiceAccount(String serviceAccount) {
            this.serviceAccount = serviceAccount;
            return this
        }

        public Pod nodeSelector(String nodeSelector) {
            this.nodeSelector = nodeSelector
            return this
        }

        public Pod workingDir(String workingDir) {
            this.workingDir = workingDir;
            return this;
        }


        public <V> V inside(def container = "", Closure<V> body) {
            if (kubernetes.script.env.HOME != null) { // http://unix.stackexchange.com/a/123859/26736
                // Already inside a node block.
                kubernetes.script.withPod(name: name, containers: containers, envVars: envVars, volumes: volumes, serviceAccount: serviceAccount, nodeSelector: nodeSelector, workingDir: workingDir) {
                    body()
                }
            } else {
                def label = "buildpod.${kubernetes.script.env.JOB_NAME}.${kubernetes.script.env.BUILD_NUMBER}".replace('-', '_').replace('/', '_')
                List<PodEnvVar> podEnvVars = new ArrayList<>();
                for (Map.Entry<String, String> entry : envVars.entrySet()) {
                    podEnvVars.add(new PodEnvVar(entry.getKey(), entry.getValue()));
                }
                kubernetes.script.podTemplate(name: name, label: label, containers: containers, envVars: podEnvVars, volumes: volumes, serviceAccount: serviceAccount, nodeSelector: nodeSelector) {
                    kubernetes.script.node(label) {
                        if (container != null && !container.isEmpty()) {
                            kubernetes.script.container(name: container) {
                                body()
                            }
                        } else if (containers.size() == 1) {
                            String name = containers.get(0).name;
                            kubernetes.script.container(name: name) {
                                body()
                            }
                        } else {
                            body()
                        }
                    }
                }
            }
        }
    }

    public static class Container implements Serializable {
        private transient Pod pod;
        private String name = "buildcnt";
        private String image;
        private Boolean privileged = false;
        private Boolean alwaysPullImage = false;
        private String workingDir = "/home/jenkins";
        private String command = "/bin/sh -c";
        private String args = "cat";
        private Boolean ttyEnabled = true;
        private String resourceRequestCpu;
        private String resourceRequestMemory;
        private String resourceLimitCpu;
        private String resourceLimitMemory;
        private transient Map<String, String> envVars = new HashMap<>()

        public Container (Pod pod) {
            this.pod = pod
        }

        public Container (Pod pod, ContainerTemplate c) {
            this(pod, c.name, c.image, c.alwaysPullImage, c.workingDir, c.command, c.args,
                    c.ttyEnabled, c.resourceRequestCpu, c.resourceRequestMemory,
                    c.resourceLimitCpu, c.resourceLimitMemory, c.envVars)
        }

        public Container(Pod pod, String name, String image, Boolean alwaysPullImage, Boolean privileged, String workingDir, String command, String args, Boolean ttyEnabled, String resourceRequestCpu, String resourceRequestMemory, String resourceLimitCpu, String resourceLimitMemory, Map<String, String> envVars) {
            this.pod = pod;
            this.name = name;
            this.image = image;
            this.privileged = privileged;
            this.alwaysPullImage = alwaysPullImage;
            this.workingDir = workingDir;
            this.command = command;
            this.args = args;
            this.ttyEnabled = ttyEnabled;
            this.resourceRequestCpu = resourceRequestCpu;
            this.resourceRequestMemory = resourceRequestMemory;
            this.resourceLimitCpu = resourceLimitCpu;
            this.resourceLimitMemory = resourceLimitMemory;
            this.envVars = envVars;
        }

        @NonCPS
        public Container withName(String name) {
            this.name = name
            return this
        }

        @NonCPS
        public Container withImage(String image) {
            this.image = image
            return this
        }

        @NonCPS
        public Container withPrivileged(Boolean privileged) {
            this.privileged = privileged
            return this
        }

        @NonCPS
        public Container withAlwaysPullImage(Boolean alwaysPullImage) {
            this.alwaysPullImage = alwaysPullImage
            return this
        }

        @NonCPS
        public Container withWorkingDir(String workingDir) {
            this.workingDir = workingDir
            return this
        }

        @NonCPS
        public Container withCommand(String command) {
            this.command = command
            return this
        }

        @NonCPS
        public Container withArgs(String args) {
            this.args = args
            return this
        }

        @NonCPS
        public Container withTtyEnabled(Boolean ttyEnabled) {
            this.ttyEnabled = ttyEnabled
            return this
        }

        @NonCPS
        public Container withResourceRequestCpu(String resourceRequestCpu) {
            this.resourceRequestCpu
            return this
        }

        @NonCPS
        public Container withResourceRequestMemory(String resourceRequestMemory) {
            this.resourceLimitMemory
            return this
        }

        @NonCPS
        public Container withResourceLimitCpu(String resourceLimitCpu) {
            this.resourceLimitCpu = resourceLimitMemory
            return this

        }

        @NonCPS
        public Container withResourceLimitMemory(String resourceLimitMemory) {
            this.resourceLimitMemory = resourceLimitMemory
            return this
        }

        @NonCPS
        public Container withEnvar(String key, String value) {
            this.envVars.put(key, value)
            return this
        }

        @NonCPS
        private ContainerTemplate asTemplate() {
            def containerEnvVars = new ArrayList<>();
            for (Map.Entry<String, String> entry : envVars.entrySet()) {
                containerEnvVars.add(new ContainerEnvVar(entry.getKey(), entry.getValue()));
            }

            ContainerTemplate template  = new ContainerTemplate(name, image);
            template.setAlwaysPullImage(alwaysPullImage)
            template.setCommand(command)
            template.setArgs(args)
            template.setTtyEnabled(ttyEnabled)
            template.setEnvVars(containerEnvVars)
            template.setPrivileged(privileged)
            template.setResourceRequestCpu(resourceRequestCpu)
            template.setResourceRequestMemory(resourceRequestMemory)
            template.setResourceLimitCpu(resourceLimitCpu)
            template.setResourceLimitMemory(resourceRequestMemory)
            return template;
        }


        @NonCPS
        public Pod done() {
            pod.containers.add(asTemplate())
            return pod
        }

        //Just added for the shake of readability
        @NonCPS
        public Pod and() {
            pod.containers.add(asTemplate())
            return pod
        }

        @NonCPS
        public <V> V inside(Closure<V> body) {
            return done().inside(container: name, body)
        }
    }

    public class Image implements Serializable {

        private final Kubernetes kubernetes

        Image(Kubernetes kubernetes) {
            this.kubernetes = kubernetes
        }

        //Shortcut to build
        Pod build(String name, String path = ".", boolean rm = false, long  timeout = 600000L) {
            this.withName(name)
                    .build()
                    .withTimeout(timeout)
                    .removingIntermediate(rm)
                    .fromPath(path)
        }

        //Shortcut to tag
        void tag(String name, String tag, String repo = "") {
            this.withName(name)
                    .tag()
                    .inRepository(repo != null && !repo.isEmpty() ? repo : name)
                    .withTag(tag)
        }

        //Shortcut to push
        void push(String name, String tagName = "latest", long  timeout = 600000L) {
            this.withName(name)
                    .push()
                    .withTimeout(timeout)
                    .withTag(tagName)
                    .toRegistry()
        }

        NamedImage withName(String name) {
            return new NamedImage(kubernetes, name)
        }
     }

    private static class NamedImage implements Serializable {

        private final Kubernetes kubernetes
        private final String name

        NamedImage(Kubernetes kubernetes, String name) {
            this.kubernetes = kubernetes
            this.name = name
        }

        Pod toPod() {
            return new Pod(kubernetes, "jenkins-buildpod", name, "", false, new HashMap<String, String>(), new HashMap<String, String>(), new HashMap<String, String>(), new HashMap<String, String>(), new HashMap<String, String>())
        }


        BuildImage build() {
            return new BuildImage(kubernetes, name, false, 600000L, null, null, null, new ArrayList<String>())
        }

        PushImage push() {
            return new PushImage(kubernetes, name, null, 600000L, null, null, null)
        }

        void tag() {
            new TagImage(kubernetes, name, null, null, false, null, null, null)
        }
    }

    private static class BuildImage implements Serializable {
        private final Kubernetes kubernetes
        private final String name
        private final Boolean rm
        private final long timeout
        private final String username
        private final String password
        private final String email
        private final List<String> ignorePatterns

        BuildImage(Kubernetes kubernetes, String name, Boolean rm, long timeout, String username, String password, String email, List<String> ignorePatterns) {
            this.kubernetes = kubernetes
            this.name = name
            this.rm = rm
            this.timeout = timeout
            this.username = username
            this.password = password
            this.email = email
            this.ignorePatterns = ignorePatterns != null ? ignorePatterns : new ArrayList<String>()
        }

        BuildImage removingIntermediate() {
            return removingIntermediate(true)
        }

        BuildImage removingIntermediate(Boolean rm) {
            return new BuildImage(kubernetes, name, rm, timeout, username, password, email, ignorePatterns)
        }

        BuildImage withTimeout(long timeout) {
            return new BuildImage(kubernetes, name, rm, timeout, username, password, email, ignorePatterns)
        }

        BuildImage withUsername(String username) {
            return new BuildImage(kubernetes, name, rm, timeout, username, password, email, ignorePatterns)
        }

        BuildImage withPassword(String password) {
            return new BuildImage(kubernetes, name, rm, timeout, username, password, email, ignorePatterns)
        }

        BuildImage withEmail(String email) {
            return new BuildImage(kubernetes, name, rm, timeout, username, password, email, ignorePatterns)
        }

        BuildImage ignoringPattern(String pattern) {
            List<String> newIgnorePatterns = new ArrayList<>(ignorePatterns)
            newIgnorePatterns.add(pattern)
            return new BuildImage(kubernetes, name, rm, timeout, username, password, email, newIgnorePatterns)
        }

        Pod fromPath(String path) {
            kubernetes.node {
                kubernetes.script.buildImage(name: name, rm: rm, path: path, timeout: timeout, username: username, password: password, email: email, ignorePatterns: ignorePatterns)
            }
            return new NamedImage(kubernetes, name).toPod()
        }
    }

    private static class TagImage implements Serializable {
        private final Kubernetes kubernetes
        private final String name
        private final String repo
        private final String tagName
        private final Boolean force
        private final String username
        private final String password
        private final String email

        TagImage(Kubernetes kubernetes, String name, String repo, String tagName, Boolean force, String username, String password, String email) {
            this.kubernetes = kubernetes
            this.name = name
            this.repo = repo
            this.tagName = tagName
            this.force = force
            this.username = username
            this.password = password
            this.email = email
        }

        TagImage inRepository(String repo) {
            return new TagImage(kubernetes, name, repo, tagName, force, username, password, email)
        }

        TagImage force() {
            return new TagImage(kubernetes, name, repo, tagName, true, username, password, email)
        }

        TagImage withUsername(String username) {
            return new TagImage(kubernetes, name, repo, tagName, force, username, password, email)
        }

        TagImage withPassword(String password) {
            return new TagImage(kubernetes, name, repo, tagName, force, username, password, email)
        }

        TagImage withEmail(String email) {
            return new TagImage(kubernetes, name, repo, tagName, force, username, password, email)
        }

        void withTag(String tagName) {
            kubernetes.node {
                kubernetes.script.tagImage(name: name, repo: repo, tag: tagName, force: force, username: username, password: password, email: email)
            }
        }
    }

    private static class PushImage implements Serializable {
        private final Kubernetes kubernetes
        private final String name
        private final String tagName
        private final long timeout
        private final String username
        private final String password
        private final String email



        PushImage(Kubernetes kubernetes, String name, String tagName, long timeout, String username, String password, String email) {
            this.kubernetes = kubernetes
            this.name = name
            this.tagName = tagName
            this.timeout = timeout
            this.username = username
            this.password = password
            this.email = email
        }

        PushImage force() {
            return new PushImage(kubernetes, name, tagName, timeout, username, password, email)
        }

        PushImage withTag(String tagName) {
            return new PushImage(kubernetes, name, tagName, timeout, username, password, email)
        }

        PushImage withTimeout(long timeout) {
            return new PushImage(kubernetes, name, tagName, timeout, username, password, email)
        }

        PushImage withUsername(String username) {
            return new PushImage(kubernetes, name, tagName, timeout, username, password, email)
        }

        PushImage withPassword(String password) {
            return new PushImage(kubernetes, name, tagName, timeout, username, password, email)
        }

        PushImage withEmail(String email) {
            return new PushImage(kubernetes, name, tagName, timeout, username, password, email)
        }

        void toRegistry(String registry) {
            kubernetes.node {
                kubernetes.script.pushImage(name: name, tagName: tagName, timeout: timeout, registry: registry, username: username, password: password, email: email)
            }
        }

        void toRegistry() {
            toRegistry('')
        }
    }
}
