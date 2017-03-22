package org.vertexium.query;

import com.google.common.collect.Lists;
import org.vertexium.*;
import org.vertexium.util.SelectManyIterable;

import java.util.*;

public class CompositeGraphQuery implements Query {
    private final List<Query> queries;

    public CompositeGraphQuery(Query... queries) {
        this(Arrays.asList(queries));
    }

    public CompositeGraphQuery(Collection<Query> queries) {
        this.queries = new ArrayList<>(queries);
    }

    @Override
    public QueryResultsIterable<Vertex> vertices() {
        return vertices(FetchHint.DEFAULT);
    }

    @Override
    public QueryResultsIterable<Vertex> vertices(final EnumSet<FetchHint> fetchHints) {
        final Set<String> seenIds = new HashSet<>();
        return new QueryResultsSelectManyIterable<Vertex>(this.queries) {
            @Override
            public Iterable<Vertex> getIterable(Query query) {
                return query.vertices(fetchHints);
            }

            @Override
            protected boolean isIncluded(Vertex vertex) {
                if (seenIds.contains(vertex.getId())) {
                    return false;
                }
                seenIds.add(vertex.getId());
                return super.isIncluded(vertex);
            }
        };
    }

    @Override
    public QueryResultsIterable<Edge> edges() {
        return edges(FetchHint.DEFAULT);
    }

    @Override
    public QueryResultsIterable<Edge> edges(final EnumSet<FetchHint> fetchHints) {
        final Set<String> seenIds = new HashSet<>();
        return new QueryResultsSelectManyIterable<Edge>(this.queries) {
            @Override
            public Iterable<Edge> getIterable(Query query) {
                return query.edges(fetchHints);
            }

            @Override
            protected boolean isIncluded(Edge edge) {
                if (seenIds.contains(edge.getId())) {
                    return false;
                }
                seenIds.add(edge.getId());
                return super.isIncluded(edge);
            }
        };
    }

    @Override
    @Deprecated
    public QueryResultsIterable<Edge> edges(final String label) {
        hasEdgeLabel(label);
        return edges(FetchHint.DEFAULT);
    }

    @Override
    @Deprecated
    public QueryResultsIterable<Edge> edges(final String label, final EnumSet<FetchHint> fetchHints) {
        hasEdgeLabel(label);
        return edges(fetchHints);
    }

    @Override
    public QueryResultsIterable<Element> elements() {
        return elements(FetchHint.DEFAULT);
    }

    @Override
    public QueryResultsIterable<Element> elements(final EnumSet<FetchHint> fetchHints) {
        final Set<String> seenIds = new HashSet<>();
        return new QueryResultsSelectManyIterable<Element>(this.queries) {
            @Override
            public Iterable<Element> getIterable(Query query) {
                return query.elements(fetchHints);
            }

            @Override
            protected boolean isIncluded(Element element) {
                if (seenIds.contains(element.getId())) {
                    return false;
                }
                seenIds.add(element.getId());
                return super.isIncluded(element);
            }
        };
    }

    @Override
    public QueryResultsIterable<ExtendedDataRow> extendedDataRows() {
        return extendedDataRows(FetchHint.DEFAULT);
    }

    @Override
    public QueryResultsIterable<ExtendedDataRow> extendedDataRows(EnumSet<FetchHint> fetchHints) {
        final Set<ExtendedDataRowId> seenIds = new HashSet<>();
        return new QueryResultsSelectManyIterable<ExtendedDataRow>(this.queries) {
            @Override
            public Iterable<ExtendedDataRow> getIterable(Query query) {
                return query.extendedDataRows(fetchHints);
            }

            @Override
            protected boolean isIncluded(ExtendedDataRow extendedDataRows) {
                if (seenIds.contains(extendedDataRows.getId())) {
                    return false;
                }
                seenIds.add(extendedDataRows.getId());
                return super.isIncluded(extendedDataRows);
            }
        };
    }

