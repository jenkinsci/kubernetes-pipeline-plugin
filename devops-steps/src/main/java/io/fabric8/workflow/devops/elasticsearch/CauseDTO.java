/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.workflow.devops.elasticsearch;

import hudson.model.Cause;

/**
 */
public class CauseDTO extends DTOSupport {
    private String shortDescription;
    private String remoteAddr;
    private String remoteNote;
    private String userName;
    private String userId;
    private String upstreamProject;
    private String upstreamUrl;

    public CauseDTO() {
    }

    public CauseDTO(Cause cause) {
        this.shortDescription = cause.getShortDescription();
        if (cause instanceof Cause.UserIdCause) {
            Cause.UserIdCause userIdCause = (Cause.UserIdCause) cause;
            this.userId = userIdCause.getUserId();
            this.userName = userIdCause.getUserName();
        } else if (cause instanceof Cause.RemoteCause) {
            Cause.RemoteCause remoteCause = (Cause.RemoteCause) cause;
            this.remoteAddr = remoteCause.getAddr();
            this.remoteNote = remoteCause.getNote();
        } else if (cause instanceof Cause.UpstreamCause) {
            Cause.UpstreamCause upstreamCause = (Cause.UpstreamCause) cause;
            this.upstreamProject = upstreamCause.getUpstreamProject();
            this.upstreamUrl = upstreamCause.getUpstreamUrl();

        }
    }

    public String getRemoteAddr() {
        return remoteAddr;
    }

    public void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    public String getRemoteNote() {
        return remoteNote;
    }

    public void setRemoteNote(String remoteNote) {
        this.remoteNote = remoteNote;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getUpstreamProject() {
        return upstreamProject;
    }

    public void setUpstreamProject(String upstreamProject) {
        this.upstreamProject = upstreamProject;
    }

    public String getUpstreamUrl() {
        return upstreamUrl;
    }

    public void setUpstreamUrl(String upstreamUrl) {
        this.upstreamUrl = upstreamUrl;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
