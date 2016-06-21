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

import java.util.Date;

public class CreateApprovalEvent extends BaseSendEvent {

    /**
     * Java main to test creating events in elasticsearch.  Set the following ENV VARS to point to a local ES running in OpenShift
     *
     * PIPELINE_ELASTICSEARCH_HOST=elasticsearch.vagrant.f8
     * ELASTICSEARCH_SERVICE_PORT=80
     */
    public static void main(String[] args) {
        final ApprovalEventDTO approval = createTestApprovalEvent();
        send(approval, ElasticsearchClient.APPROVE);
    }

    private static ApprovalEventDTO createTestApprovalEvent(){
        ApprovalEventDTO event = new ApprovalEventDTO();
        event.setEnvironment("test");
        event.setApp("mytestapp");
        event.setRequestedTime(new Date());

        return event;
    }
}