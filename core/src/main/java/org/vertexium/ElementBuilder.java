package org.vertexium;

import com.google.common.collect.ImmutableSet;
import org.vertexium.mutation.*;
import org.vertexium.property.MutablePropertyImpl;
import org.vertexium.search.IndexHint;
import org.vertexium.util.KeyUtils;
import org.vertexium.util.Preconditions;
import org.vertexium.util.StreamUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class ElementBuilder<T extends Element> implements ElementMutation<T> {
    private final List<Property> properties = new ArrayList<>();
    private final List<PropertyDeleteMutation> propertyDeletes = new ArrayList<>();
    private final List<PropertySoftDeleteMutation> propertySoftDeletes = new ArrayList<>();
    private final List<ExtendedDataMutation> extendedDatas = new ArrayList<>();
    private final List<ExtendedDataDeleteMutation> extendedDataDeletes = new ArrayList<>();
    private final List<AdditionalVisibilityAddMutation> additionalVisibilities = new ArrayList<>();
    private final List<AdditionalVisibilityDeleteMutation> additionalVisibilityDeletes = new ArrayList<>();
    private final List<AdditionalExtendedDataVisibilityAddMutation> additionalExtendedDataVisibilities = new ArrayList<>();
    private final List<AdditionalExtendedDataVisibilityDeleteMutation> additionalExtendedDataVisibilityDeletes = new ArrayList<>();
    private final ElementType elementType;
    private final String elementId;
    private final Visibility elementVisibility;
    private IndexHint indexHint = IndexHint.INDEX;

    protected ElementBuilder(ElementType elementType, String elementId, Visibility elementVisibility) {
        KeyUtils.checkKey(elementId, "Invalid elementId");
        this.elementType = elementType;
        this.elementId = elementId;
        this.elementVisibility = elementVisibility;
    }

    @Override
    public ElementType getElementType() {
        return elementType;
    }

    @Override
    public Visibility getVisibility() {
        return elementVisibility;
    }

    @Override
    public String getId() {
        return elementId;
    }

    /**
     * @deprecated use {@link #getId()}
     */
    @Deprecated
    public String getElementId() {
        return elementId;
    }

    /**
     * Sets or updates a property value. The property key will be set to a constant. This is a convenience method
     * which allows treating the multi-valued nature of properties as only containing a single value. Care must be
     * taken when using this method because properties are not only uniquely identified by just key and name but also
     * visibility so adding properties with the same name and different visibility strings is still permitted.
     * <p>
     * The added property will also be indexed in the configured search provider. The type of the value
     * will determine how it gets indexed.
     *
     * @param name       The name of the property.
     * @param value      The value of the property.
     * @param visibility The visibility to give this property.
     */
    public ElementBuilder<T> setProperty(String name, Object value, Visibility visibility) {
        return setProperty(name, value, Metadata.create(FetchHints.ALL), visibility);
    }

    /**
     * Sets or updates a property value. The property key will be set to a constant. This is a convenience method
     * which allows treating the multi-valued nature of properties as only containing a single value. Care must be
     * taken when using this method because properties are not only uniquely identified by just key and name but also
     * visibility so adding properties with the same name and different visibility strings is still permitted.
     * <p>
     * The added property will also be indexed in the configured search provider. The type of the value
     * will determine how it gets indexed.
     *
     * @param name       The name of the property.
     * @param value      The value of the property.
     * @param metadata   The metadata to assign to this property.
     * @param visibility The visibility to give this property.
     */
    public ElementBuilder<T> setProperty(String name, Object value, Metadata metadata, Visibility visibility) {
        return addPropertyValue(ElementMutation.DEFAULT_KEY, name, value, metadata, visibility);
    }

    /**
     * Adds or updates a property.
     * <p>
     * The added property will also be indexed in the configured search provider. The type of the value
     * will determine how it gets indexed.
     *
     * @param key        The unique key given to the property allowing for multi-valued properties.
     * @param name       The name of the property.
     * @param value      The value of the property.
     * @param visibility The visibility to give this property.
     */
    public ElementBuilder<T> addPropertyValue(String key, String name, Object value, Visibility visibility) {
        return addPropertyValue(key, name, value, Metadata.create(FetchHints.ALL), visibility);
    }

    /**
     * Adds or updates a property.
     * <p>
     * The added property will also be indexed in the configured search provider. The type of the value
     * will determine how it gets indexed.
     *
     * @param key        The unique key given to the property allowing for multi-valued properties.
     * @param name       The name of the property.
     * @param value      The value of the property.
     * @param metadata   The metadata to assign to this property.
     * @param visibility The visibility to give this property.
     */
    public ElementBuilder<T> addPropertyValue(String key, String name, Object value, Metadata metadata, Visibility visibility) {
        return addPropertyValue(key, name, value, metadata, null, visibility);
    }

    @Override
    public ElementBuilder<T> addPropertyValue(String key, String name, Object value, Metadata metadata, Long timestamp, Visibility visibility) {
        if (name == null) {
            throw new NullPointerException("property name cannot be null for property: " + name + ":" + key);
        }
        if (value == null) {
            throw new NullPointerException("property value cannot be null for property: " + name + ":" + key);
        }
        this.properties.add(new MutablePropertyImpl(
            key,
            name,
            value,
            metadata,
            timestamp,
            null,
            visibility,
            FetchHints.ALL_INCLUDING_HIDDEN
        ));
        return this;
    }

    @Override
    public ElementBuilder<T> deleteProperty(Property property) {
        propertyDeletes.add(new PropertyPropertyDeleteMutation(property));
        return this;
    }

    @Override
    public ElementBuilder<T> deleteProperty(String name, Visibility visibility) {
        return deleteProperty(ElementMutation.DEFAULT_KEY, name, visibility);
    }

    @Override
    public ElementBuilder<T> deleteProperty(String key, String name, Visibility visibility) {
        Preconditions.checkNotNull(name, "property name cannot be null for property: " + name + ":" + key);
        propertyDeletes.add(new KeyNameVisibilityPropertyDeleteMutation(key, name, visibility));
        return this;
    }

    @Override
    public ElementBuilder<T> softDeleteProperty(Property property, Object eventData) {
        propertySoftDeletes.add(new PropertyPropertySoftDeleteMutation(property, eventData));
        return this;
    }

    @Override
    public ElementBuilder<T> softDeleteProperty(String name, Visibility visibility, Object eventData) {
        return softDeleteProperty(ElementMutation.DEFAULT_KEY, name, visibility, eventData);
    }

    @Override
    public ElementBuilder<T> softDeleteProperty(String key, String name, Visibility visibility, Object eventData) {
        Preconditions.checkNotNull(name, "property name cannot be null for property: " + name + ":" + key);
        propertySoftDeletes.add(new KeyNameVisibilityPropertySoftDeleteMutation(key, name, visibility, eventData));
        return this;
    }

    @Override
    public ElementBuilder<T> addExtendedData(String tableName, String row, String column, Object value, Visibility visibility) {
        return addExtendedData(tableName, row, column, null, value, visibility);
    }

    @Override
    public ElementMutation<T> addExtendedData(String tableName, String row, String column, Object value, Long timestamp, Visibility visibility) {
        return addExtendedData(tableName, row, column, null, value, timestamp, visibility);
    }

    @Override
    public ElementBuilder<T> addExtendedData(String tableName, String row, String column, String key, Object value, Visibility visibility) {
        return addExtendedData(tableName, row, column, key, value, null, visibility);
    }

    @Override
    public ElementBuilder<T> addExtendedData(String tableName, String row, String column, String key, Object value, Long timestamp, Visibility visibility) {
        this.extendedDatas.add(new ExtendedDataMutation(tableName, row, column, key, value, timestamp, visibility));
        return this;
    }

    @Override
    public ElementBuilder<T> deleteExtendedData(String tableName, String row, String column, Visibility visibility) {
        return deleteExtendedData(tableName, row, column, null, visibility);
    }

    @Override
    public ElementBuilder<T> deleteExtendedData(String tableName, String row, String column, String key, Visibility visibility) {
        extendedDataDeletes.add(new ExtendedDataDeleteMutation(tableName, row, column, key, visibility));
        return this;
    }

    @Override
    public ElementMutation<T> addAdditionalVisibility(String visibility, Object eventData) {
        additionalVisibilities.add(new AdditionalVisibilityAddMutation(visibility, eventData));
        return this;
    }

    @Override
    public ElementMutation<T> deleteAdditionalVisibility(String visibility, Object eventData) {
        additionalVisibilityDeletes.add(new AdditionalVisibilityDeleteMutation(visibility, eventData));
        return this;
    }

    @Override
    public ElementMutation<T> addExtendedDataAdditionalVisibility(
        String tableName,
        String row,
        String additionalVisibility,
        Object eventData
    ) {
        additionalExtendedDataVisibilities.add(new AdditionalExtendedDataVisibilityAddMutation(
            tableName,
            row,
            additionalVisibility,
            eventData
        ));
        return this;
    }

    @Override
    public ElementMutation<T> deleteExtendedDataAdditionalVisibility(
        String tableName,
        String row,
        String additionalVisibility,
        Object eventData
    ) {
        additionalExtendedDataVisibilityDeletes.add(new AdditionalExtendedDataVisibilityDeleteMutation(
            tableName,
            row,
            additionalVisibility,
            eventData
        ));
        return this;
    }

    /**
     * saves the element to the graph.
     *
     * @return either the vertex or edge just saved.
     */
    public abstract T save(Authorizations authorizations);

    @Override
    public Iterable<Property> getProperties() {
        return properties;
    }

    @Override
    public Iterable<PropertyDeleteMutation> getPropertyDeletes() {
        return propertyDeletes;
    }

    @Override
    public Iterable<PropertySoftDeleteMutation> getPropertySoftDeletes() {
        return propertySoftDeletes;
    }

    @Override
    public Iterable<ExtendedDataMutation> getExtendedData() {
        return extendedDatas;
    }

    @Override
    public Iterable<ExtendedDataDeleteMutation> getExtendedDataDeletes() {
        return extendedDataDeletes;
    }

    @Override
    public List<AdditionalVisibilityAddMutation> getAdditionalVisibilities() {
        return additionalVisibilities;
    }

    @Override
    public List<AdditionalVisibilityDeleteMutation> getAdditionalVisibilityDeletes() {
        return additionalVisibilityDeletes;
    }

    @Override
    public List<AdditionalExtendedDataVisibilityAddMutation> getAdditionalExtendedDataVisibilities() {
        return additionalExtendedDataVisibilities;
    }

    @Override
    public List<AdditionalExtendedDataVisibilityDeleteMutation> getAdditionalExtendedDataVisibilityDeletes() {
        return additionalExtendedDataVisibilityDeletes;
    }

    public Set<String> getAdditionalVisibilitiesAsStringSet() {
        Set<String> results = getAdditionalVisibilities().stream()
            .map(AdditionalVisibilityAddMutation::getAdditionalVisibility)
            .collect(Collectors.toSet());
        results.removeAll(
            getAdditionalVisibilityDeletes().stream()
                .map(AdditionalVisibilityDeleteMutation::getAdditionalVisibility)
                .collect(Collectors.toSet())
        );
        return results;
    }

    @Override
    public IndexHint getIndexHint() {
        return indexHint;
    }

    public ImmutableSet<String> getExtendedDataTableNames() {
        return extendedDatas.stream()
            .map(ExtendedDataMutation::getTableName)
            .collect(StreamUtils.toImmutableSet());
    }

    @Override
    public ElementMutation<T> setIndexHint(IndexHint indexHint) {
        this.indexHint = indexHint;
        return this;
    }

    @Override
    public boolean hasChanges() {
        if (properties.size() > 0) {
            return true;
        }

        if (propertyDeletes.size() > 0) {
            return true;
        }

        if (propertySoftDeletes.size() > 0) {
            return true;
        }

        if (extendedDatas.size() > 0) {
            return true;
        }

        if (extendedDataDeletes.size() > 0) {
            return true;
        }

        return false;
    }
}
