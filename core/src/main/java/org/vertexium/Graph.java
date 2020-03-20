package org.vertexium;

import com.google.common.collect.Lists;
import org.vertexium.event.GraphEventListener;
import org.vertexium.historicalEvent.HistoricalEvent;
import org.vertexium.historicalEvent.HistoricalEventId;
import org.vertexium.id.IdGenerator;
import org.vertexium.metric.VertexiumMetricRegistry;
import org.vertexium.mutation.ElementMutation;
import org.vertexium.property.StreamingPropertyValue;
import org.vertexium.query.Aggregation;
import org.vertexium.query.GraphQuery;
import org.vertexium.query.MultiVertexQuery;
import org.vertexium.query.SimilarToGraphQuery;
import org.vertexium.util.JoinIterable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Stream;

import static org.vertexium.util.Preconditions.checkNotNull;

public interface Graph {
    /**
     * Adds a vertex to the graph. The id of the new vertex will be generated using an IdGenerator.
     *
     * @param visibility     The visibility to assign to the new vertex.
     * @param authorizations The authorizations required to add and retrieve the new vertex.
     * @return The newly added vertex.
     * @deprecated Use {@link #prepareVertex(Visibility)}
     */
    @Deprecated
    default Vertex addVertex(Visibility visibility, Authorizations authorizations) {
        return prepareVertex(visibility).save(authorizations);
    }

    /**
     * Adds a vertex to the graph.
     *
     * @param vertexId       The id to assign the new vertex.
     * @param visibility     The visibility to assign to the new vertex.
     * @param authorizations The authorizations required to add and retrieve the new vertex.
     * @return The newly added vertex.
     * * @deprecated Use {@link #prepareVertex(String, Visibility)}
     */
    @Deprecated
    default Vertex addVertex(String vertexId, Visibility visibility, Authorizations authorizations) {
        return prepareVertex(vertexId, visibility).save(authorizations);
    }

    /**
     * Adds the vertices to the graph.
     *
     * @param vertices       The vertices to add.
     * @param authorizations The authorizations required to add and retrieve the new vertex.
     * @return The vertices.
     */
    Iterable<Vertex> addVertices(Iterable<ElementBuilder<Vertex>> vertices, Authorizations authorizations);

    /**
     * Prepare a vertex to be added to the graph. This method provides a way to build up a vertex with it's properties to be inserted
     * with a single operation. The id of the new vertex will be generated using an IdGenerator.
     *
     * @param visibility The visibility to assign to the new vertex.
     * @return The vertex builder.
     */
    VertexBuilder prepareVertex(Visibility visibility);

    /**
     * Prepare a vertex to be added to the graph. This method provides a way to build up a vertex with it's properties to be inserted
     * with a single operation. The id of the new vertex will be generated using an IdGenerator.
     *
     * @param timestamp  The timestamp of the vertex. null, to use the system generated time.
     * @param visibility The visibility to assign to the new vertex.
     * @return The vertex builder.
     */
    VertexBuilder prepareVertex(Long timestamp, Visibility visibility);

    /**
     * Prepare a vertex to be added to the graph. This method provides a way to build up a vertex with it's properties to be inserted
     * with a single operation.
     *
     * @param vertexId   The id to assign the new vertex.
     * @param visibility The visibility to assign to the new vertex.
     * @return The vertex builder.
     */
    VertexBuilder prepareVertex(String vertexId, Visibility visibility);

    /**
     * Prepare a vertex to be added to the graph. This method provides a way to build up a vertex with it's properties to be inserted
     * with a single operation.
     *
     * @param vertexId   The id to assign the new vertex.
     * @param timestamp  The timestamp of the vertex.
     * @param visibility The visibility to assign to the new vertex.
     * @return The vertex builder.
     */
    VertexBuilder prepareVertex(String vertexId, Long timestamp, Visibility visibility);

    /**
     * Tests the existence of a vertex with the given authorizations.
     *
     * @param vertexId       The vertex id to check existence of.
     * @param authorizations The authorizations required to load the vertex.
     * @return True if vertex exists.
     */
    boolean doesVertexExist(String vertexId, Authorizations authorizations);

    /**
     * Get an element from the graph.
     *
     * @param elementId      The element id to retrieve from the graph.
     * @param authorizations The authorizations required to load the element.
     * @return The element if successful. null if the element is not found or the required authorizations were not provided.
     */
    Element getElement(ElementId elementId, Authorizations authorizations);

    /**
     * Get an element from the graph.
     *
     * @param elementId      The element id to retrieve from the graph.
     * @param fetchHints     Hint at what parts of the element to fetch.
     * @param authorizations The authorizations required to load the element.
     * @return The vertex if successful. null if the element is not found or the required authorizations were not provided.
     */
    default Element getElement(ElementId elementId, FetchHints fetchHints, Authorizations authorizations) {
        if (elementId instanceof Element) {
            Element element = (Element) elementId;
            if (element.getFetchHints().hasFetchHints(fetchHints)) {
                return element;
            }
        }
        switch (elementId.getElementType()) {
            case VERTEX:
                return getVertex(elementId.getId(), fetchHints, authorizations);
            case EDGE:
                return getEdge(elementId.getId(), fetchHints, authorizations);
            default:
                throw new VertexiumException("Unhandled element type: " + elementId.getElementType());
        }
    }

    /**
     * Gets elements from the graph
     *
     * @param elementIds     The element ids to retrieve from the graph.
     * @param authorizations The authorizations required to load the elements.
     * @return The found elements, if an element is not found it will not be returned.
     */
    default Iterable<? extends Element> getElements(
        Iterable<ElementId> elementIds,
        Authorizations authorizations
    ) {
        return getElements(elementIds, FetchHints.ALL, authorizations);
    }

    /**
     * Gets elements from the graph
     *
     * @param elementIds     The element ids to retrieve from the graph.
     * @param fetchHints     Hint at what parts of the elements to fetch.
     * @param authorizations The authorizations required to load the elements.
     * @return The found elements, if an element is not found it will not be returned.
     */
    default Iterable<? extends Element> getElements(
        Iterable<ElementId> elementIds,
        FetchHints fetchHints,
        Authorizations authorizations
    ) {
        Set<String> vertexIds = new HashSet<>();
        Set<String> edgeIds = new HashSet<>();
        for (ElementId elementId : elementIds) {
            switch (elementId.getElementType()) {
                case VERTEX:
                    vertexIds.add(elementId.getId());
                    break;
                case EDGE:
                    edgeIds.add(elementId.getId());
                    break;
                default:
                    throw new VertexiumException("unhandled element type: " + elementId.getElementType());
            }
        }

        if (vertexIds.size() == 0 && edgeIds.size() == 0) {
            return Collections.emptyList();
        }

        if (vertexIds.size() == 0) {
            return getEdges(edgeIds, fetchHints, authorizations);
        }

        if (edgeIds.size() == 0) {
            return getVertices(vertexIds, fetchHints, authorizations);
        }

        return new JoinIterable<Element>(
            getVertices(vertexIds, fetchHints, authorizations),
            getEdges(edgeIds, fetchHints, authorizations)
        );
    }

    /**
     * Deletes multiple elements
     *
     * @param elementIds     The element ids to delete
     * @param authorizations The authorizations required to delete the elements
     */
    default void deleteElements(Stream<? extends ElementId> elementIds, Authorizations authorizations) {
        elementIds.forEach(elementId -> deleteElement(elementId, authorizations));
    }

