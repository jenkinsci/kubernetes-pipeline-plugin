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

package io.fabric8.kubernetes.workflow

class Kubernetes implements Serializable {

    private org.jenkinsci.plugins.workflow.cps.CpsScript script

    public final image

    public Kubernetes(org.jenkinsci.plugins.workflow.cps.CpsScript script) {
        this.script = script
        this.image = new Image(this)
    }

    private <V> V node(Closure<V> body) {
        if (script.env.HOME != null) { // http://unix.stackexchange.com/a/123859/26736
            // Already inside a node block.
            body()
        } else {
            script.node {
                body()
            }
        }
    }

    public Pod pod(String name = "jenkins-pod", String image = "", String serviceAccount = "", Boolean privileged = false, Map<String, String> secrets = new HashMap(), Map<String, String> hostPaths = new HashMap(), Map<String, String> emptyDirs = new HashMap(), Map<String, String> env = new HashMap<>()) {
        return new Pod(this, name, image, serviceAccount, privileged, secrets, hostPaths, emptyDirs, env);
    }

    public Image image() {
        return new Image(this);
    }

    public Image image(String name) {
        return new NamedImage(this, name);
    }

    public static class Pod implements Serializable {
        private final Kubernetes kubernetes;
        private final String name;
        private final String image;
        private final String serviceAccount;
        private final Boolean privileged;
        private final Map secrets;
        private final Map hostPathMounts;
        private final Map emptyDirs;
        private final Map env;

        Pod(Kubernetes kubernetes, String name, String image, String serviceAccount, Boolean privileged, Map<String, String> secrets, Map<String, String> hostPathMounts, Map<String, String> emptyDirs, Map<String, String> env) {
            this.kubernetes = kubernetes
            this.name = name
            this.image = image
            this.serviceAccount = serviceAccount
            this.privileged = privileged
            this.secrets = secrets
            this.hostPathMounts = hostPathMounts
            this.emptyDirs = emptyDirs
            this.env = env;
        }

        public Pod withName(String name) {
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, hostPathMounts, emptyDirs, env);
        }

