package org.vertexium.elasticsearch;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import net.jodah.recurrent.Recurrent;
import net.jodah.recurrent.RetryPolicy;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.stats.IndexStats;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.engine.VersionConflictEngineException;
import org.elasticsearch.index.query.FilteredQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermFilterBuilder;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsBuilder;
import org.vertexium.*;
import org.vertexium.id.NameSubstitutionStrategy;
import org.vertexium.property.StreamingPropertyValue;
import org.vertexium.query.*;
import org.vertexium.search.SearchIndex;
import org.vertexium.search.SearchIndexWithVertexPropertyCountByValue;
import org.vertexium.type.GeoCircle;
import org.vertexium.type.GeoPoint;
import org.vertexium.type.GeoShape;
import org.vertexium.type.IpV4Address;
import org.vertexium.util.ConfigurationUtils;
import org.vertexium.util.IOUtils;
import org.vertexium.util.VertexiumLogger;
import org.vertexium.util.VertexiumLoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.vertexium.util.Preconditions.checkNotNull;

public class ElasticsearchSingleDocumentSearchIndex implements SearchIndex, SearchIndexWithVertexPropertyCountByValue {
    private static final VertexiumLogger LOGGER = VertexiumLoggerFactory.getLogger(ElasticsearchSingleDocumentSearchIndex.class);
    protected static final VertexiumLogger MUTATION_LOGGER = VertexiumLoggerFactory.getMutationLogger(SearchIndex.class);
    public static final String ELEMENT_TYPE = "element";
    public static final String ELEMENT_TYPE_FIELD_NAME = "__elementType";
    public static final String VISIBILITY_FIELD_NAME = "__visibility";
    public static final String OUT_VERTEX_ID_FIELD_NAME = "__outVertexId";
    public static final String IN_VERTEX_ID_FIELD_NAME = "__inVertexId";
    public static final String EDGE_LABEL_FIELD_NAME = "__edgeLabel";
    public static final String EXACT_MATCH_PROPERTY_NAME_SUFFIX = "_e";
    public static final String GEO_PROPERTY_NAME_SUFFIX = "_g";
    public static final int MAX_BATCH_COUNT = 25000;
    public static final long MAX_BATCH_SIZE = 15 * 1024 * 1024;
    public static final int EXACT_MATCH_IGNORE_ABOVE_LIMIT = 10000;
    private static final long IN_PROCESS_NODE_WAIT_TIME_MS = 10 * 60 * 1000;
    private static final int MAX_RETRIES = 3;
    private final Client client;
    private final ElasticSearchSearchIndexConfiguration config;
    private final Map<String, IndexInfo> indexInfos = new HashMap<>();
    private int indexInfosLastSize = 0; // Used to prevent creating a index name array each time
    private String[] indexNamesAsArray;
    private IndexSelectionStrategy indexSelectionStrategy;
    private boolean allFieldEnabled;
    private Node inProcessNode;
    public static final Pattern PROPERTY_NAME_PATTERN = Pattern.compile("^(.*?)(_([0-9a-f]{32}))?(_([a-z]))?$");
    public static final Pattern AGGREGATION_NAME_PATTERN = Pattern.compile("(.*?)_([0-9a-f]+)");
    public static final String CONFIG_PROPERTY_NAME_VISIBILITIES_STORE = "propertyNameVisibilitiesStore";
    public static final Class<? extends PropertyNameVisibilitiesStore> DEFAULT_PROPERTY_NAME_VISIBILITIES_STORE = MetadataTablePropertyNameVisibilitiesStore.class;
    private final NameSubstitutionStrategy nameSubstitutionStrategy;
    private final PropertyNameVisibilitiesStore propertyNameVisibilitiesStore;
    private final Random random = new Random();

    public ElasticsearchSingleDocumentSearchIndex(Graph graph, GraphConfiguration config) {
        this.config = new ElasticSearchSearchIndexConfiguration(graph, config);
        this.nameSubstitutionStrategy = this.config.getNameSubstitutionStrategy();
        this.indexSelectionStrategy = this.config.getIndexSelectionStrategy();
        this.allFieldEnabled = this.config.isAllFieldEnabled(false);
        this.propertyNameVisibilitiesStore = createPropertyNameVisibilitiesStore(graph, config);
        this.client = createClient(this.config);
        loadIndexInfos(graph);
    }

    protected Client createClient(ElasticSearchSearchIndexConfiguration config) {
        if (config.isInProcessNode()) {
            return createInProcessNode(config);
        } else {
            return createTransportClient(config);
        }
    }

    private Client createInProcessNode(ElasticSearchSearchIndexConfiguration config) {
        try {
            Class.forName("groovy.lang.GroovyShell");
        } catch (ClassNotFoundException e) {
            throw new VertexiumException("Unable to load Groovy. This is required when running in-process ES.", e);
        }

        Settings settings = tryReadSettingsFromFile(config);
        if (settings == null) {
            String dataPath = config.getInProcessNodeDataPath();
            checkNotNull(dataPath, ElasticSearchSearchIndexConfiguration.IN_PROCESS_NODE_DATA_PATH + " is required for in process Elasticsearch node");
            String logsPath = config.getInProcessNodeLogsPath();
            checkNotNull(logsPath, ElasticSearchSearchIndexConfiguration.IN_PROCESS_NODE_LOGS_PATH + " is required for in process Elasticsearch node");
            String workPath = config.getInProcessNodeWorkPath();
            checkNotNull(workPath, ElasticSearchSearchIndexConfiguration.IN_PROCESS_NODE_WORK_PATH + " is required for in process Elasticsearch node");
            int numberOfShards = config.getNumberOfShards();
            settings = ImmutableSettings.settingsBuilder()
                    .put("script.disable_dynamic", "false")
                    .put("index.number_of_shards", numberOfShards)
                    .put("index.number_of_replicas", "0")
                    .put("path.data", dataPath)
                    .put("path.logs", logsPath)
                    .put("path.work", workPath)
                    .put("discovery.zen.ping.multicast.enabled", false)
                    .build();
        }

        NodeBuilder nodeBuilder = NodeBuilder
                .nodeBuilder();
        if (config.getClusterName() != null) {
            nodeBuilder = nodeBuilder.clusterName(config.getClusterName());
        }
        this.inProcessNode = nodeBuilder
                .settings(settings).node();
        inProcessNode.start();
        Client client = inProcessNode.client();

        long startTime = System.currentTimeMillis();
        while (true) {
            if (System.currentTimeMillis() > startTime + IN_PROCESS_NODE_WAIT_TIME_MS) {
                throw new VertexiumException("Status failed to exit red status after waiting " + IN_PROCESS_NODE_WAIT_TIME_MS + "ms. Giving up.");
            }
            ClusterHealthResponse health = client.admin().cluster().prepareHealth().get();
            if (health.getStatus() != ClusterHealthStatus.RED) {
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new VertexiumException("Could not sleep", e);
            }
            LOGGER.info("Status is %s, waiting...", health.getStatus());
        }
        return client;
    }

