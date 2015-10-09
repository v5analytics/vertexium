package org.vertexium.inmemory;

import org.vertexium.*;
import org.vertexium.event.*;
import org.vertexium.id.IdGenerator;
import org.vertexium.inmemory.mutations.AlterEdgeLabelMutation;
import org.vertexium.inmemory.mutations.AlterVisibilityMutation;
import org.vertexium.inmemory.mutations.EdgeSetupMutation;
import org.vertexium.inmemory.mutations.ElementTimestampMutation;
import org.vertexium.mutation.AlterPropertyVisibility;
import org.vertexium.mutation.SetPropertyMetadata;
import org.vertexium.search.IndexHint;
import org.vertexium.search.SearchIndex;
import org.vertexium.util.ConvertingIterable;
import org.vertexium.util.IncreasingTime;
import org.vertexium.util.IterableUtils;
import org.vertexium.util.LookAheadIterable;

import java.util.*;

import static org.vertexium.util.Preconditions.checkNotNull;

public class InMemoryGraph extends GraphBaseWithSearchIndex {
    protected static final InMemoryGraphConfiguration DEFAULT_CONFIGURATION =
            new InMemoryGraphConfiguration(new HashMap<String, Object>());
    private final InMemoryVertexTable vertices;
    private final InMemoryEdgeTable edges;
    private final Set<String> validAuthorizations = new HashSet<>();
    private GraphMetadataStore graphMetadataStore = new InMemoryGraphMetadataStore();

    protected InMemoryGraph(InMemoryGraphConfiguration configuration) {
        this(
                configuration,
                new InMemoryVertexTable(),
                new InMemoryEdgeTable()
        );
    }

    protected InMemoryGraph(InMemoryGraphConfiguration configuration, IdGenerator idGenerator, SearchIndex searchIndex) {
        this(
                configuration,
                idGenerator,
                searchIndex,
                new InMemoryVertexTable(),
                new InMemoryEdgeTable()
        );
    }

    protected InMemoryGraph(
            InMemoryGraphConfiguration configuration,
            InMemoryVertexTable vertices,
            InMemoryEdgeTable edges
    ) {
        super(configuration);
        this.vertices = vertices;
        this.edges = edges;
    }

    protected InMemoryGraph(
            InMemoryGraphConfiguration configuration,
            IdGenerator idGenerator,
            SearchIndex searchIndex,
            InMemoryVertexTable vertices,
            InMemoryEdgeTable edges
    ) {
        super(configuration, idGenerator, searchIndex);
        this.vertices = vertices;
        this.edges = edges;
    }

    @SuppressWarnings("unused")
    public static InMemoryGraph create() {
        return create(DEFAULT_CONFIGURATION);
    }

    public static InMemoryGraph create(InMemoryGraphConfiguration config) {
        InMemoryGraph graph = new InMemoryGraph(config);
        graph.setup();
        return graph;
    }

    @SuppressWarnings("unused")
    public static InMemoryGraph create(Map<String, Object> config) {
        return create(new InMemoryGraphConfiguration(config));
    }

    @SuppressWarnings("unused")
    public static InMemoryGraph create(InMemoryGraphConfiguration config, IdGenerator idGenerator, SearchIndex searchIndex) {
        InMemoryGraph graph = new InMemoryGraph(config, idGenerator, searchIndex);
        graph.setup();
        return graph;
    }

