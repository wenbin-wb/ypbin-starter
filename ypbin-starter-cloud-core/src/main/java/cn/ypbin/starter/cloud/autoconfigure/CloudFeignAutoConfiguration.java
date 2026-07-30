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
package cn.ypbin.starter.cloud.autoconfigure;

import cn.ypbin.starter.cloud.feign.FeignHeaderInterceptor;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 微服务 Feign 增强自动配置。
 *
 * <p>仅在引入 OpenFeign 且 {@code ypbin.cloud.feign.enabled=true}（默认）时生效。装配请求头透传
 * 拦截器，把上游认证/链路/租户头带给下游服务。业务方可提供自定义 {@link RequestInterceptor}
 * 覆盖或叠加。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
@ConditionalOnProperty(prefix = "ypbin.cloud.feign", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FeignProperties.class)
public class CloudFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FeignHeaderInterceptor.class)
    public FeignHeaderInterceptor feignHeaderInterceptor(FeignProperties properties) {
        return new FeignHeaderInterceptor(properties.getPropagateHeaders());
    }
}
