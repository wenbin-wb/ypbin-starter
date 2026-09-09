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
package cn.ypbin.starter.tools.limiter;

import cn.ypbin.starter.tools.support.RequestUtils;
import java.lang.reflect.Method;
import java.time.Duration;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * 限流切面。
 *
 * <p>拦截 {@link RateLimit} 方法，按配置的窗口与阈值通过 {@link RateLimiterStore} 计数，
 * 超限抛出 {@link RateLimitException}。限流键支持 SpEL 表达式，可按方法入参（如用户 ID）
 * 动态生成。</p>
 *
 * <p><strong>{@code byIp} 限流的 IP 取值：</strong>默认只取真实对端地址
 * （{@code request.getRemoteAddr()}），不信任可伪造的 X-Forwarded-For 等转发头；确经可信
 * 反向代理清洗过转发头时，通过 {@code ypbin.tools.rate-limit.trust-forwarded=true} 开启。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@Aspect
public class RateLimitAspect {

    private final RateLimiterStore store;
    private final boolean trustForwarded;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public RateLimitAspect(RateLimiterStore store) {
        this(store, false);
    }

    /**
     * @param store          限流计数存储
     * @param trustForwarded {@code byIp} 限流是否信任转发头（false 时只取真实对端地址，防伪造绕过）
     */
    public RateLimitAspect(RateLimiterStore store, boolean trustForwarded) {
        this.store = store;
        this.trustForwarded = trustForwarded;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        String key = buildKey(point, rateLimit);
        long current = store.incrementAndGet(key, Duration.ofSeconds(rateLimit.window()));
        if (current > rateLimit.count()) {
            throw new RateLimitException(rateLimit.message());
        }
        return point.proceed();
    }

    private String buildKey(ProceedingJoinPoint point, RateLimit rateLimit) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        String prefix;
        String rawKey = rateLimit.key();
        if (rawKey.isBlank()) {
            // 用目标类（而非代理类）的真实名，避免 CGLIB 代理下拿到 xxx$$SpringCGLIB$$ 名称
            prefix = point.getTarget().getClass().getName() + "#" + method.getName();
        } else if (isSpel(rawKey)) {
            prefix = evaluateSpel(rawKey, method, point.getArgs());
        } else {
            prefix = rawKey;
        }
        StringBuilder sb = new StringBuilder("ypbin:rate:").append(prefix);
        if (rateLimit.byIp()) {
            sb.append(':').append(RequestUtils.getClientIp(trustForwarded));
        }
        return sb.toString();
    }

    private boolean isSpel(String key) {
        return key.startsWith("#") || key.startsWith("T(") || key.contains("#");
    }

    private String evaluateSpel(String expression, Method method, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        Expression exp = expressionParser.parseExpression(expression);
        Object value = exp.getValue(context);
        return String.valueOf(value);
    }
}
