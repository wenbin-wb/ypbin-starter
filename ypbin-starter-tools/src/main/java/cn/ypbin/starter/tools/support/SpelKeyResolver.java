/*
 * Copyright (c) 2024-present ypbin-starter authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ypbin.starter.tools.support;

import java.lang.reflect.Method;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * SpEL 键解析工具。
 *
 * <p>供限流、幂等等注解共享：将 key 表达式在方法入参上下文中求值。若表达式不含 SpEL
 * 特征（{@code #} / {@code T(}），则原样返回作为静态键。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class SpelKeyResolver {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private SpelKeyResolver() {
    }

    /**
     * 解析键表达式。
     *
     * @param expression 键（可能是 SpEL，也可能是静态字符串）
     * @param method     目标方法
     * @param args       方法入参
     * @return 求值后的键
     */
    public static String resolve(String expression, Method method, Object[] args) {
        if (expression == null || expression.isBlank()) {
            return expression;
        }
        if (!isSpel(expression)) {
            return expression;
        }
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] paramNames = NAME_DISCOVERER.getParameterNames(method);
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        Expression exp = PARSER.parseExpression(expression);
        Object value = exp.getValue(context);
        return String.valueOf(value);
    }

    private static boolean isSpel(String key) {
        return key.contains("#") || key.startsWith("T(");
    }
}