    /**
     * Deletes an element
     *
     * @param elementId      The element to delete
     * @param authorizations The authorizations required to delete the element
     */
    default void deleteElement(ElementId elementId, Authorizations authorizations) {
        checkNotNull(elementId, "element is required");
        switch (elementId.getElementType()) {
            case VERTEX:
                if (elementId instanceof Vertex) {
                    deleteVertex((Vertex) elementId, authorizations);
                } else {
                    FetchHints fetchHints = new FetchHintsBuilder(FetchHints.EDGE_REFS)
                        .setIncludeExtendedDataTableNames(true)
                        .build();
                    deleteVertex((Vertex) getElement(elementId, fetchHints, authorizations), authorizations);
                }
                break;
            case EDGE:
                if (elementId instanceof Edge) {
                    deleteEdge((Edge) elementId, authorizations);
                } else {
                    FetchHints fetchHints = new FetchHintsBuilder()
                        .setIncludeExtendedDataTableNames(true)
                        .build();
                    deleteEdge(getEdge(elementId.getId(), fetchHints, authorizations), authorizations);
                }
                break;
            default:
                throw new VertexiumException("Unhandled element type: " + elementId.getElementType());
        }
    }

    /**
     * Get a vertex from the graph.
     *
     * @param vertexId       The vertex id to retrieve from the graph.
     * @param authorizations The authorizations required to load the vertex.
     * @return The vertex if successful. null if the vertex is not found or the required authorizations were not provided.
     */
    Vertex getVertex(String vertexId, Authorizations authorizations);

    /**
     * Get a vertex from the graph.
     *
     * @param vertexId       The vertex id to retrieve from the graph.
     * @param fetchHints     Hint at what parts of the vertex to fetch.
     * @param authorizations The authorizations required to load the vertex.
     * @return The vertex if successful. null if the vertex is not found or the required authorizations were not provided.
     */
    Vertex getVertex(String vertexId, FetchHints fetchHints, Authorizations authorizations);

    /**
     * Get a vertex from the graph.
     *
     * @param vertexId       The vertex id to retrieve from the graph.
     * @param fetchHints     Hint at what parts of the vertex to fetch.
     * @param endTime        Include all changes made up until the point in time.
     * @param authorizations The authorizations required to load the vertex.
     * @return The vertex if successful. null if the vertex is not found or the required authorizations were not provided.
     */
    Vertex getVertex(String vertexId, FetchHints fetchHints, Long endTime, Authorizations authorizations);

    /**
     * Gets vertices from the graph given the prefix.
     *
     * @param vertexIdPrefix The prefix of the vertex ids to retrieve from the graph.
     * @param authorizations The authorizations required to load the vertex.
     * @return The vertex if successful. null if the vertex is not found or the required authorizations were not provided.
     */
    Iterable<Vertex> getVerticesWithPrefix(String vertexIdPrefix, Authorizations authorizations);

    /**
     * Gets vertices from the graph given the prefix.
     *
     * @param vertexIdPrefix The prefix of the vertex ids to retrieve from the graph.
     * @param fetchHints     Hint at what parts of the vertex to fetch.
     * @param authorizations The authorizations required to load the vertex.
     * @return The vertex if successful. null if the vertex is not found or the required authorizations were not provided.
     */
    Iterable<Vertex> getVerticesWithPrefix(String vertexIdPrefix, FetchHints fetchHints, Authorizations authorizations);

    /**
     * Gets vertices from the graph given the prefix.
     *
     * @param vertexIdPrefix The prefix of the vertex ids to retrieve from the graph.
     * @param fetchHints     Hint at what parts of the vertex to fetch.
     * @param endTime        Include all changes made up until the point in time.
     * @param authorizations The authorizations required to load the vertex.
     * @return The vertex if successful. null if the vertex is not found or the required authorizations were not provided.
     */
    Iterable<Vertex> getVerticesWithPrefix(String vertexIdPrefix, FetchHints fetchHints, Long endTime, Authorizations authorizations);

    /**
     * Gets vertices from the graph in the given range.
     *
     * @param idRange        The range of ids to get.
     * @param authorizations The authorizations required to load the vertex.
     * @return The vertices in the range.
     */
    Iterable<Vertex> getVerticesInRange(IdRange idRange, Authorizations authorizations);

    /**
     * Gets vertices from the graph in the given range.
     *
     * @param idRange        The range of ids to get.
     * @param fetchHints     Hint at what parts of the vertex to fetch.
     * @param authorizations The authorizations required to load the vertex.
     * @return The vertices in the range.
     */
    Iterable<Vertex> getVerticesInRange(IdRange idRange, FetchHints fetchHints, Authorizations authorizations);

    /**
     * Gets vertices from the graph in the given range.
     *
     * @param idRange        The range of ids to get.
     * @param fetchHints     Hint at what parts of the vertex to fetch.
     * @param endTime        Include all changes made up until the point in time.
     * @param authorizations The authorizations required to load the vertex.
     * @return The vertices in the range.
     */
    Iterable<Vertex> getVerticesInRange(IdRange idRange, FetchHints fetchHints, Long endTime, Authorizations authorizations);

    /**
     * Gets all vertices on the graph.
     *
     * @param authorizations The authorizations required to load the vertex.
     * @return An iterable of all the vertices.
     */
    Iterable<Vertex> getVertices(Authorizations authorizations);

    /**
     * Gets all vertices on the graph.
     *
     * @param fetchHints     Hint at what parts of the vertex to fetch.
     * @param authorizations The authorizations required to load the vertex.
     * @return An iterable of all the vertices.
     */
    Iterable<Vertex> getVertices(FetchHints fetchHints, Authorizations authorizations);

    /**
     * Gets all vertices on the graph.
     *
     * @param fetchHints     Hint at what parts of the vertex to fetch.
     * @param endTime        Include all changes made up until the point in time.
     * @param authorizations The authorizations required to load the vertex.
     * @return An iterable of all the vertices.
     */
    Iterable<Vertex> getVertices(FetchHints fetchHints, Long endTime, Authorizations authorizations);

    /**
     * Tests the existence of vertices with the given authorizations.
     *
     * @param ids            The vertex ids to check existence of.
     * @param authorizations The authorizations required to load the vertices.
     * @return Map of ids to exists status.
     */
    Map<String, Boolean> doVerticesExist(Iterable<String> ids, Authorizations authorizations);

    /**
     * Gets all vertices matching the given ids on the graph. The order of
     * the returned vertices is not guaranteed {@link Graph#getVerticesInOrder(Iterable, Authorizations)}.
     * Vertices are not kept in memory during the iteration.
     *
     * @param ids            The ids of the vertices to get.
     * @param authorizations The authorizations required to load the vertex.
     * @return An iterable of all the vertices.
     */
    Iterable<Vertex> getVertices(Iterable<String> ids, Authorizations authorizations);

    /**
     * Gets all vertices matching the given ids on the graph. The order of
     * the returned vertices is not guaranteed {@link Graph#getVerticesInOrder(Iterable, Authorizations)}.
     * Vertices are not kept in memory during the iteration.
     *
     * @param ids            The ids of the vertices to get.
     * @param fetchHints     Hint at what parts of the vertex to fetch.
     * @param authorizations The authorizations required to load the vertex.
     * @return An iterable of all the vertices.
     */
    Iterable<Vertex> getVertices(Iterable<String> ids, FetchHints fetchHints, Authorizations authorizations);

