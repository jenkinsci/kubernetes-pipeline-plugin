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

import com.google.common.base.Strings;

import hudson.Extension;

import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.PodEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Deprecated //No longer needed as we delegate to the kubernetes-plugin.
public class WithPodStep extends AbstractStepImpl implements Serializable {

    private static final long serialVersionUID = 5588861066775717487L;

    private final String name;

    private final List<ContainerTemplate> containers;
    private final transient List<PodEnvVar> envVars;
    private final List<PodVolume> volumes;
    private final Map<String, String> labels;

    private final String serviceAccount;
    private final String nodeSelector;
    private final String workingDir;

    @DataBoundConstructor
    public WithPodStep(String name, List<ContainerTemplate> containers, Map<String, String> envVars, List<PodVolume> volumes, String serviceAccount, String nodeSelector, String workingDir, Map<String, String> labels) {
        this.name = name;
        this.containers = containers != null ? containers : Collections.emptyList();
        this.envVars = envVars != null ? asPodEnvars(envVars) : Collections.emptyList();
        this.volumes = volumes != null ? volumes : Collections.emptyList();
        this.serviceAccount = serviceAccount;
        this.nodeSelector = nodeSelector;
        this.workingDir = Strings.isNullOrEmpty(workingDir) ? ContainerTemplate.DEFAULT_WORKING_DIR : workingDir;
        this.labels = labels != null ? labels : Collections.emptyMap();
    }

    public String getName() {
        return name;
    }

    public List<ContainerTemplate> getContainers() {
        return containers;
    }

    public List<PodEnvVar> getEnvVars() {
        return envVars;
    }

    public List<PodVolume> getVolumes() {
        return volumes;
    }

    public String getServiceAccount() {
        return serviceAccount;
    }

    public String getNodeSelector() {
        return nodeSelector;
    }

    public Map<String, String> getLabels() { return labels; }

    public String getWorkingDir() {
        return workingDir;
    }


    private List<PodEnvVar> asPodEnvars(Map<String, String> map) {
        List<PodEnvVar> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            result.add(new PodEnvVar(entry.getKey(), entry.getValue()));
        }
        return result;
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
            return "Run build steps in a MyPod";
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