    @Override
    public VertexBuilder prepareVertex(String vertexId, Long timestamp, Visibility visibility) {
        if (vertexId == null) {
            vertexId = getIdGenerator().nextId();
        }
        if (timestamp == null) {
            timestamp = IncreasingTime.currentTimeMillis();
        }
        final long timestampLong = timestamp;

        return new VertexBuilder(vertexId, visibility) {
            @Override
            public Vertex save(Authorizations authorizations) {
                addValidAuthorizations(authorizations.getAuthorizations());
                boolean isNew = false;
                InMemoryTableElement vertexTableElement = InMemoryGraph.this.vertices.getTableElement(getVertexId());
                if (vertexTableElement == null) {
                    isNew = true;
                    vertices.append(getVertexId(),
                            new AlterVisibilityMutation(timestampLong, getVisibility()),
                            new ElementTimestampMutation(timestampLong)
                    );
                } else {
                    vertices.append(getVertexId(), new ElementTimestampMutation(timestampLong));
                }
                InMemoryVertex vertex = InMemoryGraph.this.vertices.get(InMemoryGraph.this, getVertexId(), authorizations);
                if (isNew && hasEventListeners()) {
                    fireGraphEvent(new AddVertexEvent(InMemoryGraph.this, vertex));
                }
                vertex.updatePropertiesInternal(this);

                // to more closely simulate how accumulo works. add a potentially sparse (in case of an update) vertex to the search index.
                if (getIndexHint() != IndexHint.DO_NOT_INDEX) {
                    getSearchIndex().addElement(InMemoryGraph.this, vertex, authorizations);
                }

                return vertex;
            }
        };
    }

    private void addValidAuthorizations(String[] authorizations) {
        Collections.addAll(this.validAuthorizations, authorizations);
    }

    @Override
    public Iterable<Vertex> getVertices(EnumSet<FetchHint> fetchHints, final Long endTime, final Authorizations authorizations) throws VertexiumException {
        validateAuthorizations(authorizations);
        return new ConvertingIterable<InMemoryVertex, Vertex>(this.vertices.getAll(InMemoryGraph.this, fetchHints, endTime, authorizations)) {
            @Override
            protected Vertex convert(InMemoryVertex o) {
                return o;
            }
        };
    }

    protected void validateAuthorizations(Authorizations authorizations) {
        for (String auth : authorizations.getAuthorizations()) {
            if (!this.validAuthorizations.contains(auth)) {
                throw new SecurityVertexiumException("Invalid authorizations", authorizations);
            }
        }
    }

    @Override
    public void deleteVertex(Vertex vertex, Authorizations authorizations) {
        if (!((InMemoryVertex) vertex).canRead(authorizations)) {
            return;
        }

        List<Edge> edgesToDelete = IterableUtils.toList(vertex.getEdges(Direction.BOTH, authorizations));
        for (Edge edgeToDelete : edgesToDelete) {
            deleteEdge(edgeToDelete, authorizations);
        }

        this.vertices.remove(vertex.getId());
        getSearchIndex().deleteElement(this, vertex, authorizations);

        if (hasEventListeners()) {
            fireGraphEvent(new DeleteVertexEvent(this, vertex));
        }
    }

    @Override
    public void softDeleteVertex(Vertex vertex, Long timestamp, Authorizations authorizations) {
        if (!((InMemoryVertex) vertex).canRead(authorizations)) {
            return;
        }
        if (timestamp == null) {
            timestamp = IncreasingTime.currentTimeMillis();
        }

        List<Edge> edgesToSoftDelete = IterableUtils.toList(vertex.getEdges(Direction.BOTH, authorizations));
        for (Edge edgeToSoftDelete : edgesToSoftDelete) {
            softDeleteEdge(edgeToSoftDelete, timestamp, authorizations);
        }

        this.vertices.getTableElement(vertex.getId()).appendSoftDeleteMutation(timestamp);

        getSearchIndex().deleteElement(this, vertex, authorizations);

        if (hasEventListeners()) {
            fireGraphEvent(new SoftDeleteVertexEvent(this, vertex));
        }
    }

    @Override
    public void markVertexHidden(Vertex vertex, Visibility visibility, Authorizations authorizations) {
        if (!((InMemoryVertex) vertex).canRead(authorizations)) {
            return;
        }

        List<Edge> edgesToMarkHidden = IterableUtils.toList(vertex.getEdges(Direction.BOTH, authorizations));
        for (Edge edgeToMarkHidden : edgesToMarkHidden) {
            markEdgeHidden(edgeToMarkHidden, visibility, authorizations);
        }

        this.vertices.getTableElement(vertex.getId()).appendMarkHiddenMutation(visibility);
        getSearchIndex().addElement(this, vertex, authorizations);

        if (hasEventListeners()) {
            fireGraphEvent(new MarkHiddenVertexEvent(this, vertex));
        }
    }

