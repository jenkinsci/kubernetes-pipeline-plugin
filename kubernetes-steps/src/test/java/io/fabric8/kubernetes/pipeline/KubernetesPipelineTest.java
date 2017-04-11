/*
 * The MIT License
 *
 * Copyright (c) 2016, Carlos Sanchez
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.fabric8.kubernetes.pipeline;

import org.apache.commons.compress.utils.IOUtils;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRuleNonLocalhost;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.net.InetAddress;
import java.net.URL;
import java.util.logging.Level;

import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.JenkinsLocationConfiguration;

import static io.fabric8.kubernetes.pipeline.KubernetesTestUtil.assumeMiniKube;
import static io.fabric8.kubernetes.pipeline.KubernetesTestUtil.miniKubeUrl;
import static org.junit.Assert.assertNotNull;

public class KubernetesPipelineTest {

    private static final String TESTING_NAMESPACE = "kubernetes-pipeline-plugin-test";

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Rule
    public LoggerRule logs = new LoggerRule().record(KubernetesCloud.class, Level.ALL);

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public JenkinsRuleNonLocalhost r = new JenkinsRuleNonLocalhost();

    private static KubernetesCloud cloud = new KubernetesCloud("kubernetes");

    @BeforeClass
    public static void configureCloud() throws Exception {
        // do not run if minikube is not running
        assumeMiniKube();

        cloud.setServerUrl(miniKubeUrl().toExternalForm());
        cloud.setNamespace(TESTING_NAMESPACE);
        KubernetesClient client = cloud.connect();
        // Run in our own testing namespace
        client.namespaces().createOrReplace(
                new NamespaceBuilder().withNewMetadata().withName(TESTING_NAMESPACE).endMetadata().build());
    }

    private void configureCloud(JenkinsRuleNonLocalhost r) throws Exception {
        // Slaves running in Kubernetes (minikube) need to connect to this server, so localhost does not work
        URL url = r.getURL();
        URL nonLocalhostUrl = new URL(url.getProtocol(), InetAddress.getLocalHost().getHostAddress(), url.getPort(),
                url.getFile());
        JenkinsLocationConfiguration.get().setUrl(nonLocalhostUrl.toString());

        r.jenkins.clouds.add(cloud);
    }

    @Test
    public void simpleMaven() throws Exception {
        configureCloud(r);
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("simpleMaven.groovy")
                , true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Apache Maven 3.3.9", b);
    }


    private String loadPipelineScript(String name) {
        try {
            return new String(IOUtils.toByteArray(getClass().getResourceAsStream(name)));
        } catch (Throwable t) {
            throw new RuntimeException("Could not read resource:["+name+"].");
        }
    }
}
