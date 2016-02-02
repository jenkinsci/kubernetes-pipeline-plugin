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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.AbortException;
import hudson.model.TaskListener;
import io.fabric8.kubernetes.workflow.elasticsearch.ElasticsearchClient;
import io.fabric8.utils.Strings;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;


public class SendEventStepExecution extends AbstractSynchronousStepExecution<String>{

    @Inject
    private transient SendEventStep step;

    @StepContextParameter
    private transient TaskListener listener;

    @Override
    public String run() throws Exception {

        String json = step.getJson();
        String elasticsearchType = step.getElasticsearchType();

        if (Strings.isNullOrBlank(json)) {
            throw new AbortException("No JSON payload found");
        }
        // validate JSON structure
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.readTree(json);

        if (!ElasticsearchClient.sendEvent(json,elasticsearchType,listener)){
            throw new AbortException("Error sending event to elasticsearch");
        }

        return "OK";

    }

}
