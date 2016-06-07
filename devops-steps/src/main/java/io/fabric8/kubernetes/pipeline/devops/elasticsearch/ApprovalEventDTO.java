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

import java.util.Date;

/**
 * Pipeline approval.
 */
public class ApprovalEventDTO extends DTOSupport {
    private Date timestamp = new Date();
    private String app;
    private String environment;
    private Boolean approved;
    private Date requestedTime;
    private Date receivedTime;

    public ApprovalEventDTO() {
    }

    public ApprovalEventDTO(Date timestamp, String app, String environment, Boolean approved, Date requestedTime, Date receivedTime) {
        this.timestamp = timestamp;
        this.app = app;
        this.environment = environment;
        this.approved = approved;
        this.requestedTime = requestedTime;
        this.receivedTime = receivedTime;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public Date getRequestedTime() {
        return requestedTime;
    }

    public void setRequestedTime(Date requestedTime) {
        this.requestedTime = requestedTime;
    }

    public Date getReceivedTime() {
        return receivedTime;
    }

    public void setReceivedTime(Date receivedTime) {
        this.receivedTime = receivedTime;
    }


    @java.lang.Override
    public java.lang.String toString() {
        return "ApprovalEventDTO{" +
                "timestamp=" + timestamp +
                ", app='" + app + '\'' +
                ", environment='" + environment + '\'' +
                ", approved=" + approved +
                ", requestedTime=" + requestedTime +
                ", receivedTime=" + receivedTime +
                '}';
    }
}
