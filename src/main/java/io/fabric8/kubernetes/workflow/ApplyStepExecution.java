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

package io.fabric8.kubernetes.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import hudson.AbortException;
import hudson.FilePath;
import hudson.model.TaskListener;
import io.fabric8.devops.ProjectConfig;
import io.fabric8.devops.ProjectConfigs;
import io.fabric8.kubernetes.api.Controller;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.internal.HasMetadataComparator;
import io.fabric8.openshift.api.model.*;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.utils.*;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.steps.*;

import static io.fabric8.utils.PropertiesHelper.toMap;

import javax.inject.Inject;
import java.io.*;
import java.net.URL;
import java.util.*;

public class ApplyStepExecution extends AbstractSynchronousStepExecution<String>{

    @Inject
    private transient ApplyStep step;

    @StepContextParameter
    private transient TaskListener listener;

    @StepContextParameter
    private transient FilePath workspace;

    @Override
    public String run() throws Exception {

        String json = step.getFile();
        String environment = step.getEnvironment();

        if (StringUtils.isBlank(json) || StringUtils.isBlank(environment)) {
            throw new AbortException("Supply file and target environment");
        }

        try {
            KubernetesClient kubernetes = new DefaultKubernetesClient();
            Controller controller = new Controller(kubernetes);
            controller.setThrowExceptionOnError(true);
            controller.setRecreateMode(false);
            controller.setAllowCreate(step.getCreateNewResources());
            controller.setServicesOnlyMode(step.getServicesOnly());
            controller.setIgnoreServiceMode(step.getIgnoreServices());
            controller.setIgnoreRunningOAuthClients(step.getIgnoreRunningOAuthClients());
            controller.setProcessTemplatesLocally(step.getProcessTemplatesLocally());
            controller.setDeletePodsOnReplicationControllerUpdate(step.getDeletePodsOnReplicationControllerUpdate());
            controller.setRollingUpgrade(step.getRollingUpgrades());
            controller.setRollingUpgradePreserveScale(step.getRollingUpgradePreserveScale());


            boolean openShift = KubernetesHelper.isOpenShift(kubernetes);
            if (!openShift) {
                listener.error("Disabling openshift features has not been implemeted yet");
                // TODO: handle
                //disableOpenShiftFeatures(controller);
            }

            Object dto = KubernetesHelper.loadJson(json);
            if (dto == null) {
                throw new AbortException("Cannot load kubernetes json: " + json);
            }

            // lets check we have created the namespace
            controller.applyNamespace(environment);
            controller.setNamespace(environment);

            // TODO do we need this?
            String fileName = "generated.json";

            if (dto instanceof Template) {
                Template template = (Template) dto;
                dto = applyTemplates(template, kubernetes, controller, fileName, environment);
            }

            if (dto instanceof KubernetesList) {
                KubernetesList list = (KubernetesList) dto;
                controller.applyList(list, fileName);
            }

            Set<KubernetesList> kubeConfigs = new LinkedHashSet<>();

            Set<HasMetadata> entities = new TreeSet<>(new HasMetadataComparator());
            for (KubernetesList c : kubeConfigs) {
                entities.addAll(c.getItems());
            }

            entities.addAll(KubernetesHelper.toItemList(dto));

            //if (createRoutes) {
                createRoutes(kubernetes, entities, environment);
            //}

            addEnvironmentAnnotations(entities);

            //Apply all items
            for (HasMetadata entity : entities) {
                if (entity instanceof Pod) {
                    Pod pod = (Pod) entity;
                    controller.applyPod(pod, fileName);
                } else if (entity instanceof Service) {
                    Service service = (Service) entity;
                    controller.applyService(service, fileName);
                } else if (entity instanceof ReplicationController) {
                    ReplicationController replicationController = (ReplicationController) entity;
                    controller.applyReplicationController(replicationController, fileName);
                } else if (entity != null) {
                    controller.apply(entity, fileName);
                }
            }
        } catch (Exception e) {
            throw new AbortException("Error during kubernetes apply: " + e.getMessage());
        }
        return "SUCCESS";
    }

