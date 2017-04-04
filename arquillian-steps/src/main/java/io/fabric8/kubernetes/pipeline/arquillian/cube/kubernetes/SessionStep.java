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
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.net.URL;
import java.util.List;
import java.util.Map;

import hudson.Extension;

public class SessionStep extends AbstractSessionManagerStep implements Serializable {

    private static final long serialVersionUID = 5588861066775717487L;


    @DataBoundConstructor
    public SessionStep(String cloud, String name, String prefix, Map<String, String> labels, Map<String, String> annotations, URL environmentSetupScriptUrl, URL environmentTeardownScriptUrl, URL environmentConfigUrl, List<URL> environmentDependencies, Long waitTimeout, List<String> waitForServiceList, Boolean namespaceLazyCreateEnabled, Boolean namespaceDestroyEnabled) {
       super(cloud, name, prefix, labels, annotations, environmentSetupScriptUrl, environmentTeardownScriptUrl, environmentConfigUrl, environmentDependencies, waitTimeout, waitForServiceList, namespaceLazyCreateEnabled, namespaceDestroyEnabled);
    }


    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

       public DescriptorImpl() {
            super(NamespaceStepExecution.class);
        }

        public DescriptorImpl(Class<? extends StepExecution> executionType) {
            super(executionType);
        }

        @Override
        public String getFunctionName() {
            return "arquillianCubeKubernetesSession";
        }

        @Override
        public String getDisplayName() {
            return "Run build steps inside an Arquillian Cube Kubernetes Session";
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
