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

import com.google.inject.Inject;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Functions;
import hudson.model.TaskListener;
import hudson.os.PosixException;
import hudson.util.IOUtils;
import hudson.util.io.Archiver;
import hudson.util.io.ArchiverFactory;
import io.fabric8.docker.api.model.ImageInspect;
import io.fabric8.docker.client.DefaultDockerClient;
import io.fabric8.docker.client.DockerClient;
import io.fabric8.docker.client.utils.DockerIgnorePathMatcher;
import io.fabric8.docker.dsl.EventListener;
import io.fabric8.docker.dsl.OutputHandle;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.tools.tar.TarEntry;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static io.fabric8.workflow.core.Constants.DEFAULT_IGNORE_PATTERNS;
import static io.fabric8.workflow.core.Constants.DOCKER_IGNORE;
import static org.apache.tools.tar.TarConstants.LF_SYMLINK;

public class BuildImageStepExecution extends AbstractSynchronousStepExecution<ImageInspect> {

    private static final transient Logger LOGGER = Logger.getLogger(BuildImageStepExecution.class.getName());

    @Inject
    private BuildImageStep step;

    @StepContextParameter
    private FilePath workspace;
    @StepContextParameter
    private TaskListener listener;
    @StepContextParameter
    private transient EnvVars env;

    @Override
    protected ImageInspect run() throws Exception {
        return workspace.getChannel().call(new MasterToSlaveCallable<ImageInspect, Exception>() {
            @Override
            public ImageInspect call() throws Exception {
                ExecutorService executorService = Executors.newFixedThreadPool(2);
                try {
                    Future<Boolean> createTarFuture;
                    Future<ImageInspect> buildImageFuture;
                    try (PipedInputStream pin = new PipedInputStream();
                         PipedOutputStream pout = new PipedOutputStream(pin)) {
                        createTarFuture = executorService.submit(new CreateTarTask(pout));
                        buildImageFuture = executorService.submit(new BuildImageTask(pin));

                        //Wait until the streams can be safely closed.
                        if (!createTarFuture.get(step.getTimeout(), TimeUnit.MILLISECONDS)) {
                            listener.getLogger().println("Failed to create docker image tarball.");
                        }
                    }

                    ImageInspect imageInspect = buildImageFuture.get(step.getTimeout(), TimeUnit.MILLISECONDS);
                    if (imageInspect == null) {
                        throw new RuntimeException("Failed to build docker image.");
                    } else {
                        return imageInspect;
                    }
                } finally {
                    executorService.shutdown();
                    if (executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                        executorService.shutdownNow();
                    }
                }
            }
        });
    }

    private class BuildImageTask implements Callable<ImageInspect> {

        private final InputStream inputStream;

