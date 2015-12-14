package io.fabric8.kubernetes.workflow;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class Utils {

    private static final String RUNNING_PHASE = "Running";
    private static final String SUCCEEDED_PHASE = "Succeeded";
    private static final String FAILED_PHASE = "Failed";

    public static final boolean isPodRunning(Pod pod) {
        return pod != null && pod.getStatus() != null && RUNNING_PHASE.equals(pod.getStatus().getPhase());
    }

    public static final boolean isPodCompleted(Pod pod) {
        return pod != null && pod.getStatus() != null &&
                (SUCCEEDED_PHASE.equals(pod.getStatus().getPhase()) || FAILED_PHASE.equals(pod.getStatus().getPhase()));
    }
}