    private static TransportClient createTransportClient(ElasticSearchSearchIndexConfiguration config) {
        Settings settings = tryReadSettingsFromFile(config);
        if (settings == null) {
            ImmutableSettings.Builder settingsBuilder = ImmutableSettings.settingsBuilder();
            if (config.getClusterName() != null) {
                settingsBuilder.put("cluster.name", config.getClusterName());
            }
            settings = settingsBuilder.build();
        }
        TransportClient transportClient = new TransportClient(settings);
        for (String esLocation : config.getEsLocations()) {
            String[] locationSocket = esLocation.split(":");
            String hostname;
            int port;
            if (locationSocket.length == 2) {
                hostname = locationSocket[0];
                port = Integer.parseInt(locationSocket[1]);
            } else if (locationSocket.length == 1) {
                hostname = locationSocket[0];
                port = config.getPort();
            } else {
                throw new VertexiumException("Invalid elastic search location: " + esLocation);
            }
            transportClient.addTransportAddress(new InetSocketTransportAddress(hostname, port));
        }
        return transportClient;
    }

    private static Settings tryReadSettingsFromFile(ElasticSearchSearchIndexConfiguration config) {
        File esConfigFile = config.getEsConfigFile();
        if (esConfigFile == null) {
            return null;
        }
        if (!esConfigFile.exists()) {
            throw new VertexiumException(esConfigFile.getAbsolutePath() + " does not exist");
        }
        try (FileInputStream fileIn = new FileInputStream(esConfigFile)) {
            return ImmutableSettings.builder().loadFromStream(esConfigFile.getAbsolutePath(), fileIn).build();
        } catch (IOException e) {
            throw new VertexiumException("Could not read ES config file: " + esConfigFile.getAbsolutePath(), e);
        }
    }

    protected final boolean isAllFieldEnabled() {
        return allFieldEnabled;
    }

    private Map<String, IndexStats> getExistingIndexNames() {
        return client.admin().indices().prepareStats().execute().actionGet().getIndices();
    }

    protected void loadIndexInfos(Graph graph) {
        Map<String, IndexStats> indices = getExistingIndexNames();
        for (String indexName : indices.keySet()) {
            if (!indexSelectionStrategy.isIncluded(this, indexName)) {
                LOGGER.debug("skipping index %s, not in indicesToQuery", indexName);
                continue;
            }

            IndexInfo indexInfo = indexInfos.get(indexName);
            if (indexInfo != null) {
                continue;
            }

            LOGGER.debug("loading index info for %s", indexName);
            indexInfo = createIndexInfo(indexName);
            addPropertyNameVisibility(graph, indexInfo, ELEMENT_TYPE_FIELD_NAME, null);
            addPropertyNameVisibility(graph, indexInfo, VISIBILITY_FIELD_NAME, null);
            addPropertyNameVisibility(graph, indexInfo, OUT_VERTEX_ID_FIELD_NAME, null);
            addPropertyNameVisibility(graph, indexInfo, IN_VERTEX_ID_FIELD_NAME, null);
            addPropertyNameVisibility(graph, indexInfo, EDGE_LABEL_FIELD_NAME, null);
            indexInfos.put(indexName, indexInfo);
        }
    }

    private PropertyNameVisibilitiesStore createPropertyNameVisibilitiesStore(Graph graph, GraphConfiguration config) {
        String className = config.getString(GraphConfiguration.SEARCH_INDEX_PROP_PREFIX + "." + CONFIG_PROPERTY_NAME_VISIBILITIES_STORE, DEFAULT_PROPERTY_NAME_VISIBILITIES_STORE.getName());
        return ConfigurationUtils.createProvider(className, graph, config);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addElement(Graph graph, final Element element, Authorizations authorizations) {
        if (MUTATION_LOGGER.isTraceEnabled()) {
            MUTATION_LOGGER.trace("addElement: %s", element.getId());
        }
        if (!getConfig().isIndexEdges() && element instanceof Edge) {
            return;
        }

        final IndexInfo indexInfo = addPropertiesToIndex(graph, element, element.getProperties());

        try {
            final XContentBuilder jsonBuilder = buildJsonContentFromElement(graph, element, authorizations);
            final XContentBuilder source = jsonBuilder.endObject();
            if (MUTATION_LOGGER.isTraceEnabled()) {
                MUTATION_LOGGER.trace("addElement json: %s: %s", element.getId(), source.string());
            }

            final AtomicInteger retryCount = new AtomicInteger(0);

            Runnable update = new Runnable() {
                public void run() {
                    if (retryCount.get() > 0) {
                        LOGGER.info("Retrying (%d of %d) due to ES version conflict for document id: %s", retryCount.get(), MAX_RETRIES, element.getId());
                        getClient().get(Requests.getRequest(indexInfo.getIndexName())
                                .id(element.getId())
                                .type(ELEMENT_TYPE)
                                .refresh(true))
                                .actionGet();
                    }

                    try {
                        UpdateResponse response = getClient()
                                .prepareUpdate(indexInfo.getIndexName(), ELEMENT_TYPE, element.getId())
                                .setDocAsUpsert(true)
                                .setDoc(source)
                                .execute()
                                .actionGet();
                        if (response.getId() == null) {
                            throw new VertexiumException("Could not index document id: " + element.getId());
                        }
                    } catch (VersionConflictEngineException e) {
                        LOGGER.warn("ES version conflict detected for document id: %s", element.getId());
                        retryCount.incrementAndGet();
                        throw e;
                    }
                }
            };

            RetryPolicy retryPolicy = new RetryPolicy()
                    .retryOn(VersionConflictEngineException.class)
                    .withDelay(100 + random.nextInt(50), TimeUnit.MILLISECONDS)
                    .withMaxRetries(MAX_RETRIES);

            Recurrent.run(update, retryPolicy);

            if (getConfig().isAutoFlush()) {
                flush();
            }
        } catch (Exception e) {
            throw new VertexiumException("Could not add element", e);
        }

        getConfig().getScoringStrategy().addElement(this, graph, element, authorizations);
    }

    private XContentBuilder buildJsonContentFromElement(Graph graph, Element element, Authorizations authorizations) throws IOException {
        XContentBuilder jsonBuilder;
        jsonBuilder = XContentFactory.jsonBuilder()
                .startObject();

        String elementTypeVisibilityPropertyName = addElementTypeVisibilityPropertyToIndex(graph, element);

        if (element instanceof Vertex) {
            jsonBuilder.field(ELEMENT_TYPE_FIELD_NAME, ElasticSearchElementType.VERTEX.getKey());
            jsonBuilder.field(elementTypeVisibilityPropertyName, ElasticSearchElementType.VERTEX.getKey());
            getConfig().getScoringStrategy().addFieldsToVertexDocument(this, jsonBuilder, (Vertex) element, null, authorizations);
        } else if (element instanceof Edge) {
            Edge edge = (Edge) element;
            jsonBuilder.field(ELEMENT_TYPE_FIELD_NAME, ElasticSearchElementType.EDGE.getKey());
            jsonBuilder.field(elementTypeVisibilityPropertyName, ElasticSearchElementType.VERTEX.getKey());
            getConfig().getScoringStrategy().addFieldsToEdgeDocument(this, jsonBuilder, edge, null, authorizations);
            jsonBuilder.field(IN_VERTEX_ID_FIELD_NAME, edge.getVertexId(Direction.IN));
            jsonBuilder.field(OUT_VERTEX_ID_FIELD_NAME, edge.getVertexId(Direction.OUT));
            jsonBuilder.field(EDGE_LABEL_FIELD_NAME, edge.getLabel());
        } else {
            throw new VertexiumException("Unexpected element type " + element.getClass().getName());
        }

        Map<String, Object> properties = getProperties(graph, element);
        for (Map.Entry<String, Object> property : properties.entrySet()) {
            if (property.getValue() instanceof List) {
                List list = (List) property.getValue();
                jsonBuilder.field(property.getKey(), list.toArray(new Object[list.size()]));
            } else {
                jsonBuilder.field(property.getKey(), convertValueForIndexing(property.getValue()));
            }
        }

        return jsonBuilder;
    }

    protected Object convertValueForIndexing(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof BigDecimal) {
            return ((BigDecimal) obj).doubleValue();
        }
        if (obj instanceof BigInteger) {
            return ((BigInteger) obj).intValue();
        }
        return obj;
    }

