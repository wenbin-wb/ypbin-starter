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
package cn.ypbin.starter.apicrypto;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.apicrypto.autoconfigure.ApiCryptoAutoConfiguration;
import cn.ypbin.starter.apicrypto.autoconfigure.ApiCryptoProperties;
import cn.ypbin.starter.apicrypto.core.AesApiCryptoProvider;
import org.junit.jupiter.api.Test;

/**
 * API 加解密自动装配与配置测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class ApiCryptoTest {

    @Test
    void propertiesShouldExposeDefaults() {
        ApiCryptoProperties props = new ApiCryptoProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(ApiCryptoProperties.PREFIX).isEqualTo("ypbin.api-crypto");
        props.setKey("0123456789abcdef0123456789abcdef");
        assertThat(props.getKey()).hasSize(32);
    }

    @Test
    void autoConfigurationShouldBuildProvider() {
        ApiCryptoProperties props = new ApiCryptoProperties();
        props.setKey("0123456789abcdef0123456789abcdef");
        ApiCryptoAutoConfiguration config = new ApiCryptoAutoConfiguration();
        assertThat(config.apiCryptoProvider(props)).isInstanceOf(AesApiCryptoProvider.class);
    }

    static class DemoApi {
        @cn.ypbin.starter.apicrypto.annotation.ApiEncrypt(requestDecrypt = true)
        public void encrypted() {
        }

        @cn.ypbin.starter.apicrypto.annotation.ApiEncrypt(requestDecrypt = false)
        public void plain() {
        }
    }

    @Test
    void decryptAdviceShouldSupportAnnotatedMethods() throws Exception {
        ApiCryptoProperties props = new ApiCryptoProperties();
        props.setKey("0123456789abcdef0123456789abcdef");
        AesApiCryptoProvider provider = new AesApiCryptoProvider(props.getKey());
        cn.ypbin.starter.apicrypto.advice.ApiDecryptRequestAdvice advice =
            new cn.ypbin.starter.apicrypto.advice.ApiDecryptRequestAdvice(provider);
        org.springframework.core.MethodParameter encrypted = org.springframework.core.MethodParameter
            .forExecutable(DemoApi.class.getMethod("encrypted"), -1);
        org.springframework.core.MethodParameter plain = org.springframework.core.MethodParameter
            .forExecutable(DemoApi.class.getMethod("plain"), -1);
        assertThat(advice.supports(encrypted, null, null)).isTrue();
        assertThat(advice.supports(plain, null, null)).isFalse();
    }

    @Test
    void decryptAdviceShouldDecryptBody() throws Exception {
        ApiCryptoProperties props = new ApiCryptoProperties();
        props.setKey("0123456789abcdef0123456789abcdef");
        AesApiCryptoProvider provider = new AesApiCryptoProvider(props.getKey());
        cn.ypbin.starter.apicrypto.advice.ApiDecryptRequestAdvice advice =
            new cn.ypbin.starter.apicrypto.advice.ApiDecryptRequestAdvice(provider);
        String cipher = provider.encrypt("{\"a\":1}");
        org.springframework.http.HttpInputMessage input =
            new org.springframework.http.HttpInputMessage() {
                @Override
                public java.io.InputStream getBody() {
                    return new java.io.ByteArrayInputStream(cipher.getBytes());
                }

                @Override
                public org.springframework.http.HttpHeaders getHeaders() {
                    return new org.springframework.http.HttpHeaders();
                }
            };
        org.springframework.http.HttpInputMessage result = advice.beforeBodyRead(
            input, null, null, null);
        assertThat(result).isNotNull();
    }

    @Test
    void encryptResponseAdviceShouldSupportAnnotatedMethods() throws Exception {
        ApiCryptoProperties props = new ApiCryptoProperties();
        props.setKey("0123456789abcdef0123456789abcdef");
        AesApiCryptoProvider provider = new AesApiCryptoProvider(props.getKey());
        cn.ypbin.starter.apicrypto.advice.ApiEncryptResponseAdvice advice =
            new cn.ypbin.starter.apicrypto.advice.ApiEncryptResponseAdvice(provider,
                tools.jackson.databind.json.JsonMapper.builder().build());
        org.springframework.core.MethodParameter encrypted = org.springframework.core.MethodParameter
            .forExecutable(DemoApi.class.getMethod("encrypted"), -1);
        assertThat(advice.supports(encrypted, null)).isTrue();
    }

    @Test
    void aesProviderShouldRoundTrip() {
        ApiCryptoProperties props = new ApiCryptoProperties();
        props.setKey("0123456789abcdef0123456789abcdef");
        AesApiCryptoProvider provider = new AesApiCryptoProvider(props.getKey());
        String cipher = provider.encrypt("敏感内容");
        assertThat(provider.decrypt(cipher)).isEqualTo("敏感内容");
    }
}
