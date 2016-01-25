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

package io.fabric8.kubernetes.workflow;

import hudson.FilePath;
import hudson.model.TaskListener;
import io.fabric8.docker.client.DefaultDockerClient;
import io.fabric8.docker.client.DockerClient;
import io.fabric8.docker.dsl.EventListener;
import io.fabric8.docker.dsl.OutputHandle;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PushImageStepExecution extends AbstractSynchronousStepExecution<Void> {

    @Inject
    private transient PushImageStep step;

    @StepContextParameter private transient FilePath workspace;
    @StepContextParameter private transient TaskListener listener;

    @Override
    protected Void run() throws Exception {
        final CountDownLatch pushFinished = new CountDownLatch(1);
        OutputHandle handle = null;
        try (DockerClient client = new DefaultDockerClient()) {
            handle = client.image().withName(step.getName())
                    .push()
                    .usingListener(new EventListener() {
                        @Override
                        public void onSuccess(String s) {
                            listener.getLogger().println(s);
                            pushFinished.countDown();
                        }

                        @Override
                        public void onError(String s) {
                            listener.error(s);
                            pushFinished.countDown();
                        }

                        @Override
                        public void onEvent(String s) {
                            listener.getLogger().println(s);
                        }
                    })
                    .withTag(step.getTagName())
                    .toRegistry();
            if (!pushFinished.await(step.getTimeout(), TimeUnit.MINUTES)) {
                listener.getLogger().println("Timed out (" + step.getTimeout()+"ms)pushing docker image.");
            }
        } finally {
            if (handle != null) {
                handle.close();
            }
        }
        return null;
    }
}
