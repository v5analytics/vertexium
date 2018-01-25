package org.vertexium.cypher.functions.scalar;

import org.vertexium.cypher.VertexiumCypherQueryContext;
import org.vertexium.cypher.VertexiumCypherScope;
import org.vertexium.cypher.ast.model.CypherAstBase;
import org.vertexium.cypher.exceptions.VertexiumCypherTypeErrorException;
import org.vertexium.cypher.executor.ExpressionScope;
import org.vertexium.cypher.functions.CypherFunction;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LengthFunction extends CypherFunction {
    @Override
    public Object invoke(VertexiumCypherQueryContext ctx, CypherAstBase[] arguments, ExpressionScope scope) {
        assertArgumentCount(arguments, 1);
        Object arg0 = ctx.getExpressionExecutor().executeExpression(ctx, arguments[0], scope);

        if (arg0 instanceof Collection) {
            arg0 = ((Collection) arg0).stream();
        }

        if (arg0 instanceof Stream) {
            return ((Stream<?>) arg0)
                    .map(this::getLength)
                    .collect(Collectors.toList());
        }

        return getLength(arg0);
    }

    private Object getLength(Object arg0) {
        if (arg0 == null) {
            return null;
        }

        if (arg0 instanceof VertexiumCypherScope.PathItem) {
            return ((VertexiumCypherScope.PathItem) arg0).getLength();
        }

        if (arg0 instanceof String) {
            return ((String) arg0).length();
        }

        throw new VertexiumCypherTypeErrorException(arg0, VertexiumCypherScope.PathItem.class, String.class, null);
    }
}
