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

import cn.ypbin.starter.log.core.LogClientProvider;
import cn.ypbin.starter.security.core.UserContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 安全 - 操作日志客户端桥接自动配置。
 *
 * <p>仅当 log 模块（{@link LogClientProvider}）存在于 classpath 时生效，
 * 用当前登录会话中的客户端信息填充操作日志的客户端字段，让审计能记录“从哪个客户端、用什么方式登录”。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@AutoConfiguration
@ConditionalOnClass(LogClientProvider.class)
@ConditionalOnProperty(prefix = "ypbin.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityLogClientAutoConfiguration {

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
