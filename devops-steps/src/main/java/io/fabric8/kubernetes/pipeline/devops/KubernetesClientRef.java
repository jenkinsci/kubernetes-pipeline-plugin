package io.fabric8.kubernetes.pipeline.devops;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

public class KubernetesClientRef {

    private static KubernetesClient kubernetesClient;

    public static synchronized KubernetesClient get() {
        if (kubernetesClient == null) {
            kubernetesClient = create();
        }
        return kubernetesClient;
    }

    public static synchronized void close() {
        if (kubernetesClient == null) {
            return;
        }
        kubernetesClient.close();
        kubernetesClient = null;
    }


    public static synchronized KubernetesClient create() {
        return new DefaultKubernetesClient();
    }

}
