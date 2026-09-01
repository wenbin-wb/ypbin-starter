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
package cn.ypbin.starter.cache.annotation;

import cn.ypbin.starter.cache.util.CacheUtils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link CacheEvict} 缓存失效切面。
 *
 * <p>拦截标注 {@link CacheEvict} 的方法，**执行成功后**按 SpEL 表达式解析缓存键并删除。
 * 方法抛异常（含事务回滚）时不删——数据未真正变更，保留旧缓存是正确语义。</p>
 *
 * <p>本切面由 {@code CacheAutoConfiguration} 注册为 Bean（starter 类不在宿主组件扫描范围）。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@Aspect
public class CacheEvictAspect {

    private static final Logger log = LoggerFactory.getLogger(CacheEvictAspect.class);

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAMETER_DISCOVERER = new DefaultParameterNameDiscoverer();

    @AfterReturning("@annotation(cacheEvict)")
    public void evict(JoinPoint joinPoint, CacheEvict cacheEvict) {
        List<String> keys = resolveKeys(joinPoint, cacheEvict);
        if (keys.isEmpty()) {
            return;
        }
        // 事务场景：等事务提交后再删缓存，避免并发读在事务提交前回源到旧数据并永久缓存
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CacheUtils.delete(keys);
                }
            });
        } else {
            CacheUtils.delete(keys);
        }
    }

    /**
     * 解析 {@link CacheEvict} 的 SpEL 键表达式为实际缓存键（包内可见，便于单测）。
     */
    List<String> resolveKeys(JoinPoint joinPoint, CacheEvict cacheEvict) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        EvaluationContext context = new MethodBasedEvaluationContext(
            joinPoint.getTarget(), method, args, PARAMETER_DISCOVERER);

        List<String> keys = new ArrayList<>(cacheEvict.keys().length);
        for (String expression : cacheEvict.keys()) {
            try {
                // SpEL 表达式：前缀用单引号字符串字面量（冒号在字符串内合法），#参数名 引用方法参数
                Object value = PARSER.parseExpression(expression).getValue(context);
                if (value != null) {
                    keys.add(value.toString());
                }
            } catch (RuntimeException e) {
                // 表达式解析失败：暴露问题，不静默吞（铁律：错误要暴露）
                log.error("[ypbin-starter] @CacheEvict SpEL 解析失败: method={}, expression={}, args={}, cause={}",
                    method.getName(), expression, Arrays.toString(args), e.getMessage(), e);
            }
        }
        return keys;
    }
}
