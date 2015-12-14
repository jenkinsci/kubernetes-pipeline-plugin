package io.fabric8.kubernetes.workflow;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fabric8.kubernetes.workflow.KubernetesFacade.isPodCompleted;
import static io.fabric8.kubernetes.workflow.KubernetesFacade.isPodRunning;

public class PodWatcher implements Watcher<Pod> {

    private final AtomicBoolean alive;
    private final CountDownLatch started;
    private final CountDownLatch finisied;
    private final Callable<Void> onCompletion;

    public PodWatcher(AtomicBoolean alive, CountDownLatch running, CountDownLatch finisied, Callable<Void> onCompletion) {
        this.alive = alive;
        this.started = running;
        this.finisied = finisied;
        this.onCompletion = onCompletion;
    }

    @Override
    public void eventReceived(Action action, Pod pod) {
        if (isPodRunning(pod)) {
            alive.set(true);
            started.countDown();
        }

        switch (action) {
            case ADDED:
            case MODIFIED:
            case ERROR:
                if (isPodCompleted(pod)) {
                    finisied.countDown();
                    callCompletionCallback();
                }
                break;
            case DELETED:
                finisied.countDown();
        }
    }

    @Override
    public void onClose(KubernetesClientException e) {
        finisied.countDown();
        callCompletionCallback();
    }

    private void callCompletionCallback() {
        if (onCompletion != null) {
            try {
                onCompletion.call();
            } catch (Throwable t) {
                //ingore
            }
        }
    }
}
