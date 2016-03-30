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

package io.fabric8.workflow.devops;

import hudson.Extension;
import io.fabric8.workflow.devops.CreateEventStepExecution;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

public class ApproveRequestedEventStep extends AbstractStepImpl {

    private final String app;
    private final String environment;

    @DataBoundConstructor
    public ApproveRequestedEventStep(String app, String environment){
        this.app = app;
        this.environment = environment;
    }

    public String getApp() {
        return app;
    }

    public String getEnvironment() {
        return environment;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(ApproveRequestedEventStepExecution.class);
        }

        public DescriptorImpl(Class<? extends StepExecution> executionType) {
            super(executionType);
        }

        @Override
        public String getFunctionName() {
            return "approveRequestedEvent";
        }

        @Override
        public String getDisplayName() {
            return "Creates an Approve requested event in Elasticsearch";
        }
    }
}
