package io.fabric8.workflow.devops.elasticsearch;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Jenkins build.
 */
public class BuildDTO extends DTOSupport {
    private Date timestamp;
    private int buildNumber;
    private String app;
    private String buildResult;
    private Date startTime;
    private long duration;
    private Map<String, String> environment;
    private String buildUrl;
    private List<CauseDTO> causes = new ArrayList<>();

    public BuildDTO() {
    }

    public BuildDTO(Date timestamp, int buildNumber, String app, String buildResult, Date startTime, long duration, Map<String, String> environment) {
        this.timestamp = timestamp;
        this.buildNumber = buildNumber;
        this.app = app;
        this.buildResult = buildResult;
        this.startTime = startTime;
        this.duration = duration;
        this.environment = environment;
    }

    public void addCause(CauseDTO cause) {
        if (causes == null) {
            causes = new ArrayList<>();
        }
        causes.add(cause);
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public int getBuildNumber() {
        return buildNumber;
    }

    public void setBuildNumber(int buildNumber) {
        this.buildNumber = buildNumber;
    }

    public String getBuildResult() {
        return buildResult;
    }

    public void setBuildResult(String buildResult) {
        this.buildResult = buildResult;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public void setEnvironment(Map<String, String> environment) {
        this.environment = environment;
    }

    public Date getTimestamp() {
      return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "Build{" +
                "@timestamp" + timestamp +
                ", number=" + buildNumber +
                ", jobName='" + app + '\'' +
                ", result='" + buildResult + '\'' +
                ", startTime=" + startTime +
                ", duration=" + duration +
                ", environment=" + environment +
                '}';
    }

    public void setBuildUrl(String buildUrl) {
        this.buildUrl = buildUrl;
    }

    public String getBuildUrl() {
        return buildUrl;
    }

    public List<CauseDTO> getCauses() {
        return causes;
    }

    public void setCauses(List<CauseDTO> causes) {
        this.causes = causes;
    }
}