    private String addElementTypeVisibilityPropertyToIndex(Graph graph, Element element) throws IOException {
        String elementTypeVisibilityPropertyName = deflatePropertyName(graph, ELEMENT_TYPE_FIELD_NAME, element.getVisibility());
        String indexName = getIndexName(element);
        IndexInfo indexInfo = ensureIndexCreatedAndInitialized(indexName);
        addPropertyToIndex(graph, indexInfo, elementTypeVisibilityPropertyName, element.getVisibility(), String.class, false, false);
        return elementTypeVisibilityPropertyName;
    }

    private Map<String, Object> getProperties(Graph graph, Element element) throws IOException {
        Map<String, Object> propertiesMap = new HashMap<>();
        for (Property property : element.getProperties()) {
            addPropertyToMap(graph, property, propertiesMap);
        }
        return propertiesMap;
    }

    private void addPropertyToMap(Graph graph, Property property, Map<String, Object> propertiesMap) throws IOException {
        Object propertyValue = property.getValue();
        String propertyName = deflatePropertyName(graph, property);
        PropertyDefinition propertyDefinition = getPropertyDefinition(graph, propertyName);
        if (propertyValue != null && shouldIgnoreType(propertyValue.getClass())) {
            return;
        } else if (propertyValue instanceof GeoPoint) {
            convertGeoPoint(graph, propertiesMap, property, (GeoPoint) propertyValue);
            return;
        } else if (propertyValue instanceof GeoCircle) {
            convertGeoCircle(graph, propertiesMap, property, (GeoCircle) propertyValue);
            return;
        } else if (propertyValue instanceof StreamingPropertyValue) {
            StreamingPropertyValue streamingPropertyValue = (StreamingPropertyValue) propertyValue;
            if (!streamingPropertyValue.isSearchIndex()) {
                return;
            }

            if (propertyDefinition != null && !propertyDefinition.getTextIndexHints().contains(TextIndexHint.FULL_TEXT)) {
                return;
            }

            Class valueType = streamingPropertyValue.getValueType();
            if (valueType == String.class) {
                InputStream in = streamingPropertyValue.getInputStream();
                propertyValue = IOUtils.toString(in);
            } else {
                throw new VertexiumException("Unhandled StreamingPropertyValue type: " + valueType.getName());
            }
        } else if (propertyValue instanceof String) {
            if (propertyDefinition == null || propertyDefinition.getTextIndexHints().contains(TextIndexHint.EXACT_MATCH)) {
                addPropertyValueToPropertiesMap(propertiesMap, propertyName + EXACT_MATCH_PROPERTY_NAME_SUFFIX, propertyValue);
            }
            if (propertyDefinition == null || propertyDefinition.getTextIndexHints().contains(TextIndexHint.FULL_TEXT)) {
                addPropertyValueToPropertiesMap(propertiesMap, propertyName, propertyValue);
            }
            if (propertyDefinition != null && propertyDefinition.isSortable()) {
                String s = ((String) propertyValue).substring(0, Math.min(100, ((String) propertyValue).length()));
                addPropertyValueToPropertiesMap(propertiesMap, propertyDefinition.getPropertyName(), s);
            }
            return;
        }

        if (propertyValue instanceof DateOnly) {
            propertyValue = ((DateOnly) propertyValue).getDate();
        }

        addPropertyValueToPropertiesMap(propertiesMap, propertyName, propertyValue);
        if (propertyDefinition != null && propertyDefinition.isSortable()) {
            addPropertyValueToPropertiesMap(propertiesMap, propertyDefinition.getPropertyName(), propertyValue);
        }
    }

    protected String deflatePropertyName(Graph graph, Property property) {
        String propertyName = property.getName();
        Visibility propertyVisibility = property.getVisibility();
        return deflatePropertyName(graph, propertyName, propertyVisibility);
    }

    private String deflatePropertyName(Graph graph, String propertyName, Visibility propertyVisibility) {
        String visibilityHash = getVisibilityHash(graph, propertyName, propertyVisibility);
        return this.nameSubstitutionStrategy.deflate(propertyName) + "_" + visibilityHash;
    }

    protected String inflatePropertyName(String string) {
        Matcher m = PROPERTY_NAME_PATTERN.matcher(string);
        if (m.matches()) {
            string = m.group(1);
        }
        return nameSubstitutionStrategy.inflate(string);
    }

    private String inflatePropertyNameWithTypeSuffix(String string) {
        Matcher m = PROPERTY_NAME_PATTERN.matcher(string);
        if (m.matches()) {
            if (m.groupCount() >= 5 && m.group(5) != null) {
                string = m.group(1) + "_" + m.group(5);
            } else {
                string = m.group(1);
            }
        }
        return nameSubstitutionStrategy.inflate(string);
    }

