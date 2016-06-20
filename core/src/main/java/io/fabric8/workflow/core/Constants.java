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

package io.fabric8.workflow.core;

public class Constants {
    public static final String RUNNING_PHASE = "Running";
    public static final String SUCCEEDED_PHASE = "Succeeded";
    public static final String FAILED_PHASE = "Failed";
    public static final String SPACE = " ";
    public static final String SEMICOLN = ";";
    public static final String EMPTY = "";
    public static final String EXIT = "exit";
    public static final String NEWLINE = "\n";
    public static final String UTF_8 = "UTF-8";

    public static final String VOLUME_PREFIX = "volume-";

    public static final char CTRL_C = '\u0003';

    public static final String DOCKER_IGNORE = ".dockerignore";
    public static final String DEFAULT_DOCKER_REGISTRY = "DEFAULT_DOCKER_REGISTRY";
    public static final String FABRIC8_DOCKER_REGISTRY_SERVICE_HOST = "FABRIC8_DOCKER_REGISTRY_SERVICE_HOST";
    public static final String FABRIC8_DOCKER_REGISTRY_SERVICE_PORT = "FABRIC8_DOCKER_REGISTRY_SERVICE_PORT";

    public static final String HOSTNAME = "HOSTNAME";
    public static final String KUBERNETES_HOSTNAME = "kubernetes.io/hostname";

    public static final String[] DEFAULT_IGNORE_PATTERNS = {DOCKER_IGNORE, ".git", ".git/*"};

}
