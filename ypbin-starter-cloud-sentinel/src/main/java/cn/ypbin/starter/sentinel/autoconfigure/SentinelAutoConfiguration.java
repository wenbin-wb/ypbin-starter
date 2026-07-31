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
package cn.ypbin.starter.sentinel.autoconfigure;

import cn.ypbin.starter.sentinel.handler.RBlockExceptionHandler;
import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Sentinel 增强自动配置。
 *
 * <p>仅在 Servlet Web 应用引入 Sentinel Web 适配器且 {@code ypbin.cloud.sentinel.enabled=true}（默认）
 * 时生效，注册统一 {@link RBlockExceptionHandler}，把限流/降级拒绝响应统一为项目 {@code R} JSON。
 * 限流规则、Dashboard 连接、Nacos 数据源由 spring-cloud-starter-alibaba-sentinel 自身按标准配置装配。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@AutoConfiguration
@ConditionalOnClass({BlockExceptionHandler.class, HttpServletRequest.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "ypbin.cloud.sentinel", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SentinelProperties.class)
public class SentinelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BlockExceptionHandler blockExceptionHandler(ObjectMapper objectMapper, SentinelProperties properties) {
        return new RBlockExceptionHandler(objectMapper, properties.getBlockMessage());
    }
}
