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

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class WithPodStep extends AbstractStepImpl implements Serializable {

    private static final long serialVersionUID = 5588861066775717487L;

    private final String name;
    private final String image;
    private final String serviceAccount;
    private final Boolean privileged;
    private final Map secrets;
    private final Map hostPathMounts;
    private final Map emptyDirs;
    private final Map volumeClaims;
    private final Map env;

    @DataBoundConstructor
    public WithPodStep(String name, String image, String serviceAccount, Boolean privileged, Map secrets, Map hostPathMounts, Map emptyDirs, Map volumeClaims, Map env) {
        this.name = name;
        this.image = image;
        this.serviceAccount = serviceAccount;
        this.privileged = privileged;
        this.secrets = secrets != null ? secrets : new HashMap();
        this.hostPathMounts = hostPathMounts != null ? hostPathMounts : new HashMap();
        this.emptyDirs = emptyDirs != null ? emptyDirs : new HashMap();
        this.volumeClaims = volumeClaims != null ? volumeClaims : new HashMap();
        this.env = env != null ? env : new HashMap();
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

    public Map getSecrets() {
        return secrets;
    }

    public Map getHostPathMounts() {
        return hostPathMounts;
    }

    public Map getEmptyDirs() {
        return emptyDirs;
    }

    public Map getVolumeClaims() {
        return volumeClaims;
    }

    public Map getEnv() {
        return env;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

       public DescriptorImpl() {
            super(WithPodStepExecution.class);
        }

        public DescriptorImpl(Class<? extends StepExecution> executionType) {
            super(executionType);
        }

        @Override
        public String getFunctionName() {
            return "withPod";
        }

        @Override
        public String getDisplayName() {
            return "Run build steps in a Pod";
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
