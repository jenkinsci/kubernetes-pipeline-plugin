/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.kubernetes.pipeline.devops;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * The YAML structure to store the service URLs and deployment versions for an environment
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class EnvironmentRollout {
    private final String environmentName;
    private final Map<String, String> serviceUrls;
    private final Map<String, String> deploymentVersions;

    public EnvironmentRollout(String environmentName, Map<String, String> serviceUrls, Map<String, String> deploymentVersions) {
        this.environmentName = environmentName;
        this.serviceUrls = serviceUrls;
        this.deploymentVersions = deploymentVersions;
    }

    @Override
    public String toString() {
        return "EnvironmentRollout{" +
                "environmentName='" + environmentName + '\'' +
                ", serviceUrls=" + serviceUrls +
                ", deploymentVersions=" + deploymentVersions +
                '}';
    }

    public String getEnvironmentName() {
        return environmentName;
    }

    public Map<String, String> getServiceUrls() {
        return serviceUrls;
    }

    public Map<String, String> getDeploymentVersions() {
        return deploymentVersions;
    }
}
