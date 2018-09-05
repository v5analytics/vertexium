package org.vertexium.query;

import com.google.common.base.Joiner;
import org.vertexium.*;
import org.vertexium.util.IterableUtils;
import org.vertexium.util.JoinIterable;
import org.vertexium.util.VerticesToEdgeIdsIterable;

import java.util.EnumSet;

public class DefaultMultiVertexQuery extends QueryBase implements MultiVertexQuery {
    private final String[] vertexIds;

    public DefaultMultiVertexQuery(Graph graph, String[] vertexIds, String queryString, Authorizations authorizations) {
        super(graph, queryString, authorizations);
        this.vertexIds = vertexIds;
    }

    @Override
    public QueryResultsIterable<Vertex> vertices(FetchHints fetchHints) {
        Iterable<Vertex> vertices = getGraph().getVertices(IterableUtils.toIterable(getVertexIds()), fetchHints, getParameters().getAuthorizations());
        return new DefaultGraphQueryIterableWithAggregations<>(getParameters(), vertices, true, true, true, getAggregations());
    }

    @Override
    public QueryResultsIterable<Edge> edges(FetchHints fetchHints) {
        Iterable<Vertex> vertices = getGraph().getVertices(IterableUtils.toIterable(getVertexIds()), fetchHints, getParameters().getAuthorizations());
        Iterable<String> edgeIds = new VerticesToEdgeIdsIterable(vertices, getParameters().getAuthorizations());
        Iterable<Edge> edges = getGraph().getEdges(edgeIds, fetchHints, getParameters().getAuthorizations());
        return new DefaultGraphQueryIterableWithAggregations<>(getParameters(), edges, true, true, true, getAggregations());
    }

    @Override
    protected QueryResultsIterable<? extends VertexiumObject> extendedData(FetchHints fetchHints) {
        Iterable<Vertex> vertices = getGraph().getVertices(IterableUtils.toIterable(getVertexIds()), fetchHints, getParameters().getAuthorizations());
        Iterable<String> edgeIds = new VerticesToEdgeIdsIterable(vertices, getParameters().getAuthorizations());
        Iterable<Edge> edges = getGraph().getEdges(edgeIds, fetchHints, getParameters().getAuthorizations());
        return extendedData(fetchHints, new JoinIterable<>(vertices, edges));
    }

    public String[] getVertexIds() {
        return vertexIds;
    }

    @Override
    public String toString() {
        return super.toString() +
                ", vertexIds=" + Joiner.on(", ").join(vertexIds);
    }
}
