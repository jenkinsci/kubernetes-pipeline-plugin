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

package io.fabric8.kubernetes.pipeline.devops;

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.PluginManager;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.fabric8.devops.ProjectConfig;
import io.fabric8.devops.ProjectConfigs;
import io.fabric8.devops.ProjectRepositories;
import io.fabric8.kubernetes.api.Controller;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.ServiceNames;
import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.internal.HasMetadataComparator;
import io.fabric8.kubernetes.pipeline.devops.elasticsearch.DeploymentEventDTO;
import io.fabric8.kubernetes.pipeline.devops.elasticsearch.ElasticsearchClient;
import io.fabric8.kubernetes.pipeline.devops.elasticsearch.JsonUtils;
import io.fabric8.kubernetes.pipeline.devops.git.GitConfig;
import io.fabric8.kubernetes.pipeline.devops.git.GitInfoCallback;
import io.fabric8.openshift.api.model.*;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftAPIGroups;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.dsl.BuildResource;
import io.fabric8.utils.IOHelpers;
import io.fabric8.utils.Strings;
import io.fabric8.utils.Systems;
import io.fabric8.utils.URLUtils;
import io.fabric8.workflow.core.Constants;
import jenkins.model.Jenkins;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.fabric8.utils.PropertiesHelper.toMap;

public class ApplyStepExecution extends AbstractSynchronousStepExecution<List<HasMetadata>> {

    private final ServiceResourceUtil serviceFixUtil = new ServiceResourceUtil();

    @Inject
    private transient ApplyStep step;

    @StepContextParameter
    private transient TaskListener listener;

    @StepContextParameter
    private transient FilePath workspace;

    @StepContextParameter
    transient EnvVars env;

    @StepContextParameter
    transient Job job;

    @StepContextParameter
    transient Run run;

    private KubernetesClient kubernetes;

    private final List<HasMetadata> items = new ArrayList<>();

    private transient String buildConfigName;
    private transient String buildConfigNamespace;
    private transient String buildName;

