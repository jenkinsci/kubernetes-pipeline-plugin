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

import com.google.inject.Inject;
import hudson.FilePath;
import hudson.model.TaskListener;
import io.fabric8.docker.client.DefaultDockerClient;
import io.fabric8.docker.client.DockerClient;
import jenkins.security.MasterToSlaveCallable;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

public class TagImageStepExecution extends AbstractSynchronousStepExecution<Boolean> {

    @Inject
    private TagImageStep step;

    @StepContextParameter
    private TaskListener listener;

    @StepContextParameter
    private FilePath workspace;

    @Override
    protected Boolean run() throws Exception {
        return workspace.getChannel().call(new MasterToSlaveCallable<Boolean, Exception>() {
            @Override
            public Boolean call() throws Exception {
                try (DockerClient client = new DefaultDockerClient(step.getDockerConfig())) {
                    listener.getLogger().println("Tagging image:" + step.getName() + " with tag:" + step.getTag() + ".");
                        return client.image()
                                .withName(step.getName()).tag()
                                .inRepository(step.getRepo())
                                .force(step.getForce())
                                .withTagName(step.getTag());
                }
            }
        });
    }
}
