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
package cn.ypbin.starter.tools.idempotent;

import cn.ypbin.starter.tools.support.SpelKeyResolver;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 幂等切面。
 *
 * <p>拦截 {@link Idempotent} 方法，占位成功放行，命中重复抛 {@link IdempotentException}。
 * 幂等键支持 SpEL；未指定时用「目标类名 + 方法名 + 参数指纹」。</p>
 *
 * <p><strong>用户维度边界</strong>：本模块（tools）不依赖 security，无法在默认键中自动拼入当前
 * 用户维度——同一窗口内不同用户对同参方法的调用会互相拦截。需要在用户间隔离防重的场景，
 * 由宿主在 {@link Idempotent#key()} 的 SpEL 中显式带上用户维度（如 {@code #userId} 或当前用户标识）。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@Aspect
public class IdempotentAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);

    private final IdempotentStore store;

    public IdempotentAspect(IdempotentStore store) {
        this.store = store;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint point, Idempotent idempotent) throws Throwable {
        String key = buildKey(point, idempotent);
        if (!store.tryAcquire(key, Duration.ofSeconds(idempotent.interval()))) {
            throw new IdempotentException(idempotent.message());
        }
        try {
            return point.proceed();
        } catch (Throwable e) {
            // 业务执行失败：释放占位键，允许客户端立即重试（成功路径保留窗口防重复提交）；
            // 释放失败只告警（占位会在窗口到期后自动过期），不掩盖业务异常
            try {
                store.release(key);
            } catch (RuntimeException releaseException) {
                log.warn("[ypbin-starter] 幂等占位释放失败，等待窗口到期自动释放：{}", key, releaseException);
            }
            throw e;
        }
    }

    private String buildKey(ProceedingJoinPoint point, Idempotent idempotent) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        String suffix;
        String rawKey = idempotent.key();
        if (rawKey.isBlank()) {
            // 用目标类（非代理类）+ 方法 + 参数指纹
            suffix = point.getTarget().getClass().getName() + "#" + method.getName()
                + ":" + Arrays.deepHashCode(point.getArgs());
        } else {
            suffix = SpelKeyResolver.resolve(rawKey, method, point.getArgs());
        }
        return "ypbin:idem:" + suffix;
    }
}
