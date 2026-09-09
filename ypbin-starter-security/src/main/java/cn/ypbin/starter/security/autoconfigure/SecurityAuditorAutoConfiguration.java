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

import cn.ypbin.starter.data.autoconfigure.DataAutoConfiguration;
import cn.ypbin.starter.data.core.AuditorProvider;
import cn.ypbin.starter.security.core.UserContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 安全 - 数据审计桥接自动配置。
 *
 * <p>仅当 data 模块（{@link AuditorProvider}）存在于 classpath 时生效，
 * 用当前登录用户 ID 覆盖 data 模块的默认空审计人实现，从而让审计字段自动填充能记录真实操作人。</p>
 *
 * <p>审计人取值委托 {@link UserContext} 门面（{@link UserContext#getUserIdSafely()}）：
 * 身份头模式优先（微服务：网关签发身份头，{@code IdentityHeaderFilter} 写入上下文），
 * 无身份头时回退 Sa-Token 会话（单体模式）；异步线程/定时任务等无上下文场景安全返回空，
 * 审计字段自动跳过而非崩溃。业务方自定义的 AuditorProvider 仍可通过
 * {@code @ConditionalOnMissingBean} 覆盖本桥接。</p>
 *
 * <p>用 {@link AutoConfigureBefore} 保证本桥接先于 data 模块注册，其 {@code @ConditionalOnMissingBean} 的
 * 默认空实现随即被跳过。跨自动配置类用 {@code @ConditionalOnMissingBean} 覆盖默认 Bean 时，
 * 必须配合加载顺序注解，否则存在注册顺序竞态。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@AutoConfigureBefore(DataAutoConfiguration.class)
@ConditionalOnClass(AuditorProvider.class)
@ConditionalOnProperty(prefix = "ypbin.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SecurityAuditorAutoConfiguration {

    /**
     * 基于当前登录用户的审计人提供者。
     *
     * <p>委托 {@link UserContext} 安全取值：身份头模式优先、Sa-Token 会话回退，
     * 两种部署形态下都能取到真实操作人 ID（无上下文返回空，不抛异常）。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditorProvider securityAuditorProvider() {
        return UserContext::getUserIdSafely;
    }
}