    @Override
    public List<HasMetadata> run() throws Exception {
        String environment = step.getEnvironment();
        String environmentName = step.getEnvironmentName();
        if (StringUtils.isBlank(environmentName)) {
            environmentName = createDefaultEnvironmentName(environment);
        }

        if (StringUtils.isBlank(environment)) {
            throw new AbortException("Supply target environment");
        }

        try (KubernetesClient kubernetes = getKubernetes()) {
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

            String resources = getResources();

            Object dto;
            if (resources.startsWith("{")) {
                dto = KubernetesHelper.loadJson(resources);
            } else {
                dto = KubernetesHelper.loadYaml(resources.getBytes(), KubernetesResource.class);
            }

            if (dto == null) {
                throw new AbortException("Cannot load kubernetes json: " + resources);
            }

            Set<HasMetadata> entities = new TreeSet<>(new HasMetadataComparator());

            List<HasMetadata> imageStreams = loadImageStreams();
            if (imageStreams != null) {
                entities.addAll(imageStreams);
            }

            createEnvironment(environment, controller);
            controller.setNamespace(environment);

            // TODO do we need this?
            String fileName = "pipeline.json";

            entities.addAll(KubernetesHelper.toItemList(dto));

            addEnvironmentAnnotations(entities);

            if (isOpenShift()) {
                findOpenShiftBuildConfigName();
                listener.getLogger().println("Found BuildConfig: " + buildConfigName + " namespace: " + buildConfigNamespace + " Build: " + buildName);
            }

            String registry = getRegistry();
            if (Strings.isNotBlank(registry)) {
                listener.getLogger().println("Adapting resources to use pull images from registry: " + registry);
                addRegistryToImageNameIfNotPresent(entities, registry);
            }
            
            Map<String,String> deploymentVersions = new HashMap<>();
            List<Service> services = new ArrayList<>();
            //Apply all items
            serviceFixUtil.patchServiceName(entities);
            for (HasMetadata entity : entities) {
                listener.getLogger().println("Applying " + KubernetesHelper.getKind(entity) + " name: " + KubernetesHelper.getKind(entity) + " data: " + entity);
                if (entity instanceof Pod) {
                    Pod pod = (Pod) entity;
                    controller.applyPod(pod, fileName);
                    items.add(pod);
                    String event = getDeploymentEventJson(entity.getKind(), environment, environmentName);
                    ElasticsearchClient.createEvent(event, ElasticsearchClient.DEPLOYMENT, listener);

                } else if (entity instanceof Service) {
                    Service service = (Service) entity;
                    services.add(service);
                    controller.applyService(service, fileName);

                } else if (entity instanceof ReplicationController) {
                    ReplicationController replicationController = (ReplicationController) entity;
                    controller.applyReplicationController(replicationController, fileName);
                    items.add(replicationController);
                    String event = getDeploymentEventJson(entity.getKind(), environment, environmentName);
                    ElasticsearchClient.createEvent(event, ElasticsearchClient.DEPLOYMENT, listener);

                } else if (entity instanceof ReplicaSet) {
                    ReplicaSet replicaSet = (ReplicaSet) entity;
                    controller.apply(entity, fileName);
                    items.add(replicaSet);
                    String event = getDeploymentEventJson(entity.getKind(), environment, environmentName);
                    ElasticsearchClient.createEvent(event, ElasticsearchClient.DEPLOYMENT, listener);

                } else if (entity instanceof Deployment) {
                    Deployment deployment = (Deployment) entity;
                    controller.apply(entity, fileName);
                    items.add(deployment);
                    addDeploymentVersion(deploymentVersions, deployment);
                    String event = getDeploymentEventJson(entity.getKind(), environment, environmentName);
                    ElasticsearchClient.createEvent(event, ElasticsearchClient.DEPLOYMENT, listener);

                } else if (entity instanceof DeploymentConfig) {
                    DeploymentConfig deploymentConfig = (DeploymentConfig) entity;
                    controller.apply(entity, fileName);
                    items.add(deploymentConfig);
                    addDeploymentVersion(deploymentVersions, deploymentConfig);
                    String event = getDeploymentEventJson(entity.getKind(), environment, environmentName);
                    ElasticsearchClient.createEvent(event, ElasticsearchClient.DEPLOYMENT, listener);

                } else if (entity != null) {
                    controller.apply(entity, fileName);
                }
            }
            if (!services.isEmpty()) {
                runExposeController(kubernetes, environment);
            }
            List<HasMetadata> answer = this.items;
            if (step.getReadinessTimeout().intValue() > 0) {
                answer = kubernetes.resourceList(items).accept(new TypedVisitor<ObjectMetaBuilder>() {

                    @Override
                    public void visit(ObjectMetaBuilder objectMetaBuilder) {
                        objectMetaBuilder.withNamespace(environment);
                    }
                }).waitUntilReady(step.getReadinessTimeout(), TimeUnit.MILLISECONDS);
            }

            // now lets try annotate the BuildConfig with the running services in this environment
            Map<String,String> serviceUrls = new HashMap<>();
            for (Service service : services) {
                String serviceName = KubernetesHelper.getName(service);
                String url = KubernetesHelper.getServiceURL(kubernetes, serviceName, environment, "http", true);
                if (url != null && !url.isEmpty()) {
                    listener.getLogger().println("Service " + serviceName + " in environment " + environmentName + " namespace " + environment + " is at URL: " + url);
                    serviceUrls.put(serviceName, url);
                } else {
                    listener.getLogger().println("Service " + serviceName + " in environment " + environmentName + " namespace " + environment + " has no exposeUrl yet!");
                }
            }

            // TODO should we export the serviceUrls as an artifact so it can be viewed in the Jenkins console?

            if (this.buildName != null && !serviceUrls.isEmpty() && isOpenShift()) {
                EnvironmentRollout environmentRollout = new EnvironmentRollout(environmentName, serviceUrls, deploymentVersions);
                String yaml = KubernetesHelper.toYaml(environmentRollout);
                OpenShiftClient oClient = openShiftClient();
                BuildResource<Build, DoneableBuild, String, LogWatch> resource = oClient.builds().inNamespace(this.buildConfigNamespace).withName(buildName);
                try {
                    Build build = resource.get();
                    if (build == null) {
                        listener.getLogger().println("Failed to annotate Build " + buildName + " in namespace " + this.buildConfigNamespace + " due to not found!");
                    } else {
                        Map<String, String> annotations = KubernetesHelper.getOrCreateAnnotations(build);
                        annotations.put("environment.services.fabric8.io/" + environment, yaml);
                        String version = deploymentVersions.get(this.buildConfigName);
                        if (Strings.isNotBlank(version)) {
                            annotations.put("fabric8.io/version", version);
                        } else {
                            listener.getLogger().println("Could not annotate the environment service URLs for buildConfigName: " + this.buildConfigName + " as there is no deployment version but has serviceUrls: " + serviceUrls);
                        }
                        resource.replace(build);
                        listener.getLogger().println("Annotated Build " + buildName + " in namespace " + this.buildConfigNamespace + " with version " + version + " environment yaml " + environment + "=" + yaml);
                    }
                } catch (Exception e) {
                    // lets try use the edit operation in case that works better:
                    try {
                        BuildFluent.MetadataNested<DoneableBuild> builder = resource.edit().
                                editMetadata().addToAnnotations("environment.services.fabric8.io/" + environment, yaml);
                        String version = deploymentVersions.get(this.buildConfigName);
                        if (Strings.isNotBlank(version)) {
                            builder.addToAnnotations("fabric8.io/version", version);
                        } else {
                            listener.getLogger().println("Could not annotate the environment service URLs for buildConfigName: " + this.buildConfigName + " as there is no deployment version but has serviceUrls: " + serviceUrls);
                        }
                        builder.endMetadata().done();

                    } catch (Exception e2) {
                        listener.getLogger().println("Failed to annotate Build " + buildName + " in namespace " + this.buildConfigNamespace + " due to: " + e);
                    }
                }
            } else {
                listener.getLogger().println("Could not annotate the environment service URLs due to buildName: " + buildName + " isOpenShift: " + isOpenShift() + " serviceUrls: " + serviceUrls);
            }
            return answer;
        } catch (Exception e) {
            String stacktrace = ExceptionUtils.getStackTrace(e);
            throw new AbortException("Error during kubernetes apply: " + stacktrace);
        }
    }

