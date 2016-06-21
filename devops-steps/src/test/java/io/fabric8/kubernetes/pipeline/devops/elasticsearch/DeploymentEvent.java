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

package io.fabric8.kubernetes.pipeline.devops.elasticsearch;

import java.util.*;

public class DeploymentEvent extends BaseSendEvent {

    /**
     * Java main to test creating events in elasticsearch.  Set the following ENV VARS to point to a local ES running in OpenShift
     *
     * PIPELINE_ELASTICSEARCH_HOST=elasticsearch.vagrant.f8
     * ELASTICSEARCH_SERVICE_PORT=80
     */
    public static void main(String[] args) {
        final DeploymentEventDTO build = createTestDeploymentEvent();
        send(build, ElasticsearchClient.DEPLOYMENT);
    }

    private static DeploymentEventDTO createTestDeploymentEvent(){
        DeploymentEventDTO event = new DeploymentEventDTO();

        event.setTimestamp(new Date());
        event.setEnvironment("test-staging");
        event.setApp("mytestapp");
        event.setAuthor("tester");
        event.setCommit("1234abcd");
        event.setNamespace("test-namespace");
        event.setResource("pod");
        event.setVersion("123");

        return event;
    }
}