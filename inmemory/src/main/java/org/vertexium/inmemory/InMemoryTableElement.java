package org.vertexium.inmemory;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.vertexium.*;
import org.vertexium.historicalEvent.*;
import org.vertexium.inmemory.mutations.*;
import org.vertexium.property.MutablePropertyImpl;
import org.vertexium.property.StreamingPropertyValue;
import org.vertexium.property.StreamingPropertyValueRef;
import org.vertexium.util.IncreasingTime;
import org.vertexium.util.LookAheadIterable;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class InMemoryTableElement<TElement extends InMemoryElement> implements Serializable, ElementId {
    private final ReadWriteLock mutationLock = new ReentrantReadWriteLock();
    private final String id;
    private final MetadataPlugin metadataPlugin;
    protected final TreeSet<Mutation> mutations = new TreeSet<>();

    protected InMemoryTableElement(String id, MetadataPlugin metadataPlugin) {
        this.id = id;
        this.metadataPlugin = metadataPlugin;
    }

    @Override
    public String getId() {
        return id;
    }

    public void addAll(Mutation... newMutations) {
        mutationLock.writeLock().lock();
        try {
            Collections.addAll(mutations, newMutations);
        } finally {
            mutationLock.writeLock().unlock();
        }
    }

    public long getFirstTimestamp() {
        return findFirstMutation(ElementTimestampMutation.class).getTimestamp();
    }

    @SuppressWarnings("unchecked")
    protected <T extends Mutation> T findLastMutation(Class<T> clazz) {
        List<Mutation> filteredMutations = getFilteredMutations(m -> clazz.isAssignableFrom(m.getClass()));
        return filteredMutations.isEmpty() ? null : (T) filteredMutations.get(filteredMutations.size() - 1);
    }

    @SuppressWarnings("unchecked")
    protected <T extends Mutation> T findFirstMutation(Class<T> clazz) {
        List<Mutation> filteredMutations = getFilteredMutations(m -> clazz.isAssignableFrom(m.getClass()));
        //noinspection unchecked
        return filteredMutations.isEmpty() ? null : (T) filteredMutations.get(0);
    }

    @SuppressWarnings("unchecked")
    protected <T extends Mutation> Iterable<T> findMutations(Class<T> clazz) {
        //noinspection unchecked
        return (Iterable<T>) getFilteredMutations(m -> clazz.isAssignableFrom(m.getClass()));
    }

    public Visibility getVisibility() {
        return findLastMutation(AlterVisibilityMutation.class).getNewVisibility();
    }

    public long getTimestamp() {
        return findLastMutation(ElementTimestampMutation.class).getTimestamp();
    }

    private List<PropertyMutation> findPropertyMutations(Property p) {
        return findPropertyMutations(p.getKey(), p.getName(), p.getVisibility());
    }

    public Property deleteProperty(String key, String name, Authorizations authorizations) {
        return deleteProperty(key, name, null, authorizations);
    }

    public Property getProperty(String key, String name, Visibility visibility, FetchHints fetchHints, Authorizations authorizations) {
        List<PropertyMutation> propertyMutations = findPropertyMutations(key, name, visibility);
        if (propertyMutations == null || propertyMutations.size() == 0) {
            return null;
        }
        return toProperty(propertyMutations, fetchHints, authorizations);
    }

    public Property deleteProperty(String key, String name, Visibility visibility, Authorizations authorizations) {
        Property p = getProperty(key, name, visibility, FetchHints.ALL_INCLUDING_HIDDEN, authorizations);
        if (p != null) {
            deleteProperty(p);
        }
        return p;
    }

    protected void deleteProperty(Property p) {
        List<PropertyMutation> propertyMutations = findPropertyMutations(p);
        mutationLock.writeLock().lock();
        try {
            this.mutations.removeAll(propertyMutations);
        } finally {
            mutationLock.writeLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public Stream<HistoricalEvent> getHistoricalEvents(
        InMemoryGraph graph,
        HistoricalEventId after,
        HistoricalEventsFetchHints historicalEventsFetchHints,
        Authorizations authorizations
    ) {
        List<Mutation> mutations = getFilteredMutations(m -> true).stream()
            .sorted(Comparator.comparingLong(Mutation::getTimestamp).thenComparing(this::getHistoricalOrder))
            .collect(Collectors.toList());
        List<HistoricalEvent> historicalEvents = new ArrayList<>();
        Map<String, Object> previousValues = new HashMap<>();
        Visibility createVisibility = null;
        boolean isFirstAlterVisibilityMutation = true;
        ZonedDateTime createTimestamp = null;
        boolean isFirstElementTimestampMutation = true;
        String edgeLabel = null;
        boolean isFirstAlterEdgeLabelMutation = true;
        String inVertexId = null;
        String outVertexId = null;
        boolean isFirstEdgeSetupMutation = true;
        boolean isHistoricalAddElementEventAdded = false;

        for (Mutation m : mutations) {
            if (!canRead(m.getVisibility(), authorizations)) {
                continue;
            }

            if (!isMutationInTimeRangeAndVisible(m, historicalEventsFetchHints.getStartTime(), historicalEventsFetchHints.getEndTime(), authorizations)) {
                if (m instanceof AddPropertyValueMutation) {
                    AddPropertyValueMutation addPropertyValueMutation = (AddPropertyValueMutation) m;
                    String previousValueKey = Joiner.on("_").join(
                        getElementType(),
                        getId(),
                        addPropertyValueMutation.getPropertyKey(),
                        addPropertyValueMutation.getPropertyName()
                    );
                    previousValues.put(previousValueKey, addPropertyValueMutation.getValue());
                }
                continue;
            }

            if (m instanceof AddPropertyValueMutation) {
                AddPropertyValueMutation addPropertyValueMutation = (AddPropertyValueMutation) m;
                String previousValueKey = Joiner.on("_").join(
                    getElementType(),
                    getId(),
                    addPropertyValueMutation.getPropertyKey(),
                    addPropertyValueMutation.getPropertyName()
                );
                Object previousValue = historicalEventsFetchHints.isIncludePreviousPropertyValues()
                    ? previousValues.get(previousValueKey)
                    : null;
                long timestamp = addPropertyValueMutation.getTimestamp();
                Object value;
                if (historicalEventsFetchHints.isIncludePropertyValues()) {
                    value = addPropertyValueMutation.getValue();
                    if (value instanceof StreamingPropertyValueRef) {
                        value = ((StreamingPropertyValueRef) value).toStreamingPropertyValue(graph, timestamp);
                    }
                } else {
                    value = null;
                }
                historicalEvents.add(new HistoricalAddPropertyEvent(
                    getElementType(),
                    getId(),
                    addPropertyValueMutation.getPropertyKey(),
                    addPropertyValueMutation.getPropertyName(),
                    addPropertyValueMutation.getPropertyVisibility(),
                    previousValue,
                    value,
                    addPropertyValueMutation.getMetadata(FetchHints.ALL),
                    HistoricalEvent.zonedDateTimeFromTimestamp(timestamp),
                    historicalEventsFetchHints
                ));
                previousValues.put(previousValueKey, value);
            } else if (m instanceof AddPropertyMetadataMutation) {
                // metadata will always come after the add property mutation
                AddPropertyMetadataMutation addPropertyMetadataMutation = (AddPropertyMetadataMutation) m;
                for (int i = historicalEvents.size() - 1; i >= 0; i--) {
                    HistoricalEvent e = historicalEvents.get(i);
                    if (e instanceof HistoricalAddPropertyEvent) {
                        HistoricalAddPropertyEvent addPropertyEvent = (HistoricalAddPropertyEvent) e;
                        if (addPropertyEvent.getPropertyKey().equals(addPropertyMetadataMutation.getPropertyKey())
                            && addPropertyEvent.getPropertyName().equals(addPropertyMetadataMutation.getPropertyName())
                            && addPropertyEvent.getPropertyVisibility().equals(addPropertyMetadataMutation.getPropertyVisibility())) {
                            for (Metadata.Entry entry : addPropertyMetadataMutation.getMetadata(FetchHints.ALL).entrySet()) {
                                addPropertyEvent.getMetadata().add(entry.getKey(), entry.getValue(), entry.getVisibility());
                            }
                            break;
                        }
                    }
                }
            } else if (m instanceof AlterEdgeLabelMutation) {
                AlterEdgeLabelMutation alterEdgeLabelMutation = (AlterEdgeLabelMutation) m;
                if (isFirstAlterEdgeLabelMutation) {
                    edgeLabel = alterEdgeLabelMutation.getNewEdgeLabel();
                    isFirstAlterEdgeLabelMutation = false;
                } else {
                    edgeLabel = alterEdgeLabelMutation.getNewEdgeLabel();
                    historicalEvents.add(new HistoricalAlterEdgeLabelEvent(
                        getId(),
                        edgeLabel,
                        HistoricalEvent.zonedDateTimeFromTimestamp(alterEdgeLabelMutation.getTimestamp()),
                        historicalEventsFetchHints
                    ));
                }
            } else if (m instanceof AlterVisibilityMutation) {
                AlterVisibilityMutation alterVisibilityMutation = (AlterVisibilityMutation) m;
                if (isFirstAlterVisibilityMutation) {
                    createVisibility = alterVisibilityMutation.getNewVisibility();
                    isFirstAlterVisibilityMutation = false;
                } else {
                    if (getElementType() == ElementType.VERTEX) {
                        historicalEvents.add(new HistoricalAlterVertexVisibilityEvent(
                            getId(),
                            createVisibility,
                            alterVisibilityMutation.getNewVisibility(),
                            HistoricalEvent.zonedDateTimeFromTimestamp(alterVisibilityMutation.getTimestamp()),
                            alterVisibilityMutation.getData(),
                            historicalEventsFetchHints
                        ));
                    } else if (getElementType() == ElementType.EDGE) {
                        historicalEvents.add(new HistoricalAlterEdgeVisibilityEvent(
                            getId(),
                            outVertexId,
                            inVertexId,
                            edgeLabel,
                            createVisibility,
                            alterVisibilityMutation.getNewVisibility(),
                            HistoricalEvent.zonedDateTimeFromTimestamp(alterVisibilityMutation.getTimestamp()),
                            alterVisibilityMutation.getData(),
                            historicalEventsFetchHints
                        ));
                    } else {
                        throw new VertexiumException("Unhandled element type: " + getElementType());
                    }
                    createVisibility = alterVisibilityMutation.getNewVisibility();
                }
            } else if (m instanceof EdgeSetupMutation) {
                EdgeSetupMutation edgeSetupMutation = (EdgeSetupMutation) m;
                if (isFirstEdgeSetupMutation) {
                    inVertexId = edgeSetupMutation.getInVertexId();
                    outVertexId = edgeSetupMutation.getOutVertexId();
                    isFirstEdgeSetupMutation = false;
                } else {
                    throw new VertexiumException("Unhandled Mutation: " + m.getClass().getName());
                }
            } else if (m instanceof ElementTimestampMutation) {
                if (isFirstElementTimestampMutation) {
                    createTimestamp = HistoricalEvent.zonedDateTimeFromTimestamp(m.getTimestamp());
                    isFirstElementTimestampMutation = false;
                }
            } else if (m instanceof MarkHiddenMutation) {
                MarkHiddenMutation markHiddenMutation = (MarkHiddenMutation) m;
                historicalEvents.add(new HistoricalMarkHiddenEvent(
                    getElementType(),
                    getId(),
                    markHiddenMutation.getVisibility(),
                    HistoricalEvent.zonedDateTimeFromTimestamp(markHiddenMutation.getTimestamp()),
                    markHiddenMutation.getData(),
                    historicalEventsFetchHints
                ));
            } else if (m instanceof MarkPropertyHiddenMutation) {
                MarkPropertyHiddenMutation markPropertyHiddenMutation = (MarkPropertyHiddenMutation) m;
                historicalEvents.add(new HistoricalMarkPropertyHiddenEvent(
                    getElementType(),
                    getId(),
                    markPropertyHiddenMutation.getPropertyKey(),
                    markPropertyHiddenMutation.getPropertyName(),
                    markPropertyHiddenMutation.getPropertyVisibility(),
                    markPropertyHiddenMutation.getVisibility(),
                    HistoricalEvent.zonedDateTimeFromTimestamp(markPropertyHiddenMutation.getTimestamp()),
                    markPropertyHiddenMutation.getData(),
                    historicalEventsFetchHints
                ));
            } else if (m instanceof MarkPropertyVisibleMutation) {
                MarkPropertyVisibleMutation markPropertyVisibleMutation = (MarkPropertyVisibleMutation) m;
                historicalEvents.add(new HistoricalMarkPropertyVisibleEvent(
                    getElementType(),
                    getId(),
                    markPropertyVisibleMutation.getPropertyKey(),
                    markPropertyVisibleMutation.getPropertyName(),
                    markPropertyVisibleMutation.getPropertyVisibility(),
                    markPropertyVisibleMutation.getVisibility(),
                    HistoricalEvent.zonedDateTimeFromTimestamp(markPropertyVisibleMutation.getTimestamp()),
                    markPropertyVisibleMutation.getData(),
                    historicalEventsFetchHints
                ));
            } else if (m instanceof MarkVisibleMutation) {
                MarkVisibleMutation markVisibleMutation = (MarkVisibleMutation) m;
                historicalEvents.add(new HistoricalMarkVisibleEvent(
                    getElementType(),
                    getId(),
                    markVisibleMutation.getVisibility(),
                    HistoricalEvent.zonedDateTimeFromTimestamp(markVisibleMutation.getTimestamp()),
                    markVisibleMutation.getData(),
                    historicalEventsFetchHints
                ));
            } else if (m instanceof SoftDeletePropertyMutation) {
                SoftDeletePropertyMutation softDeletePropertyMutation = (SoftDeletePropertyMutation) m;
                historicalEvents.add(new HistoricalSoftDeletePropertyEvent(
                    getElementType(),
                    getId(),
                    softDeletePropertyMutation.getPropertyKey(),
                    softDeletePropertyMutation.getPropertyName(),
                    softDeletePropertyMutation.getPropertyVisibility(),
                    HistoricalEvent.zonedDateTimeFromTimestamp(softDeletePropertyMutation.getTimestamp()),
                    softDeletePropertyMutation.getData(),
                    historicalEventsFetchHints
                ));
            } else if (m instanceof SoftDeleteMutation) {
                SoftDeleteMutation softDeleteMutation = (SoftDeleteMutation) m;
                if (getElementType() == ElementType.VERTEX) {
                    historicalEvents.add(new HistoricalSoftDeleteVertexEvent(
                        getId(),
                        HistoricalEvent.zonedDateTimeFromTimestamp(softDeleteMutation.getTimestamp()),
                        softDeleteMutation.getData(),
                        historicalEventsFetchHints
                    ));
                } else if (getElementType() == ElementType.EDGE) {
                    historicalEvents.add(new HistoricalSoftDeleteEdgeEvent(
                        getId(),
                        outVertexId,
                        inVertexId,
                        edgeLabel,
                        HistoricalEvent.zonedDateTimeFromTimestamp(softDeleteMutation.getTimestamp()),
                        softDeleteMutation.getData(),
                        historicalEventsFetchHints
                    ));
                } else {
                    throw new VertexiumException("Unhandled element type: " + getElementType());
                }
                isHistoricalAddElementEventAdded = false;
                isFirstElementTimestampMutation = true;
                isFirstAlterEdgeLabelMutation = true;
                createTimestamp = null;
                edgeLabel = null;
            } else {
                throw new VertexiumException("Unhandled Mutation: " + m.getClass().getName());
            }

            if (!isHistoricalAddElementEventAdded && createVisibility != null && createTimestamp != null) {
                if (getElementType() == ElementType.EDGE) {
                    if (edgeLabel != null && outVertexId != null && inVertexId != null) {
                        historicalEvents.add(new HistoricalAddEdgeEvent(
                            getId(),
                            outVertexId,
                            inVertexId,
                            edgeLabel,
                            createVisibility,
                            createTimestamp,
                            historicalEventsFetchHints
                        ));
                        isHistoricalAddElementEventAdded = true;
                    }
                } else if (getElementType() == ElementType.VERTEX) {
                    historicalEvents.add(new HistoricalAddVertexEvent(
                        getId(),
                        createVisibility,
                        createTimestamp,
                        historicalEventsFetchHints
                    ));
                    isHistoricalAddElementEventAdded = true;
                }
            }
        }

        if (getElementType() == ElementType.VERTEX) {
            historicalEvents.addAll(
                graph.getHistoricalVertexEdgeEvents(getId(), historicalEventsFetchHints, authorizations)
                    .collect(Collectors.toList())
            );
        }

        return historicalEventsFetchHints.applyToResults(historicalEvents.stream(), after);
    }

    private String getHistoricalOrder(Mutation m) {
        if (m instanceof AlterEdgeLabelMutation
            || m instanceof AlterVisibilityMutation
            || m instanceof EdgeSetupMutation) {
            return "0";
        } else if (m instanceof ElementTimestampMutation) {
            return "1";
        } else if (m instanceof AddPropertyValueMutation) {
            AddPropertyValueMutation addPropertyValueMutation = (AddPropertyValueMutation) m;
            return String.format("2%s_%sa", addPropertyValueMutation.getPropertyName(), addPropertyValueMutation.getPropertyKey());
        } else if (m instanceof AddPropertyMetadataMutation) {
            AddPropertyMetadataMutation addPropertyMetadataMutation = (AddPropertyMetadataMutation) m;
            return String.format("2%s_%sz", addPropertyMetadataMutation.getPropertyName(), addPropertyMetadataMutation.getPropertyKey());
        } else if (m instanceof MarkHiddenMutation
            || m instanceof MarkPropertyHiddenMutation
            || m instanceof MarkPropertyVisibleMutation
            || m instanceof MarkVisibleMutation
            || m instanceof SoftDeletePropertyMutation
            || m instanceof SoftDeleteMutation) {
            return "z";
        }
        throw new VertexiumException("Unhandled Mutation: " + m.getClass().getName());
    }

    private List<PropertyMutation> findPropertyMutations(String key, String name, Visibility visibility) {
        return getFilteredMutations(m ->
            m instanceof PropertyMutation &&
                (key == null || ((PropertyMutation) m).getPropertyKey().equals(key))
                && (name == null || ((PropertyMutation) m).getPropertyName().equals(name))
                && (visibility == null || ((PropertyMutation) m).getPropertyVisibility().equals(visibility))
        ).stream().map(m -> (PropertyMutation) m).collect(Collectors.toList());
    }

    @SuppressWarnings("deprecation")
    public Iterable<HistoricalPropertyValue> getHistoricalPropertyValues(
        String key,
        String name,
        Visibility visibility,
        Long startTime,
        Long endTime,
        Authorizations authorizations
    ) {
        List<PropertyMutation> propertyMutations = findPropertyMutations(key, name, visibility);
        List<HistoricalPropertyValue> historicalPropertyValues = new ArrayList<>();

        /*
         * There is the expectation that historical property values are a snapshot of the property in
         * time. This method attempts to reconstruct the property current state from mutations.
         */
        Map<String, HistoricalPropertyValue.HistoricalPropertyValueBuilder> currentPropertyBuilders = Maps.newHashMap();
        Set<Visibility> hiddenVisibilities = new HashSet<>();

        for (PropertyMutation m : propertyMutations) {
            String propertyIdentifier = m.getPropertyKey() + m.getPropertyName();
            HistoricalPropertyValue.HistoricalPropertyValueBuilder builder = currentPropertyBuilders.computeIfAbsent(
                propertyIdentifier,
                k -> new HistoricalPropertyValue.HistoricalPropertyValueBuilder(m.getPropertyKey(), m.getPropertyName(), m.getTimestamp())
            );

            if (!isMutationInTimeRangeAndVisible(m, startTime, endTime, authorizations)) {
                continue;
            }
            // Ignore workspace interactions to avoid duplicated entries
            if (m.getVisibility() != null && m.getPropertyVisibility().getVisibilityString().matches("(.*)WORKSPACE(.*)")) {
                continue;
            }

            if (m instanceof SoftDeletePropertyMutation) {
                builder.isDeleted(true);
                builder.timestamp(m.getTimestamp());
                historicalPropertyValues.add(builder.build());
            } else if (m instanceof AddPropertyMetadataMutation) {
                builder.metadata(((AddPropertyMetadataMutation) m).getMetadata(FetchHints.ALL));
                builder.timestamp(m.getTimestamp());
            } else if (m instanceof MarkPropertyHiddenMutation) {
                // Ignore
            } else if (m instanceof MarkPropertyVisibleMutation) {
                // Ignore
            } else if (m instanceof AddPropertyValueMutation) {
                AddPropertyValueMutation apvm = (AddPropertyValueMutation) m;
                Object value = apvm.getValue();
                value = loadIfStreamingPropertyValue(value, m.getTimestamp());

                builder.propertyVisibility(m.getPropertyVisibility())
                    .timestamp(m.getTimestamp())
                    .value(value)
                    .metadata(apvm.getMetadata(FetchHints.ALL))
                    .hiddenVisibilities(hiddenVisibilities)
                    .isDeleted(false);

                historicalPropertyValues.add(builder.build());
            } else {
                throw new VertexiumException("Unhandled PropertyMutation: " + m.getClass().getName());
            }
        }

        Collections.reverse(historicalPropertyValues);
        return historicalPropertyValues;
    }

    private boolean isMutationInTimeRangeAndVisible(Mutation m, ZonedDateTime startTime, ZonedDateTime endTime, Authorizations authorizations) {
        return isMutationInTimeRangeAndVisible(
            m,
            startTime == null ? null : startTime.toInstant().toEpochMilli(),
            endTime == null ? null : endTime.toInstant().toEpochMilli(),
            authorizations
        );
    }

    private boolean isMutationInTimeRangeAndVisible(Mutation m, Long startTime, Long endTime, Authorizations authorizations) {
        if (startTime != null && m.getTimestamp() < startTime) {
            return false;
        }
        if (endTime != null && m.getTimestamp() > endTime) {
            return false;
        }
        if (!canRead(m.getVisibility(), authorizations)) {
            return false;
        }
        return true;
    }

    public Iterable<Property> getProperties(FetchHints fetchHints, Long endTime, Authorizations authorizations) {
        final TreeMap<String, List<PropertyMutation>> propertiesMutations = new TreeMap<>();
        for (PropertyMutation m : findMutations(PropertyMutation.class)) {
            if (endTime != null && m.getTimestamp() > endTime) {
                continue;
            }

            String mapKey = toMapKey(m);
            List<PropertyMutation> propertyMutations = propertiesMutations.computeIfAbsent(mapKey, k -> new ArrayList<>());
            propertyMutations.add(m);
        }
        return new LookAheadIterable<List<PropertyMutation>, Property>() {
            @Override
            protected boolean isIncluded(List<PropertyMutation> src, Property property) {
                return property != null;
            }

            @Override
            protected Property convert(List<PropertyMutation> propertyMutations) {
                return toProperty(propertyMutations, fetchHints, authorizations);
            }

            @Override
            protected Iterator<List<PropertyMutation>> createIterator() {
                return propertiesMutations.values().iterator();
            }
        };
    }

    private Property toProperty(List<PropertyMutation> propertyMutations, FetchHints fetchHints, Authorizations authorizations) {
        String propertyKey = null;
        String propertyName = null;
        Object value = null;
        Metadata metadata = null;
        long timestamp = 0;
        Set<Visibility> hiddenVisibilities = new HashSet<>();
        Visibility visibility = null;
        boolean softDeleted = false;
        boolean hidden = false;
        for (PropertyMutation m : propertyMutations) {
            if (!canRead(m.getVisibility(), authorizations)) {
                continue;
            }

            propertyKey = m.getPropertyKey();
            propertyName = m.getPropertyName();
            visibility = m.getPropertyVisibility();
            if (m.getTimestamp() > timestamp) {
                timestamp = m.getTimestamp();
            }
            if (m instanceof AddPropertyValueMutation) {
                AddPropertyValueMutation apvm = (AddPropertyValueMutation) m;
                value = apvm.getValue();
                metadata = mergeMetadata(metadata, apvm.getMetadata(fetchHints), fetchHints);
                softDeleted = false;
            } else if (m instanceof AddPropertyMetadataMutation) {
                AddPropertyMetadataMutation apmm = (AddPropertyMetadataMutation) m;
                metadata = mergeMetadata(metadata, apmm.getMetadata(fetchHints), fetchHints);
            } else if (m instanceof SoftDeletePropertyMutation) {
                softDeleted = true;
            } else if (m instanceof MarkPropertyHiddenMutation) {
                hidden = true;
                hiddenVisibilities.add(m.getVisibility());
            } else if (m instanceof MarkPropertyVisibleMutation) {
                hidden = false;
                hiddenVisibilities.remove(m.getVisibility());
            } else {
                throw new VertexiumException("Unhandled PropertyMutation: " + m.getClass().getName());
            }
        }
        if (softDeleted) {
            return null;
        }
        if (!fetchHints.isIncludeHidden() && hidden) {
            return null;
        }
        if (propertyKey == null) {
            return null;
        }
        value = loadIfStreamingPropertyValue(value, timestamp);
        if (metadata != null) {
            metadata = applyMetadataPlugin(metadata, timestamp);
        }
        return new MutablePropertyImpl(propertyKey, propertyName, value, metadata, timestamp, hiddenVisibilities, visibility, fetchHints);
    }

    private Metadata applyMetadataPlugin(Metadata metadata, long propertyTimestamp) {
        Metadata results = Metadata.create(metadata.getFetchHints());
        for (Metadata.Entry entry : metadataPlugin.getAllDefaultEntries(propertyTimestamp, metadata.getFetchHints())) {
            results.add(entry.getKey(), entry.getValue(), entry.getVisibility());
        }
        for (Metadata.Entry entry : metadata.entrySet()) {
            results.add(entry.getKey(), entry.getValue(), entry.getVisibility());
        }
        return results;
    }

    private Metadata mergeMetadata(Metadata metadata, Metadata metadataToMerge, FetchHints fetchHints) {
        if (metadata == null) {
            return metadataToMerge;
        }
        if (!fetchHints.isIncludePreviousMetadata()) {
            return metadataToMerge;
        }
        metadata = Metadata.create(metadata);
        for (Metadata.Entry entry : metadataToMerge.entrySet()) {
            metadata.add(entry.getKey(), entry.getValue(), entry.getVisibility());
        }
        return metadata;
    }

    private Object loadIfStreamingPropertyValue(Object value, long timestamp) {
        if (value instanceof StreamingPropertyValueRef) {
            value = loadStreamingPropertyValue((StreamingPropertyValueRef) value, timestamp);
        }
        return value;
    }

    protected StreamingPropertyValue loadStreamingPropertyValue(StreamingPropertyValueRef<?> streamingPropertyValueRef, long timestamp) {
        // There's no need to have a Graph object for the pure in-memory implementation. Subclasses should override.
        return streamingPropertyValueRef.toStreamingPropertyValue(null, timestamp);
    }

    private String toMapKey(PropertyMutation m) {
        return m.getPropertyName() + m.getPropertyKey() + m.getPropertyVisibility().getVisibilityString();
    }

    public void appendSoftDeleteMutation(Long timestamp, Object data) {
        if (timestamp == null) {
            timestamp = IncreasingTime.currentTimeMillis();
        }
        addMutation(new SoftDeleteMutation(timestamp, data));
    }

    public void appendMarkHiddenMutation(Visibility visibility, Object data) {
        long timestamp = IncreasingTime.currentTimeMillis();
        addMutation(new MarkHiddenMutation(timestamp, visibility, data));
    }

    public void appendMarkVisibleMutation(Visibility visibility, Object data) {
        long timestamp = IncreasingTime.currentTimeMillis();
        addAll(new MarkVisibleMutation(timestamp, visibility, data));
    }

    public Property appendMarkPropertyHiddenMutation(
        String key,
        String name,
        Visibility propertyVisibility,
        Long timestamp,
        Visibility visibility,
        Object data,
        Authorizations authorizations
    ) {
        Property prop = getProperty(key, name, propertyVisibility, FetchHints.ALL_INCLUDING_HIDDEN, authorizations);
        if (timestamp == null) {
            timestamp = IncreasingTime.currentTimeMillis();
        }
        addMutation(new MarkPropertyHiddenMutation(key, name, propertyVisibility, timestamp, visibility, data));
        return prop;
    }

    public Property appendMarkPropertyVisibleMutation(
        String key,
        String name,
        Visibility propertyVisibility,
        Long timestamp,
        Visibility visibility,
        Object data,
        Authorizations authorizations
    ) {
        Property prop = getProperty(key, name, propertyVisibility, FetchHints.ALL_INCLUDING_HIDDEN, authorizations);
        if (timestamp == null) {
            timestamp = IncreasingTime.currentTimeMillis();
        }
        addMutation(new MarkPropertyVisibleMutation(key, name, propertyVisibility, timestamp, visibility, data));
        return prop;
    }

    public void appendSoftDeletePropertyMutation(String key, String name, Visibility propertyVisibility, Long timestamp, Object data) {
        if (timestamp == null) {
            timestamp = IncreasingTime.currentTimeMillis();
        }
        addMutation(new SoftDeletePropertyMutation(timestamp, key, name, propertyVisibility, data));
    }

    public void appendAddAdditionalVisibilityMutation(String visibility, Object eventData) {
        long timestamp = IncreasingTime.currentTimeMillis();
        addMutation(new AddAdditionalVisibilityMutation(timestamp, visibility, eventData));
    }

    public void appendDeleteAdditionalVisibilityMutation(String visibility, Object eventData) {
        long timestamp = IncreasingTime.currentTimeMillis();
        addMutation(new DeleteAdditionalVisibilityMutation(timestamp, visibility, eventData));
    }

    public void appendAlterVisibilityMutation(Visibility newVisibility, Object data) {
        long timestamp = IncreasingTime.currentTimeMillis();
        addMutation(new AlterVisibilityMutation(timestamp, newVisibility, data));
    }

    public void appendAddPropertyValueMutation(
        String key,
        String name,
        Object value,
        Metadata metadata,
        Visibility visibility,
        Long timestamp,
        Object data
    ) {
        if (timestamp == null) {
            timestamp = IncreasingTime.currentTimeMillis();
        }
        addMutation(new AddPropertyValueMutation(timestamp, key, name, value, metadata, visibility, data));
    }

    public void appendAddPropertyMetadataMutation(String key, String name, Metadata metadata, Visibility visibility, Long timestamp) {
        if (timestamp == null) {
            timestamp = IncreasingTime.currentTimeMillis();
        }
        addMutation(new AddPropertyMetadataMutation(timestamp, key, name, metadata, visibility));
    }

    public void appendAlterEdgeLabelMutation(long timestamp, String newEdgeLabel) {
        addMutation(new AlterEdgeLabelMutation(timestamp, newEdgeLabel));
    }

    protected List<Mutation> getFilteredMutations(boolean includeHidden, Long endTime, Authorizations authorizations) {
        return getFilteredMutations(m ->
            canRead(m.getVisibility(), authorizations) &&
                (endTime == null || m.getTimestamp() <= endTime) &&
                (includeHidden || !(m instanceof MarkHiddenMutation || m instanceof MarkPropertyHiddenMutation))
        );
    }

    public boolean canRead(FetchHints fetchHints, Authorizations authorizations) {
        if (!fetchHints.isIgnoreAdditionalVisibilities()) {
            Visibility additionalVisibility = getAdditionalVisibilitiesAsVisibility();
            if (additionalVisibility != null) {
                if (!authorizations.canRead(additionalVisibility)) {
                    return false;
                }
            }
        }

        // this is just a shortcut so that we don't need to construct evaluators and visibility objects to check for an empty string.
        //noinspection SimplifiableIfStatement
        if (getVisibility().getVisibilityString().length() == 0) {
            return true;
        }

        return authorizations.canRead(getVisibility());
    }

    private static boolean canRead(Visibility visibility, Authorizations authorizations) {
        // this is just a shortcut so that we don't need to construct evaluators and visibility objects to check for an empty string.
        //noinspection SimplifiableIfStatement
        if (visibility.getVisibilityString().length() == 0) {
            return true;
        }
        return authorizations.canRead(visibility);
    }

    private Visibility getAdditionalVisibilitiesAsVisibility() {
        Set<String> additionalVisibilities = getAdditionalVisibilities();
        if (additionalVisibilities.size() == 0) {
            return null;
        }
        String visibilityString = Joiner.on("&").join(
            additionalVisibilities.stream()
                .map(av -> String.format("(%s)", av))
                .collect(Collectors.toSet())
        );
        return new Visibility(visibilityString);
    }

    public ImmutableSet<String> getAdditionalVisibilities() {
        Set<String> results = new HashSet<>();

        mutationLock.readLock().lock();
        try {
            for (Mutation m : this.mutations) {
                if (m instanceof AddAdditionalVisibilityMutation) {
                    results.add(((AddAdditionalVisibilityMutation) m).getAdditionalVisibility());
                } else if (m instanceof DeleteAdditionalVisibilityMutation) {
                    results.remove(((DeleteAdditionalVisibilityMutation) m).getAdditionalVisibility());
                }
            }
        } finally {
            mutationLock.readLock().unlock();
        }
        return ImmutableSet.copyOf(results);
    }

    public Set<Visibility> getHiddenVisibilities() {
        Set<Visibility> results = new HashSet<>();

        mutationLock.readLock().lock();
        try {
            for (Mutation m : this.mutations) {
                if (m instanceof MarkHiddenMutation) {
                    results.add(m.getVisibility());
                } else if (m instanceof MarkVisibleMutation) {
                    results.remove(m.getVisibility());
                }
            }
        } finally {
            mutationLock.readLock().unlock();
        }
        return results;
    }

    public boolean isHidden(Authorizations authorizations) {
        for (Visibility visibility : getHiddenVisibilities()) {
            if (authorizations.canRead(visibility)) {
                return true;
            }
        }
        return false;
    }

    public TElement createElement(InMemoryGraph graph, FetchHints fetchHints, Authorizations authorizations) {
        return createElement(graph, fetchHints, null, authorizations);
    }

    public final TElement createElement(InMemoryGraph graph, FetchHints fetchHints, Long endTime, Authorizations authorizations) {
        if (endTime != null && getFirstTimestamp() > endTime) {
            return null;
        }
        if (isDeleted(endTime, authorizations)) {
            return null;
        }
        return createElementInternal(graph, fetchHints, endTime, authorizations);
    }

    public boolean isDeleted(Long endTime, Authorizations authorizations) {
        List<Mutation> filteredMutations = getFilteredMutations(m ->
            canRead(m.getVisibility(), authorizations) &&
                (endTime == null || m.getTimestamp() <= endTime) &&
                (m instanceof SoftDeleteMutation || m instanceof ElementTimestampMutation)
        );
        return filteredMutations.isEmpty() || filteredMutations.get(filteredMutations.size() - 1) instanceof SoftDeleteMutation;
    }

    protected abstract TElement createElementInternal(InMemoryGraph graph, FetchHints fetchHints, Long endTime, Authorizations authorizations);

    private List<Mutation> getFilteredMutations(Predicate<Mutation> filter) {
        mutationLock.readLock().lock();
        try {
            return this.mutations.stream()
                .filter(filter)
                .collect(Collectors.toList());
        } finally {
            mutationLock.readLock().unlock();
        }
    }

    private void addMutation(Mutation mutation) {
        mutationLock.writeLock().lock();
        try {
            this.mutations.add(mutation);
        } finally {
            mutationLock.writeLock().unlock();
        }
    }
}
