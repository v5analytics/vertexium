package org.vertexium.sql;

import org.vertexium.*;
import org.vertexium.inmemory.InMemoryGraph;
import org.vertexium.inmemory.InMemoryTableVertex;
import org.vertexium.inmemory.InMemoryVertex;
import org.vertexium.inmemory.mutations.Mutation;
import org.vertexium.sql.collections.Storable;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class SqlTableVertex extends SqlTableElement<InMemoryVertex> {

    public SqlTableVertex(String id) {
        super(id);
    }

    @Override
    public InMemoryVertex createElementInternal(InMemoryGraph graph, boolean includeHidden, Long endTime,
                                                Authorizations authorizations) {
        return new InMemoryVertex(graph, getId(), asInMemoryTableVertex(), includeHidden, endTime, authorizations);
    }

    InMemoryTableVertex asInMemoryTableVertex() {
        return new InMemoryAdapter(this);
    }

    private static class InMemoryAdapter extends InMemoryTableVertex implements Storable<SqlTableElement<InMemoryVertex>> {
        private SqlTableVertex sqlTableVertex;

        InMemoryAdapter(SqlTableVertex sqlTableVertex) {
            super(sqlTableVertex.getId());
            this.sqlTableVertex = sqlTableVertex;
        }

        @Override
        public void setContainer(Map<String, SqlTableElement<InMemoryVertex>> map) {
            sqlTableVertex.setContainer(map);
        }

        @Override
        public void store() {
            sqlTableVertex.store();
        }

        @Override
        public String getId() {
            return sqlTableVertex.getId();
        }

        @Override
        public void addAll(Mutation... newMutations) {
            sqlTableVertex.addAll(newMutations);
        }

        @Override
        public long getFirstTimestamp() {
            return sqlTableVertex.getFirstTimestamp();
        }

        @Override
        protected <T extends Mutation> T findLastMutation(Class<T> clazz) {
            return sqlTableVertex.findLastMutation(clazz);
        }

        @Override
        protected <T extends Mutation> Iterable<T> findMutations(Class<T> clazz) {
            return sqlTableVertex.findMutations(clazz);
        }

        @Override
        public Visibility getVisibility() {
            return sqlTableVertex.getVisibility();
        }

        @Override
        public long getTimestamp() {
            return sqlTableVertex.getTimestamp();
        }

        @Override
        public Property deleteProperty(String key, String name, Authorizations authorizations) {
            return sqlTableVertex.deleteProperty(key, name, authorizations);
        }

        @Override
        public Property getProperty(String key, String name, Visibility visibility, Authorizations authorizations) {
            return sqlTableVertex.getProperty(key, name, visibility, authorizations);
        }

        @Override
        public Property deleteProperty(String key, String name, Visibility visibility, Authorizations authorizations) {
            return sqlTableVertex.deleteProperty(key, name, visibility, authorizations);
        }

        @Override
        protected void deleteProperty(Property p) {
            sqlTableVertex.deleteProperty(p);
        }

        @Override
        public Iterable<HistoricalPropertyValue> getHistoricalPropertyValues(String key, String name, Visibility visibility, Long startTime, Long endTime, Authorizations authorizations) {
            return sqlTableVertex.getHistoricalPropertyValues(key, name, visibility, startTime, endTime, authorizations);
        }

        @Override
        public Iterable<Property> getProperties(boolean includeHidden, Long endTime, Authorizations authorizations) {
            return sqlTableVertex.getProperties(includeHidden, endTime, authorizations);
        }

        @Override
        public void appendSoftDeleteMutation(Long timestamp) {
            sqlTableVertex.appendSoftDeleteMutation(timestamp);
        }

        @Override
        public void appendMarkHiddenMutation(Visibility visibility) {
            sqlTableVertex.appendMarkHiddenMutation(visibility);
        }

        @Override
        public void appendMarkVisibleMutation(Visibility visibility) {
            sqlTableVertex.appendMarkVisibleMutation(visibility);
        }

        @Override
        public Property appendMarkPropertyHiddenMutation(String key, String name, Visibility propertyVisibility, Long timestamp, Visibility visibility, Authorizations authorizations) {
            return sqlTableVertex.appendMarkPropertyHiddenMutation(key, name, propertyVisibility, timestamp, visibility, authorizations);
        }

        @Override
        public Property appendMarkPropertyVisibleMutation(String key, String name, Visibility propertyVisibility, Long timestamp, Visibility visibility, Authorizations authorizations) {
            return sqlTableVertex.appendMarkPropertyVisibleMutation(key, name, propertyVisibility, timestamp, visibility, authorizations);
        }

        @Override
        public void appendSoftDeletePropertyMutation(String key, String name, Visibility propertyVisibility, Long timestamp) {
            sqlTableVertex.appendSoftDeletePropertyMutation(key, name, propertyVisibility, timestamp);
        }

        @Override
        public void appendAlterVisibilityMutation(Visibility newVisibility) {
            sqlTableVertex.appendAlterVisibilityMutation(newVisibility);
        }

        @Override
        public void appendAddPropertyMutation(String key, String name, Object value, Metadata metadata, Visibility visibility, Long timestamp) {
            sqlTableVertex.appendAddPropertyMutation(key, name, value, metadata, visibility, timestamp);
        }

        @Override
        public void appendAlterEdgeLabelMutation(String newEdgeLabel) {
            sqlTableVertex.appendAlterEdgeLabelMutation(newEdgeLabel);
        }

        @Override
        protected List<Mutation> getFilteredMutations(boolean includeHidden, Long endTime, Authorizations authorizations) {
            return sqlTableVertex.getFilteredMutations(includeHidden, endTime, authorizations);
        }

        @Override
        public boolean canRead(Authorizations authorizations) {
            return sqlTableVertex.canRead(authorizations);
        }

        @Override
        public Set<Visibility> getHiddenVisibilities() {
            return sqlTableVertex.getHiddenVisibilities();
        }

        @Override
        public boolean isHidden(Authorizations authorizations) {
            return sqlTableVertex.isHidden(authorizations);
        }

        @Override
        public InMemoryVertex createElement(InMemoryGraph graph, Authorizations authorizations) {
            return sqlTableVertex.createElement(graph, authorizations);
        }

        @Override
        public boolean isDeleted(Long endTime, Authorizations authorizations) {
            return sqlTableVertex.isDeleted(endTime, authorizations);
        }

        @Override
        public InMemoryVertex createElementInternal(InMemoryGraph graph, boolean includeHidden, Long endTime, Authorizations authorizations) {
            return sqlTableVertex.createElementInternal(graph, includeHidden, endTime, authorizations);
        }
    }
}
