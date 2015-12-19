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

import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.EnumeratingWhitelist;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClassWhiteList extends EnumeratingWhitelist {

    private final Class[] classes;
    private final List<MethodSignature> methodSignatures = new ArrayList<>();
    private final List<NewSignature> newSignatures = new ArrayList<>();
    private final List<MethodSignature> staticMethodSignatures = new ArrayList<>();
    private final List<FieldSignature> fieldSignatures = new ArrayList<>();
    private final List<FieldSignature> staticFieldSignatures = new ArrayList<>();

    private final Set<Class> processed = new HashSet<>();

    public ClassWhiteList( List<Class> classes) {
       this(classes.toArray(new Class[classes.size()]));
    }

    public ClassWhiteList(Class... classes) {
        this.classes = classes;
        load();
    }

    private void load() {
        try {
            for (Class c : classes) {
                processClass(c);
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private void processClass(Class clazz) {
        if (clazz != null && !processed.contains(clazz)) {
            //Constructors
            for (Constructor ctor : clazz.getDeclaredConstructors()) {
                newSignatures.add(new NewSignature(clazz, ctor.getParameterTypes()));
            }

            for (Method method : clazz.getDeclaredMethods()) {
                MethodSignature methodSignature = new MethodSignature(clazz, method.getName(), method.getParameterTypes());
                if (Modifier.isStatic(method.getModifiers())) {
                    staticMethodSignatures.add(methodSignature);
                } else {
                    methodSignatures.add(methodSignature);
                }
            }

            for (Field field : clazz.getDeclaredFields()) {
                FieldSignature fieldSignature = new FieldSignature(field.getType(), field.getName());
                if (Modifier.isStatic(field.getModifiers())) {
                    staticFieldSignatures.add(fieldSignature);
                } else {
                    fieldSignatures.add(fieldSignature);
                }
            }
            processed.add(clazz);
            processClass(clazz.getSuperclass());
        }
    }


    @Override
    protected List<MethodSignature> methodSignatures() {
        return methodSignatures;
    }

    @Override
    protected List<NewSignature> newSignatures() {
        return newSignatures;
    }

    @Override
    protected List<MethodSignature> staticMethodSignatures() {
        return staticMethodSignatures;
    }

    @Override
    protected List<FieldSignature> fieldSignatures() {
        return fieldSignatures;
    }

    @Override
    protected List<FieldSignature> staticFieldSignatures() {
        return staticFieldSignatures;
    }
}
