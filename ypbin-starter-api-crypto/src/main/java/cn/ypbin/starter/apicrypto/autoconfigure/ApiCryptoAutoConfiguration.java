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

import cn.ypbin.starter.apicrypto.advice.ApiDecryptRequestAdvice;
import cn.ypbin.starter.apicrypto.advice.ApiEncryptResponseAdvice;
import cn.ypbin.starter.apicrypto.core.AesApiCryptoProvider;
import cn.ypbin.starter.apicrypto.core.ApiCryptoProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 接口加解密自动配置。
 *
 * <p>仅在 Servlet Web 环境且 {@code ypbin.api-crypto.enabled=true}（默认）时生效。
 * 配置了 {@code ypbin.api-crypto.key} 时装配默认 AES 加解密器；业务方也可提供
 * {@link ApiCryptoProvider} Bean 覆盖（接国密 SM4 / RSA 等）。请求/响应 Advice 仅在存在
 * {@link ApiCryptoProvider} 时注册。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "ypbin.api-crypto", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ApiCryptoProperties.class)
public class ApiCryptoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ypbin.api-crypto", name = "key")
    public ApiCryptoProvider apiCryptoProvider(ApiCryptoProperties properties) {
        return new AesApiCryptoProvider(properties.getKey());
    }

    @Bean
    @ConditionalOnBean(ApiCryptoProvider.class)
    public ApiDecryptRequestAdvice apiDecryptRequestAdvice(ApiCryptoProvider provider) {
        return new ApiDecryptRequestAdvice(provider);
    }

    @Bean
    @ConditionalOnBean(ApiCryptoProvider.class)
    public ApiEncryptResponseAdvice apiEncryptResponseAdvice(ApiCryptoProvider provider,
        ObjectMapper objectMapper) {
        return new ApiEncryptResponseAdvice(provider, objectMapper);
    }
}
