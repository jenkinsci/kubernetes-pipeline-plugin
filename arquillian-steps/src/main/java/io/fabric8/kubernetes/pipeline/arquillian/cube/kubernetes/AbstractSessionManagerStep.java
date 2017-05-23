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

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class AbstractSessionManagerStep extends AbstractStep implements Serializable {

    private static final long serialVersionUID = 5588861066775717487L;

    protected final String name;
    protected final String prefix;

    protected final Map<String, String> labels;
    protected final Map<String, String> annotations;

    protected final String environmentSetupScriptUrl;
    protected final String environmentTeardownScriptUrl;

    protected final String environmentConfigUrl;
    protected final List<String> environmentDependencies;


    protected final Long waitTimeout;
    protected final List<String> waitForServiceList;

    protected final Boolean namespaceLazyCreateEnabled;
    protected final Boolean namespaceCleanupEnabled;
    protected final Boolean namespaceDestroyEnabled;

    @DataBoundConstructor
    public AbstractSessionManagerStep(String cloud, String name, String prefix, Map<String, String> labels, Map<String, String> annotations, String environmentSetupScriptUrl, String environmentTeardownScriptUrl, String environmentConfigUrl, List<String> environmentDependencies, Long waitTimeout, List<String> waitForServiceList, Boolean namespaceLazyCreateEnabled, Boolean namespaceCleanupEnabled, Boolean namespaceDestroyEnabled) {
        super(cloud);
        this.name = name;
        this.prefix = prefix;
        this.labels = labels;
        this.annotations = annotations;
        this.environmentSetupScriptUrl = environmentSetupScriptUrl;
        this.environmentTeardownScriptUrl = environmentTeardownScriptUrl;
        this.environmentConfigUrl = environmentConfigUrl;
        this.environmentDependencies = environmentDependencies;
        this.waitTimeout = waitTimeout;
        this.waitForServiceList = waitForServiceList;
        this.namespaceLazyCreateEnabled = namespaceLazyCreateEnabled != null ? namespaceLazyCreateEnabled : true;
        this.namespaceCleanupEnabled = namespaceCleanupEnabled;
        this.namespaceDestroyEnabled = namespaceDestroyEnabled;
    }

    public String getName() {
        return name;
    }

    public String getPrefix() {
        return prefix;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public Map<String, String> getAnnotations() {
        return annotations;
    }

    public String getEnvironmentSetupScriptUrl() {
        return environmentSetupScriptUrl;
    }

    public String getEnvironmentTeardownScriptUrl() {
        return environmentTeardownScriptUrl;
    }

    public String getEnvironmentConfigUrl() {
        return environmentConfigUrl;
    }

    public List<String> getEnvironmentDependencies() {
        return environmentDependencies;
    }

    public Long getWaitTimeout() {
        return waitTimeout;
    }

    public List<String> getWaitForServiceList() {
        return waitForServiceList;
    }

    public Boolean isNamespaceLazyCreateEnabled() {
        return namespaceLazyCreateEnabled;
    }


    public Boolean isNamespaceCleanupEnabled() {
        return namespaceCleanupEnabled;
    }

    public Boolean isNamespaceDestroyEnabled() {
        return namespaceDestroyEnabled;
    }
}
