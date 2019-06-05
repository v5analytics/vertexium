package org.vertexium.mutation;

import org.vertexium.*;
import org.vertexium.search.IndexHint;

public interface ElementMutation<T extends Element> extends ElementLocation {
    String DEFAULT_KEY = "";

    /**
     * saves the properties to the graph.
     *
     * @return the element which was mutated.
     */
    T save(Authorizations authorizations);

    /**
     * Sets or updates a property value. The property key will be set to a constant. This is a convenience method
     * which allows treating the multi-valued nature of properties as only containing a single value. Care must be
     * taken when using this method because properties are not only uniquely identified by just key and name but also
     * visibility so adding properties with the same name and different visibility strings is still permitted.
     *
     * @param name       The name of the property.
     * @param value      The value of the property.
     * @param visibility The visibility to give this property.
     */
    ElementMutation<T> setProperty(String name, Object value, Visibility visibility);

    /**
     * Sets or updates a property value. The property key will be set to a constant. This is a convenience method
     * which allows treating the multi-valued nature of properties as only containing a single value. Care must be
     * taken when using this method because properties are not only uniquely identified by just key and name but also
     * visibility so adding properties with the same name and different visibility strings is still permitted.
     *
     * @param name       The name of the property.
     * @param value      The value of the property.
     * @param metadata   The metadata to assign to this property.
     * @param visibility The visibility to give this property.
     */
    ElementMutation<T> setProperty(String name, Object value, Metadata metadata, Visibility visibility);

    /**
     * Adds or updates a property.
     *
     * @param key        The unique key given to the property allowing for multi-valued properties.
     * @param name       The name of the property.
     * @param value      The value of the property.
     * @param visibility The visibility to give this property.
     */
    ElementMutation<T> addPropertyValue(String key, String name, Object value, Visibility visibility);

    /**
     * Adds or updates a property.
     *
     * @param key        The unique key given to the property allowing for multi-valued properties.
     * @param name       The name of the property.
     * @param value      The value of the property.
     * @param metadata   The metadata to assign to this property.
     * @param visibility The visibility to give this property.
     */
    ElementMutation<T> addPropertyValue(String key, String name, Object value, Metadata metadata, Visibility visibility);

    /**
     * Adds or updates a property.
     *
     * @param key        The unique key given to the property allowing for multi-valued properties.
     * @param name       The name of the property.
     * @param value      The value of the property.
     * @param metadata   The metadata to assign to this property.
     * @param timestamp  The timestamp of the property.
     * @param visibility The visibility to give this property.
     */
    ElementMutation<T> addPropertyValue(String key, String name, Object value, Metadata metadata, Long timestamp, Visibility visibility);

    /**
     * Deletes a property.
     *
     * @param property the property to delete.
     */
    ElementMutation<T> deleteProperty(Property property);

    /**
     * Soft deletes a property.
     *
     * @param property the property to soft delete.
     */
    default ElementMutation<T> softDeleteProperty(Property property) {
        return softDeleteProperty(property, null);
    }

    /**
     * Soft deletes a property.
     *
     * @param property  the property to soft delete.
     * @param eventData Data to store with the soft delete
     */
    ElementMutation<T> softDeleteProperty(Property property, Object eventData);

    /**
     * Deletes the default property with that name.
     *
     * @param name       the property name to delete.
     * @param visibility the visibility of the property to delete.
     */
    ElementMutation<T> deleteProperty(String name, Visibility visibility);

    /**
     * Soft deletes the default property with that name.
     *
     * @param name       the property name to soft delete.
     * @param visibility the visibility of the property to soft delete.
     */
    default ElementMutation<T> softDeleteProperty(String name, Visibility visibility) {
        return softDeleteProperty(name, visibility, null);
    }

    /**
     * Soft deletes the default property with that name.
     *
     * @param name       the property name to soft delete.
     * @param visibility the visibility of the property to soft delete.
     * @param eventData  Data to store with the soft delete
     */
    ElementMutation<T> softDeleteProperty(String name, Visibility visibility, Object eventData);