        public Pod withImage(String image) {
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, hostPathMounts, emptyDirs, env);
        }

        public Pod withServiceAccount(String serviceAccount) {
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, hostPathMounts, emptyDirs, env);
        }

        public Pod withPrivileged(Boolean privileged) {
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, hostPathMounts, emptyDirs, env);
        }

        public Pod withSecret(String secretName, String mountPath) {
            Map<String, String> newSecrets = new HashMap<>(secrets);
            newSecrets.put(secretName, mountPath);
            return new Pod(kubernetes, name, image, serviceAccount, privileged, newSecrets, hostPathMounts, emptyDirs, env);
        }

        public Pod withHostPathMount(String hostPath, String mountPath) {
            Map<String, String> newHostPathMounts = new HashMap<>(hostPathMounts);
            newHostPathMounts.put(hostPath, mountPath);
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, newHostPathMounts, emptyDirs, env);
        }

        public Pod withEmptyDir(String mountPath) {
            return withEmptyDir(mountPath, null);
        }

        public Pod withEmptyDir(String mountPath, String medium) {
            Set<String> newEmptyDirs = new HashSet<>(emptyDirs);
            newEmptyDirs.put(emptyDir, medium);
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, hostPathMounts, newEmptyDirs, env);
        }

        public Pod withEnvVar(String key, String value) {
            Map<String, String> newEnv = new HashMap<>(secrets);
            newEnv.put(key, value);
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, hostPathMounts, emptyDirs, newEnv);
        }

        public <V> V inside(Closure<V> body) {
            kubernetes.node {
                kubernetes.script.withKubernetesPod(name: name, image: image, serviceAccount: serviceAccount, privileged: privileged, secrets: secrets, hostPathMounts: hostPathMounts, emptyDirs: emptyDirs, env: env) {
                    body()
                }
            }
        }
    }

    public class Image implements Serializable {

        private final Kubernetes kubernetes;

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
            return new NamedImage(kubernetes, name);
        }
     }

    private static class NamedImage implements Serializable {

        private final Kubernetes kubernetes;
        private final String name;

        NamedImage(Kubernetes kubernetes, String name) {
            this.kubernetes = kubernetes
            this.name = name
        }

        Pod toPod() {
            return new Pod(kubernetes, "jenkins-buildpod", name, "", false, new HashMap<String, String>(), new HashMap<String, String>(), new HashMap<String, String>(), new HashMap<String, String>())
        }


        BuildImage build() {
            return new BuildImage(kubernetes, name, false, 600000L, null, null, null);
        }

        PushImage push() {
            return new PushImage(kubernetes, name, null, false, 600000L, null, null, null);
        }

        void tag() {
            new TagImage(kubernetes, name, null, null, null, null, null);
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

        BuildImage(Kubernetes kubernetes, String name, Boolean rm, long timeout, String username, String password, String email) {
            this.kubernetes = kubernetes
            this.name = name
            this.rm = rm
            this.timeout = timeout
            this.username = username
            this.password = password
            this.email = email
        }

        BuildImage removingIntermediate() {
            return removingIntermediate(true)
        }

        BuildImage removingIntermediate(Boolean rm) {
            return new BuildImage(kubernetes, name, rm, timeout, username, password, email);
        }

        BuildImage withTimeout(long timeout) {
            return new BuildImage(kubernetes, name, rm, timeout, username, password, email);
        }

        BuildImage withUsername(String username) {
            return new BuildImage(kubernetes, name, rm, timeout, username, password, email);
        }

        BuildImage withPassword(String password) {
            return new BuildImage(kubernetes, name, rm, timeout, username, password, email);
        }

        BuildImage withEmail(String email) {
            return new BuildImage(kubernetes, name, rm, timeout, username, password, email);
        }

        Pod fromPath(String path) {
            kubernetes.node {
                kubernetes.script.buildImage(name: name, rm: rm, path: path, timeout: timeout, username: username, password: password, email: email);
            }
            return new NamedImage(kubernetes, name).toPod()
        }
    }

    private static class TagImage implements Serializable {
        private final Kubernetes kubernetes;
        private final String name;
        private final String repo;
        private final String tagName;
        private final String username
        private final String password
        private final String email

        TagImage(Kubernetes kubernetes, String name, String repo, String tagName, String username, String password, String email) {
            this.kubernetes = kubernetes
            this.name = name
            this.repo = repo
            this.tagName = tagName
            this.username = username
            this.password = password
            this.email = email
        }

        TagImage inRepository(String repo) {
            return new TagImage(kubernetes, name, repo, tagName, username, password, email);
        }

        TagImage withUsername(String username) {
            return new TagImage(kubernetes, name, repo, tagName, username, password, email);
        }

        TagImage withPassword(String password) {
            return new TagImage(kubernetes, name, repo, tagName, username, password, email);
        }

        TagImage withEmail(String email) {
            return new TagImage(kubernetes, name, repo, tagName, username, password, email);
        }

        void withTag(String tagName) {
            kubernetes.node {
                kubernetes.script.tagImage(name: name, repo: repo, tag: tagName, username: username, password: password, email: email);
            }
        }
    }

    private static class PushImage implements Serializable {
        private final Kubernetes kubernetes;
        private final String name;
        private final String tagName;
        private final Boolean force;
        private final long timeout;
        private final String username
        private final String password
        private final String email



        PushImage(Kubernetes kubernetes, String name, String tagName, Boolean force, long timeout, String username, String password, String email) {
            this.kubernetes = kubernetes
            this.name = name
            this.tagName = tagName
            this.force = force
            this.timeout = timeout
            this.username = username
            this.password = password
            this.email = email
        }

        PushImage force() {
            return new PushImage(kubernetes, name, tagName, true, timeout, username, password, email);
        }

        PushImage withTag(String tagName) {
            return new PushImage(kubernetes, name, tagName, force, timeout, username, password, email);
        }

        PushImage withTimeout(long timeout) {
            return new PushImage(kubernetes, name, tagName, force, timeout, username, password, email);
        }

        PushImage withUsername(String username) {
            return new PushImage(kubernetes, name, tagName, force, timeout, username, password, email);
        }

        PushImage withPassword(String password) {
            return new PushImage(kubernetes, name, tagName, force, timeout, username, password, email);
        }

        PushImage withEmail(String email) {
            return new PushImage(kubernetes, name, tagName, force, timeout, username, password, email);
        }

        void toRegistry() {
            kubernetes.node {
                kubernetes.script.pushImage(name: name, force: force, tagName: tagName, timeout: timeout, username: username, password: password, email: email);
            }
        }
    }

    public String apply(String file, String environment) {
        return new Apply(this, file, environment, true, false, false, false, false, false, true, true);
    }

    public static class Apply implements Serializable {
        private final Kubernetes kubernetes;
        private final String file;
        private final String environment;
        private final Boolean createNewResources;
        private final Boolean servicesOnly;
        private final Boolean ignoreServices;
        private final Boolean ignoreRunningOAuthClients;
        private final Boolean processTemplatesLocally;
        private final Boolean deletePodsOnReplicationControllerUpdate;
        private final Boolean rollingUpgrades;
        private final Boolean rollingUpgradePreserveScale;

        Apply(Kubernetes kubernetes, String file, String environment, Boolean createNewResources, Boolean servicesOnly, Boolean ignoreServices, Boolean ignoreRunningOAuthClients, Boolean processTemplatesLocally, Boolean deletePodsOnReplicationControllerUpdate, Boolean rollingUpgrades, Boolean rollingUpgradePreserveScale){
            this.kubernetes = kubernetes
            this.file = file;
            this.environment = environment;
            this.createNewResources = createNewResources;
            this.servicesOnly = servicesOnly;
            this.ignoreServices = ignoreServices;
            this.ignoreRunningOAuthClients = ignoreRunningOAuthClients;
            this.processTemplatesLocally = processTemplatesLocally;
            this.deletePodsOnReplicationControllerUpdate = deletePodsOnReplicationControllerUpdate;
            this.rollingUpgrades = rollingUpgrades;
            this.rollingUpgradePreserveScale = rollingUpgradePreserveScale;

        }

        /**
         * In services only mode we only process services so that those can be recursively created/updated first
         * before creating/updating any pods and replication controllers
         */
        public Apply withEnvironment(String environment) {
            return new Apply(kubernetes, file, environment,createNewResources, servicesOnly, ignoreServices, ignoreRunningOAuthClients, processTemplatesLocally, deletePodsOnReplicationControllerUpdate, rollingUpgrades, rollingUpgradePreserveScale);
        }


        /**
         * Should we create new kubernetes resources?
         */
        public Apply withCreateNewResources(Boolean createNewResources) {
            return new Apply(kubernetes, file, environment,createNewResources, servicesOnly, ignoreServices, ignoreRunningOAuthClients, processTemplatesLocally, deletePodsOnReplicationControllerUpdate, rollingUpgrades, rollingUpgradePreserveScale);
        }

        /**
         * In services only mode we only process services so that those can be recursively created/updated first
         * before creating/updating any pods and replication controllers
         */
        public Apply withServicesOnly(Boolean servicesOnly) {
            return new Apply(kubernetes, file, environment,createNewResources, servicesOnly, ignoreServices, ignoreRunningOAuthClients, processTemplatesLocally, deletePodsOnReplicationControllerUpdate, rollingUpgrades, rollingUpgradePreserveScale);
        }

        /**
         * Do we want to ignore services. This is particularly useful when in recreate mode
         * to let you easily recreate all the ReplicationControllers and Pods but leave any service
         * definitions alone to avoid changing the portalIP addresses and breaking existing pods using
         * the service.
         */
        public Apply withIgnoreServices(Boolean ignoreServices) {
            return new Apply(kubernetes, file, environment,createNewResources, servicesOnly, ignoreServices, ignoreRunningOAuthClients, processTemplatesLocally, deletePodsOnReplicationControllerUpdate, rollingUpgrades, rollingUpgradePreserveScale);
        }

        /**
         * Do we want to ignore OAuthClients which are already running?. OAuthClients are shared across namespaces
         * so we should not try to update or create/delete global oauth clients
         */
        public Apply withIgnoreRunningOAuthClients(Boolean ignoreRunningOAuthClients) {
            return new Apply(kubernetes, file, environment,createNewResources, servicesOnly, ignoreServices, ignoreRunningOAuthClients, processTemplatesLocally, deletePodsOnReplicationControllerUpdate, rollingUpgrades, rollingUpgradePreserveScale);
        }

        /**
         * Process templates locally in Java so that we can apply OpenShift templates on any Kubernetes environment
         */
        public Apply withProcessTemplatesLocally(Boolean processTemplatesLocally) {
            return new Apply(kubernetes, file, environment,createNewResources, servicesOnly, ignoreServices, ignoreRunningOAuthClients, processTemplatesLocally, deletePodsOnReplicationControllerUpdate, rollingUpgrades, rollingUpgradePreserveScale);
        }

        /**
         * Should we delete all the pods if we update a Replication Controller
         */
        public Apply withDeletePodsOnReplicationControllerUpdate(Boolean deletePodsOnReplicationControllerUpdate) {
            return new Apply(kubernetes, file, environment,createNewResources, servicesOnly, ignoreServices, ignoreRunningOAuthClients, processTemplatesLocally, deletePodsOnReplicationControllerUpdate, rollingUpgrades, rollingUpgradePreserveScale);
        }

        /**
         * Should we use rolling upgrades to apply changes?
         */
        public Apply withRollingUpgrades(Boolean rollingUpgrades) {
            return new Apply(kubernetes, file, environment,createNewResources, servicesOnly, ignoreServices, ignoreRunningOAuthClients, processTemplatesLocally, deletePodsOnReplicationControllerUpdate, rollingUpgrades, rollingUpgradePreserveScale);
        }

        public Apply withRollingUpgradePreserveScale(Boolean rollingUpgradePreserveScale) {
            return new Apply(kubernetes, file, environment,createNewResources, servicesOnly, ignoreServices, ignoreRunningOAuthClients, processTemplatesLocally, deletePodsOnReplicationControllerUpdate, rollingUpgrades, rollingUpgradePreserveScale);
        }
        public void apply() {
            kubernetes.node {
                kubernetes.script.kubernetesApply(file: file, environment: environment, createNewResources: createNewResources, servicesOnly: servicesOnly, ignoreServices: ignoreServices, ignoreRunningOAuthClients: ignoreRunningOAuthClients, processTemplatesLocally: processTemplatesLocally, deletePodsOnReplicationControllerUpdate: deletePodsOnReplicationControllerUpdate, rollingUpgrades: rollingUpgrades, rollingUpgradePreserveScale: rollingUpgradePreserveScale)
            }
        }
    }
}