    /**
     * Gets all vertices matching the given ids on the graph. The order of
     * the returned vertices is not guaranteed {@link Graph#getVerticesInOrder(Iterable, Authorizations)}.
     * Vertices are not kept in memory during the iteration.
     *
     * @param ids            The ids of the vertices to get.
     * @param fetchHints     Hint at what parts of the vertex to fetch.
     * @param endTime        Include all changes made up until the point in time.
     * @param authorizations The authorizations required to load the vertex.
     * @return An iterable of all the vertices.
     */
    Iterable<Vertex> getVertices(Iterable<String> ids, FetchHints fetchHints, Long endTime, Authorizations authorizations);

    /**
     * Gets all vertices matching the given ids on the graph. This method is similar to
     * {@link Graph#getVertices(Iterable, Authorizations)}
     * but returns the vertices in the order that you passed in the ids. This requires loading
     * all the vertices in memory to sort them.
     *
     * @param ids            The ids of the vertices to get.
     * @param authorizations The authorizations required to load the vertex.
     * @return An iterable of all the vertices.
     */
    List<Vertex> getVerticesInOrder(Iterable<String> ids, Authorizations authorizations);

    /**
     * Gets all vertices matching the given ids on the graph. This method is similar to
     * {@link Graph#getVertices(Iterable, Authorizations)}
     * but returns the vertices in the order that you passed in the ids. This requires loading
     * all the vertices in memory to sort them.
     *
     * @param ids            The ids of the vertices to get.
     * @param fetchHints     Hint at what parts of the vertex to fetch.
     * @param authorizations The authorizations required to load the vertex.
     * @return An iterable of all the vertices.
     */
    List<Vertex> getVerticesInOrder(Iterable<String> ids, FetchHints fetchHints, Authorizations authorizations);

    /**
     * Permanently deletes a vertex from the graph.
     *
     * @param vertex         The vertex to delete.
     * @param authorizations The authorizations required to delete the vertex.
     */
    void deleteVertex(Vertex vertex, Authorizations authorizations);

    /**
     * Permanently deletes a vertex from the graph.
     *
     * @param vertexId       The vertex id to delete.
     * @param authorizations The authorizations required to delete the vertex.
     */
    void deleteVertex(String vertexId, Authorizations authorizations);

    /**
     * Soft deletes a vertex from the graph.
     *
     * @param vertex         The vertex to soft delete.
     * @param authorizations The authorizations required to soft delete the vertex.
     */
    default void softDeleteVertex(Vertex vertex, Authorizations authorizations) {
        softDeleteVertex(vertex, (Object) null, authorizations);
    }

    /**
     * Soft deletes a vertex from the graph.
     *
     * @param vertex         The vertex to soft delete.
     * @param eventData      Data to store with the soft delete
     * @param authorizations The authorizations required to soft delete the vertex.
     */
    void softDeleteVertex(Vertex vertex, Object eventData, Authorizations authorizations);

    /**
     * Soft deletes a vertex from the graph.
     *
     * @param vertex         The vertex to soft delete.
     * @param eventData      Data to store with the soft delete
     * @param authorizations The authorizations required to soft delete the vertex.
     */
    void softDeleteVertex(Vertex vertex, Long timestamp, Object eventData, Authorizations authorizations);

    /**
     * Soft deletes a vertex from the graph.
     *
     * @param vertexId       The vertex id to soft delete.
     * @param authorizations The authorizations required to soft delete the vertex.
     */
    default void softDeleteVertex(String vertexId, Authorizations authorizations) {
        softDeleteVertex(vertexId, (Object) null, authorizations);
    }

    /**
     * Soft deletes a vertex from the graph.
     *
     * @param vertex         The vertex to soft delete.
     * @param authorizations The authorizations required to soft delete the vertex.
     */
    default void softDeleteVertex(Vertex vertex, Long timestamp, Authorizations authorizations) {
        softDeleteVertex(vertex, timestamp, null, authorizations);
    }

    /**
     * Soft deletes a vertex from the graph.
     *
     * @param vertexId       The vertex id to soft delete.
     * @param eventData      Data to store with the soft delete
     * @param authorizations The authorizations required to soft delete the vertex.
     */
    void softDeleteVertex(String vertexId, Object eventData, Authorizations authorizations);

    /**
     * Soft deletes a vertex from the graph.
     *
     * @param vertexId       The vertex id to soft delete.
     * @param authorizations The authorizations required to soft delete the vertex.
     */
    default void softDeleteVertex(String vertexId, Long timestamp, Authorizations authorizations) {
        softDeleteVertex(vertexId, timestamp, null, authorizations);
    }

    /**
     * Soft deletes a vertex from the graph.
     *
     * @param vertexId       The vertex id to soft delete.
     * @param eventData      Data to store with the soft delete
     * @param authorizations The authorizations required to soft delete the vertex.
     */
    void softDeleteVertex(String vertexId, Long timestamp, Object eventData, Authorizations authorizations);

    /**
     * Adds an edge between two vertices. The id of the new vertex will be generated using an IdGenerator.
     *
     * @param outVertex      The source vertex. The "out" side of the edge.
     * @param inVertex       The destination vertex. The "in" side of the edge.
     * @param label          The label to assign to the edge. eg knows, works at, etc.
     * @param visibility     The visibility to assign to the new edge.
     * @param authorizations The authorizations required to add and retrieve the new edge.
     * @return The newly created edge.
     * @deprecated Use {@link #prepareEdge(Vertex, Vertex, String, Visibility)}
     */
    @Deprecated
    default Edge addEdge(Vertex outVertex, Vertex inVertex, String label, Visibility visibility, Authorizations authorizations) {
        return prepareEdge(outVertex, inVertex, label, visibility).save(authorizations);
    }

    /**
     * Adds an edge between two vertices.
     *
     * @param edgeId         The id to assign the new edge.
     * @param outVertex      The source vertex. The "out" side of the edge.
     * @param inVertex       The destination vertex. The "in" side of the edge.
     * @param label          The label to assign to the edge. eg knows, works at, etc.
     * @param visibility     The visibility to assign to the new edge.
     * @param authorizations The authorizations required to add and retrieve the new edge.
     * @return The newly created edge.
     * @deprecated Use {@link #prepareEdge(String, Vertex, Vertex, String, Visibility)}
     */
    @Deprecated
    default Edge addEdge(String edgeId, Vertex outVertex, Vertex inVertex, String label, Visibility visibility, Authorizations authorizations) {
        return prepareEdge(edgeId, outVertex, inVertex, label, visibility).save(authorizations);
    }

    /**
     * Adds an edge between two vertices.
     *
     * @param outVertexId    The source vertex id. The "out" side of the edge.
     * @param inVertexId     The destination vertex id. The "in" side of the edge.
     * @param label          The label to assign to the edge. eg knows, works at, etc.
     * @param visibility     The visibility to assign to the new edge.
     * @param authorizations The authorizations required to add and retrieve the new edge.
     * @return The newly created edge.
     * @deprecated Use {@link #prepareEdge(String, String, String, Visibility)}
     */
    @Deprecated
    default Edge addEdge(String outVertexId, String inVertexId, String label, Visibility visibility, Authorizations authorizations) {
        return prepareEdge(outVertexId, inVertexId, label, visibility).save(authorizations);
    }

