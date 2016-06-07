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

package io.fabric8.kubernetes.pipeline;

import hudson.Proc;
import io.fabric8.kubernetes.client.dsl.ExecWatch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fabric8.workflow.core.Constants.CTRL_C;
import static io.fabric8.workflow.core.Constants.EXIT;
import static io.fabric8.workflow.core.Constants.NEWLINE;
import static io.fabric8.workflow.core.Constants.UTF_8;

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
        //What we actually do is send a ctrl-c to the current process and then exit the shell.
        watch.getInput().write(CTRL_C);
        watch.getInput().write(EXIT.getBytes(UTF_8));
        watch.getInput().write(NEWLINE.getBytes(UTF_8));
        watch.getInput().flush();
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
        return watch.getInput();
    }
}
