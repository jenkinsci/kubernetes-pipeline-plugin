package io.fabric8.kubernetes.workflow;

import groovy.lang.Binding;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;

import javax.annotation.Nonnull;

@Extension
public class KubernetesDSL extends GlobalVariable {

    private static final String KUBERNETES = "kubernetes";

    @Nonnull
    @Override
    public String getName() {
        return KUBERNETES;
    }

    @Nonnull
    @Override
    public Object getValue(CpsScript script) throws Exception {
        Binding binding = script.getBinding();
        Object kubernetes;
        if (binding.hasVariable(getName())) {
            kubernetes = binding.getVariable(getName());
        } else {
            // Note that if this were a method rather than a constructor, we would need to mark it @NonCPS lest it throw CpsCallableInvocation.
            kubernetes = script.getClass().getClassLoader().loadClass("io.fabric8.kubernetes.workflow.Kubernetes").getConstructor(CpsScript.class).newInstance(script);
            binding.setVariable(getName(), kubernetes);
        }
        return kubernetes;
    }

}
