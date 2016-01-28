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

import io.fabric8.docker.api.model.AuthConfig;
import io.fabric8.docker.client.Config;
import io.fabric8.docker.client.ConfigBuilder;
import io.fabric8.docker.client.utils.Utils;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;

import java.io.Serializable;

public class AbstractDockerStep extends AbstractStepImpl implements Serializable {

    private static final long serialVersionUID = -9155746436499494358L;
    private final String name;

    private final String username;
    private final String password;
    private final String email;


    public AbstractDockerStep(String name, String username, String password, String email) {
        this.name = name;
        this.username = username;
        this.password = password;
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public Config getDockerConfig() {
        Config config = new ConfigBuilder().build();
        AuthConfig fallbackAuthConfig = config.getAuthConfigs().get(Config.DOCKER_AUTH_FALLBACK_KEY);
        if (Utils.isNotNullOrEmpty(username)) {
            fallbackAuthConfig.setUsername(username);
        }

        if (Utils.isNotNullOrEmpty(password)) {
            fallbackAuthConfig.setPassword(password);
        }

        if (Utils.isNotNullOrEmpty(email)) {
            fallbackAuthConfig.setEmail(email);
        }
        return config;
    }
}