    /**
     * Adds an edge between two vertices.
     *
     * @param edgeId         The id to assign the new edge.
     * @param outVertexId    The source vertex id. The "out" side of the edge.
     * @param inVertexId     The destination vertex id. The "in" side of the edge.
     * @param label          The label to assign to the edge. eg knows, works at, etc.
     * @param visibility     The visibility to assign to the new edge.
     * @param authorizations The authorizations required to add and retrieve the new edge.
     * @return The newly created edge.
     * @deprecated Use {@link #prepareEdge(String, String, String, String, Visibility)}
     */
    @Deprecated
    default Edge addEdge(String edgeId, String outVertexId, String inVertexId, String label, Visibility visibility, Authorizations authorizations) {
        return prepareEdge(edgeId, outVertexId, inVertexId, label, visibility).save(authorizations);
    }

    /**
     * Prepare an edge to be added to the graph. This method provides a way to build up an edge with it's properties to be inserted
     * with a single operation. The id of the new edge will be generated using an IdGenerator.
     *
     * @param outVertex  The source vertex. The "out" side of the edge.
     * @param inVertex   The destination vertex. The "in" side of the edge.
     * @param label      The label to assign to the edge. eg knows, works at, etc.
     * @param visibility The visibility to assign to the new edge.
     * @return The edge builder.
     */
    EdgeBuilder prepareEdge(Vertex outVertex, Vertex inVertex, String label, Visibility visibility);

    /**
     * Prepare an edge to be added to the graph. This method provides a way to build up an edge with it's properties to be inserted
     * with a single operation.
     *
     * @param edgeId     The id to assign the new edge.
     * @param outVertex  The source vertex. The "out" side of the edge.
     * @param inVertex   The destination vertex. The "in" side of the edge.
     * @param label      The label to assign to the edge. eg knows, works at, etc.
     * @param visibility The visibility to assign to the new edge.
     * @return The edge builder.
     */
    EdgeBuilder prepareEdge(String edgeId, Vertex outVertex, Vertex inVertex, String label, Visibility visibility);

    /**
     * Prepare an edge to be added to the graph. This method provides a way to build up an edge with it's properties to be inserted
     * with a single operation.
     *
     * @param edgeId     The id to assign the new edge.
     * @param outVertex  The source vertex. The "out" side of the edge.
     * @param inVertex   The destination vertex. The "in" side of the edge.
     * @param label      The label to assign to the edge. eg knows, works at, etc.
     * @param timestamp  The timestamp of the edge.
     * @param visibility The visibility to assign to the new edge.
     * @return The edge builder.
     */
    EdgeBuilder prepareEdge(String edgeId, Vertex outVertex, Vertex inVertex, String label, Long timestamp, Visibility visibility);

    /**
     * Prepare an edge to be added to the graph. This method provides a way to build up an edge with it's properties to be inserted
     * with a single operation.
     *
     * @param outVertexId The source vertex id. The "out" side of the edge.
     * @param inVertexId  The destination vertex id. The "in" side of the edge.
     * @param label       The label to assign to the edge. eg knows, works at, etc.
     * @param visibility  The visibility to assign to the new edge.
     * @return The edge builder.
     */
    EdgeBuilderByVertexId prepareEdge(String outVertexId, String inVertexId, String label, Visibility visibility);

    /**
     * Prepare an edge to be added to the graph. This method provides a way to build up an edge with it's properties to be inserted
     * with a single operation.
     *
     * @param edgeId      The id to assign the new edge.
     * @param outVertexId The source vertex id. The "out" side of the edge.
     * @param inVertexId  The destination vertex id. The "in" side of the edge.
     * @param label       The label to assign to the edge. eg knows, works at, etc.
     * @param visibility  The visibility to assign to the new edge.
     * @return The edge builder.
     */
    EdgeBuilderByVertexId prepareEdge(String edgeId, String outVertexId, String inVertexId, String label, Visibility visibility);

    /**
     * Prepare an edge to be added to the graph. This method provides a way to build up an edge with it's properties to be inserted
     * with a single operation.
     *
     * @param edgeId      The id to assign the new edge.
     * @param outVertexId The source vertex id. The "out" side of the edge.
     * @param inVertexId  The destination vertex id. The "in" side of the edge.
     * @param label       The label to assign to the edge. eg knows, works at, etc.
     * @param timestamp   The timestamp of the edge.
     * @param visibility  The visibility to assign to the new edge.
     * @return The edge builder.
     */
    EdgeBuilderByVertexId prepareEdge(String edgeId, String outVertexId, String inVertexId, String label, Long timestamp, Visibility visibility);

    /**
     * Tests the existence of a edge with the given authorizations.
     *
     * @param edgeId         The edge id to check existence of.
     * @param authorizations The authorizations required to load the edge.
     * @return True if edge exists.
     */
    boolean doesEdgeExist(String edgeId, Authorizations authorizations);

    /**
     * Get an edge from the graph.
     *
     * @param edgeId         The edge id to retrieve from the graph.
     * @param authorizations The authorizations required to load the edge.
     * @return The edge if successful. null if the edge is not found or the required authorizations were not provided.
     */
    Edge getEdge(String edgeId, Authorizations authorizations);

    /**
     * Get an edge from the graph.
     *
     * @param edgeId         The edge id to retrieve from the graph.
     * @param fetchHints     Hint at what parts of the edge to fetch.
     * @param authorizations The authorizations required to load the edge.
     * @return The edge if successful. null if the edge is not found or the required authorizations were not provided.
     */
    Edge getEdge(String edgeId, FetchHints fetchHints, Authorizations authorizations);

    /**
     * Get an edge from the graph.
     *
     * @param edgeId         The edge id to retrieve from the graph.
     * @param fetchHints     Hint at what parts of the edge to fetch.
     * @param endTime        Include all changes made up until the point in time.
     * @param authorizations The authorizations required to load the edge.
     * @return The edge if successful. null if the edge is not found or the required authorizations were not provided.
     */
    Edge getEdge(String edgeId, FetchHints fetchHints, Long endTime, Authorizations authorizations);

    /**
     * Gets all edges on the graph.
     *
     * @param authorizations The authorizations required to load the edge.
     * @return An iterable of all the edges.
     */
    Iterable<Edge> getEdges(Authorizations authorizations);

    /**
     * Gets all edges on the graph.
     *
     * @param fetchHints     Hint at what parts of the edge to fetch.
     * @param authorizations The authorizations required to load the edge.
     * @return An iterable of all the edges.
     */
    Iterable<Edge> getEdges(FetchHints fetchHints, Authorizations authorizations);

    /**
     * Gets all edges on the graph.
     *
     * @param fetchHints     Hint at what parts of the edge to fetch.
     * @param endTime        Include all changes made up until the point in time.
     * @param authorizations The authorizations required to load the edge.
     * @return An iterable of all the edges.
     */
    Iterable<Edge> getEdges(FetchHints fetchHints, Long endTime, Authorizations authorizations);

    /**
     * Gets edges from the graph in the given range.
     *
     * @param idRange        The range of ids to get.
     * @param authorizations The authorizations required to load the vertex.
     * @return The edges in the range.
     */
    Iterable<Edge> getEdgesInRange(IdRange idRange, Authorizations authorizations);

    /**
     * Gets edges from the graph in the given range.
     *
     * @param idRange        The range of ids to get.
     * @param fetchHints     Hint at what parts of the vertex to fetch.
     * @param authorizations The authorizations required to load the vertex.
     * @return The edges in the range.
     */
    Iterable<Edge> getEdgesInRange(IdRange idRange, FetchHints fetchHints, Authorizations authorizations);

