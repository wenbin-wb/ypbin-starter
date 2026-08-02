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

import cn.ypbin.starter.log.autoconfigure.LogAutoConfiguration;
import cn.ypbin.starter.log.core.LogClientProvider;
import cn.ypbin.starter.log.core.LogUserProvider;
import cn.ypbin.starter.security.core.LoginHelper;
import cn.ypbin.starter.security.core.UserContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 安全 - 操作日志客户端桥接自动配置。
 *
 * <p>仅当 log 模块存在于 classpath 时生效，把当前登录信息桥接给操作日志：
 * <ul>
 *     <li>{@link LogUserProvider} —— 操作人 userId（来自 Sa-Token 登录态），让日志能记录“谁做的”；</li>
 *     <li>{@link LogClientProvider} —— 客户端信息，让日志能记录“从哪个客户端、用什么方式登录”。</li>
 * </ul>
 * 两者均 {@code @ConditionalOnMissingBean}，业务方可覆盖。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@AutoConfiguration
@AutoConfigureBefore(LogAutoConfiguration.class)
@ConditionalOnClass(LogClientProvider.class)
@ConditionalOnProperty(prefix = "ypbin.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityLogClientAutoConfiguration {

    /**
     * 基于 Sa-Token 登录态的操作人提供者：用当前登录用户 ID 填充操作日志的 userId。
     *
     * <p>用 {@link LoginHelper#getUserIdSafely()} 取值（不依赖业务方是否写入 LoginUser，且无上下文线程安全），
     * 与 data 模块 AuditorProvider 的取值方式一致。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public LogUserProvider securityLogUserProvider() {
        return LoginHelper::getUserIdSafely;
    }

    /**
     * 基于登录会话的日志客户端提供者。
     */
    @Bean
    @ConditionalOnMissingBean
    public LogClientProvider securityLogClientProvider() {
        return () -> UserContext.getLoginUser().map(user -> new LogClientProvider.LogClientInfo(
            user.getClientId(), user.getClientType(), user.getAuthType()));
    }
}
