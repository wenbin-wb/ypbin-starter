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
package cn.ypbin.starter.tools.lock;

import cn.ypbin.starter.tools.support.SpelKeyResolver;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 分布式锁切面。
 *
 * <p>拦截 {@link DistributedLock} 方法：进入前尝试加锁（可配等待/重试），成功则执行并在结束后释放，
 * 失败按策略跳过（返回 null）或抛出 {@link LockAcquireException}。持有者用唯一 UUID，释放时校验，
 * 保证只释放自己持有的锁。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@Aspect
public class DistributedLockAspect {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockAspect.class);

    private final LockService lockService;

    public DistributedLockAspect(LockService lockService) {
        this.lockService = lockService;
    }

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint point, DistributedLock distributedLock) throws Throwable {
        String key = buildKey(point, distributedLock);
        String owner = UUID.randomUUID().toString();
        Duration ttl = Duration.ofSeconds(distributedLock.ttl());

        boolean locked = acquire(key, owner, ttl, distributedLock);
        if (!locked) {
            if (distributedLock.failStrategy() == DistributedLock.FailStrategy.EXCEPTION) {
                throw new LockAcquireException(distributedLock.message());
            }
            log.debug("[ypbin-starter] 未获取到分布式锁，跳过执行：key={}", key);
            return null;
        }

        try {
            return point.proceed();
        } finally {
            if (!lockService.unlock(key, owner)) {
                // 释放失败通常意味着锁已因 TTL 过期（方法执行超过 ttl），提示业务方调大 ttl
                log.warn("[ypbin-starter] 释放分布式锁失败（可能已超时过期）：key={}, ttl={}s", key, distributedLock.ttl());
            }
        }
    }

    private boolean acquire(String key, String owner, Duration ttl, DistributedLock lock) throws InterruptedException {
        if (lockService.tryLock(key, owner, ttl)) {
            return true;
        }
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(lock.waitTime()).toMillis();
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(lock.retryInterval());
            if (lockService.tryLock(key, owner, ttl)) {
                return true;
            }
        }
        return false;
    }

    private String buildKey(ProceedingJoinPoint point, DistributedLock lock) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        String raw = lock.key();
        String suffix;
        if (raw.isBlank()) {
            // 用目标类真实名，避免 CGLIB 代理下拿到 xxx$$SpringCGLIB$$ 名称
            suffix = point.getTarget().getClass().getName() + "#" + method.getName();
        } else {
            suffix = SpelKeyResolver.resolve(raw, method, point.getArgs());
        }
        return "ypbin:lock:" + suffix;
    }
}