    /**
     * Gets edges from the graph in the given range.
     *
     * @param idRange        The range of ids to get.
     * @param fetchHints     Hint at what parts of the vertex to fetch.
     * @param endTime        Include all changes made up until the point in time.
     * @param authorizations The authorizations required to load the vertex.
     * @return The edges in the range.
     */
    Iterable<Edge> getEdgesInRange(IdRange idRange, FetchHints fetchHints, Long endTime, Authorizations authorizations);

    /**
     * Filters a collection of edge ids by the authorizations of that edge, properties, etc. If
     * any of the filtered items match that edge id will be included.
     *
     * @param edgeIds              The edge ids to filter on.
     * @param authorizationToMatch The authorization to look for
     * @param filters              The parts of the edge to filter on
     * @param authorizations       The authorization to find the edges with
     * @return The filtered down list of edge ids
     */
    Iterable<String> filterEdgeIdsByAuthorization(Iterable<String> edgeIds, String authorizationToMatch, EnumSet<ElementFilter> filters, Authorizations authorizations);

    /**
     * Filters a collection of vertex ids by the authorizations of that vertex, properties, etc. If
     * any of the filtered items match that vertex id will be included.
     *
     * @param vertexIds            The vertex ids to filter on.
     * @param authorizationToMatch The authorization to look for
     * @param filters              The parts of the edge to filter on
     * @param authorizations       The authorization to find the edges with
     * @return The filtered down list of vertex ids
     */
    Iterable<String> filterVertexIdsByAuthorization(Iterable<String> vertexIds, String authorizationToMatch, EnumSet<ElementFilter> filters, Authorizations authorizations);

    /**
     * Tests the existence of edges with the given authorizations.
     *
     * @param ids            The edge ids to check existence of.
     * @param authorizations The authorizations required to load the edges.
     * @return Maps of ids to exists status.
     */
    Map<String, Boolean> doEdgesExist(Iterable<String> ids, Authorizations authorizations);

    /**
     * Tests the existence of edges with the given authorizations.
     *
     * @param ids            The edge ids to check existence of.
     * @param endTime        Include all changes made up until the point in time.
     * @param authorizations The authorizations required to load the edges.
     * @return Maps of ids to exists status.
     */
    Map<String, Boolean> doEdgesExist(Iterable<String> ids, Long endTime, Authorizations authorizations);

    /**
     * Gets all edges on the graph matching the given ids.
     *
     * @param ids            The ids of the edges to get.
     * @param authorizations The authorizations required to load the edge.
     * @return An iterable of all the edges.
     */
    Iterable<Edge> getEdges(Iterable<String> ids, Authorizations authorizations);

    /**
     * Gets all edges on the graph matching the given ids.
     *
     * @param ids            The ids of the edges to get.
     * @param fetchHints     Hint at what parts of the edge to fetch.
     * @param authorizations The authorizations required to load the edge.
     * @return An iterable of all the edges.
     */
    Iterable<Edge> getEdges(Iterable<String> ids, FetchHints fetchHints, Authorizations authorizations);

    /**
     * Gets all edges on the graph matching the given ids.
     *
     * @param ids            The ids of the edges to get.
     * @param fetchHints     Hint at what parts of the edge to fetch.
     * @param endTime        Include all changes made up until the point in time.
     * @param authorizations The authorizations required to load the edge.
     * @return An iterable of all the edges.
     */
    Iterable<Edge> getEdges(Iterable<String> ids, FetchHints fetchHints, Long endTime, Authorizations authorizations);

    /**
     * Given a list of vertices, find all the edge ids that connect them.
     *
     * @param vertices       The list of vertices.
     * @param authorizations The authorizations required to load the edges.
     * @return An iterable of all the edge ids between any two vertices.
     */
    Iterable<String> findRelatedEdgeIdsForVertices(Iterable<Vertex> vertices, Authorizations authorizations);

    /**
     * Given a list of vertex ids, find all the edge ids that connect them.
     *
     * @param vertexIds      The list of vertex ids.
     * @param authorizations The authorizations required to load the edges.
     * @return An iterable of all the edge ids between any two vertices.
     */
    Iterable<String> findRelatedEdgeIds(Iterable<String> vertexIds, Authorizations authorizations);

    /**
     * Given a list of vertex ids, find all the edge ids that connect them.
     *
     * @param vertexIds      The list of vertex ids.
     * @param endTime        Include all changes made up until the point in time.
     * @param authorizations The authorizations required to load the edges.
     * @return An iterable of all the edge ids between any two vertices.
     */
    Iterable<String> findRelatedEdgeIds(Iterable<String> vertexIds, Long endTime, Authorizations authorizations);

    /**
     * Given a list of vertices, find all the edges that connect them.
     *
     * @param vertices       The list of vertices.
     * @param authorizations The authorizations required to load the edges.
     * @return Summary information about the related edges.
     */
    Iterable<RelatedEdge> findRelatedEdgeSummaryForVertices(Iterable<Vertex> vertices, Authorizations authorizations);

    /**
     * Given a list of vertex ids, find all the edges that connect them.
     *
     * @param vertexIds      The list of vertex ids.
     * @param authorizations The authorizations required to load the edges.
     * @return Summary information about the related edges.
     */
    Iterable<RelatedEdge> findRelatedEdgeSummary(Iterable<String> vertexIds, Authorizations authorizations);

    /**
     * Given a list of vertex ids, find all the edges that connect them.
     *
     * @param vertexIds      The list of vertex ids.
     * @param endTime        Include all changes made up until the point in time.
     * @param authorizations The authorizations required to load the edges.
     * @return Summary information about the related edges.
     */
    Iterable<RelatedEdge> findRelatedEdgeSummary(Iterable<String> vertexIds, Long endTime, Authorizations authorizations);

    /**
     * Permanently deletes an edge from the graph.
     *
     * @param edge           The edge to delete from the graph.
     * @param authorizations The authorizations required to delete the edge.
     */
    void deleteEdge(Edge edge, Authorizations authorizations);

    /**
     * Permanently deletes an edge from the graph. This method requires fetching the edge before deletion.
     *
     * @param edgeId         The edge id of the edge to delete from the graph.
     * @param authorizations The authorizations required to delete the edge.
     */
    void deleteEdge(String edgeId, Authorizations authorizations);

    /**
     * Soft deletes an edge from the graph.
     *
     * @param edge           The edge to soft delete from the graph.
     * @param authorizations The authorizations required to delete the edge.
     */
    default void softDeleteEdge(Edge edge, Authorizations authorizations) {
        softDeleteEdge(edge, (Object) null, authorizations);
    }

    /**
     * Soft deletes an edge from the graph.
     *
     * @param edge           The edge to soft delete from the graph.
     * @param eventData      Data to store with the soft delete
     * @param authorizations The authorizations required to delete the edge.
     */
    void softDeleteEdge(Edge edge, Object eventData, Authorizations authorizations);

    /**
     * Soft deletes an edge from the graph.
     *
     * @param edge           The edge to soft delete from the graph.
     * @param eventData      Data to store with the soft delete
     * @param authorizations The authorizations required to delete the edge.
     */
    void softDeleteEdge(Edge edge, Long timestamp, Object eventData, Authorizations authorizations);

    /**
     * Soft deletes an edge from the graph. This method requires fetching the edge before soft deletion.
     *
     * @param edge           The edge to soft delete from the graph.
     * @param authorizations The authorizations required to delete the edge.
     */
    default void softDeleteEdge(Edge edge, Long timestamp, Authorizations authorizations) {
        softDeleteEdge(edge, timestamp, null, authorizations);
    }

