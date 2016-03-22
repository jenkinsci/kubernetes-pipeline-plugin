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

package io.fabric8.workflow.devops.elasticsearch;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.response.ResponseBody;
import hudson.model.TaskListener;
import io.fabric8.utils.Systems;

import java.io.IOException;
import java.net.InetAddress;

import static com.jayway.restassured.RestAssured.given;

/**
 * Used to send events to elasticsearch
 */
public class ElasticsearchClient {

    public final static String DEPLOYMENT = "deployment";
    private final static String INDEX = "/pipeline/";

    /**
     * Attempts to send an event to elasticsearch for the `pipeline` index
     *
     * @param json
     * @param type
     * @param listener
     * @return boolean whether event was created in elasticsearch.
     */
    public static boolean sendEvent(String json, String type, TaskListener listener){
        String protocol = Systems.getEnvVarOrSystemProperty("PIPELINE_ELASTICSEARCH_PROTOCOL","http");
        String server = Systems.getEnvVarOrSystemProperty("PIPELINE_ELASTICSEARCH_HOST","elasticsearch");
        try {
            int timeout = Integer.parseInt(Systems.getEnvVarOrSystemProperty("ES_TIMEOUT","20"));
            InetAddress.getByName(server).isReachable(timeout);
        } catch (IOException e) {
            listener.getLogger().println("Unable to connect to Elasticsearch service. Check Elasticsearch is running in the correct namespace." +e.getMessage());
            return false;
        }

        String port = Systems.getEnvVarOrSystemProperty("ELASTICSEARCH_SERVICE_PORT", "9200");
        try {
            RestAssured.baseURI  = protocol + "://" + server + ":" + port + INDEX + type;
            Response r = given()
                    .contentType("application/json").
                            body(json).
                            when().
                            post("");

            ResponseBody body = r.getBody();
            if (body != null) {
                JsonPath path = body.jsonPath();
                if (path != null) {
                    Boolean created = path.get("created");
                    if (created != null) {
                        return created;
                    }
                }
            }
            return false;
        } catch (Exception e){
            // handle exceptions as we dont want to abort the pipeline
            e.printStackTrace(listener.getLogger());
            listener.error("Failed to send event: "+json);

            return false;
        }
    }

}
