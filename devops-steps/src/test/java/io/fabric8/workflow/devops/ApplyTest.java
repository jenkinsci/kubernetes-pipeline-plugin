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

package io.fabric8.workflow.devops;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.internal.HasMetadataComparator;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApplyTest {

    @Test
    public void testAddRegistryToImageNameIfNotPresent() throws Exception {

        String json = FileUtils.readFileToString(
                new File(this.getClass().getResource("/kubernetesJsonWithoutRegistry.json").toURI()));

        String registry = "MY-REGISTRY:5000";
        String expectedImageName = registry+"/TEST_NS/my-test-image:TEST-1.0";
        testAddRegistryToImageName(json, registry, expectedImageName);
    }

    @Test
    public void testRegistryNotAddedIfAlreadyPresent() throws Exception {
        String json = FileUtils.readFileToString(
                new File(this.getClass().getResource("/kubernetesJsonWithRegistry.json").toURI()));

        String registry = "MY-REGISTRY:5000";
        String expectedImageName = "myexternalregistry.io:5000/TEST_NS/my-test-image:TEST-1.0";
        testAddRegistryToImageName(json, registry, expectedImageName);

    }

    @Test
    public void testImageNameWithRegistry(){
        String imageName = "myexternalregistry.io:5000/TEST_NS/my-test-image:TEST-1.0";
        assertTrue(ApplyStepExecution.hasRegistry(imageName));
    }

    @Test
    public void testImageNameWithoutRegistry(){
        String imageName = "TEST_NS/my-test-image:TEST-1.0";
        assertFalse(ApplyStepExecution.hasRegistry(imageName));
    }

    @Test
    public void testImageNameWithRegistryWithoutTag(){
        String imageName = "myexternalregistry.io:5000/TEST_NS/my-test-image";
        assertTrue(ApplyStepExecution.hasRegistry(imageName));
    }

    @Test
    public void testImageNameWithoutRegistryWithoutTag(){
        String imageName = "TEST_NS/my-test-image";
        assertFalse(ApplyStepExecution.hasRegistry(imageName));
    }


    private void testAddRegistryToImageName(String json, String registry, String expectedImageName) throws Exception {
        Set<HasMetadata> entities = new TreeSet<>(new HasMetadataComparator());
        Object dto = KubernetesHelper.loadJson(json);

        entities.addAll(KubernetesHelper.toItemList(dto));

        ApplyStepExecution execution = new ApplyStepExecution();
        execution.addRegistryToImageNameIfNotPresent(entities, registry);

        boolean matched = false;
        for (HasMetadata entity : entities) {
            if (entity instanceof ReplicationController){
                assertEquals(expectedImageName, ((ReplicationController) entity).getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
                matched = true;
            }
        }
        assertTrue(matched);
    }

}