        private BuildImageTask(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public ImageInspect call() throws Exception {
            OutputHandle handle = null;
            try (DockerClient client = new DefaultDockerClient(step.getDockerConfig())) {
                final BlockingQueue queue = new LinkedBlockingQueue();
                listener.getLogger().println("Building image: [" + step.getName() + "].");
                handle = client.image().build()
                        .withRepositoryName(step.getName())
                        .removingIntermediateOnSuccess()
                        .usingListener(new EventListener() {
                            @Override
                            public void onSuccess(String s) {
                                listener.getLogger().println(s);
                                queue.add(true);
                            }

                            @Override
                            public void onError(String s) {
                                queue.add(new RuntimeException("Failed to build image. Error:" + s));
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                queue.add(new RuntimeException("Failed to build image. Error:" + throwable));
                            }

                            @Override
                            public void onEvent(String s) {
                                listener.getLogger().println(s);
                            }

                        }).fromTar(inputStream);

                Object result = queue.poll(step.getTimeout(), TimeUnit.MILLISECONDS);
                if (result == null) {
                    throw new RuntimeException("Timed out (" + step.getTimeout() + "ms)building docker image.");
                } else if (result instanceof Throwable) {
                    throw new RuntimeException((Throwable) result);
                } else {
                    return client.image().withName(step.getName()).inspect();
                }
            } catch (Throwable t) {
                t.printStackTrace(listener.getLogger());
                return null;
            } finally {
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
            try {
                FilePath path = workspace.child(step.getPath());
                DockerIgnorePathMatcher matcher = createMatcher(path);
                FileFilter dockerIgnoreFilter = new DockerIgnoreFileFilter(matcher);
                listener.getLogger().println("Creating tar from path: [" + path.getRemote() + "]:");
                for (FilePath c : path.list(dockerIgnoreFilter)) {
                    listener.getLogger().println("\t" + c.getRemote());
                }
                path.archive(new DockerArchiverFactory(matcher), outputStream, dockerIgnoreFilter);
                outputStream.flush();
                outputStream.close();
                return true;
            } catch (Throwable t) {
                t.printStackTrace(listener.getLogger());
                return false;
            }
        }
    }

    private class DockerIgnoreFileFilter implements FileFilter, Serializable {
        private final DockerIgnorePathMatcher dockerIgnorePathMatcher;

        private DockerIgnoreFileFilter(DockerIgnorePathMatcher dockerIgnorePathMatcher) {
            this.dockerIgnorePathMatcher = dockerIgnorePathMatcher;
        }

        @Override
        public boolean accept(File pathname) {
            return !dockerIgnorePathMatcher.matches(pathname.toPath().toAbsolutePath());
        }
    }


    private class DockerArchiverFactory extends ArchiverFactory {

        private final DockerIgnorePathMatcher matcher;

        public DockerArchiverFactory(DockerIgnorePathMatcher matcher) {
            this.matcher = matcher;
        }

        @Override
        public Archiver create(OutputStream out) throws IOException {
            return new DockerImageArchiver(out, matcher);
        }
    }

    private static class DockerImageArchiver extends Archiver {

            private final byte[] buf = new byte[8192];
            private final TarArchiveOutputStream tar;
            private final DockerIgnorePathMatcher dockerIgnore;

            DockerImageArchiver(OutputStream out, DockerIgnorePathMatcher dockerIgnore) {
                this.dockerIgnore = dockerIgnore;
                tar = new TarArchiveOutputStream(out);
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            }

            @Override
            public void visitSymlink(File link, String target, String relativePath) throws IOException {
                TarArchiveEntry e = new TarArchiveEntry(relativePath, LF_SYMLINK);
                try {
                    int mode = IOUtils.mode(link);
                    if (mode != -1) {
                        e.setMode(mode);
                    }
                } catch (PosixException x) {
                    // ignore
                }

                try {
                    StringBuffer linkName = (StringBuffer) LINKNAME_FIELD.get(e);
                    linkName.setLength(0);
                    linkName.append(target);
                } catch (IllegalAccessException x) {
                    throw new IOException("Failed to set linkName", x);
                }

                tar.putArchiveEntry(e);
                entriesWritten++;
            }

            @Override
            public boolean understandsSymlink() {
                return true;
            }

        public void visit(File file, String relativePath) throws IOException {
            if (recursiveMatch(dockerIgnore, file.toPath())) {
                 return;
            }

            if (relativePath.contains("/")) {
                relativePath = relativePath.substring(relativePath.indexOf("/") + 1);
            } else {
                relativePath = ".";
            }

            if(Functions.isWindows()) {
                relativePath = relativePath.replace('\\', '/');
            }

            if(file.isDirectory()) {
                relativePath += '/';
            }

            TarArchiveEntry te = new TarArchiveEntry(file);
            te.setName(relativePath);

            int mode = IOUtils.mode(file);
            if (mode!=-1) {
                te.setMode(mode);
            }
            te.setModTime(file.lastModified());

            if(!file.isDirectory()) {
                te.setSize(file.length());
            }

            tar.putArchiveEntry(te);

            if (!file.isDirectory()) {
                FileInputStream in = new FileInputStream(file);
                try {
                    int len;
                    while((len=in.read(buf))>=0)
                        tar.write(buf,0,len);
                } finally {
                    in.close();
                }
            }

            tar.closeArchiveEntry();
            entriesWritten++;
        }

        public void close() throws IOException {
            tar.close();
        }

        private static final Field LINKNAME_FIELD = getTarEntryLinkNameField();

        private static Field getTarEntryLinkNameField() {
            try {
                Field f = TarEntry.class.getDeclaredField("linkName");
                f.setAccessible(true);
                return f;
            } catch (SecurityException e) {
                throw new AssertionError(e);
            } catch (NoSuchFieldException e) {
                throw new AssertionError(e);
            }
        }
    }

    private DockerIgnorePathMatcher createMatcher(FilePath root) throws IOException, InterruptedException {

        FilePath dockerIgnorePath = root.child(DOCKER_IGNORE);

        Set<String> ignorePatterns = new LinkedHashSet<>();

        if (dockerIgnorePath.exists()) {


            ignorePatterns.addAll(readAllLines(dockerIgnorePath));
        }

        if (step.getIgnorePatterns() != null && !step.getIgnorePatterns().isEmpty()) {
            ignorePatterns.addAll(step.getIgnorePatterns());
        }

        if (ignorePatterns.isEmpty()) {
            ignorePatterns.addAll(Arrays.asList(DEFAULT_IGNORE_PATTERNS));
        }

        return new DockerIgnorePathMatcher(apply(root, ignorePatterns));
    }


    public static List<String> readAllLines(FilePath path)
            throws IOException, InterruptedException {
        try (InputStream is = path.read();
             InputStreamReader isr = new InputStreamReader(is);
             BufferedReader reader = new BufferedReader(isr)) {
            List<String> result = new ArrayList<>();
            for (; ; ) {
                String line = reader.readLine();
                if (line == null)
                    break;
                result.add(line);
            }
            return result;
        }
    }

    private static List<String> apply(FilePath path, Collection<String> patterns) {
        return apply(path, patterns.toArray(new String[patterns.size()]));
    }

    private static List<String> apply(FilePath path, String... patterns) {
        List<String> result = new ArrayList<>();
        for (String p : patterns) {
            result.add(path.getRemote().endsWith(File.separator) ? path + p : path + File.separator + p);
        }
        return result;
    }

    private static boolean recursiveMatch(DockerIgnorePathMatcher match, Path file) {
        if (match.matches(file)) {
            return true;
        } else if (file.getParent() != null) {
            return recursiveMatch(match, file.getParent());
        } else {
            return false;
        }
    }
}
