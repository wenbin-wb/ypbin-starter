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
package cn.ypbin.starter.apicrypto.autoconfigure;

import cn.ypbin.starter.apicrypto.core.ApiCryptoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;

/**
 * 接口加解密「已启用但无加解密器」的启动期告警。
 *
 * <p>{@code ApiCryptoAutoConfiguration} 的加解密 Advice 带 {@code @ConditionalOnBean
 * (ApiCryptoProvider.class)}，而默认实现仅在配置了 {@code ypbin.api-crypto.key} 时装配。本模块默认启用，
 * 使用者只加依赖不配 key 时，{@code @ApiEncrypt}/{@code @ApiDecrypt} 全程不生效（请求明文进出）却毫无提示。
 * 本配置负责把这种「能力未生效」显式说出来（禁静默不生效）。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "ypbin.api-crypto", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ApiCryptoMissingProviderAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ApiCryptoMissingProviderAutoConfiguration.class);

    ApiCryptoMissingProviderAutoConfiguration(ObjectProvider<ApiCryptoProvider> providerProvider) {
        if (providerProvider.getIfAvailable() == null) {
            log.warn("[ypbin-starter] 接口加解密已启用（ypbin.api-crypto.enabled=true）但容器中没有 "
                + "ApiCryptoProvider，@ApiEncrypt/@ApiDecrypt 不会生效（接口仍以明文收发）；"
                + "请配置 ypbin.api-crypto.key 或提供 ApiCryptoProvider Bean。");
        }
    }
}
