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
import java.net.HttpURLConnection;
import java.net.URL;

import static com.jayway.restassured.RestAssured.given;

/**
 * Used to send events to elasticsearch
 */
public class ElasticsearchClient {

    public final static String DEPLOYMENT = "deployment";
    public final static String APPROVE = "approve";


    /**
     * Attempts to send an event to elasticsearch for the `pipeline` index
     *
     * @param json
     * @param index
     * @param listener
     * @return boolean whether event was created in elasticsearch.
     */
    public static String createEvent(String json, String index, TaskListener listener){
        // use env var names that are different from default Kuberenetes services so we can overide with an existing external Elasticsearch
        String protocol = Systems.getEnvVarOrSystemProperty("PIPELINE_ELASTICSEARCH_PROTOCOL","http");
        String server = Systems.getEnvVarOrSystemProperty("PIPELINE_ELASTICSEARCH_HOST","elasticsearch");
        String port = Systems.getEnvVarOrSystemProperty("ELASTICSEARCH_SERVICE_PORT", "9200");

        if (!isUrlReachable(protocol + "://" + server + ":" + port)){
            listener.getLogger().println("Unable to connect to Elasticsearch service. Check Elasticsearch is running in the correct namespace");
            return null;
        } else {
            listener.getLogger().println("Found Elasticsearch server, sending:"+json);
        }

        try {
            RestAssured.baseURI  = protocol + "://" + server + ":" + port + "/" + index + "/custom";
            Response r = given()
                    .contentType("application/json").
                            body(json).
                            when().
                            post("");

            ResponseBody body = r.getBody();
            //listener.getLogger().println("here: "+body.prettyPrint());
            if (body != null) {
                JsonPath path = body.jsonPath();
                if (path != null) {
                    Boolean created = path.get("created");
                    if (created != null && created == true) {
                        listener.getLogger().println("Event created");
                        return path.get("_id");
                    } else if (path.get("error") != null){
                        listener.error("Elasticsearch response: "+path.get("error.reason"));
                    }
                }
            }
            return null;
        } catch (Exception e){
            // handle exceptions as we dont want to abort the pipeline
            e.printStackTrace(listener.getLogger());
            listener.error("Failed to send event: "+json);

            return null;
        }
    }

    public static boolean updateEvent(String id, String json, String index, TaskListener listener) {
        // use env var names that are different from default Kuberenetes services so we can overide with an external Elasticsearch
        String protocol = Systems.getEnvVarOrSystemProperty("PIPELINE_ELASTICSEARCH_PROTOCOL","http");
        String server = Systems.getEnvVarOrSystemProperty("PIPELINE_ELASTICSEARCH_HOST","elasticsearch");
        String port = Systems.getEnvVarOrSystemProperty("ELASTICSEARCH_SERVICE_PORT", "9200");
        json = "{\"doc\": "+ json + "}";

        if (!isUrlReachable(protocol + "://" + server + ":" + port)){
            listener.getLogger().println("Unable to connect to Elasticsearch service. Check Elasticsearch is running in the correct namespace");
            return false;
        } else {
            listener.getLogger().println("Found Elasticsearch server, sending:"+json);
        }

        try {
            RestAssured.baseURI  = protocol + "://" + server + ":" + port + "/" + index + "/custom/" + id + "/_update";
            Response r = given()
                    .contentType("application/json").
                            body(json).
                            when().
                            post("");

            ResponseBody body = r.getBody();
            if (body != null) {
                JsonPath path = body.jsonPath();
                if (path != null) {
                    int successful = path.get("_shards.successful");
                    if (successful > 0) {
                        listener.getLogger().println("Event updated");
                        return true;
                    } else if (path.get("error") != null){
                        listener.error("Elasticsearch response: "+path.get("error.reason"));
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

    public static boolean isUrlReachable(String url){
        int timeout = Integer.parseInt(Systems.getEnvVarOrSystemProperty("ES_TIMEOUT","1000")); // default to 1 second
        try {
            HttpURLConnection.setFollowRedirects(false);
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("HEAD");

            con.setConnectTimeout(timeout);

            return (con.getResponseCode() == HttpURLConnection.HTTP_OK);
        } catch (java.net.SocketTimeoutException e) {
            return false;
        } catch (java.io.IOException e) {
            return false;
        }
    }
}
