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
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Cause;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Send Jenkins build information to Elasticsearch.
 */
@Extension
public class BuildListener extends RunListener<Run> {
    private static final Logger LOG = Logger.getLogger(BuildListener.class.getName());
    private ObjectMapper mapper = JsonUtils.createObjectMapper();

    public BuildListener() {
    }

    @Override
    public synchronized void onCompleted(Run run, TaskListener listener) {
        final BuildDTO build = createBuild(run, listener);
        try {
            String json = mapper.writeValueAsString(build);
            ElasticsearchClient.createEvent(json, "build", listener);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error when sending build data: " + build, e);
        }
    }

    private BuildDTO createBuild(Run run, TaskListener listener) {
        final Job job = run.getParent();

        EnvVars environment = null;
        try {
            environment = run.getEnvironment(listener);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error getting environment", e);
        } catch (InterruptedException e) {
            LOG.log(Level.WARNING, "Error getting environment", e);
        }

        final BuildDTO build = new BuildDTO();
        build.setDuration(run.getDuration());
        build.setApp(job.getFullName());
        Result result = run.getResult();
        if (result != null) {
            build.setBuildResult(result.toString());
        }
        build.setStartTime(new Date(run.getStartTimeInMillis()));
        build.setBuildNumber(run.getNumber());
        build.setEnvVars(environment);
        Calendar timestamp = run.getTimestamp();
        if (timestamp != null) {
            build.setTimestamp(timestamp.getTime());
        }
        build.setBuildUrl(run.getUrl());
        List<Cause> causes = run.getCauses();
        for (Cause cause : causes) {
            CauseDTO causeDTO = new CauseDTO(cause);
            if (causeDTO != null) {
                build.addCause(causeDTO);
            }
        }
        return build;
    }
}
