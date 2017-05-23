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
import org.csanchez.jenkins.plugins.kubernetes.pipeline.NamespaceAction;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.fabric8.kubernetes.client.utils.Utils;
import io.fabric8.kubernetes.clnt.v2_2.KubernetesClient;
import io.fabric8.openshift.clnt.v2_2.OpenShiftClient;

public abstract class AbstractSessionManagerStepExecution<S extends AbstractSessionManagerStep> extends AbstractStepExecution<S> {

    protected Session session;
    protected transient KubernetesClient client;
    protected transient Configuration configuration;
    protected transient SessionManager sessionManager;

    protected boolean isOpenShift;

    /**
     * Called when the execution starts
     * @return  true if the execution of this step has synchronously completed before this method returns.
     * @throws Exception
     */
    public abstract boolean onStart(SessionManager sessionManager) throws Exception;

    /**
     * Called when the execution ends.
     * @param sessionManager
     * @throws Exception
     */
    public abstract void onStop(SessionManager sessionManager) throws Exception;

    @Override
    public boolean start() throws Exception {
        init();
        return onStart(sessionManager);
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        onStop(sessionManager);
    }

    protected void init() throws Exception {
        String sessionId = generateSessionId();
        String namespace = generateNamespaceId(sessionId);

        client = getKubernetesClient();
        isOpenShift = client.isAdaptable(OpenShiftClient.class);

        boolean isNamespaceCleanupEnabled = getStep().isNamespaceCleanupEnabled() != null
                ? getStep().isNamespaceCleanupEnabled()
                : false;

        boolean isNamespaceDestroyEnabled = getStep().isNamespaceDestroyEnabled() != null
                ? getStep().isNamespaceDestroyEnabled()
                : !isNamespaceProvided();

        configuration = new DefaultConfigurationBuilder()
                .withMasterUrl(client.getMasterUrl())
                .withNamespace(namespace)
                .withEnvironmentInitEnabled(true)
                .withNamespaceLazyCreateEnabled(getStep().isNamespaceLazyCreateEnabled())
                .withNamespaceCleanupEnabled(isNamespaceCleanupEnabled)
                .withNamespaceDestroyEnabled(isNamespaceDestroyEnabled)
                .withEnvironmentDependencies(toURL(getStep().getEnvironmentDependencies()))
                .withEnvironmentConfigUrl(toURL(getStep().getEnvironmentConfigUrl()))
                .withEnvironmentSetupScriptUrl(toURL(getStep().getEnvironmentSetupScriptUrl()))
                .withEnvironmentTeardownScriptUrl(toURL(getStep().getEnvironmentTeardownScriptUrl()))
                .build();

        TaskListener listener = getContext().get(TaskListener.class);
        StreamLogger logger = new StreamLogger(listener.getLogger());
        session = new DefaultSession(sessionId, namespace, logger);

        LabelProvider labelProvider = new MapLabelProvider(getStep().getLabels());
        AnnotationProvider annotationProvider = new MapAnnotationProvider(getStep().getAnnotations());

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

    protected boolean isNamespaceProvided() {
        if (Utils.isNotNullOrEmpty(getStep().getName())) {
            return true;
        }

        NamespaceAction namespaceAction = getNamespaceAction();
        if (namespaceAction != null && Utils.isNotNullOrEmpty(namespaceAction.getNamespace())) {
            return true;
        }
        return false;
    }

    /**
     * Generates a namespace id/name if one has not been explicitly specified.
     * If no name or prefix is specified, it will try to determine a namespace based on the enclosing elements.
     * Finally it will fallback to generating one using the default prefix.
     *
     * @param sessionId     The id of the session.
     * @return              The name if not null or empty, else prefix-sessionId.
     */
    protected String generateNamespaceId(String sessionId) {
        return super.generateNamespaceId(getStep().getName(), getStep().getPrefix(), sessionId);
    }


    /**
     * Lookup namespace that is already defined in the current build.
     * @return The {@link NamespaceAction}.
     */
    protected NamespaceAction getNamespaceAction() {
        try {
            return new NamespaceAction(getContext().get(Run.class));
        } catch (Throwable t) {
            return null;
        }
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