    /**
     * Lets run the <a href="https://github.com/fabric8io/exposecontroller/">exposecontroller</a> in one shot mode to update any Services
     * to use their external URLs
     */
    protected void runExposeController(KubernetesClient kubernetes, String environment) throws Exception {
        if (exposeControllerInstalled()) {
            String commands = "exposecontroller --watch-namespace " + environment;
            OpenShiftClient oClient = openShiftClient();
            if (isOpenShift() && oClient.supportsOpenShiftAPIGroup(OpenShiftAPIGroups.ROUTE)) {
                commands += " --exposer Route";
            } else {
                commands = loadKubernetesExposeControllerCommandLine(kubernetes, commands);
            }

            if (commands == null) {
                return;
            }

            // lets avoid using any input
            commands = "echo | " + commands;
            try {
                listener.getLogger().println("Running: " + commands);
                runProcess(commands, true);
                listener.getLogger().println("exposecontroller completed");
            } catch (Exception e) {
                throw new Exception("Failed to invoke command " + commands + " due to: " + e, e);
            }
        } else {
            listener.getLogger().println("WARNING: no exposecontroller on the PATH to cannot annotate the Services with their external URLs. You could try the fabric8 docker image for jenkins?");
        }
    }

    private String loadKubernetesExposeControllerCommandLine(KubernetesClient kubernetes, String commands) throws Exception {
        String namespace = System.getenv("KUBERNETES_NAMESPACE");
        if (Strings.isNullOrBlank(namespace)) {
            namespace = KubernetesHelper.getNamespace(kubernetes);
        }
        ConfigMap configMap = kubernetes.configMaps().inNamespace(namespace).withName("exposecontroller").get();
        if (configMap == null) {
            listener.getLogger().println("WARNING: no ConfigMap in namespace " + namespace + " called: exposecontroller so cannot run exposecontroller to expose Service URLs");
            return null;
        }
        String configYaml = null;
        Map<String, String> data = configMap.getData();
        if (data != null) {
            configYaml = data.get("config.yml");
        }
        if (Strings.isNullOrBlank(configYaml)) {
            throw new Exception("ConfigMap " + namespace + "/exposecontroller does not have a `config.yml` data entry");
        }
        Map map;
        try {
            map = KubernetesHelper.loadYaml(configYaml, Map.class);
        } catch (IOException e) {
            throw new Exception("Could not parse YAML in ConfigMap " + namespace + "/exposecontroller entry `config.yml`: " + e, e);
        }
        StringBuilder builder = new StringBuilder(commands);
        appendCliArgument(builder, "--http", map.get("http"));
        appendCliArgument(builder, "--exposer", map.get("exposer"));
        appendCliArgument(builder, "--domain", map.get("domain"));
        return builder.toString();
    }

