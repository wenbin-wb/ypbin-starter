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
package cn.ypbin.starter.cache.multilevel;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link CacheMetricsRecorder} 的防御性装饰器：埋点异常绝不外溢到缓存主流程。
 *
 * <p><strong>为什么需要它</strong>：{@link CacheMetricsRecorder} 的方法都在
 * {@link MultiLevelCacheService} 的读写主路径上被直接调用，而实现是宿主可自定义的 Bean。
 * 一旦该实现抛异常，本应返回缓存值的调用会变成抛异常、本应成功的写入会被中断——
 * <strong>指标是观测能力、不是业务前置条件</strong>，观测后端故障不得改变缓存调用语义。
 * 接口注释虽已声明「实现不得抛异常」，但契约约束不了第三方实现，故在调用侧补一层硬隔离。</p>
 *
 * <p><strong>不是静默降级/吞异常</strong>：每次被隔离的异常都以 {@code ERROR} 级别带<strong>完整堆栈</strong>
 * 记录（含被调用的方法名与实现类名），指标后端故障仍可从日志与告警中发现；缓存返回值不受影响。</p>
 *
 * <p><strong>只兜 {@link RuntimeException}</strong>：{@code Error}（{@code OutOfMemoryError}、
 * {@code StackOverflowError}、{@code NoClassDefFoundError} 等）是 JVM 级故障，必须原样抛出——
 * 把它当作「埋点小故障」吞掉会掩盖真正的进程级问题。</p>
 *
 * <p>包级可见：它是 {@link MultiLevelCacheService} 的内部实现细节，由构造器统一套用，
 * 宿主不需要也不应该感知。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
final class SafeCacheMetricsRecorder implements CacheMetricsRecorder {

    private static final Logger log = LoggerFactory.getLogger(SafeCacheMetricsRecorder.class);

    private final CacheMetricsRecorder delegate;

    SafeCacheMetricsRecorder(CacheMetricsRecorder delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate 不能为空");
    }

    @Override
    public void l1Hit() {
        safeCall("l1Hit", delegate::l1Hit);
    }

    @Override
    public void l1Miss() {
        safeCall("l1Miss", delegate::l1Miss);
    }

    @Override
    public void l2Hit() {
        safeCall("l2Hit", delegate::l2Hit);
    }

    @Override
    public void l2Miss() {
        safeCall("l2Miss", delegate::l2Miss);
    }

    @Override
    public void nullSentinelHit() {
        safeCall("nullSentinelHit", delegate::nullSentinelHit);
    }

    @Override
    public void load() {
        safeCall("load", delegate::load);
    }

    private void safeCall(String method, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.error("[ypbin-starter] 多级缓存埋点异常已隔离（不影响缓存调用语义）: method={}, recorder={}, cause={}",
                method, delegate.getClass().getName(), ex.getMessage(), ex);
        }
    }
}
