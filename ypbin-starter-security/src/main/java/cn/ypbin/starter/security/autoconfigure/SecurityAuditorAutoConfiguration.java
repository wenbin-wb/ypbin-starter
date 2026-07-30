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

import cn.ypbin.starter.data.core.AuditorProvider;
import cn.ypbin.starter.security.core.LoginHelper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 安全 - 数据审计桥接自动配置。
 *
 * <p>仅当 data 模块（{@link AuditorProvider}）存在于 classpath 时生效，
 * 用当前登录用户 ID 覆盖 data 模块的默认空审计人实现，从而让审计字段自动填充
 * 能记录真实操作人。标记 {@link Primary} 以优先于 data 模块的默认 Bean。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(AuditorProvider.class)
@ConditionalOnProperty(prefix = "ypbin.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityAuditorAutoConfiguration {

    /**
     * 基于登录会话的审计人提供者。
     */
    @Bean
    @Primary
    public AuditorProvider securityAuditorProvider() {
        return LoginHelper::getUserIdSafely;
    }
}