    /**
     * Appends a CLI argument if the value is not null or empty
     */
    private void appendCliArgument(StringBuilder builder, String arg, Object value) {
        if (value != null) {
            String text = value.toString();
            if (Strings.isNotBlank(text)) {
                builder.append(" ");
                builder.append(arg);
                builder.append(" ");
                builder.append(text);
            }
        }
    }

    protected boolean exposeControllerInstalled() {
        String commands = "exposecontroller --help";
        try {
            runProcess(commands, false);
            return true;
        } catch (Exception e) {
            listener.getLogger().println("Failed to execute process " +commands + " : " + e);
        }
        return false;
    }

    private void runProcess(String commands, boolean verbose) throws IOException {
        ProcessBuilder builder = new ProcessBuilder("bash", "-c", commands);
        Process process = builder.start();

        String out = IOHelpers.readFully(process.getInputStream());
        String err = IOHelpers.readFully(process.getErrorStream());
        if (verbose) {
            listener.getLogger().println("command: " + commands + " out: " + out);
            listener.getLogger().println("command: " + commands + " err: " + err);
        }
    }

    private void addDeploymentVersion(Map<String, String> deploymentVersions, HasMetadata resource) {
        String name = KubernetesHelper.getName(resource);
        String version = KubernetesHelper.getLabels(resource).get("version");
        // TODO if there is no version label could we find it from somewhere else?
        if (Strings.isNotBlank(version)) {
            deploymentVersions.put(name, version);
        } else {
            listener.getLogger().println("No version label for  " + KubernetesHelper.getKind(resource) + " "
                    + KubernetesHelper.getName(resource) + " in namespace "
                    + KubernetesHelper.getNamespace(resource));
        }
    }

    private void createEnvironment(String environment, Controller controller) {
        boolean found = false;
        OpenShiftClient oClient = openShiftClient();
        if (isOpenShift() && oClient.supportsOpenShiftAPIGroup(OpenShiftAPIGroups.PROJECT)) {
            ProjectList ps = oClient.projects().list();
            for(Project p : ps.getItems()){
                listener.getLogger().println("Found namespace " +p.getMetadata().getName());
                if (environment.equalsIgnoreCase(p.getMetadata().getName())){
                    found = true;
                    listener.getLogger().println("Found existing environment " + environment);
                    break;
                }
            }
        } else {
            NamespaceList ns = getKubernetes().namespaces().list();
            for(Namespace n : ns.getItems()){
                listener.getLogger().println("Found namespace " +n.getMetadata().getName());
                if (environment.equalsIgnoreCase(n.getMetadata().getName())){
                    found = true;
                    listener.getLogger().println("Found existing environment " + environment);
                    break;
                }
            }

        }
        if (!found){
            listener.getLogger().println("Creating environment " + environment);
            controller.applyNamespace(environment);
        }
    }

