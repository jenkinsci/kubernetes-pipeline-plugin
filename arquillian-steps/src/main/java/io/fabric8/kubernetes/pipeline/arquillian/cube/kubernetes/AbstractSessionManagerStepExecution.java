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

import org.arquillian.cube.kubernetes.api.AnnotationProvider;
import org.arquillian.cube.kubernetes.api.Configuration;
import org.arquillian.cube.kubernetes.api.DependencyResolver;
import org.arquillian.cube.kubernetes.api.FeedbackProvider;
import org.arquillian.cube.kubernetes.api.KubernetesResourceLocator;
import org.arquillian.cube.kubernetes.api.LabelProvider;
import org.arquillian.cube.kubernetes.api.NamespaceService;
import org.arquillian.cube.kubernetes.api.ResourceInstaller;
import org.arquillian.cube.kubernetes.api.Session;
import org.arquillian.cube.kubernetes.impl.DefaultConfigurationBuilder;
import org.arquillian.cube.kubernetes.impl.DefaultSession;
import org.arquillian.cube.kubernetes.impl.SessionManager;
import org.arquillian.cube.kubernetes.impl.feedback.DefaultFeedbackProvider;
import org.arquillian.cube.kubernetes.impl.install.DefaultResourceInstaller;
import org.arquillian.cube.kubernetes.impl.locator.DefaultKubernetesResourceLocator;
import org.arquillian.cube.kubernetes.impl.namespace.DefaultNamespaceService;
import org.arquillian.cube.openshift.impl.install.OpenshiftResourceInstaller;
import org.arquillian.cube.openshift.impl.locator.OpenshiftKubernetesResourceLocator;
import org.arquillian.cube.openshift.impl.namespace.OpenshiftNamespaceService;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import hudson.AbortException;
import hudson.model.TaskListener;
import io.fabric8.kubernetes.client.utils.Utils;
import io.fabric8.kubernetes.clnt.v2_2.KubernetesClient;
import io.fabric8.openshift.clnt.v2_2.OpenShiftClient;

public abstract class AbstractSessionManagerStepExecution<S extends AbstractSessionManagerStep> extends AbstractStepExecution<S> {

    @StepContextParameter private transient TaskListener listener;

    protected Session session;
    protected transient KubernetesClient client;
    protected transient Configuration configuration;
    protected transient SessionManager sessionManager;

    protected boolean isOpenShift;

    public abstract void onStart(SessionManager sessionManager) throws Exception;

    public abstract void onStop(SessionManager sessionManager) throws Exception;

    @Override
    public boolean start() throws Exception {
        init();
        onStart(sessionManager);
        return true;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        onStop(sessionManager);
    }

    private void init() throws AbortException {
        String sessionId = generateSessionId();
        String namespace = generateNamespaceId(step.getName(), step.getPrefix(), sessionId);

        client = getKubernetesClient();
        isOpenShift = client.isAdaptable(OpenShiftClient.class);

        configuration = new DefaultConfigurationBuilder()
                .withMasterUrl(client.getMasterUrl())
                .withNamespace(namespace)
                .withEnvironmentInitEnabled(true)
                .withNamespaceLazyCreateEnabled(step.isNamespaceLazyCreateEnabled())
                .withNamespaceDestroyEnabled(step.isNamespaceDestroyEnabled())
                .withEnvironmentDependencies(toURL(step.getEnvironmentDependencies()))
                .withEnvironmentConfigUrl(toURL(step.getEnvironmentConfigUrl()))
                .withEnvironmentSetupScriptUrl(toURL(step.getEnvironmentSetupScriptUrl()))
                .withEnvironmentTeardownScriptUrl(toURL(step.getEnvironmentTeardownScriptUrl()))
                .build();

        StreamLogger logger = new StreamLogger(listener.getLogger());
        session = new DefaultSession(sessionId, namespace, logger);

        LabelProvider labelProvider = new MapLabelProvider(step.getLabels());
        AnnotationProvider annotationProvider = new MapAnnotationProvider(step.getAnnotations());

        NamespaceService namespaceService = isOpenShift
                ? new OpenshiftNamespaceService.ImmutableOpenshiftNamespaceService(client, configuration, labelProvider, logger)
                : new DefaultNamespaceService.ImmutableNamespaceService(client, configuration, labelProvider, logger);

        KubernetesResourceLocator resourceLocator = isOpenShift
                ? new OpenshiftKubernetesResourceLocator()
                : new DefaultKubernetesResourceLocator();


        ResourceInstaller resourceInstaller = isOpenShift
                ? new OpenshiftResourceInstaller.ImmutableResourceInstaller(client, configuration, logger, Collections.emptyList())
                : new DefaultResourceInstaller.ImmutableResourceInstaller(client, configuration, logger, Collections.emptyList());

        FeedbackProvider feedbackProvider = new DefaultFeedbackProvider.ImmutableFeedbackProvider(client, logger);
        DependencyResolver dependencyResolver = new EmptyDependencyResolver();

        sessionManager = new SessionManager(session, client, configuration,
                annotationProvider, namespaceService, resourceLocator,
                dependencyResolver, resourceInstaller, feedbackProvider);
    }


    protected URL toURL(String url) {
        if (Utils.isNullOrEmpty(url)) {
            return null;
        }
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Specified url:["+url+"] is malformed");
        }
    }


    protected List<URL> toURL(List<String> urls) {
        if (urls == null) {
            return null;
        }

        if (urls.isEmpty()) {
            return Collections.emptyList();
        }

        List<URL> result = new ArrayList<>();
        urls.forEach(u -> result.add(toURL(u)));
        return result;
    }

}
