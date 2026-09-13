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
package cn.ypbin.starter.core.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Spring 7 原生方法级弹性能力自动配置。
 *
 * <p>Spring Framework 7 把重试与并发限制内建到 {@code spring-context}
 * （{@code org.springframework.resilience.annotation}），无需再引入 Spring Retry 或
 * Resilience4j 即可对任意 Spring Bean 方法声明弹性策略：</p>
 * <ul>
 *     <li>{@code @Retryable}：按异常类型重试，支持 {@code maxRetries}/{@code delay}/{@code multiplier}/
 *         {@code jitter}/{@code maxDelay}/{@code timeout}，可用 {@code includes}/{@code excludes}
 *         精确圈定异常，或用 {@code predicate} 自定义判定；</li>
 *     <li>{@code @ConcurrencyLimit}：限制同一方法的并发执行数，超出时按 {@code policy}
 *         拒绝或阻塞，用于保护下游脆弱依赖。</li>
 * </ul>
 *
 * <p>本配置默认开启（{@code ypbin.resilience.enabled} 缺省为 true），可显式关闭。注解驱动意味着
 * 未使用这两个注解的应用不会被代理，无额外开销。注意：与所有 Spring AOP 能力一样，
 * 同类内部自调用不经过代理，弹性注解不生效，须跨 Bean 调用。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
@AutoConfiguration
@ConditionalOnClass(EnableResilientMethods.class)
@ConditionalOnProperty(prefix = "ypbin.resilience", name = "enabled", havingValue = "true",
    matchIfMissing = true)
@EnableResilientMethods
public class ResilienceAutoConfiguration {
}