    @Override
    public void markVertexVisible(Vertex vertex, Visibility visibility, Authorizations authorizations) {
        if (!((InMemoryVertex) vertex).canRead(authorizations)) {
            return;
        }

        List<Edge> edgesToMarkVisible = IterableUtils.toList(vertex.getEdges(Direction.BOTH, FetchHint.ALL_INCLUDING_HIDDEN, authorizations));
        for (Edge edgeToMarkVisible : edgesToMarkVisible) {
            markEdgeVisible(edgeToMarkVisible, visibility, authorizations);
        }

        this.vertices.getTableElement(vertex.getId()).appendMarkVisibleMutation(visibility);
        getSearchIndex().addElement(this, vertex, authorizations);

        if (hasEventListeners()) {
            fireGraphEvent(new MarkVisibleVertexEvent(this, vertex));
        }
    }

    public void markPropertyHidden(InMemoryElement element, InMemoryTableElement inMemoryTableElement, String key, String name, Visibility propertyVisibility, Long timestamp, Visibility visibility, Authorizations authorizations) {
        if (!element.canRead(authorizations)) {
            return;
        }

        Property property = inMemoryTableElement.appendMarkPropertyHiddenMutation(key, name, propertyVisibility, timestamp, visibility, authorizations);

        if (property != null && hasEventListeners()) {
            fireGraphEvent(new MarkHiddenPropertyEvent(this, element, property, visibility));
        }
    }

    public void markPropertyVisible(InMemoryElement element, InMemoryTableElement inMemoryTableElement, String key, String name, Visibility propertyVisibility, Long timestamp, Visibility visibility, Authorizations authorizations) {
        if (!element.canRead(authorizations)) {
            return;
        }

        Property property = inMemoryTableElement.appendMarkPropertyVisibleMutation(key, name, propertyVisibility, timestamp, visibility, authorizations);

        if (property != null && hasEventListeners()) {
            fireGraphEvent(new MarkVisiblePropertyEvent(this, element, property, visibility));
        }
    }

    @Override
    public EdgeBuilderByVertexId prepareEdge(String edgeId, String outVertexId, String inVertexId, String label, final Long timestamp, Visibility visibility) {
        if (edgeId == null) {
            edgeId = getIdGenerator().nextId();
        }

        return new EdgeBuilderByVertexId(edgeId, outVertexId, inVertexId, label, visibility) {
            @Override
            public Edge save(Authorizations authorizations) {
                addValidAuthorizations(authorizations.getAuthorizations());
                return savePreparedEdge(this, getOutVertexId(), getInVertexId(), timestamp, authorizations);
            }
        };
    }

    @Override
    public EdgeBuilder prepareEdge(String edgeId, Vertex outVertex, Vertex inVertex, String label, final Long timestamp, Visibility visibility) {
        if (edgeId == null) {
            edgeId = getIdGenerator().nextId();
        }

        return new EdgeBuilder(edgeId, outVertex, inVertex, label, visibility) {
            @Override
            public Edge save(Authorizations authorizations) {
                addValidAuthorizations(authorizations.getAuthorizations());
                return savePreparedEdge(this, getOutVertex().getId(), getInVertex().getId(), timestamp, authorizations);
            }
        };
    }

