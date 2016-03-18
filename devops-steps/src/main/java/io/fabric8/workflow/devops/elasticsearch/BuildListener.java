package io.fabric8.workflow.devops.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Send Jenkins build information to Elasticsearch.
 */
@Extension
public class BuildListener extends RunListener<Run> {
    private static final Logger LOG = Logger.getLogger(BuildListener.class.getName());

    public BuildListener() {
    }

    @Override
    public synchronized void onCompleted(Run run, TaskListener listener) {
        final Build build = createBuild(run, listener);
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            String json = mapper.writeValueAsString(build);

            ElasticsearchClient.sendEvent(json, "build", listener);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error when sending build data: " + build, e);
        }
    }

    private Build createBuild(Run run, TaskListener listener) {
        final Job job = run.getParent();

        EnvVars environment = null;
        try {
            environment = run.getEnvironment(listener);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error getting environment", e);
        } catch (InterruptedException e) {
            LOG.log(Level.WARNING, "Error getting environment", e);
        }

        final Build build = new Build();
        build.setDuration(run.getDuration());
        build.setJobName(job.getFullName());
        Result result = run.getResult();
        if (result != null) {
            build.setResult(result.toString());
        }
        build.setStartTime(run.getStartTimeInMillis());
        build.setNumber(run.getNumber());
        build.setEnvironment(environment);
        build.setTimestamp(run.getTimestamp());

        return build;
    }
}