    @Override
    public QueryResultsIterable<? extends VertexiumObject> search(EnumSet<VertexiumObjectType> objectTypes, EnumSet<FetchHint> fetchHints) {
        final Set<Object> seenIds = new HashSet<>();
        return new QueryResultsSelectManyIterable<VertexiumObject>(this.queries) {
            @Override
            public Iterable<? extends VertexiumObject> getIterable(Query query) {
                return query.search(objectTypes, fetchHints);
            }

            @Override
            protected boolean isIncluded(VertexiumObject vertexiumObject) {
                if (seenIds.contains(vertexiumObject.getId())) {
                    return false;
                }
                seenIds.add(vertexiumObject.getId());
                return super.isIncluded(vertexiumObject);
            }
        };
    }

    @Override
    public QueryResultsIterable<? extends VertexiumObject> search() {
        return search(VertexiumObjectType.ALL, FetchHint.DEFAULT);
    }

    @Override
    public <T> Query range(String propertyName, T startValue, T endValue) {
        for (Query query : queries) {
            query.range(propertyName, startValue, endValue);
        }
        return this;
    }

    @Override
    public <T> Query range(String propertyName, T startValue, boolean inclusiveStartValue, T endValue, boolean inclusiveEndValue) {
        for (Query query : queries) {
            query.range(propertyName, startValue, inclusiveStartValue, endValue, inclusiveEndValue);
        }
        return this;
    }

    @Override
    public Query hasEdgeLabel(String... edgeLabels) {
        for (Query query : queries) {
            query.hasEdgeLabel(edgeLabels);
        }
        return this;
    }

    @Override
    public Query hasEdgeLabel(Collection<String> edgeLabels) {
        for (Query query : queries) {
            query.hasEdgeLabel(edgeLabels);
        }
        return this;
    }

    @Override
    public Query hasExtendedData(ElementType elementType, String elementId) {
        return hasExtendedData(elementType, elementId, null);
    }

    @Override
    public Query hasExtendedData(String tableName) {
        return hasExtendedData(null, null, tableName);
    }

    @Override
    public Query hasExtendedData(ElementType elementType, String elementId, String tableName) {
        for (Query query : queries) {
            query.hasExtendedData(elementType, elementId, tableName);
        }
        return this;
    }

    @Override
    public Query hasExtendedData(Iterable<HasExtendedDataFilter> filters) {
        filters = Lists.newArrayList(filters);
        for (Query query : queries) {
            query.hasExtendedData(filters);
        }
        return this;
    }

    @Override
    public <T> Query has(String propertyName, T value) {
        for (Query query : queries) {
            query.has(propertyName, value);
        }
        return this;
    }

    @Override
    public <T> Query hasNot(String propertyName, T value) {
        for (Query query : queries) {
            query.hasNot(propertyName, value);
        }
        return this;
    }

    @Override
    public <T> Query has(String propertyName, Predicate predicate, T value) {
        for (Query query : queries) {
            query.has(propertyName, predicate, value);
        }
        return this;
    }

    @Override
    public Query has(String propertyName) {
        for (Query query : queries) {
            query.has(propertyName);
        }
        return this;
    }

    @Override
    public Query hasNot(String propertyName) {
        for (Query query : queries) {
            query.hasNot(propertyName);
        }
        return this;
    }

    @Override
    public Query skip(int count) {
        for (Query query : queries) {
            query.skip(count);
        }
        return this;
    }

    @Override
    public Query limit(Integer count) {
        for (Query query : queries) {
            query.limit(count);
        }
        return this;
    }

    @Override
    public Query limit(Long count) {
        for (Query query : queries) {
            query.limit(count);
        }
        return this;
    }

    @Override
    public Query sort(String propertyName, SortDirection direction) {
        for (Query query : queries) {
            query.sort(propertyName, direction);
        }
        return this;
    }

    @Override
    public boolean isAggregationSupported(Aggregation aggregation) {
        for (Query query : queries) {
            if (!query.isAggregationSupported(aggregation)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Query addAggregation(Aggregation aggregation) {
        for (Query query : queries) {
            if (!query.isAggregationSupported(aggregation)) {
                throw new VertexiumException(query.getClass().getName() + " does not support aggregation of type " + aggregation.getClass().getName());
            }
        }
        for (Query query : queries) {
            query.addAggregation(aggregation);
        }
        return this;
    }

    @Override
    public Iterable<Aggregation> getAggregations() {
        return new SelectManyIterable<Query, Aggregation>(queries) {
            @Override
            protected Iterable<? extends Aggregation> getIterable(Query query) {
                return query.getAggregations();
            }
        };
    }
}