    private Edge savePreparedEdge(final EdgeBuilderBase edgeBuilder, final String outVertexId, final String inVertexId, Long timestamp, Authorizations authorizations) {
        if (timestamp == null) {
            timestamp = IncreasingTime.currentTimeMillis();
        }
        long incrementingTimestamp = timestamp;
        InMemoryTableElement edgeTableElement = this.edges.getTableElement(edgeBuilder.getEdgeId());
        boolean isNew = false;
        if (edgeTableElement == null) {
            isNew = true;
            edges.append(edgeBuilder.getEdgeId(),
                    new AlterVisibilityMutation(incrementingTimestamp++, edgeBuilder.getVisibility()),
                    new ElementTimestampMutation(incrementingTimestamp++),
                    new AlterEdgeLabelMutation(incrementingTimestamp++, edgeBuilder.getLabel()),
                    new EdgeSetupMutation(incrementingTimestamp++, outVertexId, inVertexId)
            );
        } else {
            edges.append(edgeBuilder.getEdgeId(), new ElementTimestampMutation(incrementingTimestamp++));
        }
        if (edgeBuilder.getNewEdgeLabel() != null) {
            edges.append(edgeBuilder.getEdgeId(), new AlterEdgeLabelMutation(incrementingTimestamp, edgeBuilder.getNewEdgeLabel()));
        }

        InMemoryEdge edge = this.edges.get(InMemoryGraph.this, edgeBuilder.getEdgeId(), authorizations);
        if (isNew && hasEventListeners()) {
            fireGraphEvent(new AddEdgeEvent(InMemoryGraph.this, edge));
        }
        edge.updatePropertiesInternal(edgeBuilder);

        if (edgeBuilder.getIndexHint() != IndexHint.DO_NOT_INDEX) {
            getSearchIndex().addElement(InMemoryGraph.this, edge, authorizations);
        }

        return edge;
    }

    @Override
    public Iterable<Edge> getEdges(EnumSet<FetchHint> fetchHints, final Long endTime, final Authorizations authorizations) {
        return new ConvertingIterable<InMemoryEdge, Edge>(this.edges.getAll(InMemoryGraph.this, fetchHints, endTime, authorizations)) {
            @Override
            protected Edge convert(InMemoryEdge o) {
                return o;
            }
        };
    }

    @Override
    protected GraphMetadataStore getGraphMetadataStore() {
        return graphMetadataStore;
    }

    @Override
    public void deleteEdge(Edge edge, Authorizations authorizations) {
        checkNotNull(edge, "Edge cannot be null");
        if (!((InMemoryEdge) edge).canRead(authorizations)) {
            return;
        }

        this.edges.remove(edge.getId());
        getSearchIndex().deleteElement(this, edge, authorizations);

        if (hasEventListeners()) {
            fireGraphEvent(new DeleteEdgeEvent(this, edge));
        }
    }

    @Override
    public void softDeleteEdge(Edge edge, Long timestamp, Authorizations authorizations) {
        checkNotNull(edge, "Edge cannot be null");
        if (!((InMemoryEdge) edge).canRead(authorizations)) {
            return;
        }
        if (timestamp == null) {
            timestamp = IncreasingTime.currentTimeMillis();
        }

        this.edges.getTableElement(edge.getId()).appendSoftDeleteMutation(timestamp);

        getSearchIndex().deleteElement(this, edge, authorizations);

        if (hasEventListeners()) {
            fireGraphEvent(new SoftDeleteEdgeEvent(this, edge));
        }
    }

    @Override
    public void markEdgeHidden(Edge edge, Visibility visibility, Authorizations authorizations) {
        if (!((InMemoryEdge) edge).canRead(authorizations)) {
            return;
        }

        Vertex inVertex = getVertex(edge.getVertexId(Direction.IN), authorizations);
        checkNotNull(inVertex, "Could not find in vertex: " + edge.getVertexId(Direction.IN));
        Vertex outVertex = getVertex(edge.getVertexId(Direction.OUT), authorizations);
        checkNotNull(outVertex, "Could not find out vertex: " + edge.getVertexId(Direction.OUT));

        this.edges.getTableElement(edge.getId()).appendMarkHiddenMutation(visibility);
        getSearchIndex().addElement(this, edge, authorizations);

        if (hasEventListeners()) {
            fireGraphEvent(new MarkHiddenEdgeEvent(this, edge));
        }
    }