    private DefaultOpenShiftClient openShiftClient() {
        return new DefaultOpenShiftClient();
    }

    private List<HasMetadata> loadImageStreams() throws IOException, InterruptedException {
        if (kubernetes.isAdaptable(OpenShiftClient.class)) {
            FilePath child = workspace.child("target");
            if (child.exists() && child.isDirectory()) {
                List<FilePath> paths = child.list();
                if (paths != null) {
                    for (FilePath path : paths) {
                        String name = path.getName();
                        if (path.exists() && !path.isDirectory() && name.endsWith("-is.yml")) {
                            try (InputStream is = path.read()) {
                                listener.getLogger().println("Loading OpenShift ImageStreams file: " + name);
                                KubernetesResource dto = KubernetesHelper.loadYaml(is, KubernetesResource.class);
                                return KubernetesHelper.toItemList(dto);
                            }
                        }
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    private String getResources() throws AbortException {
        if (Strings.isNotBlank(step.getFile())){
            return step.getFile();
        } else {
            if (openShiftClient().supportsOpenShiftAPIGroup(OpenShiftAPIGroups.IMAGE)){
                try{
                    return readFile("target/classes/META-INF/fabric8/openshift.yml");
                } catch (AbortException e){
                    // lets not worry yet if we can't find the yaml as we may be using the old f-m-p
                    listener.getLogger().println("no openshift.yml found");
                }

            } else {
                try {
                    return readFile("target/classes/META-INF/fabric8/kubernetes.yml");
                } catch (AbortException e) {
                    // lets not worry yet if we can't find the yaml as we may be using the old f-m-p
                    listener.getLogger().println("no kubernetes.yml found");
                }
            }

            try {
                return readFile("target/classes/kubernetes.json");
            } catch (AbortException e) {
                throw new AbortException("No resources passed into the step using the 'file' argument or found in the default target folder generated by the fabric8-maven-plugin");
            }
        }
    }

    private void findOpenShiftBuildConfigName() {
        this.buildConfigName = null;
        this.buildConfigNamespace = null;
        this.buildName = null;

        if (job != null) {
            Class<?> clazz = findPluginClass("io.fabric8.jenkins.openshiftsync.BuildConfigProjectProperty");
            if (clazz == null) {
                listener.getLogger().println("No jenkins sync plugin installed as we couldn't find the BuildConfigProjectProperty class");
            }

            if (clazz != null) {
                Object cause = job.getProperty(clazz);
                if (cause != null) {
                    try {
                        this.buildConfigName = BeanUtils.getProperty(cause, "name");
                        this.buildConfigNamespace = BeanUtils.getProperty(cause, "namespace");
                        listener.getLogger().println("Found BuildConfig name " + this.buildConfigName + " namespace " + this.buildConfigNamespace + " from BuildConfigProjectProperty");

                    } catch (Exception e) {
                        listener.getLogger().println("Failed to get property `name` on bean " + cause + " due to: " + e);
                    }
                } else {
                    listener.getLogger().println("The Job has not been enriched by the jenkins-sync plugin with the BuildConfigProjectProperty to make it easy to detect the BuildConfig name");
                }
            }
        }
        if (run != null) {
            Class<?> clazz = findPluginClass("io.fabric8.jenkins.openshiftsync.BuildCause");
            if (clazz == null) {
                listener.getLogger().println("No jenkins sync plugin installed as we couldn't find the BuildCause class");
            }

            if (clazz != null) {
                Object cause = run.getCause(clazz);
                if (cause != null) {
                    try {
                        this.buildName = BeanUtils.getProperty(cause, "name");
                        listener.getLogger().println("Found Build name " + this.buildName + " from BuildCause");
                    } catch (Exception e) {
                        listener.getLogger().println("Failed to get property `name` on bean " + cause + " due to: " + e);
                    }
                } else {
                    listener.getLogger().println("The Run has not been enriched by the jenkins-sync plugin with the BuildCause to make it easy to detect the Build name");
                }
            }
        }
        // lets use the job name instead
        if (this.buildConfigName == null && job != null) {
            String name = job.getName();
            if (name != null) {
                String[] split = name.split("/");
                if (split.length == 1) {
                    this.buildConfigName = split[0];
                } else if (split.length > 1) {
                    int last = split.length - 1;
                    // lets ignore master branch builds
                    if (split[last].equals("master") && last > 0) {
                        last--;
                    }
                    this.buildConfigName = split[last];
                    if (this.buildConfigNamespace == null && last > 0) {
                        // lets try default the namespace to the previous path
                        this.buildConfigNamespace = split[last - 1];
                    }
                }
            }
        }
        if (this.buildConfigName != null && this.buildName == null) {
            this.buildName = this.buildConfigName + "-" + run.getId();
        }
    }

    protected Class<?> findPluginClass(String className) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = ClassLoader.getSystemClassLoader();
            }
            if (classLoader == null) {
                throw new ClassNotFoundException();
            }
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            Jenkins instance = Jenkins.getInstance();
            PluginManager pluginManager = instance.getPluginManager();
            try {
                return pluginManager.uberClassLoader.loadClass(className);
            } catch (ClassNotFoundException e1) {
                // ignore
            }
        }
        return null;
    }

    private String createDefaultEnvironmentName(String environment) {
        if (StringUtils.isBlank(environment)) {
            return environment;
        }
        String[] split = environment.split("-");
        String answer = environment;
        if (split.length > 0) {
            answer = split[split.length - 1];
        }
        return StringUtils.capitalize(answer);
    }

    private boolean isOpenShift() {
        return openShiftClient().isAdaptable(OpenShiftClient.class);
    }

    private String getRegistry() {
        if (Strings.isNullOrBlank(step.getRegistry())) {
            // lets try and find an external docker registry in the users home namespace pipeline config
            KubernetesClient client = getKubernetes();
            ConfigMap cm = client.configMaps().inNamespace(this.buildConfigNamespace).withName(Constants.USERS_PIPELINE_CONFIGMAP_NAME).get();
            if (cm != null){
                Map<String, String> data = cm.getData();
                if (data != null){
                    String rs = data.get(Constants.EXTERNAL_DOCKER_REGISTRY_URL);
                    if (Strings.isNotBlank(rs)){
                        return rs;
                    }
                }
            }

            // fall back to namespace env vars to support old fabric8 version
            if (isOpenShift() && openShiftClient().supportsOpenShiftAPIGroup(OpenShiftAPIGroups.IMAGE)) {
                if (Strings.isNotBlank(env.get(Constants.OPENSHIFT_DOCKER_REGISTRY_SERVICE_HOST))){
                    return env.get(Constants.OPENSHIFT_DOCKER_REGISTRY_SERVICE_HOST) + ":" + env.get(Constants.OPENSHIFT_DOCKER_REGISTRY_SERVICE_PORT);
                }
            } else if (Strings.isNotBlank(env.get(Constants.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST))) {
                return env.get(Constants.FABRIC8_DOCKER_REGISTRY_SERVICE_HOST) + ":" + env.get(Constants.FABRIC8_DOCKER_REGISTRY_SERVICE_PORT);
            }
            return null;
        } else {
            return step.getRegistry();
        }
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

    public void addRegistryToImageNameIfNotPresent(Iterable<HasMetadata> items, String registry) {
        if (items != null) {
            for (HasMetadata item : items) {
                if (item instanceof KubernetesList) {
                    KubernetesList list = (KubernetesList) item;
                    addRegistryToImageNameIfNotPresent(list.getItems(), registry);
                } else if (item instanceof Template) {
                    Template template = (Template) item;
                    addRegistryToImageNameIfNotPresent(template.getObjects(), registry);
                } else if (item instanceof Pod) {
                    List<Container> containers = ((Pod) item).getSpec().getContainers();
                    prefixRegistryIfNotPresent(containers, registry);

                } else if (item instanceof ReplicationController) {
                    List<Container> containers = ((ReplicationController) item).getSpec().getTemplate().getSpec().getContainers();
                    prefixRegistryIfNotPresent(containers, registry);

                } else if (item instanceof ReplicaSet) {
                    List<Container> containers = ((ReplicaSet) item).getSpec().getTemplate().getSpec().getContainers();
                    prefixRegistryIfNotPresent(containers, registry);

                } else if (item instanceof DeploymentConfig) {
                    List<Container> containers = ((DeploymentConfig) item).getSpec().getTemplate().getSpec().getContainers();
                    prefixRegistryIfNotPresent(containers, registry);

                } else if (item instanceof Deployment) {
                    List<Container> containers = ((Deployment) item).getSpec().getTemplate().getSpec().getContainers();
                    prefixRegistryIfNotPresent(containers, registry);
                }

            }
        }
    }

    private void prefixRegistryIfNotPresent(List<Container> containers, String registry) {
        for (Container container : containers) {
            if (!hasRegistry(container.getImage())){
                container.setImage(registry+"/"+container.getImage());
            }
        }
    }

    /**
     * Checks to see if there's a registry name already provided in the image name
     *
     * Code influenced from <a href="https://github.com/rhuss/docker-maven-plugin/blob/master/src/main/java/org/jolokia/docker/maven/util/ImageName.java">docker-maven-plugin</a>
     * @param imageName the image name
     * @return true if the image name contains a registry
     */
    public static boolean hasRegistry(String imageName) {
        if (imageName == null) {
            throw new NullPointerException("Image name must not be null");
        }
        Pattern tagPattern = Pattern.compile("^(.+?)(?::([^:/]+))?$");
        Matcher matcher = tagPattern.matcher(imageName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(imageName + " is not a proper image name ([registry/][repo][:port]");
        }

        String rest = matcher.group(1);
        String[] parts = rest.split("\\s*/\\s*");
        String part = parts[0];

        return part.contains(".") || part.contains(":");
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
        addPropertiesFileToMap(new File("./src/main/fabric8/environmentToAnnotations.properties"), mapEnvVarToAnnotation);
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
     * <p>
     * This can happen if using Jenkins Workflow which doesn't seem to define BUILD_URL or GIT_URL for example
     *
     * @return the value of the environment variable name if it can be found or calculated
     */
    protected String tryDefaultAnnotationEnvVar(String envVarName) throws AbortException {

        ProjectConfig projectConfig = getProjectConfig();
        if (projectConfig != null){
            GitConfig gitConfig = getGitConfig();
            String repoName = projectConfig.getBuildName();
            String userEnvVar = "JENKINS_GOGS_USER";
            String username = env.get(userEnvVar);

            if (io.fabric8.utils.Objects.equal("BUILD_URL", envVarName)) {
                String jobUrl = projectConfig.getLink("Job");
                if (Strings.isNullOrBlank(jobUrl)) {
                    String name = projectConfig.getBuildName();
                    if (Strings.isNullOrBlank(name)) {
                        // lets try deduce the jenkins build name we'll generate
                        if (Strings.isNotBlank(repoName)) {
                            name = repoName;
                            if (Strings.isNotBlank(username)) {
                                name = ProjectRepositories.createBuildName(username, repoName);
                            } else {
                                listener.getLogger().println("Cannot auto-default BUILD_URL as there is no environment variable `" + userEnvVar + "` defined so we can't guess the Jenkins build URL");
                            }
                        }
                    }
                    if (Strings.isNotBlank(name)) {
                        String jenkinsUrl = KubernetesHelper.getServiceURLInCurrentNamespace(getKubernetes(), ServiceNames.JENKINS, "http", null, true);
                        jobUrl = URLUtils.pathJoin(jenkinsUrl, "/job", name);

                    }
                }
                if (Strings.isNotBlank(jobUrl)) {
                    String buildId = env.get("BUILD_ID");
                    if (Strings.isNotBlank(buildId)) {
                        jobUrl = URLUtils.pathJoin(jobUrl, buildId);
                    } else {
                        listener.getLogger().println("Cannot find BUILD_ID to create a specific jenkins build URL. So using: " + jobUrl);
                    }
                }
                return jobUrl;
            } else if (io.fabric8.utils.Objects.equal("GIT_URL", envVarName)) {
                String gitUrl = projectConfig.getLinks().get("Git");
                if (Strings.isNullOrBlank(gitUrl)) {
                    listener.getLogger().println("No Job link found in fabric8.yml so we cannot set the GIT_URL");
                } else {
                    if (gitUrl.endsWith(".git")) {
                        gitUrl = gitUrl.substring(0, gitUrl.length() - 4);
                    }
                    String gitCommitId = gitConfig.getCommit();
                    if (Strings.isNotBlank(gitCommitId)) {
                        gitUrl = URLUtils.pathJoin(gitUrl, "commit", gitCommitId);
                    }
                    return gitUrl;
                }

            } else if (io.fabric8.utils.Objects.equal("GIT_COMMIT", envVarName)) {
                String gitCommit = gitConfig.getCommit();
                if (Strings.isNullOrBlank(gitCommit)) {
                    listener.getLogger().println("No git commit found in git.yml so we cannot set the GIT_COMMIT");
                }
                return gitCommit;

            } else if (io.fabric8.utils.Objects.equal("GIT_BRANCH", envVarName)) {
                String gitBranch = gitConfig.getBranch();
                if (Strings.isNullOrBlank(gitBranch)) {
                    listener.getLogger().println("No git branch found in git.yml so we cannot set the GIT_BRANCH");
                }
                return gitBranch;

            }
        } else {
            listener.getLogger().println("No fabric8.yml so unable to add environment pod annotations");
        }
        return null;
    }

    protected static void addPropertiesFileToMap(File file, Map<String, String> answer) throws AbortException {
        if (file != null && file.isFile() && file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
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
            GitClient client = Git.with(listener, env).in(workspace).getClient();
            return client.withRepository(new GitInfoCallback(listener));
        } catch (Exception e) {
            throw new AbortException("Error getting git config " + e);
        }
    }

    public ProjectConfig getProjectConfig() {
        try {
            return ProjectConfigs.parseProjectConfig(readFile("fabric8.yml"));
        } catch (Exception e) {
            // its fine if no fabric8.yml file is found
            return null;
        }
    }

    private String readFile(String fileName) throws AbortException {
        try {
            try (InputStream is = workspace.child(fileName).read()) {
                return IOUtils.toString(is, Charsets.UTF_8);
            }
        } catch (Exception e) {
            throw new AbortException("Unable to read file " + fileName + ". " + e);
        }
    }

    public String getDeploymentEventJson(String resource, String environment, String environmentName) throws IOException {
        DeploymentEventDTO event = new DeploymentEventDTO();

        GitConfig config = getGitConfig();

        event.setAuthor(config.getAuthor());
        event.setCommit(config.getCommit());
        event.setNamespace(environment);
        event.setEnvironment(environmentName);
        event.setApp(env.get("JOB_NAME"));
        event.setResource(resource);
        event.setVersion(env.get("VERSION"));

        ObjectMapper mapper = JsonUtils.createObjectMapper();
        return mapper.writeValueAsString(event);
    }

    public KubernetesClient getKubernetes() {
        if (kubernetes == null) {
            kubernetes = new DefaultKubernetesClient();
        }
        return kubernetes;
    }
}
