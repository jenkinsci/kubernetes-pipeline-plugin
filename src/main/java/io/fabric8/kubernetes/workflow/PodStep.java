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
import java.util.Map;
import java.util.Set;

public class PodStep extends AbstractStepImpl implements Serializable {

    private static final long serialVersionUID = 5588861066775717487L;

    private final String name;
    private final String image;
    private final String serviceAccount;
    private final Boolean privileged;
    private final Map secrets;
    private final Map hostPathMounts;
    private final Map emptyDirs;
    private final Map env;

    @DataBoundConstructor
    public PodStep(String name, String image, String serviceAccount, Boolean privileged, Map secrets, Map hostPathMounts, Map emptyDirs, Map env) {
        this.name = name;
        this.image = image;
        this.serviceAccount = serviceAccount;
        this.privileged = privileged;
        this.secrets = secrets;
        this.hostPathMounts = hostPathMounts;
        this.emptyDirs = emptyDirs;
        this.env = env;
    }

    public String getName() {
        return name;
    }

    public String getImage() {
        return image;
    }

    public String getServiceAccount() {
        return serviceAccount;
    }

    public Boolean getPrivileged() {
        return privileged;
    }

    public Map<String, String> getSecrets() {
        return secrets;
    }

    public Map<String, String> getHostPathMounts() {
        return hostPathMounts;
    }

    public  Map<String, String> getEmptyDirs() {
        return emptyDirs;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

       public DescriptorImpl() {
            super(PodStepExecution.class);
        }

        public DescriptorImpl(Class<? extends StepExecution> executionType) {
            super(executionType);
        }

        @Override
        public String getFunctionName() {
            return "withKubernetesPod";
        }

        @Override
        public String getDisplayName() {
            return "Run build steps as a Kubernetes Pod";
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
