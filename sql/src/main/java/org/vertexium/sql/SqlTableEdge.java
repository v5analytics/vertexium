package org.vertexium.sql;

import org.vertexium.*;
import org.vertexium.inmemory.InMemoryEdge;
import org.vertexium.inmemory.InMemoryGraph;
import org.vertexium.inmemory.InMemoryTableEdge;
import org.vertexium.inmemory.mutations.EdgeSetupMutation;
import org.vertexium.inmemory.mutations.Mutation;
import org.vertexium.sql.collections.Storable;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SqlTableEdge extends SqlTableElement<InMemoryEdge> {
    private static final long serialVersionUID = 3053012430427429799L;

    public SqlTableEdge(String id) {
        super(id);
    }

    @Override
    public InMemoryEdge createElementInternal(InMemoryGraph graph, EnumSet<FetchHint> fetchHints, Long endTime, Authorizations authorizations) {
        return new InMemoryEdge(graph, getId(), asInMemoryTableElement(), fetchHints, endTime, authorizations);
    }

    String inVertexId() {
        EdgeSetupMutation edgeSetupMutation = findLastMutation(EdgeSetupMutation.class);
        return edgeSetupMutation != null ? edgeSetupMutation.getInVertexId() : null;
    }

    String outVertexId() {
        EdgeSetupMutation edgeSetupMutation = findLastMutation(EdgeSetupMutation.class);
        return edgeSetupMutation != null ? edgeSetupMutation.getOutVertexId() : null;
    }

    @Override
    InMemoryTableEdge asInMemoryTableElement() {
        return new InMemoryAdapter(this);
    }

    private static class InMemoryAdapter extends InMemoryTableEdge
            implements Storable<SqlTableElement<InMemoryEdge>, SqlGraph> {
        private SqlTableEdge sqlTableEdge;

        InMemoryAdapter(SqlTableEdge sqlTableEdge) {
            super(sqlTableEdge.getId());
            this.sqlTableEdge = sqlTableEdge;
        }

        @Override
        public void setContainer(Map<String, SqlTableElement<InMemoryEdge>> map, SqlGraph graph) {
            sqlTableEdge.setContainer(map, graph);
        }

        @Override
        public void store() {
            sqlTableEdge.store();
        }

        @Override
        public String getId() {
            return sqlTableEdge.getId();
        }

        @Override
        public void addAll(Mutation... newMutations) {
            sqlTableEdge.addAll(newMutations);
        }

        @Override
        public long getFirstTimestamp() {
            return sqlTableEdge.getFirstTimestamp();
        }

        @Override
        protected <T extends Mutation> T findLastMutation(Class<T> clazz) {
            return sqlTableEdge.findLastMutation(clazz);
        }

        @Override
        protected <T extends Mutation> Iterable<T> findMutations(Class<T> clazz) {
            return sqlTableEdge.findMutations(clazz);
        }

        @Override
        public Visibility getVisibility() {
            return sqlTableEdge.getVisibility();
        }

        @Override
        public long getTimestamp() {
            return sqlTableEdge.getTimestamp();
        }

        @Override
        public Property deleteProperty(String key, String name, Authorizations authorizations) {
            return sqlTableEdge.deleteProperty(key, name, authorizations);
        }

        @Override
        public Property getProperty(String key, String name, Visibility visibility, EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
            return sqlTableEdge.getProperty(key, name, visibility, fetchHints, authorizations);
        }

        @Override
        public Property deleteProperty(String key, String name, Visibility visibility, Authorizations authorizations) {
            return sqlTableEdge.deleteProperty(key, name, visibility, authorizations);
        }

        @Override
        protected void deleteProperty(Property p) {
            sqlTableEdge.deleteProperty(p);
        }

        @Override
        public Iterable<HistoricalPropertyValue> getHistoricalPropertyValues(String key, String name, Visibility visibility, Long startTime, Long endTime, Authorizations authorizations) {
            return sqlTableEdge.getHistoricalPropertyValues(key, name, visibility, startTime, endTime, authorizations);
        }

        @Override
        public Iterable<Property> getProperties(EnumSet<FetchHint> fetchHints, Long endTime, Authorizations authorizations) {
            return sqlTableEdge.getProperties(fetchHints, endTime, authorizations);
        }

        @Override
        public void appendSoftDeleteMutation(Long timestamp) {
            sqlTableEdge.appendSoftDeleteMutation(timestamp);
        }

        @Override
        public void appendMarkHiddenMutation(Visibility visibility) {
            sqlTableEdge.appendMarkHiddenMutation(visibility);
        }

        @Override
        public void appendMarkVisibleMutation(Visibility visibility) {
            sqlTableEdge.appendMarkVisibleMutation(visibility);
        }

        @Override
        public Property appendMarkPropertyHiddenMutation(String key, String name, Visibility propertyVisibility, Long timestamp, Visibility visibility, Authorizations authorizations) {
            return sqlTableEdge.appendMarkPropertyHiddenMutation(key, name, propertyVisibility, timestamp, visibility, authorizations);
        }

        @Override
        public Property appendMarkPropertyVisibleMutation(String key, String name, Visibility propertyVisibility, Long timestamp, Visibility visibility, Authorizations authorizations) {
            return sqlTableEdge.appendMarkPropertyVisibleMutation(key, name, propertyVisibility, timestamp, visibility, authorizations);
        }

        @Override
        public void appendSoftDeletePropertyMutation(String key, String name, Visibility propertyVisibility, Long timestamp) {
            sqlTableEdge.appendSoftDeletePropertyMutation(key, name, propertyVisibility, timestamp);
        }

        @Override
        public void appendAlterVisibilityMutation(Visibility newVisibility) {
            sqlTableEdge.appendAlterVisibilityMutation(newVisibility);
        }

        @Override
        public void appendAddPropertyValueMutation(String key, String name, Object value, Metadata metadata, Visibility visibility, Long timestamp) {
            sqlTableEdge.appendAddPropertyValueMutation(key, name, value, metadata, visibility, timestamp);
        }

        @Override
        public void appendAlterEdgeLabelMutation(long timestamp, String newEdgeLabel) {
            sqlTableEdge.appendAlterEdgeLabelMutation(timestamp, newEdgeLabel);
        }

        @Override
        public void appendAddPropertyMetadataMutation(String key, String name, Metadata metadata, Visibility visibility, Long timestamp) {
            sqlTableEdge.appendAddPropertyMetadataMutation(key, name, metadata, visibility, timestamp);
        }

        @Override
        protected List<Mutation> getFilteredMutations(boolean includeHidden, Long endTime, Authorizations authorizations) {
            return sqlTableEdge.getFilteredMutations(includeHidden, endTime, authorizations);
        }

        @Override
        public boolean canRead(Authorizations authorizations) {
            return sqlTableEdge.canRead(authorizations);
        }

        @Override
        public Set<Visibility> getHiddenVisibilities() {
            return sqlTableEdge.getHiddenVisibilities();
        }

        @Override
        public boolean isHidden(Authorizations authorizations) {
            return sqlTableEdge.isHidden(authorizations);
        }

        @Override
        public InMemoryEdge createElement(InMemoryGraph graph, EnumSet<FetchHint> fetchHints, Authorizations authorizations) {
            return sqlTableEdge.createElement(graph, fetchHints, authorizations);
        }

        @Override
        public boolean isDeleted(Long endTime, Authorizations authorizations) {
            return sqlTableEdge.isDeleted(endTime, authorizations);
        }

        @Override
        public InMemoryEdge createElementInternal(InMemoryGraph graph, EnumSet<FetchHint> fetchHints, Long endTime, Authorizations authorizations) {
            return sqlTableEdge.createElementInternal(graph, fetchHints, endTime, authorizations);
        }
    }
}
