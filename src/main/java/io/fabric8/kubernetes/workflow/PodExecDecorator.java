package io.fabric8.kubernetes.workflow;

import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import io.fabric8.kubernetes.client.dsl.ExecWatch;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fabric8.kubernetes.workflow.KubernetesFacade.deletePod;
import static io.fabric8.kubernetes.workflow.KubernetesFacade.exec;

public class PodExecDecorator extends LauncherDecorator implements Serializable {

    private final String podName;
    private final transient AtomicBoolean alive;
    private final transient CountDownLatch started;
    private final transient CountDownLatch finished;

    public PodExecDecorator(String podName, AtomicBoolean alive, CountDownLatch started, CountDownLatch finished) {
        this.podName = podName;
        this.alive = alive;
        this.started = started;
        this.finished = finished;
    }

    @Override
    public Launcher decorate(final Launcher launcher, Node node) {
        return new Launcher.DecoratedLauncher(launcher) {
            @Override
            public Proc launch(Launcher.ProcStarter starter) throws IOException {
                    ExecWatch execWatch = exec(podName, launcher.getListener().getLogger(), splitStatemets(starter.cmds()));
                    return new PodExecProc(podName, alive, finished, execWatch);
            }

            @Override
            public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
                deletePod(podName);
            }
        };
    }

    static String join(Collection<String> str) {
        return join(str.toArray(new String[str.size()]));
    }

    static String join(String... str) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        String lastPart = "";
        for (String c : str) {
            if (first) {
                first = false;
            } else if (!lastPart.endsWith("=")) {
                sb.append(" ");
            }
            sb.append(c);
            lastPart = c;
        }
        return sb.toString();
    }

    static String[] splitStatemets(List<String> commands) {
        List<String> result = new ArrayList<>();
        String full = join(commands);
        for (String part : full.split(";")) {
            result.add(part);
        }
        return result.toArray(new String[result.size()]);
    }
}
