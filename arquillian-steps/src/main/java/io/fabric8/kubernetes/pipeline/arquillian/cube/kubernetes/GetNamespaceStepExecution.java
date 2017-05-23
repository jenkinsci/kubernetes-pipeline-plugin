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

package io.fabric8.kubernetes.pipeline.arquillian.cube.kubernetes;

import org.csanchez.jenkins.plugins.kubernetes.pipeline.NamespaceAction;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;

import javax.inject.Inject;

import hudson.FilePath;
import hudson.model.Run;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.utils.Utils;


public class GetNamespaceStepExecution extends AbstractSynchronousStepExecution<String> {

    @Inject
    GetNamespaceStep step;

    @Override
    protected String run() throws Exception {
        String namespace = null;
        try {
            FilePath workspace = getContext().get(FilePath.class);
            namespace = workspace.child(Config.KUBERNETES_NAMESPACE_PATH).readToString();
            if (Utils.isNotNullOrEmpty(namespace)) {
                return namespace;
            }
        } catch (Throwable t) {
            //it might be executed outside of a `node` block in which case, we want to ignore.
        }

        NamespaceAction namespaceAction = new NamespaceAction(getContext().get(Run.class));
        namespace = namespaceAction.getNamespace();

        if (Utils.isNotNullOrEmpty(namespace)) {
            return namespace;
        }
        return step.getFallbackNamespace();
    }
}
