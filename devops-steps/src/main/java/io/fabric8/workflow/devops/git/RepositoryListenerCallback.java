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
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;

import java.io.IOException;

/**
 * RepositoryCallback that keeps track of the TaskListener
 * @param <T> Return type of the Callback
 */
public abstract class RepositoryListenerCallback<T> implements RepositoryCallback<T> {

    /**
     * The TaskListener for use in invoke
     */
    public final TaskListener listener;

    /**
     * Constructor for a RepositoryListenerAwareCallback
     * @param listener The TaskListener
     */
    public RepositoryListenerCallback(TaskListener listener) {
        this.listener = listener;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public abstract T invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException;
}