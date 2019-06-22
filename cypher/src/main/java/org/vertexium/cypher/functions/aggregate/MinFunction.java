package org.vertexium.cypher.functions.aggregate;

import org.vertexium.cypher.CypherResultRow;
import org.vertexium.cypher.VertexiumCypherQueryContext;
import org.vertexium.cypher.executionPlan.AggregationFunctionInvocationExecutionStep;
import org.vertexium.cypher.executionPlan.ExecutionStepWithResultName;
import org.vertexium.util.ObjectUtils;

import java.util.stream.Stream;

public class MinFunction implements AggregationFunction {
    @Override
    public ExecutionStepWithResultName create(String resultName, boolean distinct, ExecutionStepWithResultName[] argumentsExecutionStep) {
        return new AggregationFunctionInvocationExecutionStep(getClass().getSimpleName(), resultName, distinct, argumentsExecutionStep) {
            @Override
            protected CypherResultRow executeAggregation(VertexiumCypherQueryContext ctx, CypherResultRow group, Stream<RowWithArguments> rows) {
                if (rows == null) {
                    return group.clone()
                        .pushScope(getResultName(), null);
                }

                Object minValue = rows
                    .filter(r -> r.arguments[0] != null)
                    .min((row1, row2) -> ObjectUtils.compare(row1.arguments[0], row2.arguments[0]))
                    .map(r -> r.arguments[0])
                    .orElse(null);
                return group.clone()
                    .pushScope(resultName, minValue);
            }
        };
    }
}
