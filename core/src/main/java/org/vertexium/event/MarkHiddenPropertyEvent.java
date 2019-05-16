package org.vertexium.event;

import org.vertexium.Element;
import org.vertexium.Graph;
import org.vertexium.Visibility;

public class MarkHiddenPropertyEvent extends GraphEvent {
    private final Element element;
    private final String key;
    private final String name;
    private final Visibility propertyVisibility;
    private final Long timestamp;
    private final Visibility visibility;
    private final Object data;

    public MarkHiddenPropertyEvent(Graph graph, Element element, String key, String name, Visibility propertyVisibility, Long timestamp, Visibility visibility, Object data) {
        super(graph);
        this.element = element;
        this.key = key;
        this.name = name;
        this.propertyVisibility = propertyVisibility;
        this.timestamp = timestamp;
        this.visibility = visibility;
        this.data = data;
    }

    public Element getElement() {
        return element;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public Visibility getPropertyVisibility() {
        return propertyVisibility;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public Object getData() {
        return data;
    }

    @Override
    public String toString() {
        return "MarkHiddenPropertyEvent{element=" + element + '}';
    }

    @Override
    public int hashCode() {
        return getElement().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MarkHiddenPropertyEvent)) {
            return false;
        }

        MarkHiddenPropertyEvent other = (MarkHiddenPropertyEvent) obj;
        return getElement().equals(other.getElement());
    }
}