    public String getPropertyVisibilityHashFromDeflatedPropertyName(String deflatedPropertyName) {
        Matcher m = PROPERTY_NAME_PATTERN.matcher(deflatedPropertyName);
        if (m.matches()) {
            return m.group(3);
        }
        throw new VertexiumException("Could not match property name: " + deflatedPropertyName);
    }

    public String getAggregationName(String name) {
        Matcher m = AGGREGATION_NAME_PATTERN.matcher(name);
        if (m.matches()) {
            return m.group(1);
        }
        throw new VertexiumException("Could not get aggregation name from: " + name);
    }

    public String[] getAllMatchingPropertyNames(Graph graph, String propertyName, Authorizations authorizations) {
        Collection<String> hashes = this.propertyNameVisibilitiesStore.getHashes(graph, propertyName, authorizations);
        if (hashes.size() == 0) {
            return new String[0];
        }
        String[] results = new String[hashes.size()];
        String deflatedPropertyName = this.nameSubstitutionStrategy.deflate(propertyName);
        int i = 0;
        for (String hash : hashes) {
            results[i++] = deflatedPropertyName + "_" + hash;
        }
        return results;
    }

    public Collection<String> getQueryablePropertyNames(Graph graph, boolean includeNonStringFields, Authorizations authorizations) {
        Set<String> propertyNames = new HashSet<>();
        for (PropertyDefinition propertyDefinition : graph.getPropertyDefinitions()) {
            List<String> queryableTypeSuffixes = getQueryableTypeSuffixes(propertyDefinition, includeNonStringFields);
            if (queryableTypeSuffixes.size() == 0) {
                continue;
            }
            String inflatedPropertyName = inflatePropertyName(propertyDefinition.getPropertyName()); // could be stored deflated
            String deflatedPropertyName = this.nameSubstitutionStrategy.deflate(inflatedPropertyName);
            if (isReservedFieldName(inflatedPropertyName)) {
                continue;
            }
            for (String hash : this.propertyNameVisibilitiesStore.getHashes(graph, inflatedPropertyName, authorizations)) {
                for (String typeSuffix : queryableTypeSuffixes) {
                    propertyNames.add(deflatedPropertyName + "_" + hash + typeSuffix);
                }
            }
        }
        return propertyNames;
    }

    public Collection<String> getQueryableElementTypeVisibilityPropertyNames(Graph graph, Authorizations authorizations) {
        Set<String> propertyNames = new HashSet<>();
        for (String hash : propertyNameVisibilitiesStore.getHashes(graph, ELEMENT_TYPE_FIELD_NAME, authorizations)) {
            propertyNames.add(ELEMENT_TYPE_FIELD_NAME + "_" + hash);
        }
        if (propertyNames.size() == 0) {
            throw new VertexiumNoMatchingPropertiesException("No queryable " + ELEMENT_TYPE_FIELD_NAME + " for authorizations " + authorizations);
        }
        return propertyNames;
    }

    private List<String> getQueryableTypeSuffixes(PropertyDefinition propertyDefinition, boolean includeNonStringFields) {
        List<String> typeSuffixes = new ArrayList<>();
        if (propertyDefinition.getDataType() == String.class) {
            if (propertyDefinition.getTextIndexHints().contains(TextIndexHint.EXACT_MATCH)) {
                typeSuffixes.add(EXACT_MATCH_PROPERTY_NAME_SUFFIX);
            }
            if (propertyDefinition.getTextIndexHints().contains(TextIndexHint.FULL_TEXT)) {
                typeSuffixes.add("");
            }
        } else if (propertyDefinition.getDataType() == GeoPoint.class
                || propertyDefinition.getDataType() == GeoCircle.class) {
            typeSuffixes.add("");
        } else if (includeNonStringFields && !shouldIgnoreType(propertyDefinition.getDataType())) {
            typeSuffixes.add("");
        }
        return typeSuffixes;
    }

    private String getVisibilityHash(Graph graph, String propertyName, Visibility visibility) {
        return this.propertyNameVisibilitiesStore.getHash(graph, propertyName, visibility);
    }

    @Override
    public void deleteElement(Graph graph, Element element, Authorizations authorizations) {
        String indexName = getIndexName(element);
        String id = element.getId();
        if (MUTATION_LOGGER.isTraceEnabled()) {
            LOGGER.trace("deleting document %s", id);
        }
        getClient().delete(
                getClient()
                        .prepareDelete(indexName, ELEMENT_TYPE, id)
                        .request()
        ).actionGet();
    }

    @Override
    public SearchIndexSecurityGranularity getSearchIndexSecurityGranularity() {
        return SearchIndexSecurityGranularity.PROPERTY;
    }

    @Override
    public GraphQuery queryGraph(Graph graph, String queryString, Authorizations authorizations) {
        return new ElasticSearchSingleDocumentSearchGraphQuery(
                getClient(),
                graph,
                queryString,
                getConfig().getScoringStrategy(),
                getIndexSelectionStrategy(),
                getConfig().getQueryPageSize(),
                authorizations);
    }

    @Override
    public VertexQuery queryVertex(Graph graph, Vertex vertex, String queryString, Authorizations authorizations) {
        return new ElasticSearchSingleDocumentSearchVertexQuery(
                getClient(),
                graph,
                vertex,
                queryString,
                getConfig().getScoringStrategy(),
                getIndexSelectionStrategy(),
                getConfig().getQueryPageSize(),
                authorizations);
    }

    @Override
    public SimilarToGraphQuery querySimilarTo(Graph graph, String[] similarToFields, String similarToText, Authorizations authorizations) {
        return new ElasticSearchSingleDocumentSearchGraphQuery(
                getClient(),
                graph,
                similarToFields,
                similarToText,
                getConfig().getScoringStrategy(),
                getIndexSelectionStrategy(),
                getConfig().getQueryPageSize(),
                authorizations);
    }

    @Override
    public boolean isFieldLevelSecuritySupported() {
        return true;
    }

