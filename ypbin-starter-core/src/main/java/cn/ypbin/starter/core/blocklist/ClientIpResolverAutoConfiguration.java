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
package cn.ypbin.starter.core.blocklist;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 客户端 IP 解析器自动配置。
 *
 * <p>与黑名单总开关解耦：{@link ClientIpResolver} 是通用的可信代理解析工具，宿主即使在
 * 未启用黑名单的服务里也需要用它与过滤器保持同一套 IP 口径（如登录失败计数、审计、限流 key）。</p>
 *
 * @author wenbin
 * @since 2026-11-05
 */
@AutoConfiguration
@AutoConfigureBefore(name = {
    "cn.ypbin.starter.web.autoconfigure.WebAutoConfiguration",
    "cn.ypbin.starter.gateway.autoconfigure.GatewayAutoConfiguration"
})
@EnableConfigurationProperties(BlocklistProperties.class)
public class ClientIpResolverAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ClientIpResolver clientIpResolver(BlocklistProperties properties) {
        return new ClientIpResolver(properties.getTrustedProxies());
    }
}
