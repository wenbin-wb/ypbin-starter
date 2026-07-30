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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解。
 *
 * <p>标注在接口方法上，在 {@link #window()} 秒内最多允许 {@link #count()} 次调用，
 * 超限抛出 {@link RateLimitException}。限流键默认按“方法 + 客户端 IP”生成，
 * 可通过 {@link #key()} 指定业务维度键，支持 SpEL 表达式。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流键（为空时用目标方法全限定名）。
     *
     * <p>支持 SpEL 表达式，可引用方法入参，如 {@code key = "#userId"} 或
     * {@code key = "#user.id"}，实现按用户维度限流。表达式以 {@code #} 或 {@code T(} 开头
     * 时按 SpEL 求值，否则作为静态字符串。</p>
     *
     * @return 键（支持 SpEL）
     */
    String key() default "";

    /**
     * 时间窗口（秒）。
     *
     * @return 窗口秒数
     */
    int window() default 1;

    /**
     * 窗口内允许的最大次数。
     *
     * @return 最大次数
     */
    int count() default 10;

    /**
     * 是否将客户端 IP 纳入限流键。
     *
     * @return 是否按 IP 限流
     */
    boolean byIp() default true;

    /**
     * 超限提示信息。
     *
     * @return 提示信息
     */
    String message() default "请求过于频繁，请稍后再试";
}