    @Override
    public void markEdgeVisible(Edge edge, Visibility visibility, Authorizations authorizations) {
        if (!((InMemoryEdge) edge).canRead(authorizations)) {
            return;
        }

        Vertex inVertex = getVertex(edge.getVertexId(Direction.IN), FetchHint.ALL_INCLUDING_HIDDEN, authorizations);
        checkNotNull(inVertex, "Could not find in vertex: " + edge.getVertexId(Direction.IN));
        Vertex outVertex = getVertex(edge.getVertexId(Direction.OUT), FetchHint.ALL_INCLUDING_HIDDEN, authorizations);
        checkNotNull(outVertex, "Could not find out vertex: " + edge.getVertexId(Direction.OUT));

        this.edges.getTableElement(edge.getId()).appendMarkVisibleMutation(visibility);
        getSearchIndex().addElement(this, edge, authorizations);

        if (hasEventListeners()) {
            fireGraphEvent(new MarkVisibleEdgeEvent(this, edge));
        }
    }

    @Override
    public Authorizations createAuthorizations(String... auths) {
        addValidAuthorizations(auths);
        return new InMemoryAuthorizations(auths);
    }

    protected Iterable<Edge> getEdgesFromVertex(final String vertexId, final EnumSet<FetchHint> fetchHints,
                                                final Long endTime, final Authorizations authorizations) {
        final boolean includeHidden = fetchHints.contains(FetchHint.INCLUDE_HIDDEN);

        return new LookAheadIterable<InMemoryTableEdge, Edge>() {
            @Override
            protected boolean isIncluded(InMemoryTableEdge inMemoryTableElement, Edge edge) {
                if (edge == null) {
                    return false; // edge deleted or outside of time range
                }

                EdgeSetupMutation edgeSetupMutation = inMemoryTableElement.findLastMutation(EdgeSetupMutation.class);
                String inVertexId = edgeSetupMutation.getInVertexId();
                checkNotNull(inVertexId, "inVertexId was null");
                String outVertexId = edgeSetupMutation.getOutVertexId();
                checkNotNull(outVertexId, "outVertexId was null");

                return (inVertexId.equals(vertexId) || outVertexId.equals(vertexId)) &&
                        InMemoryGraph.this.isIncluded(inMemoryTableElement, fetchHints, authorizations);
            }

            @Override
            protected Edge convert(InMemoryTableEdge inMemoryTableElement) {
                return inMemoryTableElement.createElement(InMemoryGraph.this, includeHidden, endTime, authorizations);
            }

            @Override
            protected Iterator<InMemoryTableEdge> createIterator() {
                return edges.getAllTableElements().iterator();
            }
        };
    }

    protected boolean isIncluded(InMemoryTableElement element, EnumSet<FetchHint> fetchHints,
                                 Authorizations authorizations) {
        boolean includeHidden = fetchHints.contains(FetchHint.INCLUDE_HIDDEN);

        if (!element.canRead(authorizations)) {
            return false;
        }

        if (!includeHidden) {
            if (element.isHidden(authorizations)) {
                return false;
            }
        }

        return true;
    }

    protected boolean isIncludedInTimeSpan(InMemoryTableElement element, EnumSet<FetchHint> fetchHints, Long endTime,
                                           Authorizations authorizations) {
        boolean includeHidden = fetchHints.contains(FetchHint.INCLUDE_HIDDEN);

        if (!element.canRead(authorizations)) {
            return false;
        }
        if (!includeHidden && element.isHidden(authorizations)) {
            return false;
        }

        if (element.isDeleted(endTime, authorizations)) {
            return false;
        }

        if (endTime != null && element.getFirstTimestamp() > endTime) {
            return false;
        }

        return true;
    }

    protected void softDeleteProperty(InMemoryTableElement inMemoryTableElement, Property property, Long timestamp, Authorizations authorizations) {
        Element element;
        if (inMemoryTableElement instanceof InMemoryTableVertex) {
            inMemoryTableElement.appendSoftDeletePropertyMutation(property.getKey(), property.getName(), property.getVisibility(), timestamp);
            element = getVertex(inMemoryTableElement.getId(), authorizations);
        } else if (inMemoryTableElement instanceof InMemoryTableEdge) {
            inMemoryTableElement.appendSoftDeletePropertyMutation(property.getKey(), property.getName(), property.getVisibility(), timestamp);
            element = getEdge(inMemoryTableElement.getId(), authorizations);
        } else {
            throw new IllegalArgumentException("Unexpected element type: " + inMemoryTableElement.getClass().getName());
        }
        getSearchIndex().deleteProperty(this, element, property, authorizations);

        if (hasEventListeners()) {
            fireGraphEvent(new SoftDeletePropertyEvent(this, element, property));
        }
    }

