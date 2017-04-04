package io.fabric8.kubernetes.pipeline.arquillian.cube.kubernetes;

import org.arquillian.cube.kubernetes.api.DependencyResolver;
import org.arquillian.cube.kubernetes.api.Session;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

public class EmptyDependencyResolver implements DependencyResolver {

    @Override
    public List<URL> resolve(Session session) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public DependencyResolver toImmutable() {
        return this;
    }
}
