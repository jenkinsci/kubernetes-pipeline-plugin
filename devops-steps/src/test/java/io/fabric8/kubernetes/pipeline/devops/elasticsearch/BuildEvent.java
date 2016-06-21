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

import java.util.*;

public class BuildEvent extends BaseSendEvent{

    /**
     * Java main to test creating events in elasticsearch.  Set the following ENV VARS to point to a local ES running in OpenShift
     *
     * PIPELINE_ELASTICSEARCH_HOST=elasticsearch.vagrant.f8
     * ELASTICSEARCH_SERVICE_PORT=80
     */
    public static void main(String[] args) {
        final BuildDTO buildEvent = createTestBuildEvent();
        send(buildEvent, "build");
    }

    private static BuildDTO createTestBuildEvent(){
        BuildDTO event = new BuildDTO();
        Map<String, String> environment = new HashMap<>();
        environment.put("BUILD_DISPLAY_NAME","#1");
        environment.put("BUILD_ID","1");
        environment.put("BUILD_NUMBER","1");
        environment.put("BUILD_TAG","jenkins-jr-int3-1");
        environment.put("CLASSPATH","");
        environment.put("HUDSON_HOME","/var/jenkins_home");
        environment.put("HUDSON_SERVER_COOKIE","de93f00fb246988e");
        environment.put("JENKINS_HOME","/var/jenkins_home");
        environment.put("JENKINS_SERVER_COOKIE","de93f00fb246988e");
        environment.put("JOB_NAME","jr-int3");

        CauseDTO cause = new CauseDTO();
        cause.setUserId("annonymous");
        cause.setShortDescription("Started by user anonymous");
        List<CauseDTO> causeDTOList = Collections.singletonList(cause);

        event.setTimestamp(new Date());
        event.setStartTime(new Date());
        event.setBuildResult("SUCCESS");
        event.setEnvVars(environment);
        event.setApp("mytestapp");
        event.setCauses(causeDTOList);

        return event;
    }
}