    protected void createRoutes(KubernetesClient kubernetes, Collection<HasMetadata> collection, String namespace) throws AbortException {

        String domain = Systems.getEnvVarOrSystemProperty("DOMAIN");
        if (Strings.isNullOrBlank(domain)){
            throw new AbortException("No DOMAIN environment variable set so cannot create routes");
        }
        String routeDomainPostfix = namespace+"."+Systems.getEnvVarOrSystemProperty("DOMAIN");

        // lets get the routes first to see if we should bother
        try {
            RouteList routes = kubernetes.adapt(OpenShiftClient.class).routes().inNamespace(namespace).list();
            if (routes != null) {
                routes.getItems();
            }
        } catch (Exception e) {

            throw new AbortException("Cannot load OpenShift Routes; maybe not connected to an OpenShift platform? " + e);

        }
        List<Route> routes = new ArrayList<>();
        for (Object object : collection) {
            if (object instanceof Service) {
                Service service = (Service) object;
                Route route = createRouteForService(routeDomainPostfix, namespace, service, listener);
                if (route != null) {
                    routes.add(route);
                }
            }
        }
        collection.addAll(routes);
    }

    public static Route createRouteForService(String routeDomainPostfix, String namespace, Service service, TaskListener listener) {
        Route route = null;
        String id = KubernetesHelper.getName(service);
        if (Strings.isNotBlank(id) && shouldCreateRouteForService(service, id, listener)) {
            route = new Route();
            String routeId = id;
            KubernetesHelper.setName(route, namespace, routeId);
            RouteSpec routeSpec = new RouteSpec();
            ObjectReference objectRef = new ObjectReference();
            objectRef.setName(id);
            objectRef.setNamespace(namespace);
            routeSpec.setTo(objectRef);
            if (!Strings.isNullOrBlank(routeDomainPostfix)) {
                String host = Strings.stripSuffix(Strings.stripSuffix(id, "-service"), ".");
                routeSpec.setHost(host + "." + Strings.stripPrefix(routeDomainPostfix, "."));
            } else {
                routeSpec.setHost("");
            }
            route.setSpec(routeSpec);
            String json;
            try {
                json = KubernetesHelper.toJson(route);
            } catch (JsonProcessingException e) {
                json = e.getMessage() + ". object: " + route;
            }
        }
        return route;
    }

    /**
     * Should we try to create a route for the given service?
     * <p/>
     * By default lets ignore the kubernetes services and any service which does not expose ports 80 and 443
     *
     * @return true if we should create an OpenShift Route for this service.
     */
    protected static boolean shouldCreateRouteForService(Service service, String id, TaskListener listener) {
        if ("kubernetes".equals(id) || "kubernetes-ro".equals(id)) {
            return false;
        }
        Set<Integer> ports = KubernetesHelper.getPorts(service);
        if (ports.size() == 1) {
            return true;
        } else {
            listener.getLogger().println("Not generating route for service " + id + " as only single port services are supported. Has ports: " + ports);
            return false;
        }
    }

    private Object applyTemplates(Template template, KubernetesClient kubernetes, Controller controller, String fileName, String namespace) throws Exception {
        KubernetesHelper.setNamespace(template, namespace);
        // TODO do we need to override template values during a CD pipeline?
        //overrideTemplateParameters(template);
        return controller.applyTemplate(template, fileName);
    }

    private void addEnvironmentAnnotations(Iterable<HasMetadata> items) throws Exception {
        if (items != null) {
            for (HasMetadata item : items) {
                if (item instanceof KubernetesList) {
                    KubernetesList list = (KubernetesList) item;
                    addEnvironmentAnnotations(list.getItems());
                } else if (item instanceof Template) {
                    Template template = (Template) item;
                    addEnvironmentAnnotations(template.getObjects());
                } else if (item instanceof ReplicationController) {
                    addEnvironmentAnnotations(item);
                } else if (item instanceof DeploymentConfig) {
                    addEnvironmentAnnotations(item);
                }
            }
        }
    }

    protected void addEnvironmentAnnotations(HasMetadata resource) throws AbortException {
        Map<String, String> mapEnvVarToAnnotation = new HashMap<>();
        String resourceName = "environmentAnnotations.properties";
        URL url = getClass().getResource(resourceName);
        if (url == null) {
            throw new AbortException("Cannot find resource `" + resourceName + "` on the classpath!");
        }
        addPropertiesFileToMap(url, mapEnvVarToAnnotation);
        //TODO add this in and support for non java projects
        //addPropertiesFileToMap(this.environmentVariableToAnnotationsFile, mapEnvVarToAnnotation);
        addPropertiesFileToMap(new File("./src/main/fabric8/environemntToAnnotations.properties"), mapEnvVarToAnnotation);
        Map<String, String> annotations = KubernetesHelper.getOrCreateAnnotations(resource);
        Set<Map.Entry<String, String>> entries = mapEnvVarToAnnotation.entrySet();
        for (Map.Entry<String, String> entry : entries) {
            String envVar = entry.getKey();
            String annotation = entry.getValue();
            if (Strings.isNotBlank(envVar) && Strings.isNotBlank(annotation)) {
                String value = Systems.getEnvVarOrSystemProperty(envVar);
                if (Strings.isNullOrBlank(value)) {
                    value = tryDefaultAnnotationEnvVar(envVar);
                }
                if (Strings.isNotBlank(value)) {
                    String oldValue = annotations.get(annotation);
                    if (Strings.isNotBlank(oldValue)) {
                        listener.getLogger().println("Not adding annotation `" + annotation + "` to " + KubernetesHelper.getKind(resource) + " " + KubernetesHelper.getName(resource) + " with value `" + value + "` as there is already an annotation value of `" + oldValue + "`");
                    } else {
                        annotations.put(annotation, value);
                    }
                }
            }
        }
    }