    protected boolean addPropertyDefinitionToIndex(Graph graph, IndexInfo indexInfo, String propertyName, Visibility propertyVisibility, PropertyDefinition propertyDefinition) throws IOException {
        if (propertyDefinition.getDataType() == String.class) {
            if (propertyDefinition.getTextIndexHints().contains(TextIndexHint.EXACT_MATCH)) {
                addPropertyToIndex(graph, indexInfo, propertyName + EXACT_MATCH_PROPERTY_NAME_SUFFIX, propertyVisibility, String.class, false, propertyDefinition.getBoost(), propertyDefinition.isSortable());
            }
            if (propertyDefinition.getTextIndexHints().contains(TextIndexHint.FULL_TEXT)) {
                addPropertyToIndex(graph, indexInfo, propertyName, propertyVisibility, String.class, true, propertyDefinition.getBoost(), propertyDefinition.isSortable());
            }
            return true;
        }

        if (propertyDefinition.getDataType() == GeoPoint.class
                || propertyDefinition.getDataType() == GeoCircle.class) {
            addPropertyToIndex(graph, indexInfo, propertyName + GEO_PROPERTY_NAME_SUFFIX, propertyVisibility, propertyDefinition.getDataType(), true, propertyDefinition.getBoost(), propertyDefinition.isSortable());
            addPropertyToIndex(graph, indexInfo, propertyName, propertyVisibility, String.class, true, propertyDefinition.getBoost(), propertyDefinition.isSortable());
            return true;
        }

        addPropertyToIndex(graph, indexInfo, propertyName, propertyVisibility, propertyDefinition.getDataType(), true, propertyDefinition.getBoost(), propertyDefinition.isSortable());
        return true;
    }

    protected PropertyDefinition getPropertyDefinition(Graph graph, String propertyName) {
        propertyName = inflatePropertyNameWithTypeSuffix(propertyName);
        return graph.getPropertyDefinition(propertyName);
    }

    public void addPropertyToIndex(Graph graph, IndexInfo indexInfo, Property property) throws IOException {
        // unlike the super class we need to lookup property definitions based on the property name without
        // the hash and define the property that way.
        Object propertyValue = property.getValue();

        PropertyDefinition propertyDefinition = getPropertyDefinition(graph, property.getName());
        if (propertyDefinition != null) {
            String deflatedPropertyName = deflatePropertyName(graph, property);
            addPropertyDefinitionToIndex(graph, indexInfo, deflatedPropertyName, property.getVisibility(), propertyDefinition);
        } else {
            addPropertyToIndexInner(graph, indexInfo, property);
        }

        propertyDefinition = getPropertyDefinition(graph, property.getName() + EXACT_MATCH_PROPERTY_NAME_SUFFIX);
        if (propertyDefinition != null) {
            String deflatedPropertyName = deflatePropertyName(graph, property);
            addPropertyDefinitionToIndex(graph, indexInfo, deflatedPropertyName, property.getVisibility(), propertyDefinition);
        }

        if (propertyValue instanceof GeoShape) {
            propertyDefinition = getPropertyDefinition(graph, property.getName() + GEO_PROPERTY_NAME_SUFFIX);
            if (propertyDefinition != null) {
                String deflatedPropertyName = deflatePropertyName(graph, property);
                addPropertyDefinitionToIndex(graph, indexInfo, deflatedPropertyName, property.getVisibility(), propertyDefinition);
            }
        }
    }

    public void addPropertyToIndexInner(Graph graph, IndexInfo indexInfo, Property property) throws IOException {
        String deflatedPropertyName = deflatePropertyName(graph, property);

        if (indexInfo.isPropertyDefined(deflatedPropertyName, property.getVisibility())) {
            return;
        }

        Class dataType;
        Object propertyValue = property.getValue();
        if (propertyValue instanceof StreamingPropertyValue) {
            StreamingPropertyValue streamingPropertyValue = (StreamingPropertyValue) propertyValue;
            if (!streamingPropertyValue.isSearchIndex()) {
                return;
            }
            dataType = streamingPropertyValue.getValueType();
            addPropertyToIndex(graph, indexInfo, deflatedPropertyName, property.getVisibility(), dataType, true, false);
        } else if (propertyValue instanceof String) {
            dataType = String.class;
            addPropertyToIndex(graph, indexInfo, deflatedPropertyName + EXACT_MATCH_PROPERTY_NAME_SUFFIX, property.getVisibility(), dataType, false, false);
            addPropertyToIndex(graph, indexInfo, deflatedPropertyName, property.getVisibility(), dataType, true, false);
        } else if (propertyValue instanceof GeoPoint) {
            addPropertyToIndex(graph, indexInfo, deflatedPropertyName + GEO_PROPERTY_NAME_SUFFIX, property.getVisibility(), GeoPoint.class, true, false);
            addPropertyToIndex(graph, indexInfo, deflatedPropertyName, property.getVisibility(), String.class, true, false);
        } else if (propertyValue instanceof GeoCircle) {
            addPropertyToIndex(graph, indexInfo, deflatedPropertyName + GEO_PROPERTY_NAME_SUFFIX, property.getVisibility(), GeoCircle.class, true, false);
            addPropertyToIndex(graph, indexInfo, deflatedPropertyName, property.getVisibility(), String.class, true, false);
        } else {
            checkNotNull(propertyValue, "property value cannot be null for property: " + deflatedPropertyName);
            dataType = propertyValue.getClass();
            addPropertyToIndex(graph, indexInfo, deflatedPropertyName, property.getVisibility(), dataType, true, true);
        }
    }

    protected void addPropertyToIndex(
            Graph graph,
            IndexInfo indexInfo,
            String propertyName,
            Visibility propertyVisibility,
            Class dataType,
            boolean analyzed,
            Double boost,
            boolean sortable
    ) throws IOException {
        if (indexInfo.isPropertyDefined(propertyName, propertyVisibility)) {
            return;
        }

        if (shouldIgnoreType(dataType)) {
            return;
        }

        XContentBuilder mapping = XContentFactory.jsonBuilder()
                .startObject()
                .startObject(ELEMENT_TYPE)
                .startObject("properties")
                .startObject(propertyName);

        addTypeToMapping(mapping, propertyName, dataType, analyzed, boost);

        mapping
                .endObject()
                .endObject()
                .endObject()
                .endObject();
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("addPropertyToIndex: %s: %s", dataType.getName(), mapping.string());
        }

        getClient()
                .admin()
                .indices()
                .preparePutMapping(indexInfo.getIndexName())
                .setIgnoreConflicts(false)
                .setType(ELEMENT_TYPE)
                .setSource(mapping)
                .execute()
                .actionGet();