    /**
     * Deletes a property.
     *
     * @param key        the key of the property to delete.
     * @param name       the name of the property to delete.
     * @param visibility the visibility of the property to delete.
     */
    ElementMutation<T> deleteProperty(String key, String name, Visibility visibility);

    /**
     * Soft deletes a property.
     *
     * @param key        the key of the property to soft delete.
     * @param name       the name of the property to soft delete.
     * @param visibility the visibility of the property to soft delete.
     */
    default ElementMutation<T> softDeleteProperty(String key, String name, Visibility visibility) {
        return softDeleteProperty(key, name, visibility, null);
    }

    /**
     * Soft deletes a property.
     *
     * @param key        the key of the property to soft delete.
     * @param name       the name of the property to soft delete.
     * @param visibility the visibility of the property to soft delete.
     * @param eventData  Data to store with the soft delete
     */
    ElementMutation<T> softDeleteProperty(String key, String name, Visibility visibility, Object eventData);

    /**
     * Adds additional visibilities to the element. These visibilities will not effect the Visibility returned from
     * {@link Element#getVisibility()}. These visibilities can also be bypassed by {@link FetchHints}.
     *
     * @param visibility The additional visibility to be applied
     */
    default ElementMutation<T> addAdditionalVisibility(String visibility) {
        return addAdditionalVisibility(visibility, null);
    }

    /**
     * Adds additional visibilities to the element. These visibilities will not effect the Visibility returned from
     * {@link Element#getVisibility()}. These visibilities can also be bypassed by {@link FetchHints}.
     *
     * @param visibility The additional visibility to be applied
     * @param eventData  Data to store with the mutation
     */
    ElementMutation<T> addAdditionalVisibility(String visibility, Object eventData);

    /**
     * Deletes a previously set additional visibility.
     *
     * @param visibility The additional visibility to be deleted
     */
    default ElementMutation<T> deleteAdditionalVisibility(String visibility) {
        return deleteAdditionalVisibility(visibility, null);
    }

    /**
     * Deletes a previously set additional visibility.
     *
     * @param visibility The additional visibility to be deleted
     * @param eventData  Data to store with the mutation
     */
    ElementMutation<T> deleteAdditionalVisibility(String visibility, Object eventData);

    /**
     * Adds additional visibilities to the row column. These visibilities will not effect the Visibility returned from
     * {@link Element#getVisibility()}. These visibilities can also be bypassed by {@link FetchHints}.
     *
     * @param tableName            Name of the table the row resides in
     * @param row                  The row
     * @param additionalVisibility The additional visibility to be applied
     */
    default ElementMutation<T> addExtendedDataAdditionalVisibility(
        String tableName,
        String row,
        String additionalVisibility
    ) {
        return addExtendedDataAdditionalVisibility(tableName, row, additionalVisibility, null);
    }

    /**
     * Adds additional visibilities to the row column. These visibilities will not effect the Visibility returned from
     * {@link Element#getVisibility()}. These visibilities can also be bypassed by {@link FetchHints}.
     *
     * @param tableName            Name of the table the row resides in
     * @param row                  The row
     * @param additionalVisibility The additional visibility to be applied
     * @param eventData            Data to store with the mutation
     */
    ElementMutation<T> addExtendedDataAdditionalVisibility(
        String tableName,
        String row,
        String additionalVisibility,
        Object eventData
    );

    /**
     * Deletes a previously set additional visibility.
     *
     * @param tableName            Table name of the row to delete the additional visibility on.
     * @param row                  Row to delete the additional visibility on.
     * @param additionalVisibility The additional visibility to be deleted
     */
    default ElementMutation<T> deleteExtendedDataAdditionalVisibility(
        String tableName,
        String row,
        String additionalVisibility
    ) {
        return deleteExtendedDataAdditionalVisibility(tableName, row, additionalVisibility, null);
    }

    /**
     * Deletes a previously set additional visibility.
     *
     * @param tableName            Table name of the row to delete the additional visibility on.
     * @param row                  Row to delete the additional visibility on.
     * @param additionalVisibility The additional visibility to be deleted
     * @param eventData            Data to store with the delete
     */
    ElementMutation<T> deleteExtendedDataAdditionalVisibility(
        String tableName,
        String row,
        String additionalVisibility,
        Object eventData
    );

