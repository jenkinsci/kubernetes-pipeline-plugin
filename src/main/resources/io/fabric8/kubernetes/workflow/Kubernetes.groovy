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
        new Pod(this, name)
    }

    public static class Pod implements Serializable {

        private final Kubernetes kubernetes;
        private final String name;

        Pod(Kubernetes kubernetes, String name) {
            this.kubernetes = kubernetes
            this.name = name;
        }

        public <V> V withImage(String image, Closure<V> body) {
            kubernetes.node {
                kubernetes.script.withKubernetesPod(image: image, podName:name) {
                    body()
                }
            }
        }
    }
}
