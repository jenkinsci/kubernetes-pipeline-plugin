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

import groovy.lang.Binding;

import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.csanchez.jenkins.plugins.kubernetes.ContainerEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.PodEnvVar;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.Callable;

import javax.annotation.Nonnull;

import hudson.Extension;
import io.fabric8.kubernetes.api.model.PodTemplate;
import io.fabric8.workflow.core.ClassWhiteList;

@Extension
public class CubeDSL extends GlobalVariable {

    private static final String CUBE = "cube";

    @Nonnull
    @Override
    public String getName() {
        return CUBE;
    }

    @Nonnull
    @Override
    public Object getValue(CpsScript script) throws Exception {
        Binding binding = script.getBinding();
        Object kubernetes;
        if (binding.hasVariable(getName())) {
            kubernetes = binding.getVariable(getName());
        } else {
            // Note that if this were a method rather than a constructor, we would need to mark it @NonCPS lest it throw CpsCallableInvocation.
            kubernetes = CubeDSL.class.getClassLoader().loadClass("io.fabric8.kubernetes.pipeline.arquillian.cube.kubernetes.Cube").getConstructor(CpsScript.class).newInstance(script);
            binding.setVariable(getName(), kubernetes);
        }
        return kubernetes;
    }


    @Extension
    public static class PlugiWhiteList extends ClassWhiteList {
        public PlugiWhiteList() throws IOException {
            super(ScriptBytecodeAdapter.class,
                    URL.class,
                    ArrayList.class, Collection.class, HashMap.class, HashSet.class, Collections.class,
                    Callable.class);
        }
    }
}
