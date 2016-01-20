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

package io.fabric8.kubernetes.workflow;

import com.google.inject.Inject;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.util.io.Archiver;
import hudson.util.io.ArchiverFactory;
import io.fabric8.docker.client.Config;
import io.fabric8.docker.client.ConfigBuilder;
import io.fabric8.docker.client.DefaultDockerClient;
import io.fabric8.docker.client.DockerClient;
import io.fabric8.docker.dsl.EventListener;
import io.fabric8.docker.dsl.OutputHandle;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Serializable;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class BuildImageStepExecution extends AbstractSynchronousStepExecution<Void> {

    @Inject
    private transient BuildImageStep step;

    @StepContextParameter private transient FilePath workspace;
    @StepContextParameter private transient TaskListener listener;
    @StepContextParameter private transient EnvVars env;

    @Override
    protected Void run() throws Exception {
        final CountDownLatch buildFinished = new CountDownLatch(1);
        Config config = new ConfigBuilder().build();
        DockerClient client = new DefaultDockerClient(config);
        OutputHandle handle = null;
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            PipedInputStream pin = new PipedInputStream();
            PipedOutputStream pout = new PipedOutputStream(pin);
            Future<Boolean> tarFuture = executorService.submit(new CreateTarTask(pout));
            handle = client.image().build()
                    .withRepositoryName(step.getName())
                    .removingIntermediateOnSuccess()
                    .usingListener(new EventListener() {
                        @Override
                        public void onSuccess(String s) {
                            listener.error(s);
                            listener.getLogger().flush();
                            buildFinished.countDown();
                        }

                        @Override
                        public void onError(String s) {
                            listener.error(s);
                            listener.getLogger().flush();
                            buildFinished.countDown();
                        }

                        @Override
                        public void onEvent(String s) {
                            listener.getLogger().println(s);
                            listener.getLogger().flush();
                        }

                    })
                    .fromTar(pin);
            listener.getLogger().flush();

            tarFuture.get(2, TimeUnit.MINUTES);
            buildFinished.await(5, TimeUnit.MINUTES);
        } catch (Throwable t) {
            t.printStackTrace(listener.getLogger());
        } finally {
            if (handle != null) {
                handle.close();
            }
            executorService.shutdown();
            if (executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        }

        return null; //Void
    }

    private class CreateTarTask implements Callable<Boolean> {
        private final OutputStream outputStream;

        private CreateTarTask(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public Boolean call() throws Exception {
            try {
                workspace.child(step.getPath()).archive(new DockerArchiverFactory(), outputStream, new TrueFileFilter());
                return true;
            } catch (Throwable t) {
                listener.error(t.getMessage());
                return false;
            }
        }
    }

    private class TrueFileFilter implements FileFilter, Serializable {
        @Override
        public boolean accept(File pathname) {
            return true;
        }
    }

    private class DockerArchiverFactory extends ArchiverFactory {

        @Override
        public Archiver create(OutputStream out) throws IOException {
            return new DockerImageArchiver(new TarArchiveOutputStream(out));
        }
    }

    private class DockerImageArchiver extends Archiver {

        private final TarArchiveOutputStream tout;

        private DockerImageArchiver(TarArchiveOutputStream tarOutputStream) {
            this.tout = tarOutputStream;
        }

        @Override
        public void close() throws IOException {
            tout.close();
        }

        @Override
        public void visit(final File rootFile, String relativePath) throws IOException {
            final Path root = rootFile.toPath();
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    final Path relativePath = root.relativize(file);
                    final TarArchiveEntry entry = new TarArchiveEntry(file.toFile());
                    entry.setName(relativePath.toString());
                    entry.setMode(TarArchiveEntry.DEFAULT_FILE_MODE);
                    entry.setSize(attrs.size());
                    tout.putArchiveEntry(entry);
                    Files.copy(file, tout);
                    tout.closeArchiveEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
