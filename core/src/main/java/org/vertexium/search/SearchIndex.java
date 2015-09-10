package org.vertexium.search;

import org.vertexium.*;
import org.vertexium.query.GraphQuery;
import org.vertexium.query.MultiVertexQuery;
import org.vertexium.query.SimilarToGraphQuery;
import org.vertexium.query.VertexQuery;

import java.io.IOException;

public interface SearchIndex {
    void addElement(Graph graph, Element element, Authorizations authorizations);

    void deleteElement(Graph graph, Element element, Authorizations authorizations);

    void deleteProperty(Graph graph, Element element, Property property, Authorizations authorizations);

    void deleteProperty(
            Graph graph,
            Element element,
            String propertyKey,
            String propertyName,
            Visibility propertyVisibility,
            Authorizations authorizations
    );

    void addElements(Graph graph, Iterable<? extends Element> elements, Authorizations authorizations);

    GraphQuery queryGraph(Graph graph, String queryString, Authorizations authorizations);

    MultiVertexQuery queryGraph(Graph graph, String[] vertexIds, String queryString, Authorizations authorizations);

    VertexQuery queryVertex(Graph graph, Vertex vertex, String queryString, Authorizations authorizations);

    void flush();

    void shutdown();

    void addPropertyDefinition(Graph graph, PropertyDefinition propertyDefinition) throws IOException;

    boolean isPropertyDefined(String propertyName);

    boolean isFieldBoostSupported();

    void truncate();

    void drop();

    SearchIndexSecurityGranularity getSearchIndexSecurityGranularity();

    boolean isQuerySimilarToTextSupported();

    SimilarToGraphQuery querySimilarTo(Graph graph, String[] fields, String text, Authorizations authorizations);

    boolean isFieldLevelSecuritySupported();
}
