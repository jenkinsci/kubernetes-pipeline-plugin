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
import java.io.InputStream;
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
import java.util.concurrent.CompletionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class BuildImageStepExecution extends AbstractSynchronousStepExecution<Void> {

    private static final transient Logger LOGGER = Logger.getLogger(BuildImageStepExecution.class.getName());

    @Inject
    private transient BuildImageStep step;

    @StepContextParameter private transient FilePath workspace;
    @StepContextParameter private transient TaskListener listener;
    @StepContextParameter private transient EnvVars env;

    @Override
    protected Void run() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try (PipedInputStream pin = new PipedInputStream();
             PipedOutputStream pout = new PipedOutputStream(pin)) {

            Future<Boolean> createTarFuture = executorService.submit(new CreateTarTask(pout));
            Future<Boolean> buildImageFuture = executorService.submit(new BuildImageTask(pin));

            //Wait for the two tasks to complete.
            if (!createTarFuture.get(2, TimeUnit.MINUTES)) {
                listener.getLogger().println("Timed out creating docker image tarball.");
            } else if (!buildImageFuture.get(7, TimeUnit.MINUTES)) {
                listener.getLogger().println("Timed out building docker image.");
            } else {
                listener.getLogger().println("Image Build Successfully");
            }
        } finally {
            executorService.shutdown();
            if (executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        }

        return null; //Void
    }

    private class BuildImageTask implements Callable<Boolean> {

        private final InputStream inputStream;

        private BuildImageTask(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public Boolean call() throws Exception {
            OutputHandle handle = null;
            try {
                LOGGER.info("Start of BuildImageTask");
                Config config = new ConfigBuilder().build();
                DockerClient client = new DefaultDockerClient(config);
                final CountDownLatch buildFinished = new CountDownLatch(1);
                handle = client.image().build()
                        .withRepositoryName(step.getName())
                        .removingIntermediateOnSuccess()
                        .usingListener(new EventListener() {
                            @Override
                            public void onSuccess(String s) {
                                LOGGER.info("BuildImageTask Success:" + s);
                                listener.getLogger().println(s);
                                buildFinished.countDown();
                            }

                            @Override
                            public void onError(String s) {
                                LOGGER.info("BuildImageTask Error:" + s);
                                listener.error(s);
                                buildFinished.countDown();
                            }

                            @Override
                            public void onEvent(String s) {
                                LOGGER.info("BuildImageTask Event:" + s);
                                listener.getLogger().println(s);
                            }

                        }).fromTar(inputStream);

                return buildFinished.await(5, TimeUnit.MINUTES);
            } catch (Throwable t) {
                t.printStackTrace(listener.getLogger());
                return false;
            } finally {
                LOGGER.info("End of BuildImageTask");
                if (handle != null) {
                    handle.close();
                }
            }
        }
    }

    private class CreateTarTask implements Callable<Boolean> {
        private final OutputStream outputStream;

        private CreateTarTask(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public Boolean call() throws Exception {
            LOGGER.info("Start of BuildImageTask");
            try {
                listener.getLogger().printf("Creating tar from path: %s.", step.getPath());
                workspace.child(step.getPath()).archive(new DockerArchiverFactory(), outputStream, new TrueFileFilter());
                outputStream.flush();
                outputStream.close();
                return true;
            } catch (Throwable t) {
                t.printStackTrace(listener.getLogger());
                return false;
            } finally {
                LOGGER.info("End of BuildImageTask");
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
