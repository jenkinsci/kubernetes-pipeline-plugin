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

    public Kubernetes(org.jenkinsci.plugins.workflow.cps.CpsScript script) {
        this.script = script
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

    public Pod pod(String name) {
        return new Pod(this, name, null, null, false, new HashMap<String, String>(), new HashMap<String, String>(), new HashMap<String, String>());
    }

    public static class Pod implements Serializable {
        private final Kubernetes kubernetes;
        private final String name;
        private final String image;
        private final String serviceAccount;
        private final Boolean privileged;
        private final Map secrets;
        private final Map hostPathMounts;
        private final Map env;

        Pod(Kubernetes kubernetes, String name, String image, String serviceAccount, Boolean privileged, Map<String, String> secrets, Map<String, String> hostPathMounts, Map<String, String> env) {
            this.kubernetes = kubernetes
            this.name = name
            this.image = image
            this.serviceAccount = serviceAccount
            this.privileged = privileged
            this.secrets = secrets
            this.hostPathMounts = hostPathMounts
            this.env = env;
        }

        public Pod withName(String name) {
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, hostPathMounts, env);
        }

        public Pod withImage(String image) {
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, hostPathMounts, env);
        }

        public Pod withServiceAccount(String serviceAccount) {
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, hostPathMounts, env);
        }

        public Pod withPrivileged(Boolean privileged) {
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, hostPathMounts, env);
        }

        public Pod withSecret(String secretName, String mountPath) {
            Map<String, String> newSecrets = new HashMap<>(secrets);
            newSecrets.put(secretName, mountPath);
            return new Pod(kubernetes, name, image, serviceAccount, privileged, newSecrets, hostPathMounts, env);
        }

        public Pod withHostPathMount(String hostPath, String mountPath) {
            Map<String, String> newHostPathMounts = new HashMap<>(secrets);
            newHostPathMounts.put(hostPath, mountPath);
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, newHostPathMounts, env);
        }


        public Pod withEnvVar(String key, String value) {
            Map<String, String> newEnv = new HashMap<>(secrets);
            newEnv.put(key, value);
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets, hostPathMounts, newEnv);
        }

        public <V> V inside(Closure<V> body) {
            kubernetes.node {
                kubernetes.script.withKubernetesPod(name: name, image: image, serviceAccount: serviceAccount, privileged: privileged, secrets: secrets, hostPathMounts: hostPathMounts, env: env) {
                    body()
                }
            }
        }
    }
}
