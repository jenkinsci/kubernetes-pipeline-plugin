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


import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.model.StreamBuildListener;

import java.nio.charset.Charset;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import static junit.framework.TestCase.assertTrue;

public class UpdateApprovalEvent {
    private static final Logger LOG = Logger.getLogger(BuildListener.class.getName());

    /**
     * Java main to test updating events in elasticsearch.  Set the following ENV VARS to point to a local ES running in OpenShift
     *
     * PIPELINE_ELASTICSEARCH_HOST=elasticsearch.vagrant.f8
     * ELASTICSEARCH_SERVICE_PORT=80
     */
    public static void main(String[] args) {
        final ApprovalEventDTO approval = createTestApprovalEvent();
        hudson.model.BuildListener listener = new StreamBuildListener(System.out, Charset.defaultCharset());
        try {
            ObjectMapper mapper = JsonUtils.createObjectMapper();
            String json = mapper.writeValueAsString(approval);
            boolean success = ElasticsearchClient.updateEvent("AVVy4MSZ_YLNT2M2J-83", json, ElasticsearchClient.APPROVE , listener);
            assertTrue(success);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error when updating event: " + approval, e);
        }
    }

    private static ApprovalEventDTO createTestApprovalEvent(){
        ApprovalEventDTO event = new ApprovalEventDTO();
        event.setApproved(true);
        event.setReceivedTime(new Date());
        return event;
    }
}