        addPropertyNameVisibility(graph, indexInfo, propertyName, propertyVisibility);
    }

    protected void addPropertyNameVisibility(Graph graph, IndexInfo indexInfo, String propertyName, Visibility propertyVisibility) {
        String inflatedPropertyName = inflatePropertyName(propertyName);
        if (propertyVisibility != null) {
            this.propertyNameVisibilitiesStore.getHash(graph, inflatedPropertyName, propertyVisibility);
        }
        indexInfo.addPropertyNameVisibility(inflatedPropertyName, propertyVisibility);
        indexInfo.addPropertyNameVisibility(propertyName, propertyVisibility);
    }

    public void addElementToBulkRequest(Graph graph, BulkRequest bulkRequest, IndexInfo indexInfo, Element element, Authorizations authorizations) {
        try {
            XContentBuilder json = buildJsonContentFromElement(graph, element, authorizations);
            UpdateRequest indexRequest = new UpdateRequest(indexInfo.getIndexName(), ELEMENT_TYPE, element.getId()).doc(json);
            indexRequest.docAsUpsert(true);
            bulkRequest.add(indexRequest);
        } catch (IOException ex) {
            throw new VertexiumException("Could not add element to bulk request", ex);
        }
    }

    @Override
    public Map<Object, Long> getVertexPropertyCountByValue(Graph graph, String propertyName, Authorizations authorizations) {
        TermFilterBuilder elementTypeFilterBuilder = new TermFilterBuilder(ELEMENT_TYPE_FIELD_NAME, ElasticSearchElementType.VERTEX.getKey());
        FilteredQueryBuilder queryBuilder = QueryBuilders.filteredQuery(
                QueryBuilders.matchAllQuery(),
                elementTypeFilterBuilder
        );
        SearchRequestBuilder q = getClient().prepareSearch(getIndexNamesAsArray())
                .setQuery(queryBuilder)
                .setSearchType(SearchType.COUNT);

        for (String p : getAllMatchingPropertyNames(graph, propertyName, authorizations)) {
            String countAggName = "count-" + p;
            PropertyDefinition propertyDefinition = getPropertyDefinition(graph, p);
            if (propertyDefinition != null && propertyDefinition.getTextIndexHints().contains(TextIndexHint.EXACT_MATCH)) {
                p = p + EXACT_MATCH_PROPERTY_NAME_SUFFIX;
            }

            TermsBuilder countAgg = new TermsBuilder(countAggName)
                    .field(p)
                    .size(500000);
            q = q.addAggregation(countAgg);
        }

        if (ElasticSearchSingleDocumentSearchQueryBase.QUERY_LOGGER.isTraceEnabled()) {
            ElasticSearchSingleDocumentSearchQueryBase.QUERY_LOGGER.trace("query: %s", q);
        }
        SearchResponse response = getClient().search(q.request()).actionGet();
        Map<Object, Long> results = new HashMap<>();
        for (Aggregation agg : response.getAggregations().asList()) {
            Terms propertyCountResults = (Terms) agg;
            for (Terms.Bucket propertyCountResult : propertyCountResults.getBuckets()) {
                String mapKey = propertyCountResult.getKey().toLowerCase();
                Long previousValue = results.get(mapKey);
                if (previousValue == null) {
                    previousValue = 0L;
                }
                results.put(mapKey, previousValue + propertyCountResult.getDocCount());
            }
        }
        return results;
    }

    protected IndexInfo ensureIndexCreatedAndInitialized(String indexName) {
        IndexInfo indexInfo = indexInfos.get(indexName);
        if (indexInfo != null && indexInfo.isElementTypeDefined()) {
            return indexInfo;
        }

        synchronized (indexInfos) {
            if (indexInfo == null) {
                if (!client.admin().indices().prepareExists(indexName).execute().actionGet().isExists()) {
                    try {
                        createIndex(indexName, true);
                    } catch (IOException e) {
                        throw new VertexiumException("Could not create index: " + indexName, e);
                    }
                }

                indexInfo = createIndexInfo(indexName);
                indexInfos.put(indexName, indexInfo);
            }

            ensureMappingsCreated(indexInfo);

            return indexInfo;
        }
    }


    protected IndexInfo createIndexInfo(String indexName) {
        return new IndexInfo(indexName);
    }

    protected void ensureMappingsCreated(IndexInfo indexInfo) {
        if (!indexInfo.isElementTypeDefined()) {
            try {
                XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                        .startObject()
                        .startObject("_source").field("enabled", true).endObject()
                        .startObject("_all").field("enabled", isAllFieldEnabled()).endObject()
                        .startObject("properties");
                createIndexAddFieldsToElementType(mappingBuilder);
                XContentBuilder mapping = mappingBuilder.endObject()
                        .endObject();

                client.admin().indices().preparePutMapping(indexInfo.getIndexName())
                        .setType(ELEMENT_TYPE)
                        .setSource(mapping)
                        .execute()
                        .actionGet();
                indexInfo.setElementTypeDefined(true);
            } catch (Throwable e) {
                throw new VertexiumException("Could not add mappings to index: " + indexInfo.getIndexName(), e);
            }
        }
    }

    protected void createIndexAddFieldsToElementType(XContentBuilder builder) throws IOException {
        builder
                .startObject(ELEMENT_TYPE_FIELD_NAME).field("type", "string").field("store", "true").endObject()
                .startObject(VISIBILITY_FIELD_NAME).field("type", "string").field("analyzer", "keyword").field("index", "not_analyzed").field("store", "true").endObject()
                .startObject(IN_VERTEX_ID_FIELD_NAME).field("type", "string").field("analyzer", "keyword").field("index", "not_analyzed").field("store", "true").endObject()
                .startObject(OUT_VERTEX_ID_FIELD_NAME).field("type", "string").field("analyzer", "keyword").field("index", "not_analyzed").field("store", "true").endObject()
                .startObject(EDGE_LABEL_FIELD_NAME).field("type", "string").field("analyzer", "keyword").field("index", "not_analyzed").field("store", "true").endObject()
        ;
        getConfig().getScoringStrategy().addFieldsToElementType(builder);
    }

    @Override
    public void deleteProperty(Graph graph, Element element, Property property, Authorizations authorizations) {
        deleteProperty(graph, element, property.getKey(), property.getName(), property.getVisibility(), authorizations);
    }

    @Override
    public void deleteProperty(Graph graph, Element element, String propertyKey, String propertyName, Visibility propertyVisibility, Authorizations authorizations) {
        String fieldName = deflatePropertyName(graph, propertyName, propertyVisibility);
        getClient().prepareUpdate()
                .setIndex(getIndexName(element))
                .setId(element.getId())
                .setType(ELEMENT_TYPE)
                .setScript(
                        "ctx._source.remove(fieldName); ctx._source.remove(fieldName + '_e')",
                        ScriptService.ScriptType.INLINE)
                .setScriptParams(ImmutableMap.<String, Object>of("fieldName", fieldName))
                .get();
    }

    @Override
    public void addElements(Graph graph, Iterable<? extends Element> elements, Authorizations authorizations) {
        int totalCount = 0;
        Map<IndexInfo, BulkRequest> bulkRequests = new HashMap<>();
        for (Element element : elements) {
            IndexInfo indexInfo = addPropertiesToIndex(graph, element, element.getProperties());
            BulkRequest bulkRequest = bulkRequests.get(indexInfo);
            if (bulkRequest == null) {
                bulkRequest = new BulkRequest();
                bulkRequests.put(indexInfo, bulkRequest);
            }

            if (bulkRequest.numberOfActions() >= MAX_BATCH_COUNT || bulkRequest.estimatedSizeInBytes() > MAX_BATCH_SIZE) {
                LOGGER.debug("adding elements... %d (est size %d)", bulkRequest.numberOfActions(), bulkRequest.estimatedSizeInBytes());
                totalCount += bulkRequest.numberOfActions();
                doBulkRequest(bulkRequest);
                bulkRequest = new BulkRequest();
                bulkRequests.put(indexInfo, bulkRequest);
            }
            addElementToBulkRequest(graph, bulkRequest, indexInfo, element, authorizations);

            getConfig().getScoringStrategy().addElement(this, graph, bulkRequest, indexInfo, element, authorizations);
        }
        for (BulkRequest bulkRequest : bulkRequests.values()) {
            if (bulkRequest.numberOfActions() > 0) {
                LOGGER.debug("adding elements... %d (est size %d)", bulkRequest.numberOfActions(), bulkRequest.estimatedSizeInBytes());
                totalCount += bulkRequest.numberOfActions();
                doBulkRequest(bulkRequest);
            }
        }
        LOGGER.debug("added %d elements", totalCount);

        if (getConfig().isAutoFlush()) {
            flush();
        }
    }

    @Override
    public MultiVertexQuery queryGraph(Graph graph, String[] vertexIds, String queryString, Authorizations authorizations) {
        return new DefaultMultiVertexQuery(graph, vertexIds, queryString, authorizations);
    }

    @Override
    public boolean isQuerySimilarToTextSupported() {
        return true;
    }

    @Override
    public void flush() {
        client.admin().indices().prepareRefresh(getIndexNamesAsArray()).execute().actionGet();
    }

    protected String[] getIndexNamesAsArray() {
        if (indexInfos.size() == indexInfosLastSize) {
            return indexNamesAsArray;
        }
        synchronized (this) {
            Set<String> keys = indexInfos.keySet();
            indexNamesAsArray = keys.toArray(new String[keys.size()]);
            indexInfosLastSize = indexInfos.size();
            return indexNamesAsArray;
        }
    }

    @Override
    public void shutdown() {
        client.close();

        if (inProcessNode != null) {
            inProcessNode.stop();
            inProcessNode = null;
        }

        if (propertyNameVisibilitiesStore instanceof Closeable) {
            try {
                ((Closeable) propertyNameVisibilitiesStore).close();
            } catch (IOException e) {
                Throwables.propagate(e);
            }
        }
    }

    @SuppressWarnings("unused")
    protected String[] getIndexNames(PropertyDefinition propertyDefinition) {
        return indexSelectionStrategy.getIndexNames(this, propertyDefinition);
    }

    @SuppressWarnings("unused")
    protected String getIndexName(Element element) {
        return indexSelectionStrategy.getIndexName(this, element);
    }

    protected String[] getIndicesToQuery() {
        return indexSelectionStrategy.getIndicesToQuery(this);
    }

    @Override
    public boolean isFieldBoostSupported() {
        return true;
    }

    public IndexInfo addPropertiesToIndex(Graph graph, Element element, Iterable<Property> properties) {
        try {
            String indexName = getIndexName(element);
            IndexInfo indexInfo = ensureIndexCreatedAndInitialized(indexName);
            for (Property property : properties) {
                addPropertyToIndex(graph, indexInfo, property);
            }
            return indexInfo;
        } catch (IOException e) {
            throw new VertexiumException("Could not add properties to index", e);
        }
    }

    protected void addPropertyToIndex(Graph graph, IndexInfo indexInfo, String propertyName, Visibility propertyVisibility, Class dataType, boolean analyzed, boolean sortable) throws IOException {
        addPropertyToIndex(graph, indexInfo, propertyName, propertyVisibility, dataType, analyzed, null, sortable);
    }

    protected String deflatePropertyName(PropertyDefinition propertyDefinition) {
        return deflatePropertyName(propertyDefinition.getPropertyName());
    }

    protected String deflatePropertyName(String propertyName) {
        return nameSubstitutionStrategy.deflate(propertyName);
    }

    protected boolean shouldIgnoreType(Class dataType) {
        return dataType == byte[].class;
    }

    protected void addTypeToMapping(XContentBuilder mapping, String propertyName, Class dataType, boolean analyzed, Double boost) throws IOException {
        if (dataType == String.class) {
            LOGGER.debug("Registering 'string' type for %s", propertyName);
            mapping.field("type", "string");
            if (!analyzed) {
                mapping.field("index", "not_analyzed");
                mapping.field("ignore_above", EXACT_MATCH_IGNORE_ABOVE_LIMIT);
            }
        } else if (dataType == IpV4Address.class) {
            LOGGER.debug("Registering 'ip' type for %s", propertyName);
            mapping.field("type", "ip");
        } else if (dataType == Float.class || dataType == Float.TYPE) {
            LOGGER.debug("Registering 'float' type for %s", propertyName);
            mapping.field("type", "float");
        } else if (dataType == Double.class || dataType == Double.TYPE) {
            LOGGER.debug("Registering 'double' type for %s", propertyName);
            mapping.field("type", "double");
        } else if (dataType == Byte.class || dataType == Byte.TYPE) {
            LOGGER.debug("Registering 'byte' type for %s", propertyName);
            mapping.field("type", "byte");
        } else if (dataType == Short.class || dataType == Short.TYPE) {
            LOGGER.debug("Registering 'short' type for %s", propertyName);
            mapping.field("type", "short");
        } else if (dataType == Integer.class || dataType == Integer.TYPE) {
            LOGGER.debug("Registering 'integer' type for %s", propertyName);
            mapping.field("type", "integer");
        } else if (dataType == Long.class || dataType == Long.TYPE) {
            LOGGER.debug("Registering 'long' type for %s", propertyName);
            mapping.field("type", "long");
        } else if (dataType == Date.class || dataType == DateOnly.class) {
            LOGGER.debug("Registering 'date' type for %s", propertyName);
            mapping.field("type", "date");
        } else if (dataType == Boolean.class || dataType == Boolean.TYPE) {
            LOGGER.debug("Registering 'boolean' type for %s", propertyName);
            mapping.field("type", "boolean");
        } else if (dataType == GeoPoint.class) {
            LOGGER.debug("Registering 'geo_point' type for %s", propertyName);
            mapping.field("type", "geo_point");
        } else if (dataType == GeoCircle.class) {
            LOGGER.debug("Registering 'geo_shape' type for %s", propertyName);
            mapping.field("type", "geo_shape");
            mapping.field("tree", "quadtree");
            mapping.field("precision", "100m");
        } else if (Number.class.isAssignableFrom(dataType)) {
            LOGGER.debug("Registering 'double' type for %s", propertyName);
            mapping.field("type", "double");
        } else {
            throw new VertexiumException("Unexpected value type for property \"" + propertyName + "\": " + dataType.getName());
        }

        if (boost != null) {
            mapping.field("boost", boost.doubleValue());
        }
    }

    protected void doBulkRequest(BulkRequest bulkRequest) {
        BulkResponse response = getClient().bulk(bulkRequest).actionGet();
        if (response.hasFailures()) {
            for (BulkItemResponse bulkResponse : response) {
                if (bulkResponse.isFailed()) {
                    LOGGER.error("Failed to index %s (message: %s)", bulkResponse.getId(), bulkResponse.getFailureMessage());
                }
            }
            throw new VertexiumException("Could not add element.");
        }
    }

    @Override
    public synchronized void truncate() {
        LOGGER.warn("Truncate of Elasticsearch is not possible, dropping the indices and recreating instead.");
        drop();
    }

    @Override
    public void drop() {
        Set<String> indexInfosSet = indexInfos.keySet();
        for (String indexName : indexInfosSet) {
            try {
                DeleteIndexRequest deleteRequest = new DeleteIndexRequest(indexName);
                getClient().admin().indices().delete(deleteRequest).actionGet();
                indexInfos.remove(indexName);
            } catch (Exception ex) {
                throw new VertexiumException("Could not delete index " + indexName, ex);
            }
            ensureIndexCreatedAndInitialized(indexName);
        }
    }

    @SuppressWarnings("unchecked")
    protected void addPropertyValueToPropertiesMap(Map<String, Object> propertiesMap, String propertyName, Object propertyValue) {
        Object existingValue = propertiesMap.get(propertyName);
        if (existingValue == null) {
            propertiesMap.put(propertyName, propertyValue);
            return;
        }

        if (existingValue instanceof List) {
            ((List) existingValue).add(propertyValue);
            return;
        }

        List list = new ArrayList();
        list.add(existingValue);
        list.add(propertyValue);
        propertiesMap.put(propertyName, list);
    }

    protected void convertGeoPoint(Graph graph, XContentBuilder jsonBuilder, Property property, GeoPoint geoPoint) throws IOException {
        Map<String, Object> propertyValueMap = new HashMap<>();
        propertyValueMap.put("lat", geoPoint.getLatitude());
        propertyValueMap.put("lon", geoPoint.getLongitude());
        jsonBuilder.field(deflatePropertyName(graph, property) + GEO_PROPERTY_NAME_SUFFIX, propertyValueMap);
        if (geoPoint.getDescription() != null) {
            jsonBuilder.field(deflatePropertyName(graph, property), geoPoint.getDescription());
        }
    }

    protected void convertGeoPoint(Graph graph, Map<String, Object> propertiesMap, Property property, GeoPoint geoPoint) throws IOException {
        Map<String, Object> propertyValueMap = new HashMap<>();
        propertyValueMap.put("lat", geoPoint.getLatitude());
        propertyValueMap.put("lon", geoPoint.getLongitude());
        addPropertyValueToPropertiesMap(propertiesMap, deflatePropertyName(graph, property) + GEO_PROPERTY_NAME_SUFFIX, propertyValueMap);
        if (geoPoint.getDescription() != null) {
            addPropertyValueToPropertiesMap(propertiesMap, deflatePropertyName(graph, property), geoPoint.getDescription());
        }
    }

    protected void convertGeoCircle(Graph graph, XContentBuilder jsonBuilder, Property property, GeoCircle geoCircle) throws IOException {
        Map<String, Object> propertyValueMap = new HashMap<>();
        propertyValueMap.put("type", "circle");
        List<Double> coordinates = new ArrayList<>();
        coordinates.add(geoCircle.getLongitude());
        coordinates.add(geoCircle.getLatitude());
        propertyValueMap.put("coordinates", coordinates);
        propertyValueMap.put("radius", geoCircle.getRadius() + "km");
        jsonBuilder.field(deflatePropertyName(graph, property) + GEO_PROPERTY_NAME_SUFFIX, propertyValueMap);
        if (geoCircle.getDescription() != null) {
            jsonBuilder.field(deflatePropertyName(graph, property), geoCircle.getDescription());
        }
    }

    protected void convertGeoCircle(Graph graph, Map<String, Object> propertiesMap, Property property, GeoCircle geoCircle) throws IOException {
        Map<String, Object> propertyValueMap = new HashMap<>();
        propertyValueMap.put("type", "circle");
        List<Double> coordinates = new ArrayList<>();
        coordinates.add(geoCircle.getLongitude());
        coordinates.add(geoCircle.getLatitude());
        propertyValueMap.put("coordinates", coordinates);
        propertyValueMap.put("radius", geoCircle.getRadius() + "km");
        addPropertyValueToPropertiesMap(propertiesMap, deflatePropertyName(graph, property) + GEO_PROPERTY_NAME_SUFFIX, propertyValueMap);
        if (geoCircle.getDescription() != null) {
            addPropertyValueToPropertiesMap(propertiesMap, deflatePropertyName(graph, property), geoCircle.getDescription());
        }
    }

    public IndexSelectionStrategy getIndexSelectionStrategy() {
        return indexSelectionStrategy;
    }

    public boolean isAuthorizationFilterEnabled() {
        return getConfig().isAuthorizationFilterEnabled();
    }

    protected boolean isReservedFieldName(String fieldName) {
        return fieldName.equals(ELEMENT_TYPE_FIELD_NAME) || fieldName.equals(VISIBILITY_FIELD_NAME);
    }

    @SuppressWarnings("unused")
    protected void createIndex(String indexName, boolean storeSourceData) throws IOException {
        int shards = getConfig().getNumberOfShards();
        CreateIndexResponse createResponse = client.admin().indices().prepareCreate(indexName)
                .setSettings(ImmutableSettings.settingsBuilder().put("number_of_shards", shards))
                .execute().actionGet();

        ClusterHealthResponse health = client.admin().cluster().prepareHealth(indexName)
                .setWaitForGreenStatus()
                .execute().actionGet();
        LOGGER.debug("Index status: %s", health.toString());
        if (health.isTimedOut()) {
            LOGGER.warn("timed out waiting for green index status, for index: %s", indexName);
        }
    }

    public Client getClient() {
        return client;
    }

    public ElasticSearchSearchIndexConfiguration getConfig() {
        return config;
    }
}
