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
    private Map<String, String> envVars;
    private String buildUrl;
    private List<CauseDTO> causes = new ArrayList<>();

    public BuildDTO() {
    }

    public BuildDTO(Date timestamp, int buildNumber, String app, String buildResult, Date startTime, long duration, Map<String, String> envVars) {
        this.timestamp = timestamp;
        this.buildNumber = buildNumber;
        this.app = app;
        this.buildResult = buildResult;
        this.startTime = startTime;
        this.duration = duration;
        this.envVars = envVars;
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

    public Map<String, String> getEnvVars() {
        return envVars;
    }

    public void setEnvVars(Map<String, String> environment) {
        this.envVars = environment;
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
                ", envVars=" + envVars +
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
