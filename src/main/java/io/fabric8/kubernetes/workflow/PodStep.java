package io.fabric8.kubernetes.workflow;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Map;

public class PodStep extends AbstractStepImpl {

    private final String name;
    private final String image;
    private final String serviceAccount;
    private final Boolean privileged;
    private final Map secrets;
    private final Map env;

    @DataBoundConstructor
    public PodStep(String name, String image, String serviceAccount, Boolean privileged, Map secrets, Map env) {
        this.name = name;
        this.image = image;
        this.serviceAccount = serviceAccount;
        this.privileged = privileged;
        this.secrets = secrets;
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
