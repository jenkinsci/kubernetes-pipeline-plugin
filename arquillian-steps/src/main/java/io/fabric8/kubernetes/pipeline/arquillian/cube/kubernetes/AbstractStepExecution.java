package io.fabric8.kubernetes.pipeline.arquillian.cube.kubernetes;

import org.apache.commons.lang3.RandomStringUtils;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.NamespaceAction;
import java.util.logging.Logger;

import hudson.AbortException;
import hudson.model.Run;
import hudson.slaves.Cloud;
import io.fabric8.kubernetes.client.utils.Utils;
import io.fabric8.kubernetes.clnt.v2_5.DefaultKubernetesClient;
import io.fabric8.kubernetes.clnt.v2_5.KubernetesClient;
import io.fabric8.kubernetes.clnt.v2_5.utils.Serialization;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

public abstract class AbstractStepExecution<S extends AbstractStep> extends StepExecution {

    private static final transient String NAME_FORMAT = "%s-%s";
    private static final transient String DEFAULT_PREFIX = "temp";
    private static final transient String RANDOM_CHARACTERS = "bcdfghjklmnpqrstvwxz0123456789";

    protected static final transient Logger LOGGER = Logger.getLogger(InSessionStepExecution.class.getName());

    abstract S getStep();

     AbstractStepExecution(StepContext context) {
        super(context);
    }

    /**
     * Obtains a {@link KubernetesClient} either from the configured {@link Cloud} or a default instance.
     * @return
     * @throws AbortException
     */
    protected KubernetesClient getKubernetesClient() throws AbortException {

        Cloud cloud = Jenkins.getInstance().getCloud(getStep().getCloud());
        if (cloud == null) {
            LOGGER.warning("Cloud does not exist: [" + getStep().getCloud() + "]. Falling back to default KubernetesClient.");
        } else if (!(cloud instanceof KubernetesCloud)) {
            LOGGER.warning("Cloud is not a Kubernetes cloud: [" + getStep().getCloud() + "]. Falling back to default KubernetesClient.");
        } else {
            KubernetesCloud kubernetesCloud = (KubernetesCloud) cloud;
            try {
                String json = Serialization.asJson(kubernetesCloud.connect().getConfiguration());
                return DefaultKubernetesClient.fromConfig(json);
            } catch (Throwable t) {
                LOGGER.warning("Could not connect to cloud: [" + getStep().getCloud() + "]. Falling back to default KubernetesClient.");
            }
        }
        return new DefaultKubernetesClient();
    }


    /**
     * Generates a unique identifier for the arquillian session.
     * @return
     */
    protected String generateSessionId() {
        return RandomStringUtils.random(5, RANDOM_CHARACTERS);
    }

    /**
     * Generates a namespace id/name if one has not been explicitly specified.
     * If no name or prefix is specified, it will try to determine a namespace based on the enclosing elements.
     * Finally it will fallback to generating one using the default prefix.
     *
     * @param name          A fixed namespace name.
     * @param prefix        The prefix to use.
     * @param sessionId     The id of the session.
     * @return              The name if not null or empty, else prefix-sessionId.
     */
    protected String generateNamespaceId(String name, String prefix, String sessionId) {
        if (Utils.isNotNullOrEmpty(name)) {
            return name;
        } else if (Utils.isNotNullOrEmpty(prefix)) {
            return String.format(NAME_FORMAT, prefix, sessionId);
        }

        NamespaceAction namespaceAction = getNamespaceAction();
        if (namespaceAction != null && Utils.isNotNullOrEmpty(namespaceAction.getNamespace())) {
            return namespaceAction.getNamespace();
        }

        return String.format(NAME_FORMAT, DEFAULT_PREFIX, sessionId);
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

}
