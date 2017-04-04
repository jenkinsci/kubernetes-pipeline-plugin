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

import org.arquillian.cube.kubernetes.api.Configuration;
import org.arquillian.cube.kubernetes.api.LabelProvider;
import org.arquillian.cube.kubernetes.api.NamespaceService;
import org.arquillian.cube.kubernetes.impl.DefaultConfigurationBuilder;
import org.arquillian.cube.kubernetes.impl.namespace.DefaultNamespaceService;
import org.arquillian.cube.openshift.impl.namespace.OpenshiftNamespaceService;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.NamespaceAction;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.fabric8.kubernetes.clnt.v2_2.KubernetesClient;
import io.fabric8.openshift.clnt.v2_2.OpenShiftClient;


public class NamespaceStepExecution extends AbstractStepExecution<NamespaceStep> {

    @Inject
    private NamespaceStep step;
    @StepContextParameter
    private transient TaskListener listener;

    private String sessionId;
    private String namespace;
    private transient KubernetesClient client;
    private transient NamespaceService namespaceService;
    private transient Configuration configuration;

    private boolean isOpenshift;

    @Override
    public boolean start() throws Exception {
        NamespaceAction namespaceAction = new NamespaceAction(getContext().get(Run.class));

        sessionId = generateSessionId();
        namespace = generateNamespaceId(step.getName(), step.getPrefix(), sessionId);

        client = getKubernetesClient();
        isOpenshift = client.isAdaptable(OpenShiftClient.class);

        configuration = new DefaultConfigurationBuilder()
                .withNamespace(namespace)
                .withNamespaceLazyCreateEnabled(step.isNamespaceLazyCreateEnabled())
                .withNamespaceDestroyEnabled(step.isNamespaceDestroyEnabled())
                .withMasterUrl(client.getMasterUrl())
                .build();

        StreamLogger logger = new StreamLogger(listener.getLogger());
        LabelProvider labelProvider = new MapLabelProvider(step.getLabels());

        namespaceService = isOpenshift
                ? new OpenshiftNamespaceService.ImmutableOpenshiftNamespaceService(client, configuration, labelProvider, logger)
                : new DefaultNamespaceService.ImmutableNamespaceService(client, configuration, labelProvider, logger);


        if (!namespaceService.exists(namespace) && configuration.isNamespaceLazyCreateEnabled()) {
            namespaceService.create(namespace);
        }
        namespaceAction.push(namespace);

        getContext().newBodyInvoker().
                withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new NamespaceExpander(namespace))).
                withCallback(new NamespaceDestructionCallback(namespace, configuration, namespaceService, namespaceAction)).
                start();

        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        if (configuration.isNamespaceDestroyEnabled()) {
            namespaceService.destroy(namespace);
        }
        String ns = new NamespaceAction(getContext().get(Run.class)).pop();
    }


}
