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
package cn.ypbin.starter.security.autoconfigure;

import cn.ypbin.starter.messaging.autoconfigure.SseAutoConfiguration;
import cn.ypbin.starter.messaging.sse.SseUserIdResolver;
import cn.ypbin.starter.security.core.LoginHelper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 安全 - SSE 订阅用户解析桥接自动配置。
 *
 * <p>仅当 messaging 模块存在于 classpath 时生效，为内置 SSE 订阅端点提供基于登录态的用户解析：
 * 端点据此用<strong>当前登录用户</strong>建立连接，而非信任前端传参，杜绝越权订阅。</p>
 *
 * <p>用 {@link LoginHelper#getUserIdSafely()} 取值（无上下文线程安全），与操作日志、审计填充的取值方式一致。
 * {@code @ConditionalOnMissingBean}，业务方可覆盖。</p>
 *
 * <p>{@code @AutoConfigureBefore(SseAutoConfiguration.class)}：messaging 的订阅/换票端点以
 * {@code @ConditionalOnBean(SseUserIdResolver)} 为条件，而 {@code @ConditionalOnBean} 对注册顺序敏感——
 * 必须让本配置先注册 resolver，messaging 评估条件时才能发现它，否则端点不生成（No mapping）。</p>
 *
 * @author wenbin
 * @since 2026-08-03
 */
@AutoConfiguration
@AutoConfigureBefore(SseAutoConfiguration.class)
@ConditionalOnClass(SseUserIdResolver.class)
@ConditionalOnProperty(prefix = "ypbin.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecuritySseAutoConfiguration {

    /**
     * 基于 Sa-Token 登录态的 SSE 订阅用户解析：用当前登录用户 ID 作为订阅标识。
     */
    @Bean
    @ConditionalOnMissingBean
    public SseUserIdResolver securitySseUserIdResolver() {
        return () -> LoginHelper.getUserIdSafely().map(String::valueOf);
    }
}
