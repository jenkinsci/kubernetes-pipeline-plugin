package io.fabric8.kubernetes.workflow;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;

public class PodStep extends AbstractStepImpl {

   private final String image;
   private final String podName;

    @DataBoundConstructor
    public PodStep(String image, String podName) {
        this.image = image;
        this.podName = podName;
    }

    public String getImage() {
        return image;
    }

    public String getPodName() {
        return podName;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(PodStepExecution.class);
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
