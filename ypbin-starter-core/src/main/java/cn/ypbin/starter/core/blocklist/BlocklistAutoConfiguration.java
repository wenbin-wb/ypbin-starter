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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 访问黑名单核心自动配置。
 *
 * <p>只装配与运行环境无关的匹配器/编码器/IP 解析器；存储实现由宿主模块按需提供：
 * starter-security 提供内存/Redis 实现，starter-cloud-gateway 提供响应式 Redis 实现。
 * 默认关闭，宿主显式开启后生效。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
@AutoConfiguration
@AutoConfigureBefore(name = {
    "cn.ypbin.starter.web.autoconfigure.WebAutoConfiguration",
    "cn.ypbin.starter.gateway.autoconfigure.GatewayAutoConfiguration"
})
@ConditionalOnProperty(prefix = BlocklistProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(BlocklistProperties.class)
public class BlocklistAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BlocklistMatcher blocklistMatcher() {
        return new BlocklistMatcher();
    }

    @Bean
    @ConditionalOnMissingBean
    public BlocklistRuleCodec blocklistRuleCodec() {
        return new BlocklistRuleCodec();
    }

    /**
     * 保留旧方法签名以维持二进制兼容；实际 Bean 由 {@link ClientIpResolverAutoConfiguration}
     * 在未启用黑名单时也注册，这里不再声明 {@code @Bean}。
     *
     * @param properties 黑名单配置
     * @return 客户端 IP 解析器
     * @deprecated 注入容器中的 {@link ClientIpResolver}，不要直接调用本方法
     */
    @Deprecated(since = "4.4.0", forRemoval = false)
    public ClientIpResolver clientIpResolver(BlocklistProperties properties) {
        return new ClientIpResolver(properties.getTrustedProxies());
    }
}
