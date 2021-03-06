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

package io.fabric8.kubernetes.pipeline.devops;

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.AbortException;
import hudson.model.TaskListener;
import io.fabric8.kubernetes.pipeline.devops.elasticsearch.ApprovalEventDTO;
import io.fabric8.kubernetes.pipeline.devops.elasticsearch.ElasticsearchClient;
import io.fabric8.kubernetes.pipeline.devops.elasticsearch.JsonUtils;
import io.fabric8.utils.Strings;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;
import java.util.Date;


public class ApproveReceivedEventStepExecution extends AbstractSynchronousStepExecution<String>{

    @Inject
    private transient ApproveReceivedEventStep step;

    @StepContextParameter
    private transient TaskListener listener;

    private ObjectMapper mapper = JsonUtils.createObjectMapper();

    private final String OK = "OK";

    @Override
    public String run() throws Exception {

        if (step.getApproved() == null) {
            throw new AbortException("No Approved boolean set");
        }

        if (Strings.isNullOrBlank(step.getId())) {
            // if we dont have an id to update ignore the request as it's likely elasticsearch isn't running
            listener.getLogger().println("No approve event id found.  Skipping approval event update request");
            return OK;
        }
        ApprovalEventDTO approval = new ApprovalEventDTO();
        approval.setApproved(step.getApproved());
        approval.setReceivedTime(new Date());

        String json = mapper.writeValueAsString(approval);

        Boolean success = ElasticsearchClient.updateEvent(step.getId(), json, ElasticsearchClient.APPROVE, listener);
        if (!success){
            throw new AbortException("Error updating Approve event id ["+step.getId()+"]");
        }

        return OK;

    }

}
