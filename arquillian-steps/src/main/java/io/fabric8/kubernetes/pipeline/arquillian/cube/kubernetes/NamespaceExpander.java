package io.fabric8.kubernetes.pipeline.arquillian.cube.kubernetes;

import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import hudson.EnvVars;

final class NamespaceExpander extends EnvironmentExpander {

   private static final long serialVersionUID = 1;
   private static final String KUBERNETES_NAMESPACE = "KUBERNETES_NAMESPACE";

   private final Map<String, String> overrides;

   NamespaceExpander(String namespace) {
       this.overrides = new HashMap<>();
       this.overrides.put(KUBERNETES_NAMESPACE, namespace);
   }

   @Override
   public void expand(EnvVars env) throws IOException, InterruptedException {
       env.overrideAll(overrides);
   }
}
