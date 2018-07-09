package org.vertexium.elasticsearch5.scoring;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScriptScoreFunctionBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.vertexium.Graph;
import org.vertexium.VertexiumException;
import org.vertexium.elasticsearch5.Elasticsearch5SearchIndex;
import org.vertexium.query.QueryParameters;
import org.vertexium.scoring.HammingDistanceScoringStrategy;
import org.vertexium.util.IOUtils;

import java.util.HashMap;
import java.util.List;

public class ElasticsearchHammingDistanceScoringStrategy
        extends HammingDistanceScoringStrategy
        implements ElasticsearchScoringStrategy {
    private final String scriptSrc;

    public ElasticsearchHammingDistanceScoringStrategy(String field, String hash) {
        super(field, hash);
        try {
            scriptSrc = IOUtils.toString(getClass().getResourceAsStream("hamming-distance.painless"));
        } catch (Exception ex) {
            throw new VertexiumException("Could not load painless script", ex);
        }
    }

    @Override
    public QueryBuilder updateElasticsearchQuery(
            Graph graph,
            Elasticsearch5SearchIndex searchIndex,
            QueryBuilder query,
            QueryParameters queryParameters
    ) {
        List<String> fieldNames = getFieldNames(graph, searchIndex, queryParameters, getField());
        if (fieldNames == null) {
            return query;
        }

        HashMap<String, Object> scriptParams = new HashMap<>();
        scriptParams.put("hash", getHash());
        scriptParams.put("fieldNames", fieldNames);
        Script script = new Script(ScriptType.INLINE, "painless", scriptSrc, scriptParams);
        return QueryBuilders.functionScoreQuery(query, new ScriptScoreFunctionBuilder(script));
    }
}
