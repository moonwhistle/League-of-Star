package com.sang.leagueofstar.redis.lock.parser;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * SpEL(Spring Expression Language)을 파싱하여 동적인 값을 추출하는 유틸리티입니다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CustomSpringELParser {

    public static Object getDynamicValue(String[] parameterNames, Object[] args, String key) {
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();

        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        return parser.parseExpression(key).getValue(context, Object.class);
    }
}
