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

class Cube implements Serializable {

    private org.jenkinsci.plugins.workflow.cps.CpsScript script


    public Cube(org.jenkinsci.plugins.workflow.cps.CpsScript script) {
        this.script = script
    }

    public String getCurrentNamespace() {
        return script.currentNamespace()
    }

    public Namespace namespace() {
        return new Namespace(this)
    }

    public Environment environment() {
        return new Environment(this)
    }

    public static class Namespace implements Serializable {
        private final Cube cube

        private String cloud

        private String name
        private String prefix

        private Map<String, String> labels
        private Map<String, String> annotations

        private Boolean namespaceLazyCreateEnabled = true
        private Boolean namespaceDestroyEnabled = true

        Namespace(Cube cube) {
            this.cube = cube
        }

        public Namespace withCloud(String cloud) {
            this.cloud = cloud
            this
        }

        public Namespace withName(String name) {
            this.name = name
            return this
        }

        public Namespace withPrefix(String prefix) {
            this.prefix = prefix
            return this
        }

        public Namespace addToLabels(String key, String value) {
            if (labels == null) {
                labels = new HashMap<>();
            }
            labels.put(key, value)
            return this
        }

        public Namespace addToAnnotations(String key, String value) {
            if (annotations == null) {
                annotations = new HashMap<>();
            }
            labels.put(key, value)
            annotations this
        }

        public Namespace withLazyCreateEnabled(namespaceLazyCreateEnabled = true) {
            this.namespaceLazyCreateEnabled = namespaceLazyCreateEnabled
            return this
        }

        public Namespace withDestroyEnabled(namespaceDestroyEnabled = true) {
            this.namespaceDestroyEnabled = namespaceDestroyEnabled
            return this
        }

        public <V> V inside(Closure<V> body) {
            cube.script.arquillianCubeKubernetesNamespace(name: name, prefix: prefix, labels: labels, annotations: annotations, namespaceLazyCreateEnabled: namespaceDestroyEnabled, namespaceDestroyEnabled: namespaceDestroyEnabled) {
                body()
            }
        }
    }


    public static class Environment implements Serializable {

        private final Cube cube

        private String cloud

        private String name
        private String prefix

        private Map<String, String> labels
        private Map<String, String> annotations

        private Boolean namespaceLazyCreateEnabled = true
        private Boolean namespaceCleanupEnabled = null
        private Boolean namespaceDestroyEnabled = null

        private String environmentSetupScriptUrl;
        private String environmentTeardownScriptUrl;

        private String environmentConfigUrl;
        private List<String> environmentDependencies;


        private Long waitTimeout;
        private List<String> waitForServiceList;

        Environment(Cube cube) {
            this.cube = cube
        }

        public Environment withCloud(String cloud) {
            this.cloud = cloud
            this
        }

        public Environment withName(String name) {
            this.name = name
            return this
        }

        public Environment withPrefix(String prefix) {
            this.prefix = prefix
            return this
        }

        public Environment addToLabels(String key, String value) {
            if (labels == null) {
                labels = new HashMap<>();
            }
            labels.put(key, value)
            return this
        }

        public Environment addToAnnotations(String key, String value) {
            if (annotations == null) {
                annotations = new HashMap<>();
            }
            labels.put(key, value)
            annotations this
        }

        public Environment withSetupScriptUrl(String environmentSetupScriptUrl) {
            this.environmentSetupScriptUrl = environmentSetupScriptUrl
            return this
        }

        public Environment withTeardownScriptUrl(String environmentTeardownScriptUrl) {
            this.environmentTeardownScriptUrl = environmentTeardownScriptUrl
            return this
        }

        public Environment withConfigUrl(String environmentConfigUrl) {
            this.environmentConfigUrl = environmentConfigUrl
            return this
        }

        public Environment withDependencies(List<String> environmentDependencies) {
            this.environmentDependencies = environmentDependencies
            return this
        }

        public Environment addToDependencies(String dep) {
            if (this.environmentDependencies == null) {
                this.environmentDependencies = new ArrayList<>();
            }
            this.environmentDependencies.add(dep)
            return this
        }

        public Environment withWaitTimeout(Long waitTimeout) {
            this.waitTimeout = waitTimeout
            return this
        }

        public Environment withServicesToWait(List<String> waitForServiceList) {
            this.waitForServiceList = waitForServiceList
            return this
        }

        public Environment addToServicesToWait(String service) {
            if (this.waitForServiceList == null) {
                this.waitForServiceList = new ArrayList<>();
            }
            this.waitForServiceList.add(service)
            return this
        }

        public Environment withLazyCreateEnabled(namespaceLazyCreateEnabled = true) {
            this.namespaceLazyCreateEnabled = namespaceLazyCreateEnabled
            return this
        }

        public Environment withNamespaceDestroyEnabled(namespaceDestroyEnabled = true) {
            this.namespaceDestroyEnabled = namespaceDestroyEnabled
            return this
        }

        public Environment withNamespaceCleanupEnabled(namespaceCleanupEnabled = true) {
            this.namespaceCleanupEnabled = namespaceCleanupEnabled
            return this
        }

        public <V> V create() {
            cube.script.arquillianCubeKubernetesCreateEnv(name: name, prefix: prefix, labels: labels, annotations: annotations,
                    environmentSetupScriptUrl: environmentSetupScriptUrl,
                    environmentTeardownScriptUrl: environmentTeardownScriptUrl,
                    environmentConfigUrl: environmentConfigUrl,
                    environmentDependencies: environmentDependencies,
                    waitTimeout: waitTimeout,
                    waitForServiceList: waitForServiceList,
                    namespaceLazyCreateEnabled: namespaceLazyCreateEnabled,
                    namespaceCleanupEnabled: namespaceCleanupEnabled,
                    namespaceDestroyEnabled: namespaceDestroyEnabled)
        }
    }
}