    /**
     * Soft deletes an edge from the graph. This method requires fetching the edge before soft deletion.
     *
     * @param edgeId         The edge id of the vertex to soft delete from the graph.
     * @param authorizations The authorizations required to delete the edge.
     */
    default void softDeleteEdge(String edgeId, Authorizations authorizations) {
        softDeleteEdge(edgeId, null, authorizations);
    }

    /**
     * Soft deletes an edge from the graph. This method requires fetching the edge before soft deletion.
     *
     * @param edgeId         The edge id of the vertex to soft delete from the graph.
     * @param eventData      Data to store with the soft delete
     * @param authorizations The authorizations required to delete the edge.
     */
    void softDeleteEdge(String edgeId, Object eventData, Authorizations authorizations);

    /**
     * Soft deletes an edge from the graph. This method requires fetching the edge before soft deletion.
     *
     * @param edgeId         The edge id of the vertex to soft delete from the graph.
     * @param authorizations The authorizations required to delete the edge.
     */
    default void softDeleteEdge(String edgeId, Long timestamp, Authorizations authorizations) {
        softDeleteEdge(edgeId, timestamp, null, authorizations);
    }

    /**
     * Soft deletes an edge from the graph. This method requires fetching the edge before soft deletion.
     *
     * @param edgeId         The edge id of the vertex to soft delete from the graph.
     * @param eventData      Data to store with the soft delete
     * @param authorizations The authorizations required to delete the edge.
     */
    void softDeleteEdge(String edgeId, Long timestamp, Object eventData, Authorizations authorizations);

    /**
     * Creates a query builder object used to query the graph.
     *
     * @param queryString    The string to search for in the text of an element. This will search all fields for the given text.
     * @param authorizations The authorizations required to load the elements.
     * @return A query builder object.
     */
    GraphQuery query(String queryString, Authorizations authorizations);

    /**
     * Creates a query builder object used to query the graph.
     *
     * @param authorizations The authorizations required to load the elements.
     * @return A query builder object.
     */
    GraphQuery query(Authorizations authorizations);

    /**
     * Creates a query builder object used to query a list of vertices.
     *
     * @param vertexIds      The vertex ids to query.
     * @param queryString    The string to search for in the text of an element. This will search all fields for the given text.
     * @param authorizations The authorizations required to load the elements.
     * @return A query builder object.
     */
    MultiVertexQuery query(String[] vertexIds, String queryString, Authorizations authorizations);

    /**
     * Creates a query builder object used to query a list of vertices.
     *
     * @param vertexIds      The vertex ids to query.
     * @param authorizations The authorizations required to load the elements.
     * @return A query builder object.
     */
    MultiVertexQuery query(String[] vertexIds, Authorizations authorizations);

    /**
     * Returns true if this graph supports similar to text queries.
     */
    boolean isQuerySimilarToTextSupported();

    /**
     * Creates a query builder object that finds all vertices similar to the given text for the specified fields.
     * This could be implemented similar to the ElasticSearch more like this query.
     *
     * @param fields         The fields to match against.
     * @param text           The text to find similar to.
     * @param authorizations The authorizations required to load the elements.
     * @return A query builder object.
     */
    SimilarToGraphQuery querySimilarTo(String[] fields, String text, Authorizations authorizations);

    /**
     * Flushes any pending mutations to the graph.
     */
    void flush();

    /**
     * Cleans up or disconnects from the underlying storage.
     */
    void shutdown();

    /**
     * Finds all paths between two vertices.
     *
     * @param options        Find path options
     * @param authorizations The authorizations required to load all edges and vertices.
     * @return An Iterable of lists of paths.
     */
    Iterable<Path> findPaths(FindPathOptions options, Authorizations authorizations);

    /**
     * Gets the id generator used by this graph to create ids.
     *
     * @return the id generator.
     */
    IdGenerator getIdGenerator();

    /**
     * Given an authorization is the visibility object valid.
     *
     * @param visibility     The visibility you want to check.
     * @param authorizations The given authorizations.
     * @return true if the visibility is valid given an authorization, else return false.
     */
    boolean isVisibilityValid(Visibility visibility, Authorizations authorizations);

    /**
     * Reindex all vertices and edges.
     *
     * @param authorizations authorizations used to query for the data to reindex.
     */
    void reindex(Authorizations authorizations);

    /**
     * Sets metadata on the graph.
     *
     * @param key   The key to the metadata.
     * @param value The value to set.
     */
    void setMetadata(String key, Object value);

    /**
     * Gets metadata from the graph.
     *
     * @param key The key to the metadata.
     * @return The metadata value, or null.
     */
    Object getMetadata(String key);

    /**
     * Force a reload of graph metadata.
     */
    void reloadMetadata();

    /**
     * Gets all metadata.
     *
     * @return Iterable of all metadata.
     */
    Iterable<GraphMetadataEntry> getMetadata();

    /**
     * Gets all metadata with the given prefix.
     */
    Iterable<GraphMetadataEntry> getMetadataWithPrefix(String prefix);

    /**
     * Determine if field boost is support. That is can you change the boost at a field level to give higher priority.
     */
    boolean isFieldBoostSupported();

    /**
     * Clears all data from the graph.
     */
    void truncate();

    /**
     * Drops all tables
     */
    void drop();

    /**
     * Gets the granularity of the search index {@link SearchIndexSecurityGranularity}
     */
    SearchIndexSecurityGranularity getSearchIndexSecurityGranularity();

    /**
     * Adds a graph event listener that will be called when graph events occur.
     */
    void addGraphEventListener(GraphEventListener graphEventListener);

    /**
     * Marks a vertex as hidden for a given visibility.
     *
     * @param vertex         The vertex to mark hidden.
     * @param visibility     The visibility string under which this vertex is hidden.
     *                       This visibility can be a superset of the vertex visibility to mark
     *                       it as hidden for only a subset of authorizations.
     * @param authorizations The authorizations used.
     */
    default void markVertexHidden(Vertex vertex, Visibility visibility, Authorizations authorizations) {
        markVertexHidden(vertex, visibility, null, authorizations);
    }

    /**
     * Marks a vertex as hidden for a given visibility.
     *
     * @param vertex         The vertex to mark hidden.
     * @param visibility     The visibility string under which this vertex is hidden.
     *                       This visibility can be a superset of the vertex visibility to mark
     *                       it as hidden for only a subset of authorizations.
     * @param eventData      Data to store with the hidden
     * @param authorizations The authorizations used.
     */
    void markVertexHidden(Vertex vertex, Visibility visibility, Object eventData, Authorizations authorizations);

    /**
     * Marks a vertex as visible for a given visibility, effectively undoing markVertexHidden.
     *
     * @param vertex         The vertex to mark visible.
     * @param visibility     The visibility string under which this vertex is now visible.
     * @param authorizations The authorizations used.
     */
    default void markVertexVisible(Vertex vertex, Visibility visibility, Authorizations authorizations) {
        markVertexVisible(vertex, visibility, null, authorizations);
    }

    /**
     * Marks a vertex as visible for a given visibility, effectively undoing markVertexHidden.
     *
     * @param vertex         The vertex to mark visible.
     * @param visibility     The visibility string under which this vertex is now visible.
     * @param eventData      Data to store with the visible
     * @param authorizations The authorizations used.
     */
    void markVertexVisible(Vertex vertex, Visibility visibility, Object eventData, Authorizations authorizations);

