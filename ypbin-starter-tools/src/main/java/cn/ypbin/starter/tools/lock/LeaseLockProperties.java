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

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 租约锁配置（{@link LeaseLockService} / {@link LeaseLockGuard} 用）。
 *
 * <p><b>为什么不复用 {@code ypbin.tools.lock} 前缀</b>：既有 {@code ypbin.tools.lock.enabled} 控制的是
 * 注解 {@link DistributedLock} 的切面（基于 {@link LockService}），本组配置服务的是**长任务租约锁**
 * 这一套新能力。两者语义与默认值都不同（前者无 TTL/续期概念），共用一个前缀会让「我到底改了哪套」
 * 无从判断。故独立前缀 {@code ypbin.lock}，与既有 {@code ypbin.tools.lock.*} 并存、互不影响。</p>
 *
 * <p>默认值面向「十几秒级的后台任务」：TTL 60 秒（一次任务最坏耗时的上界），
 * 续期间隔 20 秒（TTL 的 1/3，允许两次续期都失败才丢锁）。配置自检保证
 * {@code renew-interval < ttl}，否则锁会在第一次续期之前过期。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
@ConfigurationProperties(prefix = LeaseLockProperties.PREFIX)
public class LeaseLockProperties {

    /** 配置前缀。 */
    public static final String PREFIX = "ypbin.lock";

    /** 是否启用跨实例互斥（关闭 = 退回「每个副本都执行」，仅单实例部署可关）。 */
    private boolean enabled = true;

    /** 锁键前缀（所有任务的锁键都带它，便于在 Redis 里识别与控制）。 */
    private String keyPrefix = "ypbin:lease-lock:";

    /** 租约时长（持锁实例崩溃后，最坏这么久别的实例才能接管）。 */
    private Duration ttl = Duration.ofSeconds(60);

    /** 续期间隔（必须小于 TTL）。 */
    private Duration renewInterval = Duration.ofSeconds(20);

    /**
     * 存储层不可用时是否「继续执行」（fail-open）。
     *
     * <p>默认 {@code true}：这些是幂等的后台任务，Redis 抖动时宁可多跑一轮，也不该让告警评估/保留清理
     * 整体停摆。要严格「只有一个实例执行」时可设为 {@code false}（fail-closed，存储异常直接把任务抛错）。</p>
     */
    private boolean failOpen = true;

    /**
     * 指标名前缀（{@code skipped}/{@code unavailable}/{@code renew.failed} 三个计数器都挂在它下面）。
     *
     * <p>可配是硬要求：库是通用的，不能把宿主应用的域名写死进指标名（否则多应用共用同一注册表时
     * 命名冲突且无法区分来源）。默认 {@code ypbin.lock}，与配置前缀同名便于对照。</p>
     */
    private String metricPrefix = "ypbin.lock";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration getRenewInterval() {
        return renewInterval;
    }

    public void setRenewInterval(Duration renewInterval) {
        this.renewInterval = renewInterval;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public String getMetricPrefix() {
        return metricPrefix;
    }

    public void setMetricPrefix(String metricPrefix) {
        this.metricPrefix = metricPrefix;
    }
}
