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

package io.fabric8.workflow.devops;

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.AbortException;
import hudson.model.TaskListener;
import io.fabric8.utils.Strings;
import io.fabric8.workflow.devops.elasticsearch.ElasticsearchClient;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;


public class CreateEventStepExecution extends AbstractSynchronousStepExecution<String>{

    @Inject
    private transient CreateEventStep step;

    @StepContextParameter
    private transient TaskListener listener;

    @Override
    public String run() throws Exception {

        String json = step.getJson();
        String index = step.getIndex();

        if (Strings.isNullOrBlank(json)) {
            throw new AbortException("No JSON payload found");
        }
        // validate JSON structure
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.readTree(json);

        String id = ElasticsearchClient.createEvent(json, index, listener);
        if (Strings.isNullOrBlank(id)){
            throw new AbortException("Error creating event in elasticsearch");
        }

        return id;

    }

}
