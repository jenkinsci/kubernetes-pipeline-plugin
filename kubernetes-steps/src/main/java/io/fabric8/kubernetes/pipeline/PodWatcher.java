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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Status;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fabric8.kubernetes.pipeline.KubernetesFacade.isPodCompleted;
import static io.fabric8.kubernetes.pipeline.KubernetesFacade.isPodRunning;

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
                //ignore
            }
        }
    }
}
