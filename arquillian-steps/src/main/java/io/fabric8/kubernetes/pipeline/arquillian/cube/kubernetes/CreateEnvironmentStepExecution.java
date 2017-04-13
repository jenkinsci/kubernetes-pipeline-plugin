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

import org.arquillian.cube.kubernetes.impl.SessionManager;

import javax.inject.Inject;

public class CreateEnvironmentStepExecution extends AbstractSessionManagerStepExecution<CreateEnvironmentStep> {

    @Inject
    private CreateEnvironmentStep step;

    @Override
    public void onStart(SessionManager sessionManager) {
        sessionManager.createEnvironment(session);
    }

    @Override
    public void onStop(SessionManager sessionManager) {
        sessionManager.stop();
    }


    @Override
    CreateEnvironmentStep getStep() {
        return step;
    }
}
