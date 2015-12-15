package io.fabric8.kubernetes.workflow;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.LauncherDecorator;
import hudson.model.Computer;
import hudson.model.TaskListener;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fabric8.kubernetes.workflow.KubernetesFacade.createPod;
import static io.fabric8.kubernetes.workflow.KubernetesFacade.watch;

public class PodStepExecution extends AbstractStepExecutionImpl {

    @Inject
    private transient PodStep step;

    @StepContextParameter private transient FilePath workspace;
    @StepContextParameter private transient EnvVars env;
    @StepContextParameter private transient TaskListener listener;
    @StepContextParameter private transient Computer computer;

    private volatile BodyExecution body;

    @Override
    public boolean start() throws Exception {
        StepContext context = getContext();
        String podName = step.getPodName() + "-" + UUID.randomUUID().toString();
        final AtomicBoolean alive = new AtomicBoolean(false);
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        Pod pod = createPod(podName, step.getImage(), workspace.getRemote(), createPodEnv(), "cat");
        watch(podName, alive, started, finished, true);
        started.await();

        body = context.newBodyInvoker()
                .withContext(BodyInvoker
                        .mergeLauncherDecorators(getContext().get(LauncherDecorator.class), new PodExecDecorator(podName, alive, started, finished)))
                .withCallback(new BodyExecutionCallback() {
                    @Override
                    public void onSuccess(StepContext context, Object result) {
                        listener.error("Done");
                    }

                    @Override
                    public void onFailure(StepContext context, Throwable t) {
                        listener.error("Failed to execute step:"+t.getMessage());
                    }
                }).start();
        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {

    }

    private List<EnvVar> createPodEnv() throws IOException, InterruptedException {
        List<EnvVar> podEnv = new ArrayList<EnvVar>();
        EnvVars envReduced = new EnvVars(env);
        EnvVars envHost = computer.getEnvironment();
        envReduced.entrySet().removeAll(envHost.entrySet());

        for (Map.Entry<String,String> entry : envReduced.entrySet()) {
            podEnv.add(new EnvVar(entry.getKey(), entry.getValue(), null));
        }
        return podEnv;
    }
}
