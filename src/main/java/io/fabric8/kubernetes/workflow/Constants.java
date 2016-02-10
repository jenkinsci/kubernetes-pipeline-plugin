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

public class Constants {
    static final String RUNNING_PHASE = "Running";
    static final String SUCCEEDED_PHASE = "Succeeded";
    static final String FAILED_PHASE = "Failed";
    static final String SPACE = " ";
    static final String SEMICOLN = ";";
    static final String EMPTY = "";
    static final String EXIT = "exit";
    static final String NEWLINE = "\n";
    static final String UTF_8 = "UTF-8";

    static final String VOLUME_PREFIX = "volume-";

    static final char CTRL_C = '\u0003';

    static final String DEFAULT_DOCKER_REGISTRY = "DEFAULT_DOCKER_REGISTRY";

    static final String HOSTNAME = "HOSTNAME";
    static final String KUBERNETES_HOSTNAME = "kubernetes.io/hostname";

}