    public void addPropertyValue(
            InMemoryElement element,
            InMemoryTableElement inMemoryTableElement,
            String key,
            String name,
            Object value,
            Metadata metadata,
            Visibility visibility,
            Long timestamp,
            Authorizations authorizations
    ) {
        inMemoryTableElement.appendAddPropertyMutation(key, name, value, metadata, visibility, timestamp);
        Property property = inMemoryTableElement.getProperty(key, name, visibility, authorizations);

        if (hasEventListeners()) {
            fireGraphEvent(new AddPropertyEvent(this, element, property));
        }
    }

    protected void alterElementVisibility(InMemoryTableElement inMemoryTableElement, Visibility newEdgeVisibility) {
        inMemoryTableElement.appendAlterVisibilityMutation(newEdgeVisibility);
    }

    protected void alterElementPropertyVisibilities(InMemoryTableElement inMemoryTableElement, List<AlterPropertyVisibility> alterPropertyVisibilities, Authorizations authorizations) {
        for (AlterPropertyVisibility apv : alterPropertyVisibilities) {
            Property property = inMemoryTableElement.getProperty(apv.getKey(), apv.getName(), apv.getExistingVisibility(), authorizations);
            if (property == null) {
                throw new VertexiumException("Could not find property " + apv.getKey() + ":" + apv.getName());
            }
            if (apv.getExistingVisibility() == null) {
                apv.setExistingVisibility(property.getVisibility());
            }
            Object value = property.getValue();
            Metadata metadata = property.getMetadata();

            inMemoryTableElement.appendSoftDeletePropertyMutation(apv.getKey(), apv.getName(), apv.getExistingVisibility(), IncreasingTime.currentTimeMillis());
            inMemoryTableElement.appendAddPropertyMutation(apv.getKey(), apv.getName(), value, metadata, apv.getVisibility(), IncreasingTime.currentTimeMillis());
        }
    }

    protected void alterElementPropertyMetadata(InMemoryTableElement element, List<SetPropertyMetadata> setPropertyMetadatas, Authorizations authorizations) {
        for (SetPropertyMetadata apm : setPropertyMetadatas) {
            Property property = element.getProperty(apm.getPropertyKey(), apm.getPropertyName(), apm.getPropertyVisibility(), authorizations);
            if (property == null) {
                throw new VertexiumException("Could not find property " + apm.getPropertyKey() + ":" + apm.getPropertyName());
            }

            property.getMetadata().add(apm.getMetadataName(), apm.getNewValue(), apm.getMetadataVisibility());
        }
    }

    @Override
    public boolean isVisibilityValid(Visibility visibility, Authorizations authorizations) {
        return authorizations.canRead(visibility);
    }

    @Override
    public void truncate() {
        this.vertices.clear();
        this.edges.clear();
        getSearchIndex().truncate();
    }

    @Override
    public void drop() {
        this.vertices.clear();
        this.edges.clear();
        getSearchIndex().drop();
    }

    protected void alterEdgeLabel(InMemoryTableEdge inMemoryTableEdge, String newEdgeLabel) {
        inMemoryTableEdge.appendAlterEdgeLabelMutation(newEdgeLabel);
    }

    protected void deleteProperty(
            InMemoryElement element,
            InMemoryTableElement inMemoryTableElement,
            String key, String name, Visibility visibility,
            Authorizations authorizations
    ) {
        Property property = inMemoryTableElement.getProperty(key, name, visibility, authorizations);
        inMemoryTableElement.deleteProperty(key, name, visibility, authorizations);

        getSearchIndex().deleteProperty(this, element, property, authorizations);

        if (hasEventListeners()) {
            fireGraphEvent(new DeletePropertyEvent(this, element, property));
        }
    }
}
