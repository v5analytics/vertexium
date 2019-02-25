package org.vertexium.cli.model;

import org.vertexium.Edge;

public class LazyEdgeMap extends ModelBase {
    public LazyEdge get(String edgeId) {
        Edge e = getGraph().getEdge(edgeId, getGraph().getDefaultFetchHints(), getTime(), getAuthorizations());
        if (e == null) {
            return null;
        }
        return new LazyEdge(edgeId);
    }
}
