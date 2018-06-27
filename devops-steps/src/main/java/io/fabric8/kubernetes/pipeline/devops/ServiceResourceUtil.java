package io.fabric8.kubernetes.pipeline.devops;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.openshift.api.model.Route;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ServiceResourceUtil implements Serializable {

    private static final String SERVICE_NAME_REGEX = "[a-z]([-a-z0-9]*[a-z0-9])?";
    public static final int SERVICE_NAME_MAX_LENGTH = 58;
    public static final String SERVICE_NAME_PREFIX = "svc-";
    public static final String K8S_PIPELINE_SERVICE_PATCH = "K8S_PIPELINE_SERVICE_PATCH";

    private final Function<HasMetadata, String> resourceKeyMapper = (r) -> r.getMetadata().getName();

    public void patchServiceName(Set<HasMetadata> entities) {
        if(isPatchingDisabled()) {
            return;
        }

        Map<String, Service> services =  patchServiceIfInvalidName(entities);
        Map<String, Route> routes = patchRouteWithService(entities, services);

        replace(services, entities);
        replace(routes, entities);
    }

    private boolean isPatchingDisabled() {
        return System.getenv(K8S_PIPELINE_SERVICE_PATCH) == null ||
                System.getenv(K8S_PIPELINE_SERVICE_PATCH).isEmpty() ||
                !System.getenv("K8S_PIPELINE_SERVICE_PATCH").equalsIgnoreCase("enabled");
    }

    private Map<String, Service> patchServiceIfInvalidName(Set<HasMetadata> resources) {

        Predicate<HasMetadata> invalidService = (r) -> r instanceof Service && hasInvalidDNS((Service) r);
        Function<HasMetadata, Service> patchedService = (r) -> sanitizeServiceName((Service) r);

        Map<String, Service> patchedServices = resources.stream()
                .filter(invalidService)
                .collect(Collectors.toMap(resourceKeyMapper, patchedService));

        return patchedServices;
    }

    private Map<String, Route> patchRouteWithService(Set<HasMetadata> resources, Map<String, Service> services) {

        Predicate<HasMetadata> routeToUpdate = (r) -> r instanceof Route && services.get(serviceNameFromRoute((Route) r)) != null;
        Function<HasMetadata, Route> patchedRoute = (r) -> updateRoute((Route) r, services.get(serviceNameFromRoute((Route) r)));

        Map<String, Route> patchedRoutes = resources.stream()
                                .filter(routeToUpdate)
                                .collect(Collectors.toMap(resourceKeyMapper, patchedRoute));

        return patchedRoutes;
    }

    public boolean hasInvalidDNS(Service service) {
        if (service.getMetadata() != null && service.getMetadata().getName() != null)
            return !(Pattern.matches(SERVICE_NAME_REGEX, service.getMetadata().getName()));
        else
            return false;
    }

    private Service sanitizeServiceName(Service service) {
        String serviceName = service.getMetadata().getName();
        service.getMetadata().setName(SERVICE_NAME_PREFIX + truncate(serviceName).toLowerCase());
        return service;
    }

    private String serviceNameFromRoute(Route route) {
        if(route.getSpec() != null && route.getSpec().getTo() != null && route.getSpec().getTo().getKind().equals("Service")) {
            return route.getSpec().getTo().getName();
        } else {
            return null;
        }
    }

    private Route updateRoute(Route route, Service service) {
        route.getSpec().getTo().setName(service.getMetadata().getName());
        return route;
    }

    private String truncate(String name) {
        if (name.length() > SERVICE_NAME_MAX_LENGTH) {
            return name.substring(0, SERVICE_NAME_MAX_LENGTH);
        } else {
            return name;
        }
    }

    private void replace(Map<String, ? extends HasMetadata> patchedResources, Set<HasMetadata> originalResources) {

        originalResources = originalResources.stream()
                    .filter(r -> patchedResources.get(r.getMetadata().getName()) == null)
                    .collect(Collectors.toSet());

        originalResources.addAll(patchedResources
                    .entrySet().stream()
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toSet()));
    }

}