    /**
     * Tries to default some environment variables if they are not already defined.
     *
     * This can happen if using Jenkins Workflow which doens't seem to define BUILD_URL or GIT_URL for example
     *
     * @return the value of the environment variable name if it can be found or calculated
     */
    protected String tryDefaultAnnotationEnvVar(String envVarName) throws AbortException {

        ProjectConfig projectConfig = getProjectConfig();
        GitConfig gitConfig = getGitConfig();

        if (io.fabric8.utils.Objects.equal("BUILD_URL", envVarName)) {
            String jobUrl = projectConfig.getLink("Job");
            if (Strings.isNullOrBlank(jobUrl)) {
                listener.getLogger().println("No Job link found in fabric8.yml so we cannot set the BUILD_URL");
            }
            return jobUrl;
        } else if (io.fabric8.utils.Objects.equal("GIT_URL", envVarName)) {
            String gitUrl = projectConfig.getLinks().get("Git");
            if (Strings.isNullOrBlank(gitUrl)){
                listener.getLogger().println("No Job link found in fabric8.yml so we cannot set the GIT_URL");
            }
            return gitUrl;

        } else if (io.fabric8.utils.Objects.equal("GIT_COMMIT", envVarName)) {
            String gitCommit = gitConfig.getCommit();
            if (Strings.isNullOrBlank(gitCommit)){
                listener.getLogger().println("No git commit found in git.yml so we cannot set the GIT_COMMIT");
            }
            return gitCommit;

        } else if (io.fabric8.utils.Objects.equal("GIT_BRANCH", envVarName)) {
            String gitBranch = gitConfig.getBranch();
            if (Strings.isNullOrBlank(gitBranch)){
                listener.getLogger().println("No git branch found in git.yml so we cannot set the GIT_BRANCH");
            }
            return gitBranch;

        }
        return null;
    }

    protected static void addPropertiesFileToMap(File file, Map<String, String> answer) throws AbortException {
        if (file != null && file.isFile() && file.exists()) {
            try (FileInputStream in = new FileInputStream(file)){
                Properties properties = new Properties();
                properties.load(in);
                Map<String, String> map = toMap(properties);
                answer.putAll(map);
            } catch (IOException e) {
                throw new AbortException("Failed to load properties file: " + file + ". " + e);
            }
        }
    }

    protected static void addPropertiesFileToMap(URL url, Map<String, String> answer) throws AbortException {
        if (url != null) {
            try (InputStream in = url.openStream()) {
                Properties properties = new Properties();
                properties.load(in);
                Map<String, String> map = toMap(properties);
                answer.putAll(map);
            } catch (IOException e) {
                throw new AbortException("Failed to load properties URL: " + url + ". " + e);
            }
        }
    }

    public GitConfig getGitConfig() throws AbortException {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            return mapper.readValue(readFile("git.yml"), GitConfig.class);
        } catch (Exception e) {
            throw new AbortException("Unable to parse fabric8.yml." + e);
        }
    }

    public ProjectConfig getProjectConfig() throws AbortException {
        try {
            return ProjectConfigs.parseProjectConfig(readFile("fabric8.yml"));
        } catch (Exception e) {
            throw new AbortException("Unable to parse fabric8.yml." + e);
        }
    }

    private String readFile(String fileName) throws AbortException {
        try {
            InputStream is = workspace.child(fileName).read();
            try {
                return IOUtils.toString(is, Charsets.UTF_8);
            } finally {
                is.close();
            }
        } catch (Exception e) {
            throw new AbortException("Unable to read file " + fileName + ". " + e);
        }
    }
}
