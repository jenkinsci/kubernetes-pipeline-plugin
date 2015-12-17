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
        return new Pod(this, name, null, null, false, new HashMap<String, String>());
    }

    public static class Pod implements Serializable {
        private final Kubernetes kubernetes;
        private final String name;
        private final String image;
        private final String serviceAccount;
        private final Boolean privileged;
        private final Map secrets;

        Pod(Kubernetes kubernetes, String name, String image, String serviceAccount, Boolean privileged, Map<String, String> secrets) {
            this.kubernetes = kubernetes
            this.name = name
            this.image = image
            this.serviceAccount = serviceAccount
            this.privileged = privileged
            this.secrets = secrets
        }

        public Pod withName(String name) {
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets);
        }

        public Pod withImage(String image) {
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets);
        }

        public Pod withServiceAccount() {
            return new Pod(kubernetes, name, image, serviceAccount, privileged, secrets);
        }

        public Pod withPrivileged() {
            return new Pod(kubernetes, name, image, serviceAccount, true, secrets);
        }

        public Pod withSecret(String secretName, String mountPath) {
            Map<String, String> newSecrets = new HashMap<>(secrets);
            newSecrets.put(secretName, mountPath);
            return new Pod(kubernetes, name, image, serviceAccount, privileged, newSecrets);
        }

        public <V> V inside(Closure<V> body) {
            kubernetes.node {
                kubernetes.script.withKubernetesPod(name: name, image: image, serviceAccount: serviceAccount, privileged: privileged, secrets: secrets) {
                    body()
                }
            }
        }
    }
}
