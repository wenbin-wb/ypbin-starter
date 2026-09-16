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
package cn.ypbin.starter.tracking.aspect;

import cn.ypbin.starter.core.util.LogSanitizer;
import cn.ypbin.starter.tracking.annotation.Tracked;
import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Tracked} 切面：采集被标注方法的成功/失败与耗时。
 *
 * <p>事件在 {@code finally} 中记录，因此方法抛异常时同样会得到一条 {@code success=false} 的事件，
 * 且异常本身<strong>原样向上抛出</strong>——埋点不改变业务语义。</p>
 *
 * <p>切面自身的任何失败都被吞进日志与计数（与 {@link TrackRecorder} 的契约一致），
 * 绝不让埋点问题变成业务故障。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
@Aspect
public class TrackedAspect {

    private static final Logger log = LoggerFactory.getLogger(TrackedAspect.class);

    private final TrackRecorder recorder;

    private final @Nullable String appId;

    /**
     * 创建切面。
     *
     * @param recorder 采集门面
     * @param appId    应用标识；为空表示不写该维度
     */
    public TrackedAspect(TrackRecorder recorder, @Nullable String appId) {
        this.recorder = recorder;
        this.appId = appId;
    }

    /**
     * 环绕通知：记录耗时与结果。
     *
     * @param joinPoint 连接点
     * @param tracked   注解实例
     * @return 原方法返回值
     * @throws Throwable 原方法抛出的任何异常，原样透传
     */
    @Around("@annotation(tracked)")
    public Object around(ProceedingJoinPoint joinPoint, Tracked tracked) throws Throwable {
        long startedAt = System.nanoTime();
        boolean success = true;
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            success = false;
            throw ex;
        } finally {
            recordQuietly(tracked.value(), System.nanoTime() - startedAt, success);
        }
    }

    private void recordQuietly(String eventCode, long elapsedNanos, boolean success) {
        try {
            long durationMillis = Duration.ofNanos(elapsedNanos).toMillis();
            // 后端切面没有 HTTP 请求上下文（IP/UA 无法获取），故用少参构造器显式表达「不带请求维度」
            recorder.record(new TrackEvent(UUID.randomUUID().toString(), eventCode, Instant.now(), appId,
                null, null, null, null, durationMillis, success, Map.of()));
        } catch (RuntimeException ex) {
            log.error("[ypbin-starter] @Tracked aspect failed to record event: {}",
                LogSanitizer.sanitize(eventCode), ex);
        }
    }
}
