package org.vertexium.inmemory;

import org.vertexium.*;
import org.vertexium.inmemory.util.EdgeToEdgeIdIterable;
import org.vertexium.mutation.ExistingElementMutation;
import org.vertexium.mutation.ExistingElementMutationImpl;
import org.vertexium.query.VertexQuery;
import org.vertexium.search.IndexHint;
import org.vertexium.util.ConvertingIterable;
import org.vertexium.util.FilterIterable;
import org.vertexium.util.IterableUtils;

import java.util.EnumSet;

public class InMemoryVertex extends InMemoryElement<InMemoryVertex> implements Vertex {
    public InMemoryVertex(
            InMemoryGraph graph,
            String id,
            InMemoryTableVertex inMemoryTableElement,
            EnumSet<FetchHint> fetchHints,
            Long endTime,
            Authorizations authorizations
    ) {
        super(
                graph,
                id,
                inMemoryTableElement,
                fetchHints,
                endTime,
                authorizations
        );
    }

    @Override
    public Iterable<Edge> getEdges(Direction direction, Authorizations authorizations) {
        return getEdges(direction, FetchHint.DEFAULT, authorizations);
    }

    @Override
    public Iterable<EdgeInfo> getEdgeInfos(Direction direction, Authorizations authorizations) {
        String[] labels = null;
        return getEdgeInfos(direction, labels, authorizations);
    }

    @Override
    public Iterable<EdgeInfo> getEdgeInfos(Direction direction, String label, Authorizations authorizations) {
        return getEdgeInfos(direction, new String[]{label}, authorizations);
    }

    @Override
    public Iterable<EdgeInfo> getEdgeInfos(Direction direction, final String[] labels, Authorizations authorizations) {
        Iterable<EdgeInfo> results = new ConvertingIterable<Edge, EdgeInfo>(getEdges(direction, getFetchHints(), authorizations)) {
            @Override
            protected EdgeInfo convert(final Edge o) {
                return new EdgeInfo() {
                    @Override
                    public String getEdgeId() {
                        return o.getId();
                    }

                    @Override
                    public String getLabel() {
                        return o.getLabel();
                    }

                    @Override
                    public String getVertexId() {
                        return o.getOtherVertexId(InMemoryVertex.this.getId());
                    }
                };
            }
        };
        if (labels != null) {
            results = new FilterIterable<EdgeInfo>(results) {
                @Override
                protected boolean isIncluded(EdgeInfo o) {
                    for (String label : labels) {
                        if (o.getLabel().equals(label)) {
                            return true;
                        }
                    }
                    return false;
                }
            };
        }
        return results;
    }

    @Override
    public Iterable<Edge> getEdges(Direction direction, EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
        return getEdges(direction, fetchHints, null, authorizations);
    }

    @Override
    public Iterable<Edge> getEdges(final Direction direction, EnumSet<FetchHint> fetchHints, Long endTime, Authorizations authorizations) {
        return new FilterIterable<Edge>(getGraph().getEdgesFromVertex(getId(), fetchHints, endTime, authorizations)) {
            @Override
            protected boolean isIncluded(Edge edge) {
                switch (direction) {
                    case IN:
                        return edge.getVertexId(Direction.IN).equals(getId());
                    case OUT:
                        return edge.getVertexId(Direction.OUT).equals(getId());
                    default:
                        return true;
                }
            }
        };
    }

    @Override
    public Iterable<String> getEdgeIds(Direction direction, Authorizations authorizations) {
        return new EdgeToEdgeIdIterable(getEdges(direction, getFetchHints(), authorizations));
    }

    @Override
    public Iterable<Edge> getEdges(Direction direction, String label, Authorizations authorizations) {
        return getEdges(direction, label, FetchHint.DEFAULT, authorizations);
    }

    @Override
    public Iterable<Edge> getEdges(Direction direction, String label, EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
        return getEdges(direction, labelToArrayOrNull(label), authorizations);
    }

    @Override
    public Iterable<String> getEdgeIds(Direction direction, String label, Authorizations authorizations) {
        return new EdgeToEdgeIdIterable(getEdges(direction, label, authorizations));
    }

    @Override
    public Iterable<Edge> getEdges(Direction direction, String[] labels, Authorizations authorizations) {
        return getEdges(direction, labels, FetchHint.DEFAULT, authorizations);
    }

