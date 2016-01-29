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
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class PushImageStepExecution extends AbstractSynchronousStepExecution<Void> {

    @Inject
    private PushImageStep step;

    @StepContextParameter private FilePath workspace;
    @StepContextParameter private TaskListener listener;

    @Override
    protected Void run() throws Exception {
        return workspace.getChannel().call(new MasterToSlaveCallable<Void, Exception>() {
            @Override
            public Void call() throws Exception {
                OutputHandle handle = null;
                final BlockingQueue queue = new LinkedBlockingQueue();
                try (DockerClient client = new DefaultDockerClient(step.getDockerConfig())) {
                    listener.getLogger().println("Pushing image:" + step.getName() + " to docker registry.");
                    handle = client.image().withName(step.getName())
                            .push()
                            .usingListener(new EventListener() {
                                @Override
                                public void onSuccess(String s) {
                                    listener.getLogger().println(s);
                                    queue.add(true);
                                }

                                @Override
                                public void onError(String s) {
                                    listener.error(s);
                                    queue.add(new RuntimeException("Failed to push image. Error:" + s));
                                }

                                @Override
                                public void onEvent(String s) {
                                    listener.getLogger().println(s);
                                }
                            })
                            .withTag(step.getTag())
                            .toRegistry();

                    Object result = queue.poll(step.getTimeout(), TimeUnit.MILLISECONDS);
                    if (result == null) {
                        throw new RuntimeException("Timed out (" + step.getTimeout() + "ms)pushing docker image.");
                    } else if (result instanceof Throwable) {
                        throw new RuntimeException((Throwable) result);
                    }
                } finally {
                    if (handle != null) {
                        handle.close();
                    }
                }
                return null;
            }
        });
    }
}
