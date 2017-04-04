package io.fabric8.kubernetes.pipeline.arquillian.cube.kubernetes;

import org.arquillian.cube.kubernetes.api.AnnotationProvider;
import org.arquillian.cube.kubernetes.impl.annotation.DefaultAnnotationProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MapAnnotationProvider implements AnnotationProvider {

    private final Map<String, String> provided;
    private final AnnotationProvider delegate;

    public MapAnnotationProvider(Map<String, String> provided) {
        this(provided, new DefaultAnnotationProvider());
    }

    public MapAnnotationProvider(Map<String, String> provided, AnnotationProvider delegate) {
        this.provided = provided != null ? provided : Collections.emptyMap();
        this.delegate = delegate;
    }

    @Override
    public Map<String, String> create(String sessionId, String status) {
        Map<String, String> map = new HashMap<>(provided);
        if (delegate != null) {
            map.putAll(delegate.create(sessionId, status));
        }
        return map;
    }

    @Override
    public AnnotationProvider toImmutable() {
        return this;
    }
}