    @Override
    public Iterable<Edge> getEdges(Direction direction, final String[] labels, EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
        return new FilterIterable<Edge>(getEdges(direction, authorizations)) {
            @Override
            protected boolean isIncluded(Edge edge) {
                if (labels == null) {
                    return true;
                }
                for (String label : labels) {
                    if (label.equals(edge.getLabel())) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    @Override
    public Iterable<String> getEdgeIds(Direction direction, String[] labels, Authorizations authorizations) {
        return new EdgeToEdgeIdIterable(getEdges(direction, labels, authorizations));
    }

    @Override
    public Iterable<Edge> getEdges(Vertex otherVertex, Direction direction, Authorizations authorizations) {
        return getEdges(otherVertex, direction, FetchHint.DEFAULT, authorizations);
    }

    @Override
    public Iterable<Edge> getEdges(final Vertex otherVertex, Direction direction, EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
        return new FilterIterable<Edge>(getEdges(direction, authorizations)) {
            @Override
            protected boolean isIncluded(Edge edge) {
                return edge.getOtherVertexId(getId()).equals(otherVertex.getId());
            }
        };
    }

    @Override
    public Iterable<String> getEdgeIds(Vertex otherVertex, Direction direction, Authorizations authorizations) {
        return new EdgeToEdgeIdIterable(getEdges(otherVertex, direction, authorizations));
    }

    @Override
    public Iterable<Edge> getEdges(Vertex otherVertex, Direction direction, String label, Authorizations authorizations) {
        return getEdges(otherVertex, direction, label, FetchHint.DEFAULT, authorizations);
    }

    @Override
    public Iterable<Edge> getEdges(final Vertex otherVertex, Direction direction, String label, EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
        return new FilterIterable<Edge>(getEdges(direction, label, authorizations)) {
            @Override
            protected boolean isIncluded(Edge edge) {
                return edge.getOtherVertexId(getId()).equals(otherVertex.getId());
            }
        };
    }

    @Override
    public Iterable<String> getEdgeIds(Vertex otherVertex, Direction direction, String label, Authorizations authorizations) {
        return new EdgeToEdgeIdIterable(getEdges(otherVertex, direction, label, authorizations));
    }

    @Override
    public Iterable<Edge> getEdges(Vertex otherVertex, Direction direction, String[] labels, Authorizations authorizations) {
        return getEdges(otherVertex, direction, labels, FetchHint.DEFAULT, authorizations);
    }

    @Override
    public Iterable<Edge> getEdges(final Vertex otherVertex, Direction direction, String[] labels, EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
        return new FilterIterable<Edge>(getEdges(direction, labels, authorizations)) {
            @Override
            protected boolean isIncluded(Edge edge) {
                return edge.getOtherVertexId(getId()).equals(otherVertex.getId());
            }
        };
    }

    @Override
    public Iterable<String> getEdgeIds(Vertex otherVertex, Direction direction, String[] labels, Authorizations authorizations) {
        return new EdgeToEdgeIdIterable(getEdges(otherVertex, direction, labels, authorizations));
    }

    @Override
    public int getEdgeCount(Direction direction, Authorizations authorizations) {
        return IterableUtils.count(getEdgeIds(direction, authorizations));
    }

    @Override
    public Iterable<String> getEdgeLabels(Direction direction, Authorizations authorizations) {
        return IterableUtils.toSet(new ConvertingIterable<Edge, String>(getEdges(direction, authorizations)) {
            @Override
            protected String convert(Edge o) {
                return o.getLabel();
            }
        });
    }

    @Override
    public Iterable<Vertex> getVertices(Direction direction, Authorizations authorizations) {
        return getVertices(direction, FetchHint.DEFAULT, authorizations);
    }

    @Override
    public Iterable<Vertex> getVertices(Direction direction, final EnumSet<FetchHint> fetchHints, final Authorizations authorizations) {
        return getVertices(direction, (String[])null, fetchHints, authorizations);
    }

    @Override
    public Iterable<Vertex> getVertices(Direction direction, String label, Authorizations authorizations) {
        return getVertices(direction, label, FetchHint.DEFAULT, authorizations);
    }

    @Override
    public Iterable<Vertex> getVertices(Direction direction, String label, EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
        return getVertices(direction, labelToArrayOrNull(label), fetchHints, authorizations);
    }

    @Override
    public Iterable<Vertex> getVertices(Direction direction, String[] labels, Authorizations authorizations) {
        return getVertices(direction, labels, FetchHint.DEFAULT, authorizations);
    }

    @Override
    public Iterable<Vertex> getVertices(final Direction direction, final String[] labels, final EnumSet<FetchHint> fetchHints, final Authorizations authorizations) {
        Iterable<String> vertexIds = getVertexIds(direction, labels, authorizations);
        return getGraph().getVertices(vertexIds, fetchHints, authorizations);
    }

    @Override
    public Iterable<String> getVertexIds(Direction direction, String label, Authorizations authorizations) {
        return getVertexIds(direction, labelToArrayOrNull(label), authorizations);
    }

    @Override
    public Iterable<String> getVertexIds(Direction direction, String[] labels, Authorizations authorizations) {
        return new ConvertingIterable<EdgeInfo, String>(getEdgeInfos(direction, labels, authorizations)) {
            @Override
            protected String convert(EdgeInfo e) {
                return e.getVertexId();
            }
        };
    }

    @Override
    public Iterable<String> getVertexIds(Direction direction, Authorizations authorizations) {
        return getVertexIds(direction, (String[])null, authorizations);
    }

    @Override
    public VertexQuery query(Authorizations authorizations) {
        return query(null, authorizations);
    }

    @Override
    public VertexQuery query(String queryString, Authorizations authorizations) {
        return getGraph().getSearchIndex().queryVertex(getGraph(), this, queryString, authorizations);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ExistingElementMutation<Vertex> prepareMutation() {
        return new ExistingElementMutationImpl<Vertex>(this) {
            @Override
            public Vertex save(Authorizations authorizations) {
                IndexHint indexHint = getIndexHint();
                Visibility oldElementVisibility = InMemoryVertex.this.getVisibility();
                saveExistingElementMutation(this, indexHint, authorizations);
                Vertex vertex = getElement();
                if (indexHint != IndexHint.DO_NOT_INDEX) {
                    saveMutationToSearchIndex(
                            vertex,
                            oldElementVisibility,
                            getNewElementVisibility(),
                            getAlterPropertyVisibilities(),
                            getExtendedData(),
                            authorizations
                    );
                }
                return vertex;
            }
        };
    }

    private static String[] labelToArrayOrNull(String label) {
        return label == null ? null : new String[]{label};
    }

    @Override
    public Iterable<EdgeVertexPair> getEdgeVertexPairs(Direction direction, Authorizations authorizations) {
        return getEdgeVertexPairs(getEdgeInfos(direction, authorizations), FetchHint.DEFAULT, null, authorizations);
    }

    @Override
    public Iterable<EdgeVertexPair> getEdgeVertexPairs(Direction direction, EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
        return getEdgeVertexPairs(getEdgeInfos(direction, authorizations), fetchHints, null, authorizations);
    }

    @Override
    public Iterable<EdgeVertexPair> getEdgeVertexPairs(Direction direction, EnumSet<FetchHint> fetchHints, Long endTime, Authorizations authorizations) {
        return getEdgeVertexPairs(getEdgeInfos(direction, authorizations), fetchHints, endTime, authorizations);
    }

    @Override
    public Iterable<EdgeVertexPair> getEdgeVertexPairs(Direction direction, String label, Authorizations authorizations) {
        return getEdgeVertexPairs(getEdgeInfos(direction, label, authorizations), FetchHint.DEFAULT, null, authorizations);
    }

    @Override
    public Iterable<EdgeVertexPair> getEdgeVertexPairs(Direction direction, String label, EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
        return getEdgeVertexPairs(getEdgeInfos(direction, label, authorizations), fetchHints, null, authorizations);
    }

    @Override
    public Iterable<EdgeVertexPair> getEdgeVertexPairs(Direction direction, String[] labels, Authorizations authorizations) {
        return getEdgeVertexPairs(getEdgeInfos(direction, labels, authorizations), FetchHint.DEFAULT, null, authorizations);
    }

    @Override
    public Iterable<EdgeVertexPair> getEdgeVertexPairs(Direction direction, String[] labels, EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
        return getEdgeVertexPairs(getEdgeInfos(direction, labels, authorizations), fetchHints, null, authorizations);
    }

    private Iterable<EdgeVertexPair> getEdgeVertexPairs(Iterable<EdgeInfo> edgeInfos, EnumSet<FetchHint> fetchHints, Long endTime, Authorizations authorizations) {
        return EdgeVertexPair.getEdgeVertexPairs(getGraph(), getId(), edgeInfos, fetchHints, endTime, authorizations);
    }
}
