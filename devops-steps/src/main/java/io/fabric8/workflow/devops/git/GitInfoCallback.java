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

package io.fabric8.workflow.devops.git;

import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;

/**
 * Callback method that retrieves Git information from the current workspace git repository
 */
public class GitInfoCallback extends RepositoryListenerCallback<GitConfig> {

    /**
     * Constructor for GitInfoCallback
     * @param listener The TaskListener
     */
    public GitInfoCallback(TaskListener listener) {
        super(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GitConfig invoke(Repository repository, VirtualChannel channel) throws IOException, InterruptedException {

        Git git = new Git(repository);
        Iterable<RevCommit> log;
        try {
            log = git.log().call();
        } catch (GitAPIException e) {
            listener.error("Unable to get git log: " + e);
            GitConfig config = new GitConfig();
            config.setAuthor("UNKNOWN");
            config.setCommit("UNKNOWN");
            config.setBranch("UNKNOWN");
            return config;
        }

        RevCommit commit = log.iterator().next();

        GitConfig config = new GitConfig();
        config.setAuthor(commit.getCommitterIdent().getName());
        config.setCommit(commit.getId().getName());
        config.setBranch(repository.getBranch());

        return config;
    }
}
