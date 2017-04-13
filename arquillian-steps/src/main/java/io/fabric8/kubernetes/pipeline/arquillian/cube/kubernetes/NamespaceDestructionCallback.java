package io.fabric8.kubernetes.pipeline.arquillian.cube.kubernetes;

import org.arquillian.cube.kubernetes.api.Configuration;
import org.arquillian.cube.kubernetes.api.NamespaceService;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.NamespaceAction;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;

public class NamespaceDestructionCallback extends BodyExecutionCallback.TailCall {

    private final String namespace;
    private final transient Configuration configuration;
    private final transient NamespaceService namespaceService;
    private final transient NamespaceAction namespaceAction;

    NamespaceDestructionCallback(String namespace, Configuration configuration, NamespaceService namespaceService, NamespaceAction namespaceAction) {
        this.namespace = namespace;
        this.configuration = configuration;
        this.namespaceService = namespaceService;
        this.namespaceAction = namespaceAction;
    }

    @Override
    protected void finished(StepContext context) throws Exception {
        if (configuration.isNamespaceDestroyEnabled()) {
            namespaceService.destroy(namespace);
        }
        String ns = namespaceAction.pop();
    }
}
