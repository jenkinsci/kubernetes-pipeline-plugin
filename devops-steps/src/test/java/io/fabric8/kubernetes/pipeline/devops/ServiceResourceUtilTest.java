package io.fabric8.kubernetes.pipeline.devops;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.openshift.api.model.Route;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * Created by hshinde on 6/26/18.
 */
public class ServiceResourceUtilTest {

    @Rule
    public final EnvironmentVariables envVars = new EnvironmentVariables();

    @Test
    public void patchServiceNameAndRoute() throws Exception {
        // GIVEN
        envVars.set(ServiceResourceUtil.K8S_PIPELINE_SERVICE_PATCH, "enabled");
        Set<HasMetadata> entities = getEntitiesFromResource("/services/invalidservice1.yaml");
        ServiceResourceUtil util = new ServiceResourceUtil();

        // WHEN
        util.patchServiceName(entities);

        // THEN
        HasMetadata service = getKind(entities, "Service");
        Route route = (Route) getKind(entities, "Route");
        assertEquals(2, entities.size());
        assertFalse(util.hasInvalidDNS((Service) service));
        assertEquals(service.getMetadata().getName(), route.getSpec().getTo().getName());
    }

    @Test
    public void patchServiceNameOnlyIfNoRoute() throws Exception {
        //GIVEN
        envVars.set(ServiceResourceUtil.K8S_PIPELINE_SERVICE_PATCH, "enabled");
        Set<HasMetadata> entities = getEntitiesFromResource("/services/invalidservice2.yaml");
        ServiceResourceUtil util = new ServiceResourceUtil();

        // WHEN
        util.patchServiceName(entities);

        // THEN
        HasMetadata service = getKind(entities, "Service");
        assertEquals(1, entities.size());
        assertFalse(util.hasInvalidDNS((Service) service));
    }

    @Test
    public void patchServiceNameOnlyIfNoRouteToService() throws Exception {
        // GIVEN
        envVars.set(ServiceResourceUtil.K8S_PIPELINE_SERVICE_PATCH, "enabled");
        Set<HasMetadata> entities = getEntitiesFromResource("/services/invalidservice3.yaml");
        ServiceResourceUtil util = new ServiceResourceUtil();

        // WHEN
        util.patchServiceName(entities);

        // THEN
        HasMetadata service = getKind(entities, "Service");
        Route route = (Route) getKind(entities, "Route");
        assertEquals(2, entities.size());
        assertFalse(util.hasInvalidDNS((Service) service));
        assertEquals("somelb", route.getSpec().getTo().getName());
    }

    @Test
    public void dontPatchValidServiceAndRoute() throws Exception {
        // GIVEN
        envVars.set(ServiceResourceUtil.K8S_PIPELINE_SERVICE_PATCH, "enabled");
        Set<HasMetadata> entities = getEntitiesFromResource("/services/validservice3.yaml");
        ServiceResourceUtil util = new ServiceResourceUtil();

        // WHEN
        util.patchServiceName(entities);

        // THEN
        HasMetadata service = getKind(entities, "Service");
        Route route = (Route) getKind(entities, "Route");
        assertEquals(4, entities.size());
        assertFalse(util.hasInvalidDNS((Service) service));
        assertEquals("nodejs-rest-http", service.getMetadata().getName());
        assertEquals("nodejs-rest-http", route.getSpec().getTo().getName());
    }

    @Test
    public void dontPatchRoute() throws Exception {
        // GIVEN
        envVars.set(ServiceResourceUtil.K8S_PIPELINE_SERVICE_PATCH, "enabled");
        Set<HasMetadata> entities = getEntitiesFromResource("/services/validservice4.yaml");
        ServiceResourceUtil util = new ServiceResourceUtil();

        // WHEN
        util.patchServiceName(entities);

        // THEN
        HasMetadata service = getKind(entities, "Service");
        Route route = (Route) getKind(entities, "Route");
        assertEquals(3, entities.size());
        assertNull(service);
        assertEquals("nodejs-rest-http", route.getSpec().getTo().getName());
    }

    @Test
    public void shouldDisablePatching() throws Exception {
        // GIVEN
        envVars.set(ServiceResourceUtil.K8S_PIPELINE_SERVICE_PATCH, "disabled");
        Set<HasMetadata> entities = getEntitiesFromResource("/services/invalidservice1.yaml");
        ServiceResourceUtil util = new ServiceResourceUtil();

        // WHEN
        util.patchServiceName(entities);

        // THEN
        HasMetadata service = getKind(entities, "Service");
        Route route = (Route) getKind(entities, "Route");
        assertEquals(2, entities.size());
        assertEquals("12Nodejs-rest-http", service.getMetadata().getName());
        assertEquals("12Nodejs-rest-http", route.getSpec().getTo().getName());
    }

    public Set<HasMetadata> getEntitiesFromResource(String resourcePath) {
        InputStream resourceStream = getClass().getResourceAsStream(resourcePath);
        return new DefaultKubernetesClient().load(resourceStream).get().stream().collect(Collectors.toSet());
    }

    private HasMetadata getKind(Set<HasMetadata> entities, String kind) {
        for(HasMetadata entity : entities) {
            if(entity.getKind().equals(kind)) {
                return entity;
            }
        }

        return null;
    }

}