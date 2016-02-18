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
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.util.Set;

public class BuildImageStep extends AbstractDockerStep implements Serializable {

    private static final long serialVersionUID = 1851294902925088301L;

    private Boolean rm;
    private String path;
    private long timeout = 600000L;
    private Item[] ignorePatterns;

    @DataBoundConstructor
    public BuildImageStep(String name) {
        super(name);
    }

    public Boolean getRm() {
        return rm;
    }

    @DataBoundSetter
    public void setRm(Boolean rm) {
        this.rm = rm;
    }

    public String getPath() {
        return path;
    }

    @DataBoundSetter
    public void setPath(String path) {
        this.path = path;
    }

    public long getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public Item[] getIgnorePatterns() {
        return ignorePatterns;
    }

    @DataBoundSetter
    public void setIgnorePatterns(Item[] ignorePatterns) {
        this.ignorePatterns = ignorePatterns;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

       public DescriptorImpl() {
            super(BuildImageStepExecution.class);
        }

        public DescriptorImpl(Class<? extends StepExecution> executionType) {
            super(executionType);
        }

        @Override
        public String getFunctionName() {
            return "buildImage";
        }

        @Override
        public String getDisplayName() {
            return "Builds a Docker Image";
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