    /**
     * Gets the properties currently in this mutation.
     */
    Iterable<Property> getProperties();

    /**
     * Gets the properties currently being deleted in this mutation.
     */
    Iterable<PropertyDeleteMutation> getPropertyDeletes();

    /**
     * Gets the properties currently being soft deleted in this mutation.
     */
    Iterable<PropertySoftDeleteMutation> getPropertySoftDeletes();

    /**
     * Gets the extended data mutations.
     */
    Iterable<ExtendedDataMutation> getExtendedData();

    /**
     * Gets the extended data columns currently being deleted in this mutation
     */
    Iterable<ExtendedDataDeleteMutation> getExtendedDataDeletes();

    /**
     * Gets the additional visibility mutations
     */
    Iterable<AdditionalVisibilityAddMutation> getAdditionalVisibilities();

    /**
     * Gets the additional visibility delete mutations
     */
    Iterable<AdditionalVisibilityDeleteMutation> getAdditionalVisibilityDeletes();

    /**
     * Gets the additional extended data visibility mutations
     */
    Iterable<AdditionalExtendedDataVisibilityAddMutation> getAdditionalExtendedDataVisibilities();

    /**
     * Gets the additional extended data visibility delete mutations
     */
    Iterable<AdditionalExtendedDataVisibilityDeleteMutation> getAdditionalExtendedDataVisibilityDeletes();

    /**
     * Sets the index hint of this element.
     */
    ElementMutation<T> setIndexHint(IndexHint indexHint);

    /**
     * Gets the currently set index hint.
     */
    IndexHint getIndexHint();

    /**
     * true, if this mutation has any changes. false, if this mutation is empty.
     */
    boolean hasChanges();

    /**
     * Adds an extended data cell to the element.
     *
     * @param tableName  The extended data table to add the cell to.
     * @param row        The row to add the cell to.
     * @param column     The column name.
     * @param value      The cell value.
     * @param visibility The visibility of the value.
     */
    ElementMutation<T> addExtendedData(String tableName, String row, String column, Object value, Visibility visibility);

    /**
     * Adds an extended data cell to the element.
     *
     * @param tableName  The extended data table to add the cell to.
     * @param row        The row to add the cell to.
     * @param column     The column name.
     * @param value      The cell value.
     * @param timestamp  The timestamp of the value. null, to automatically generate one.
     * @param visibility The visibility of the value.
     */
    ElementMutation<T> addExtendedData(String tableName, String row, String column, Object value, Long timestamp, Visibility visibility);

    /**
     * Adds an extended data cell to the element.
     *
     * @param tableName  The extended data table to add the cell to.
     * @param row        The row to add the cell to.
     * @param column     The column name.
     * @param key        The column multi-value key.
     * @param value      The cell value.
     * @param visibility The visibility of the value.
     */
    ElementMutation<T> addExtendedData(String tableName, String row, String column, String key, Object value, Visibility visibility);

    /**
     * Adds an extended data cell to the element.
     *
     * @param tableName  The extended data table to add the cell to.
     * @param row        The row to add the cell to.
     * @param column     The column name.
     * @param key        The column multi-value key.
     * @param value      The cell value.
     * @param timestamp  The timestamp of the value. null, to automatically generate one.
     * @param visibility The visibility of the value.
     */
    ElementMutation<T> addExtendedData(String tableName, String row, String column, String key, Object value, Long timestamp, Visibility visibility);

    /**
     * Deletes an extended data cell from an element.
     *
     * @param tableName  The extended data table to add the cell to.
     * @param row        The row to add the cell to.
     * @param column     The column name.
     * @param visibility The visibility of the value.
     */
    ElementMutation<T> deleteExtendedData(String tableName, String row, String column, Visibility visibility);

    /**
     * Deletes an extended data cell from an element.
     *
     * @param tableName  The extended data table to add the cell to.
     * @param row        The row to add the cell to.
     * @param column     The column name.
     * @param key        The column multi-value key.
     * @param visibility The visibility of the value.
     */
    ElementMutation<T> deleteExtendedData(String tableName, String row, String column, String key, Visibility visibility);
}
