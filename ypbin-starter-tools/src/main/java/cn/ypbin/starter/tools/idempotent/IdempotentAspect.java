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

/**
 * 幂等切面。
 *
 * <p>拦截 {@link Idempotent} 方法，占位成功放行，命中重复抛 {@link IdempotentException}。
 * 幂等键支持 SpEL；未指定时用「目标类名 + 方法名 + 参数指纹」。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@Aspect
public class IdempotentAspect {

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
        return point.proceed();
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
