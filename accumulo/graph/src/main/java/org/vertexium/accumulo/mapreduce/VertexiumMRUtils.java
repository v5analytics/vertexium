package org.vertexium.accumulo.mapreduce;

import org.apache.hadoop.conf.Configuration;

import java.util.HashMap;
import java.util.Map;

@Deprecated
public class VertexiumMRUtils {
    public static final String CONFIG_AUTHORIZATIONS = "authorizations";

    @SuppressWarnings("unchecked")
    public static Map toMap(Configuration configuration) {
        Map map = new HashMap();
        for (Map.Entry<String, String> entry : configuration) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }
}