    /**
     * Marks an edge as hidden for a given visibility.
     *
     * @param edge           The edge to mark hidden.
     * @param visibility     The visibility string under which this edge is hidden.
     *                       This visibility can be a superset of the edge visibility to mark
     *                       it as hidden for only a subset of authorizations.
     * @param authorizations The authorizations used.
     */
    default void markEdgeHidden(Edge edge, Visibility visibility, Authorizations authorizations) {
        markEdgeHidden(edge, visibility, null, authorizations);
    }

    /**
     * Marks an edge as hidden for a given visibility.
     *
     * @param edge           The edge to mark hidden.
     * @param visibility     The visibility string under which this edge is hidden.
     *                       This visibility can be a superset of the edge visibility to mark
     *                       it as hidden for only a subset of authorizations.
     * @param eventData      Data to store with the hidden
     * @param authorizations The authorizations used.
     */
    void markEdgeHidden(Edge edge, Visibility visibility, Object eventData, Authorizations authorizations);

    /**
     * Marks an edge as visible for a given visibility, effectively undoing markEdgeHidden.
     *
     * @param edge           The edge to mark visible.
     * @param visibility     The visibility string under which this edge is now visible.
     * @param authorizations The authorizations used.
     */
    default void markEdgeVisible(Edge edge, Visibility visibility, Authorizations authorizations) {
        markEdgeVisible(edge, visibility, null, authorizations);
    }

    /**
     * Marks an edge as visible for a given visibility, effectively undoing markEdgeHidden.
     *
     * @param edge           The edge to mark visible.
     * @param visibility     The visibility string under which this edge is now visible.
     * @param eventData      Data to store with the visible
     * @param authorizations The authorizations used.
     */
    void markEdgeVisible(Edge edge, Visibility visibility, Object eventData, Authorizations authorizations);

    /**
     * Creates an authorizations object.
     *
     * @param auths The authorizations granted.
     * @return A new authorizations object
     */
    Authorizations createAuthorizations(String... auths);

    /**
     * Creates an authorizations object.
     *
     * @param auths The authorizations granted.
     * @return A new authorizations object
     */
    Authorizations createAuthorizations(Collection<String> auths);

    /**
     * Creates an authorizations object combining auths and additionalAuthorizations.
     *
     * @param auths                    The authorizations granted.
     * @param additionalAuthorizations additional authorizations
     * @return A new authorizations object
     */
    Authorizations createAuthorizations(Authorizations auths, String... additionalAuthorizations);

    /**
     * Creates an authorizations object combining auths and additionalAuthorizations.
     *
     * @param auths                    The authorizations granted.
     * @param additionalAuthorizations additional authorizations
     * @return A new authorizations object
     */
    Authorizations createAuthorizations(Authorizations auths, Collection<String> additionalAuthorizations);

    /**
     * Gets the number of times a property with a given value occurs on vertices
     *
     * @param propertyName   The name of the property to find
     * @param authorizations The authorizations to use to find the property
     * @return The results
     * @deprecated Use {@link org.vertexium.query.Query#addAggregation(Aggregation)}
     */
    @Deprecated
    Map<Object, Long> getVertexPropertyCountByValue(String propertyName, Authorizations authorizations);

    /**
     * Gets a count of the number of vertices in the system.
     */
    long getVertexCount(Authorizations authorizations);

    /**
     * Gets a count of the number of edges in the system.
     */
    long getEdgeCount(Authorizations authorizations);

    /**
     * Save a pre-made property definition.
     *
     * @param propertyDefinition the property definition to save.
     */
    void savePropertyDefinition(PropertyDefinition propertyDefinition);

    /**
     * Creates a defines property builder. This is typically used by the indexer to give it hints on how it should index a property.
     *
     * @param propertyName The name of the property to define.
     */
    DefinePropertyBuilder defineProperty(String propertyName);

    /**
     * Determine if a property is already defined
     */
    boolean isPropertyDefined(String propertyName);

    /**
     * Gets the property definition for the given name.
     *
     * @param propertyName name of the property
     * @return the property definition if found. null otherwise.
     */
    PropertyDefinition getPropertyDefinition(String propertyName);

    /**
     * Gets all property definitions.
     *
     * @return all property definitions.
     */
    Collection<PropertyDefinition> getPropertyDefinitions();

    /**
     * Saves multiple mutations with a single call.
     *
     * @param mutations      the mutations to save
     * @param authorizations the authorizations used during save
     * @return the elements which were saved
     */
    Iterable<Element> saveElementMutations(
        Iterable<ElementMutation<? extends Element>> mutations,
        Authorizations authorizations
    );

    /**
     * Opens multiple StreamingPropertyValue input streams at once. This can have performance benefits by
     * reducing the number of queries to the underlying data source.
     *
     * @param streamingPropertyValues list of StreamingPropertyValues to get input streams for
     * @return InputStreams in the same order as the input list
     */
    List<InputStream> getStreamingPropertyValueInputStreams(List<StreamingPropertyValue> streamingPropertyValues);

    /**
     * Reads multiple {@link StreamingPropertyValue}s at once. This can have performance benefits by
     * reducing the number of queries to the underlying data source.
     * <p>
     * WARNING: Chunks can come interleaved.
     *
     * @param streamingPropertyValues list of {@link StreamingPropertyValue}s to get chunks for
     * @return a stream of chunks
     */
    default Stream<StreamingPropertyValueChunk> readStreamingPropertyValueChunks(Iterable<StreamingPropertyValue> streamingPropertyValues) {
        return StreamingPropertyValue.readChunks(streamingPropertyValues);
    }

    /**
     * Reads multiple {@link StreamingPropertyValue}s at once. This can have performance benefits by
     * reducing the number of queries to the underlying data source.
     * <p>
     * Similar to {@link #readStreamingPropertyValueChunks(Iterable)} but reads the whole {@link StreamingPropertyValue}
     * into memory as opposed to a chunk at a time.
     *
     * @param streamingPropertyValues list of {@link StreamingPropertyValue}s to get data for
     * @return a stream of values
     */
    default Stream<StreamingPropertyValueData> readStreamingPropertyValues(Iterable<StreamingPropertyValue> streamingPropertyValues) {
        Map<StreamingPropertyValue, ByteArrayOutputStream> buffers = new HashMap<>();
        return readStreamingPropertyValueChunks(streamingPropertyValues)
            .map(chunk -> {
                ByteArrayOutputStream buffer = buffers.computeIfAbsent(chunk.getStreamingPropertyValue(), spv -> new ByteArrayOutputStream());
                buffer.write(chunk.getData(), 0, chunk.getChunkSize());
                StreamingPropertyValueData result = null;
                if (chunk.isLast()) {
                    byte[] data = buffer.toByteArray();
                    result = new StreamingPropertyValueData(chunk.getStreamingPropertyValue(), data, data.length);
                    buffers.remove(chunk.getStreamingPropertyValue());
                }
                return result;
            })
            .filter(Objects::nonNull);
    }

    /**
     * Gets the specified extended data rows.
     *
     * @param ids            The ids of the rows to get.
     * @param authorizations The authorizations used to get the rows
     * @return Rows
     */
    default Iterable<ExtendedDataRow> getExtendedData(Iterable<ExtendedDataRowId> ids, Authorizations authorizations) {
        return getExtendedData(ids, FetchHints.ALL, authorizations);
    }

    /**
     * Gets the specified extended data rows.
     *
     * @param ids            The ids of the rows to get.
     * @param authorizations The authorizations used to get the rows
     * @return Rows
     */
    Iterable<ExtendedDataRow> getExtendedData(Iterable<ExtendedDataRowId> ids, FetchHints fetchHints, Authorizations authorizations);

