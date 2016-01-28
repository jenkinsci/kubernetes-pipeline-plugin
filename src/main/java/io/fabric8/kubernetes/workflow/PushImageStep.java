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

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

public class PushImageStep extends AbstractDockerStep implements Serializable {

    private static final long serialVersionUID = -6633237919456764764L;

    private final String tagName;
    private final Boolean force;
    private final long timeout;

    @DataBoundConstructor
    public PushImageStep(String name, String tagName, Boolean force, long timeout, String username, String password, String email) {
        super(name, username, password, email);
        this.tagName = tagName;
        this.force = force;
        this.timeout = timeout;
    }

    public String getTagName() {
        return tagName;
    }

    public Boolean getForce() {
        return force;
    }

    public long getTimeout() {
        return timeout;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

       public DescriptorImpl() {
            super(PushImageStepExecution.class);
        }

        public DescriptorImpl(Class<? extends StepExecution> executionType) {
            super(executionType);
        }

        @Override
        public String getFunctionName() {
            return "pushImage";
        }

        @Override
        public String getDisplayName() {
            return "Pushes a Docker Image";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }
    }
}
