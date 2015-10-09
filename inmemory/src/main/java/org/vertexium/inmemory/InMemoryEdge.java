package org.vertexium.inmemory;

import org.vertexium.*;
import org.vertexium.inmemory.mutations.AlterEdgeLabelMutation;
import org.vertexium.inmemory.mutations.EdgeSetupMutation;
import org.vertexium.mutation.ExistingEdgeMutation;
import org.vertexium.search.IndexHint;

import java.util.EnumSet;

public class InMemoryEdge extends InMemoryElement<InMemoryEdge> implements Edge {
    private final EdgeSetupMutation edgeSetupMutation;

    public InMemoryEdge(
            InMemoryGraph graph,
            String id,
            InMemoryTableEdge inMemoryTableElement,
            boolean includeHidden,
            Long endTime,
            Authorizations authorizations
    ) {
        super(graph, id, inMemoryTableElement, includeHidden, endTime, authorizations);
        edgeSetupMutation = inMemoryTableElement.findLastMutation(EdgeSetupMutation.class);
    }

    @Override
    public String getLabel() {
        return inMemoryTableElement.findLastMutation(AlterEdgeLabelMutation.class).getNewEdgeLabel();
    }

    @Override
    public String getVertexId(Direction direction) {
        switch (direction) {
            case IN:
                return edgeSetupMutation.getInVertexId();
            case OUT:
                return edgeSetupMutation.getOutVertexId();
            default:
                throw new IllegalArgumentException("Unexpected direction: " + direction);
        }
    }

    @Override
    public Vertex getVertex(Direction direction, EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
        return getGraph().getVertex(getVertexId(direction), fetchHints, authorizations);
    }

    @Override
    public Vertex getVertex(Direction direction, Authorizations authorizations) {
        return getVertex(direction, FetchHint.ALL, authorizations);
    }

    @Override
    public String getOtherVertexId(String myVertexId) {
        if (edgeSetupMutation.getInVertexId().equals(myVertexId)) {
            return edgeSetupMutation.getOutVertexId();
        } else if (edgeSetupMutation.getOutVertexId().equals(myVertexId)) {
            return edgeSetupMutation.getInVertexId();
        }
        throw new VertexiumException("myVertexId does not appear on either the in or the out.");
    }

    @Override
    public Vertex getOtherVertex(String myVertexId, Authorizations authorizations) {
        return getOtherVertex(myVertexId, FetchHint.ALL, authorizations);
    }

    @Override
    public Vertex getOtherVertex(String myVertexId, EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
        return getGraph().getVertex(getOtherVertexId(myVertexId), fetchHints, authorizations);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExistingEdgeMutation prepareMutation() {
        return new ExistingEdgeMutation(this) {
            @Override
            public Edge save(Authorizations authorizations) {
                saveExistingElementMutation(this, authorizations);
                Edge edge = getElement();
                if (getIndexHint() != IndexHint.DO_NOT_INDEX) {
                    saveMutationToSearchIndex(edge, getAlterPropertyVisibilities(), authorizations);
                }
                return edge;
            }
        };
    }
}
