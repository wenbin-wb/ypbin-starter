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
package cn.ypbin.starter.messaging.autoconfigure;

import cn.ypbin.starter.messaging.sse.SseUserIdResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE「已启用但未注册订阅端点」的启动期告警。
 *
 * <p>内置订阅/换票端点带 {@code @ConditionalOnBean(SseUserIdResolver.class)}（由 security 模块提供）：
 * 宿主开启 {@code ypbin.sse.enabled=true} 但未引入对应模块时，端点不会注册，调用方只会看到 404 且启动期
 * 无任何提示。本配置负责把这种「能力未生效」显式说出来（禁静默不生效）。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
@AutoConfiguration
@ConditionalOnClass(SseEmitter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "ypbin.sse", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "ypbin.sse", name = "register-endpoint", havingValue = "true",
    matchIfMissing = true)
public class SseMissingUserIdResolverAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SseMissingUserIdResolverAutoConfiguration.class);

    SseMissingUserIdResolverAutoConfiguration(ObjectProvider<SseUserIdResolver> userIdResolver) {
        if (userIdResolver.getIfAvailable() == null) {
            log.warn("[ypbin-starter] 已开启 ypbin.sse.enabled=true 且 register-endpoint=true，但容器中没有 "
                + "SseUserIdResolver，内置 SSE 订阅/换票端点不会注册（订阅请求将返回 404）；"
                + "请引入 ypbin-starter-security 或自行提供 SseUserIdResolver Bean。");
        }
    }
}
