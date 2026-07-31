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
     * 锁自动过期时间（秒），防止持有者宕机导致死锁。应大于方法最长执行时间。
     *
     * @return 过期秒数
     */
    int ttl() default 30;

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
