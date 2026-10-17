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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式锁注解。
 *
 * <p>标注在方法上，进入方法前尝试加锁，方法执行完自动释放。典型用于多实例下 {@code @Scheduled}
 * 定时任务防重（同一时刻只有一个节点执行）、防重提交等场景。</p>
 *
 * <p>加锁失败时的行为由 {@link #waitTime()} 与 {@link #failStrategy()} 决定：默认不等待、失败即跳过
 * （适合定时任务——抢不到锁说明别的节点在跑，本节点直接放弃）。</p>
 *
 * <p><strong>默认边界：方法执行时间应显著短于 {@link #ttl()}</strong>。默认（{@link #autoRenew()} 为
 * {@code false}）本注解<strong>不续期</strong>：外层方法一旦执行超过 {@code ttl}，锁会在存储中过期；
 * 此时同线程的嵌套重入<strong>不会重新校验</strong>，内层调用仍直接进入临界区——互斥已经失效
 * （重入语义的固有取舍）。需要跑得比 TTL 更久时，可开启 {@link #autoRenew()} 让框架在后台续期，
 * 或改用 {@link LeaseLockGuard} 租约锁（可注入时钟、带指标、抢不到即跳过），不要靠调大 {@code ttl} 硬撑。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 锁键（为空时用目标方法全限定名）。支持 SpEL，可引用方法入参，如 {@code key = "#orderId"}。
     *
     * @return 锁键（支持 SpEL）
     */
    String key() default "";

    /**
     * 锁自动过期时间（秒），防止持有者宕机导致死锁。
     *
     * <p>默认（{@link #autoRenew()} 为 {@code false}）下<strong>必须显著大于方法最长执行时间</strong>：
     * 执行一旦超过 {@code ttl}，锁会在存储中过期；同线程的嵌套重入不再重新校验（内层直接进入临界区），
     * 互斥随之失效。开启 {@link #autoRenew()} 后 TTL 会被周期性推后，方法即可安全地执行超过 {@code ttl}。</p>
     *
     * @return 过期秒数
     */
    int ttl() default 30;

    /**
     * 是否开启自动续期（默认关闭，与既有语义一致）。
     *
     * <p>开启后，框架在成功持锁期间按 {@link #renewIntervalSeconds()} 周期性地「仅当锁仍属于本持有者」
     * 时把 TTL 推后（原子操作，不重建锁），使方法可以安全地执行超过 {@link #ttl()}。续期由独立的
     * 单线程守护调度器承担，按锁实例注册、方法正常结束或抛异常退出时**一定注销**。</p>
     *
     * <p><b>续期失败不假装持锁</b>：锁被其它实例接管（或存储不可用）时记 WARN 并立即停止续期，
     * 不会静默继续；此后本方法虽仍会跑完，但其临界区不再受互斥保护。</p>
     *
     * <p><b>何时不用它</b>：几分钟以上的后台长任务仍推荐 {@link LeaseLockGuard} 租约锁——它把到期时刻
     * 编码进锁值、可注入时钟、带 {@code skipped/unavailable/renew.failed} 指标，并且能配合业务策略
     * （fail-open/跳过）表达语义。</p>
     *
     * @return 是否自动续期
     */
    boolean autoRenew() default false;

    /**
     * 自动续期间隔（秒）；仅在 {@link #autoRenew()} 为 {@code true} 时生效。
     *
     * <p>{@code 0}（默认）表示按 {@code ttl/3} 计算（不足 1 秒时取 1 秒），即允许连续两次续期都失败
     * 才真正丢锁。显式取值时必须<strong>严格小于</strong> {@link #ttl()}，否则锁会在第一次续期发生之前
     * 过期、多副本并发执行——该配置错误在首次调用时抛 {@link IllegalStateException}，不静默降级。</p>
     *
     * @return 续期间隔秒数；{@code 0} 表示按 {@code ttl/3} 计算
     */
    int renewIntervalSeconds() default 0;

    /**
     * 加锁失败时的最大等待时间（秒）。为 0 表示不等待、立即返回。
     *
     * @return 等待秒数
     */
    int waitTime() default 0;

    /**
     * 等待期间的重试间隔（毫秒）。
     *
     * @return 重试间隔毫秒
     */
    long retryInterval() default 100;

    /**
     * 加锁失败（含等待超时）后的处理策略。
     *
     * @return 失败策略
     */
    FailStrategy failStrategy() default FailStrategy.SKIP;

    /**
     * 加锁失败提示信息（{@link FailStrategy#EXCEPTION} 时用）。
     *
     * @return 提示信息
     */
    String message() default "未获取到分布式锁，操作被跳过";

    /**
     * 加锁失败策略。
     */
    enum FailStrategy {
        /** 静默跳过，方法不执行，返回 null */
        SKIP,
        /** 抛出 {@link LockAcquireException} */
        EXCEPTION
    }
}
