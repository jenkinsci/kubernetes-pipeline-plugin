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
import io.fabric8.workflow.devops.elasticsearch.ApprovalEventDTO;
import io.fabric8.workflow.devops.elasticsearch.ElasticsearchClient;
import io.fabric8.workflow.devops.elasticsearch.JsonUtils;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;
import java.util.Date;


public class ApproveRequestedEventStepExecution extends AbstractSynchronousStepExecution<String>{

    @Inject
    private transient ApproveRequestedEventStep step;

    @StepContextParameter
    private transient TaskListener listener;

    private ObjectMapper mapper = JsonUtils.createObjectMapper();

    @Override
    public String run() throws Exception {

        if (Strings.isNullOrBlank(step.getApp())) {
            throw new AbortException("No App name provided");
        }

        if (Strings.isNullOrBlank(step.getEnvironment())) {
            throw new AbortException("No environment name provided");
        }
        ApprovalEventDTO approval = new ApprovalEventDTO();
        approval.setApp(step.getApp());
        approval.setEnvironment(step.getEnvironment());
        approval.setRequestedTime(new Date());

        String json = mapper.writeValueAsString(approval);

        String id = ElasticsearchClient.createEvent(json, ElasticsearchClient.APPROVE, listener);
        if (Strings.isNullOrBlank(id)){
            throw new AbortException("Error creating Approve event in elasticsearch");
        }

        return id;

    }
}
