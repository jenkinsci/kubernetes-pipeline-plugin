package io.fabric8.kubernetes.workflow;

import hudson.Proc;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.LogWatch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fabric8.kubernetes.workflow.KubernetesFacade.deletePod;

public class PodExecProc extends Proc {

    private final String podName;
    private final AtomicBoolean alive;
    private final CountDownLatch finished;
    private final ExecWatch watch;

    public PodExecProc(String podName, AtomicBoolean alive, CountDownLatch finished, ExecWatch watch) {
        this.podName = podName;
        this.watch = watch;
        this.alive = alive;
        this.finished = finished;
    }

    @Override
    public boolean isAlive() throws IOException, InterruptedException {
        return alive.get();
    }

    @Override
    public void kill() throws IOException, InterruptedException {
        deletePod(podName);
    }

    @Override
    public int join() throws IOException, InterruptedException {
        finished.await();
        return 1;
    }

    @Override
    public InputStream getStdout() {
        return watch.getOutput();
    }

    @Override
    public InputStream getStderr() {
        return watch.getOutput();
    }

    @Override
    public OutputStream getStdin() {
        return null;
    }
}
