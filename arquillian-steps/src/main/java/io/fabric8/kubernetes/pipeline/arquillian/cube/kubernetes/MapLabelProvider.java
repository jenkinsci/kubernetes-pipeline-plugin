package io.fabric8.kubernetes.pipeline.arquillian.cube.kubernetes;

import org.arquillian.cube.kubernetes.api.LabelProvider;
import org.arquillian.cube.kubernetes.impl.label.DefaultLabelProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MapLabelProvider implements LabelProvider {

    private final Map<String, String> provided;
    private final LabelProvider delegate;

    public MapLabelProvider(Map<String, String> provided) {
        this(provided, null);
    }

    public MapLabelProvider(Map<String, String> provided, LabelProvider delegate) {
        this.provided = provided != null ? provided : Collections.emptyMap();
        this.delegate = delegate;
    }

    @Override
    public Map<String, String> getLabels() {
        Map<String, String> map = new HashMap<>(provided);
        if (delegate != null) {
            map.putAll(delegate.getLabels());
        }
        return map;
    }

    @Override
    public LabelProvider toImmutable() {
        return this;
    }
}
