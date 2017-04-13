package io.fabric8.kubernetes.pipeline.arquillian.cube.kubernetes;

import org.arquillian.cube.kubernetes.impl.SessionManager;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;

public class SessionManagerStopCallback extends BodyExecutionCallback.TailCall {

    private final transient SessionManager sessionManager;

    public SessionManagerStopCallback(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }



    @Override
    protected void finished(StepContext context) throws Exception {
        sessionManager.stop();
    }
}
