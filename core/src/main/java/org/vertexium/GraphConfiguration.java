package org.vertexium;

import org.vertexium.id.IdGenerator;
import org.vertexium.id.UUIDIdGenerator;
import org.vertexium.metric.DropWizardMetricRegistry;
import org.vertexium.metric.VertexiumMetricRegistry;
import org.vertexium.search.DefaultSearchIndex;
import org.vertexium.search.SearchIndex;
import org.vertexium.util.ConfigurationUtils;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GraphConfiguration {
    public static final String IDGENERATOR_PROP_PREFIX = "idgenerator";
    public static final String SEARCH_INDEX_PROP_PREFIX = "search";
    public static final String AUTO_FLUSH = "autoFlush";

    public static final String DEFAULT_IDGENERATOR = UUIDIdGenerator.class.getName();
    public static final String DEFAULT_SEARCH_INDEX = DefaultSearchIndex.class.getName();
    public static final boolean DEFAULT_AUTO_FLUSH = false;
    public static final String TABLE_NAME_PREFIX = "tableNamePrefix";
    public static final String DEFAULT_TABLE_NAME_PREFIX = "vertexium";
    public static final String SERIALIZER = "serializer";
    public static final String DEFAULT_SERIALIZER = JavaVertexiumSerializer.class.getName();
    public static final String METRICS_REGISTRY = "metricsRegistry";
    public static final String DEFAULT_METRICS_REGISTRY = DropWizardMetricRegistry.class.getName();
    public static final String STRICT_TYPING = "strictTyping";
    public static final boolean DEFAULT_STRICT_TYPING = false;
    public static final String CREATE_TABLES = "createTables";
    public static final boolean DEFAULT_CREATE_TABLES = true;

    private final Map<String, Object> config;

    public GraphConfiguration(Map<String, Object> config) {
        this.config = config;
    }

    public void set(String key, Object value) {
        this.config.put(key, value);
    }

    public Map getConfig() {
        return config;
    }

    @SuppressWarnings("unused")
    public Object getConfig(String key, Object defaultValue) {
        Object o = getConfig().get(key);
        if (o == null) {
            return defaultValue;
        }
        return o;
    }

    public IdGenerator createIdGenerator(Graph graph) throws VertexiumException {
        return ConfigurationUtils.createProvider(graph, this, IDGENERATOR_PROP_PREFIX, DEFAULT_IDGENERATOR);
    }

    public SearchIndex createSearchIndex(Graph graph) throws VertexiumException {
        return ConfigurationUtils.createProvider(graph, this, SEARCH_INDEX_PROP_PREFIX, DEFAULT_SEARCH_INDEX);
    }

    public VertexiumSerializer createSerializer(Graph graph) throws VertexiumException {
        return ConfigurationUtils.createProvider(graph, this, SERIALIZER, DEFAULT_SERIALIZER);
    }

    public VertexiumSerializer createSerializer() throws VertexiumException {
        return ConfigurationUtils.createProvider(null, this, SERIALIZER, DEFAULT_SERIALIZER);
    }

    public VertexiumMetricRegistry createMetricsRegistry() {
        return ConfigurationUtils.createProvider(null, this, METRICS_REGISTRY, DEFAULT_METRICS_REGISTRY);
    }

    public boolean getBoolean(String configKey, boolean defaultValue) {
        Object obj = config.get(configKey);
        if (obj == null) {
            return defaultValue;
        }
        if (obj instanceof String) {
            return Boolean.parseBoolean((String) obj);
        }
        if (obj instanceof Boolean) {
            return (boolean) obj;
        }
        return Boolean.valueOf(obj.toString());
    }

    public double getDouble(String configKey, double defaultValue) {
        Object obj = config.get(configKey);
        if (obj == null) {
            return defaultValue;
        }
        if (obj instanceof String) {
            return Double.parseDouble((String) obj);
        }
        if (obj instanceof Double) {
            return (double) obj;
        }
        return Double.valueOf(obj.toString());
    }

    public int getInt(String configKey, int defaultValue) {
        Object obj = config.get(configKey);
        if (obj == null) {
            return defaultValue;
        }
        if (obj instanceof String) {
            return Integer.parseInt((String) obj);
        }
        if (obj instanceof Integer) {
            return (int) obj;
        }
        return Integer.valueOf(obj.toString());
    }

    public Integer getInteger(String configKey, Integer defaultValue) {
        Object obj = config.get(configKey);
        if (obj == null) {
            return defaultValue;
        }
        if (obj instanceof String) {
            return Integer.parseInt((String) obj);
        }
        if (obj instanceof Integer) {
            return (int) obj;
        }
        return Integer.valueOf(obj.toString());
    }

    public Duration getDuration(String key, String defaultValue) {
        String value = getString(key, defaultValue);
        try {
            return Duration.parse(value);
        } catch (DateTimeParseException ex) {
            Matcher m = Pattern.compile("(\\d+)(\\D+)").matcher(value);
            if (!m.matches()) {
                throw ex;
            }
            long digits = Long.parseLong(m.group(1));
            String units = m.group(2);
            switch (units) {
                case "ms":
                    return Duration.ofMillis(digits);
                case "s":
                    return Duration.ofSeconds(digits);
                case "m":
                    return Duration.ofMinutes(digits);
                case "h":
                    return Duration.ofHours(digits);
                default:
                    throw new VertexiumException("unhandled duration units: " + value);
            }
        }
    }

    public long getConfigLong(String key, long defaultValue) {
        Object obj = config.get(key);
        if (obj == null) {
            return defaultValue;
        }
        if (obj instanceof String) {
            return Integer.parseInt((String) obj);
        }
        if (obj instanceof Long) {
            return (long) obj;
        }
        return Long.valueOf(obj.toString());
    }

    public String getString(String configKey, String defaultValue) {
        Object str = config.get(configKey);
        if (str == null) {
            return defaultValue;
        }
        if (str instanceof String) {
            return ((String) str).trim();
        }
        return str.toString().trim();
    }

    public String getTableNamePrefix() {
        return getString(TABLE_NAME_PREFIX, DEFAULT_TABLE_NAME_PREFIX);
    }

    public boolean isStrictTyping() {
        return getBoolean(STRICT_TYPING, DEFAULT_STRICT_TYPING);
    }

    public boolean isCreateTables() {
        return getBoolean(CREATE_TABLES, DEFAULT_CREATE_TABLES);
    }
}