    /**
     * Gets the specified extended data row.
     *
     * @param id             The id of the row to get.
     * @param authorizations The authorizations used to get the rows
     * @return Rows
     */
    ExtendedDataRow getExtendedData(ExtendedDataRowId id, Authorizations authorizations);

    /**
     * Gets the specified extended data rows.
     *
     * @param elementType    The type of element to get the rows from
     * @param elementId      The element id to get the rows from
     * @param tableName      The name of the table within the element to get the rows from
     * @param authorizations The authorizations used to get the rows
     * @return Rows
     */
    default Iterable<ExtendedDataRow> getExtendedData(
        ElementType elementType,
        String elementId,
        String tableName,
        Authorizations authorizations
    ) {
        return getExtendedData(elementType, elementId, tableName, FetchHints.ALL, authorizations);
    }

    /**
     * Gets the specified extended data rows.
     *
     * @param elementId      The element id to get the rows from
     * @param tableName      The name of the table within the element to get the rows from
     * @param authorizations The authorizations used to get the rows
     * @return Rows
     */
    default Iterable<ExtendedDataRow> getExtendedData(
        ElementId elementId,
        String tableName,
        Authorizations authorizations
    ) {
        return getExtendedData(elementId, tableName, FetchHints.ALL, authorizations);
    }

    /**
     * Gets the specified extended data rows.
     *
     * @param elementType    The type of element to get the rows from
     * @param elementId      The element id to get the rows from
     * @param tableName      The name of the table within the element to get the rows from
     * @param fetchHints     Fetch hints to filter extended data
     * @param authorizations The authorizations used to get the rows
     * @return Rows
     */
    default Iterable<ExtendedDataRow> getExtendedData(
        ElementType elementType,
        String elementId,
        String tableName,
        FetchHints fetchHints,
        Authorizations authorizations
    ) {
        return getExtendedData(ElementId.create(elementType, elementId), tableName, fetchHints, authorizations);
    }

    /**
     * Gets the specified extended data rows.
     *
     * @param elementId      The element id to get the rows from
     * @param tableName      The name of the table within the element to get the rows from
     * @param fetchHints     Fetch hints to filter extended data
     * @param authorizations The authorizations used to get the rows
     * @return Rows
     */
    default Iterable<ExtendedDataRow> getExtendedData(
        ElementId elementId,
        String tableName,
        FetchHints fetchHints,
        Authorizations authorizations
    ) {
        return getExtendedDataForElements(Lists.newArrayList(elementId), tableName, fetchHints, authorizations);
    }

    /**
     * Gets the specified extended data rows.
     *
     * @param elementIds     The element ids of the elements to get the rows from
     * @param authorizations The authorizations used to get the rows
     * @return Rows
     */
    default Iterable<ExtendedDataRow> getExtendedDataForElements(
        Iterable<? extends ElementId> elementIds,
        Authorizations authorizations
    ) {
        return getExtendedDataForElements(elementIds, FetchHints.ALL, authorizations);
    }

    /**
     * Gets the specified extended data rows.
     *
     * @param elementIds     The element ids of the elements to get the rows from
     * @param tableName      The name of the table within the element to get the rows from
     * @param authorizations The authorizations used to get the rows
     * @return Rows
     */
    default Iterable<ExtendedDataRow> getExtendedDataForElements(
        Iterable<? extends ElementId> elementIds,
        String tableName,
        Authorizations authorizations
    ) {
        return getExtendedDataForElements(elementIds, tableName, FetchHints.ALL, authorizations);
    }

    /**
     * Gets the specified extended data rows.
     *
     * @param elementIds     The element ids of the elements to get the rows from
     * @param fetchHints     Fetch hints to filter extended data
     * @param authorizations The authorizations used to get the rows
     * @return Rows
     */
    default Iterable<ExtendedDataRow> getExtendedDataForElements(
        Iterable<? extends ElementId> elementIds,
        FetchHints fetchHints,
        Authorizations authorizations
    ) {
        return getExtendedDataForElements(elementIds, null, fetchHints, authorizations);
    }

    /**
     * Gets the specified extended data rows.
     *
     * @param elementIds     The element ids of the elements to get the rows from
     * @param tableName      The name of the table within the element to get the rows from
     * @param fetchHints     Fetch hints to filter extended data
     * @param authorizations The authorizations used to get the rows
     * @return Rows
     */
    Iterable<ExtendedDataRow> getExtendedDataForElements(
        Iterable<? extends ElementId> elementIds,
        String tableName,
        FetchHints fetchHints,
        Authorizations authorizations
    );

    /**
     * Gets extended data rows from the graph in the given range.
     *
     * @param elementType    The type of element to get the rows from
     * @param elementIdRange The range of element ids to get extended data rows for.
     * @param authorizations The authorizations required to load the vertex.
     * @return The extended data rows for the element ids in the range.
     */
    Iterable<ExtendedDataRow> getExtendedDataInRange(ElementType elementType, IdRange elementIdRange, Authorizations authorizations);

    /**
     * Gets a list of historical events.
     *
     * @param elementIds     Iterable of element ids to get events for
     * @param authorizations The authorizations required to load the events
     * @return An iterable of historic events
     */
    default Stream<HistoricalEvent> getHistoricalEvents(Iterable<ElementId> elementIds, Authorizations authorizations) {
        return getHistoricalEvents(elementIds, HistoricalEventsFetchHints.ALL, authorizations);
    }

    /**
     * Gets a list of historical events.
     *
     * @param elementIds     Iterable of element ids to get events for
     * @param fetchHints     Fetch hints to filter historical events
     * @param authorizations The authorizations required to load the events
     * @return An iterable of historic events
     */
    default Stream<HistoricalEvent> getHistoricalEvents(
        Iterable<ElementId> elementIds,
        HistoricalEventsFetchHints fetchHints,
        Authorizations authorizations
    ) {
        return getHistoricalEvents(elementIds, null, fetchHints, authorizations);
    }

    /**
     * Gets a list of historical events.
     *
     * @param elementIds     Iterable of element ids to get events for
     * @param after          Find events after the given id
     * @param fetchHints     Fetch hints to filter historical events
     * @param authorizations The authorizations required to load the events
     * @return An iterable of historic events
     */
    Stream<HistoricalEvent> getHistoricalEvents(
        Iterable<ElementId> elementIds,
        HistoricalEventId after,
        HistoricalEventsFetchHints fetchHints,
        Authorizations authorizations
    );

    /**
     * Deletes an extended data row
     */
    void deleteExtendedDataRow(ExtendedDataRowId id, Authorizations authorizations);

    /**
     * The default fetch hints to use if none are provided
     */
    FetchHints getDefaultFetchHints();

    /**
     * Visits all elements on the graph
     */
    @Deprecated
    void visitElements(GraphVisitor graphVisitor, Authorizations authorizations);

    /**
     * Visits all vertices on the graph
     */
    @Deprecated
    void visitVertices(GraphVisitor graphVisitor, Authorizations authorizations);

    /**
     * Visits all edges on the graph
     */
    @Deprecated
    void visitEdges(GraphVisitor graphVisitor, Authorizations authorizations);

    /**
     * Visits elements using the supplied elements and visitor
     */
    @Deprecated
    void visit(Iterable<? extends Element> elements, GraphVisitor visitor);

    /**
     * Gets the metrics registry to record internal Vertexium metrics
     */
    VertexiumMetricRegistry getMetricsRegistry();
}
