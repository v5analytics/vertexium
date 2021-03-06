package org.vertexium.elasticsearch5;

import org.vertexium.*;
import org.vertexium.elasticsearch5.bulk.BulkItem;
import org.vertexium.elasticsearch5.bulk.BulkUpdateItem;
import org.vertexium.util.VertexiumLogger;
import org.vertexium.util.VertexiumLoggerFactory;

import java.util.Collections;

public class LoadAndAddDocumentMissingHelper implements Elasticsearch5ExceptionHandler {
    private static final VertexiumLogger LOGGER = VertexiumLoggerFactory.getLogger(LoadAndAddDocumentMissingHelper.class);

    public static void handleDocumentMissingException(
        Graph graph,
        Elasticsearch5SearchIndex searchIndex,
        BulkItem bulkItem,
        Authorizations authorizations
    ) {
        LOGGER.info("handleDocumentMissingException (bulkItem: %s)", bulkItem);
        if (bulkItem instanceof BulkUpdateItem) {
            BulkUpdateItem updateBulkItem = (BulkUpdateItem) bulkItem;
            VertexiumObjectId vertexiumObjectId = updateBulkItem.getVertexiumObjectId();
            if (vertexiumObjectId instanceof ExtendedDataRowId) {
                handleExtendedDataRow(graph, searchIndex, updateBulkItem, authorizations);
            } else if (vertexiumObjectId instanceof ElementId) {
                handleElement(graph, searchIndex, updateBulkItem, authorizations);
            } else {
                throw new VertexiumException("unhandled VertexiumObjectId: " + vertexiumObjectId.getClass().getName());
            }
        } else {
            throw new VertexiumException("unhandled bulk item type: " + bulkItem.getClass().getName());
        }
    }

    protected static void handleElement(
        Graph graph,
        Elasticsearch5SearchIndex searchIndex,
        BulkUpdateItem bulkItem,
        Authorizations authorizations
    ) {
        ElementId elementId = (ElementId) bulkItem.getVertexiumObjectId();
        Element element;
        switch (elementId.getElementType()) {
            case VERTEX:
                element = graph.getVertex(elementId.getId(), authorizations);
                break;
            case EDGE:
                element = graph.getEdge(elementId.getId(), authorizations);
                break;
            default:
                throw new VertexiumException("Invalid element type: " + elementId.getElementType());
        }
        if (element == null) {
            throw new VertexiumException("Could not find element: " + elementId.getId());
        }
        searchIndex.addElement(
            graph,
            element,
            element.getAdditionalVisibilities(),
            null
        );
    }

    protected static void handleExtendedDataRow(
        Graph graph,
        Elasticsearch5SearchIndex searchIndex,
        BulkUpdateItem bulkItem,
        Authorizations authorizations
    ) {
        ExtendedDataRowId extendedDataRowId = (ExtendedDataRowId) bulkItem.getVertexiumObjectId();
        ExtendedDataRowId id = new ExtendedDataRowId(
            extendedDataRowId.getElementType(),
            extendedDataRowId.getElementId(),
            extendedDataRowId.getTableName(),
            extendedDataRowId.getRowId()
        );
        ExtendedDataRow row = graph.getExtendedData(id, authorizations);
        searchIndex.addExtendedData(
            graph,
            bulkItem.getSourceElementLocation(),
            Collections.singletonList(row),
            authorizations
        );
    }
}
