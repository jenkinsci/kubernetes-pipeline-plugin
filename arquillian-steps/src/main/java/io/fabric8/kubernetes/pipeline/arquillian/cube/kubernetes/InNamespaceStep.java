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

package io.fabric8.kubernetes.pipeline.arquillian.cube.kubernetes;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.Map;

import hudson.Extension;

public class InNamespaceStep extends AbstractStep implements Serializable {

    private static final long serialVersionUID = 5588861066775717487L;

    private final String name;
    private final String prefix;

    private final Map<String, String> labels;
    private final Map<String, String> annotations;

    private final Boolean namespaceLazyCreateEnabled;
    private final Boolean namespaceDestroyEnabled;

    @DataBoundConstructor
    public InNamespaceStep(String cloud, String name, String prefix, Map<String, String> labels, Map<String, String> annotations, Boolean namespaceLazyCreateEnabled, Boolean namespaceDestroyEnabled) {
        super(cloud);
        this.name = name;
        this.prefix = prefix;
        this.labels = labels;
        this.annotations = annotations;

        this.namespaceLazyCreateEnabled = namespaceLazyCreateEnabled != null ? namespaceLazyCreateEnabled : true;
        this.namespaceDestroyEnabled = namespaceDestroyEnabled != null ? namespaceDestroyEnabled : true;
    }

    public String getName() {
        return name;
    }

    public String getPrefix() {
        return prefix;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public Map<String, String> getAnnotations() {
        return annotations;
    }

    public Boolean isNamespaceLazyCreateEnabled() {
        return namespaceLazyCreateEnabled;
    }

    public Boolean isNamespaceDestroyEnabled() {
        return namespaceDestroyEnabled;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new InNamespaceStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

       public DescriptorImpl() {
            super(InNamespaceStepExecution.class);
        }

        public DescriptorImpl(Class<? extends StepExecution> executionType) {
            super(executionType);
        }

        @Override
        public String getFunctionName() {
            return "inNamespace";
        }

        @Override
        public String getDisplayName() {
            return "Run build steps inside an arquillian cube kubernetes managed namespace";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }
    }
}
