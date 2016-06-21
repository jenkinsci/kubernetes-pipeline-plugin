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
import java.util.logging.Level;
import java.util.logging.Logger;

public class BaseSendEvent {
    private static final Logger LOG = Logger.getLogger(BuildListener.class.getName());

    /**
     * Java main to test creating events in elasticsearch.  Set the following ENV VARS to point to a local ES running in OpenShift
     *
     * PIPELINE_ELASTICSEARCH_HOST=elasticsearch.vagrant.f8
     * ELASTICSEARCH_SERVICE_PORT=80
     *
     * @param event to send to elasticsearch
     */
    public static void send (DTOSupport event, String type){

        hudson.model.BuildListener listener = new StreamBuildListener(System.out, Charset.defaultCharset());
        try {
            ObjectMapper mapper = JsonUtils.createObjectMapper();
            String json = mapper.writeValueAsString(event);
            String id = ElasticsearchClient.createEvent(json, type , listener);
            listener.getLogger().println("Added events id: " + id);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error when sending build data: " + event, e);
        }
    }

}