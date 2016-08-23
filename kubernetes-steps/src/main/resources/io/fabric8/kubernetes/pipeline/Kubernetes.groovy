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

    public Pod pod(String name = "jenkins-pod", String image = "", String serviceAccount = "", Boolean privileged = false, Map<String, String> secrets = new HashMap(), Map<String, String> hostPaths = new HashMap(), Map<String, String> emptyDirs = new HashMap<>(), Map<String, String> env = new HashMap<>()) {
        return new Pod(this, name, image, serviceAccount, privileged, secrets, hostPaths, emptyDirs, env)
    }

    public Image image() {
        return new Image(this)
    }

    public Image image(String name) {
        return new NamedImage(this, name)
    }

    public static class Pod implements Serializable {
        private final Kubernetes kubernetes
        private final String name
        private final String image
        private final String serviceAccount
        private final Boolean privileged
        private final Map secrets
        private final Map hostPathMounts
        private final Map emptyDirs
        private final Map env

        Pod(Kubernetes kubernetes, String name, String image, String serviceAccount, Boolean privileged, Map<String, String> secrets, Map<String, String> hostPathMounts, Map<String, String> emptyDirs, Map<String, String> env) {
            this.kubernetes = kubernetes
            this.name = name
            this.image = image
            this.serviceAccount = serviceAccount
            this.privileged = privileged
            this.secrets = secrets
            this.hostPathMounts = hostPathMounts
            this.emptyDirs = emptyDirs
            this.env = env
        }

        public Pod withName(String name) {
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, hostPathMounts, emptyDirs, env)
        }

        public Pod withImage(String image) {
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, hostPathMounts, emptyDirs, env)
        }

        public Pod withServiceAccount(String serviceAccount) {
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, hostPathMounts, emptyDirs, env)
        }

        public Pod withPrivileged(Boolean privileged) {
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, hostPathMounts, emptyDirs, env)
        }

        public Pod withSecret(String secretName, String mountPath) {
            Map<String, String> newSecrets = new HashMap<>(secrets)
            newSecrets.put(secretName, mountPath)
            return new Pod(kubernetes, name, image, serviceAccount, privileged, newSecrets, hostPathMounts, emptyDirs, env)
        }

        public Pod withHostPathMount(String hostPath, String mountPath) {
            Map<String, String> newHostPathMounts = new HashMap<>(hostPathMounts)
            newHostPathMounts.put(hostPath, mountPath)
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, newHostPathMounts, emptyDirs, env)
        }

        public Pod withEmptyDir(String mountPath) {
            return withEmptyDir(mountPath, null)
        }

        public Pod withEmptyDir(String mountPath, String medium) {
            Map<String, String> newEmptyDirs = new HashMap<>(emptyDirs)
            newEmptyDirs.put(mountPath, medium)
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, hostPathMounts, newEmptyDirs, env)
        }

        public Pod withEnvVar(String key, String value) {
            Map<String, String> newEnv = new HashMap<>(env)
            newEnv.put(key, value)
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, hostPathMounts, emptyDirs, newEnv)
        }

        public <V> V inside(Closure<V> body) {
            kubernetes.node {
                kubernetes.script.withPod(name: name, image: image, serviceAccount: serviceAccount, privileged: privileged, secrets: secrets, hostPathMounts: hostPathMounts, emptyDirs: emptyDirs, env: env) {
                    body()
                }
            }
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
            return new Pod(kubernetes, "jenkins-buildpod", name, "", false, new HashMap<String, String>(), new HashMap<String, String>(), new HashMap<String, String>(), new HashMap<String, String>())
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
