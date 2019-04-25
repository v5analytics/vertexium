package org.vertexium.inmemory.mutations;

import org.vertexium.FetchHints;
import org.vertexium.Metadata;
import org.vertexium.Visibility;

public class AddPropertyValueMutation extends PropertyMutation {
    private final Object value;
    private final Metadata metadata;

    public AddPropertyValueMutation(
        long timestamp, String key, String name, Object value, Metadata metadata, Visibility visibility) {
        super(timestamp, key, name, visibility, visibility);
        this.value = value;
        this.metadata = Metadata.create(metadata);
    }

    public Object getValue() {
        return value;
    }

    public Metadata getMetadata(FetchHints fetchHints) {
        return Metadata.create(this.metadata, fetchHints);
    }
}
