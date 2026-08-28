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
package cn.ypbin.starter.apicrypto.advice;

import cn.ypbin.starter.apicrypto.annotation.ApiEncrypt;
import cn.ypbin.starter.apicrypto.core.ApiCryptoProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

/**
 * 请求解密切面。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@RestControllerAdvice
public class ApiDecryptRequestAdvice implements RequestBodyAdvice {

    private final ApiCryptoProvider cryptoProvider;

    public ApiDecryptRequestAdvice(ApiCryptoProvider cryptoProvider) {
        this.cryptoProvider = cryptoProvider;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
        Class<? extends HttpMessageConverter<?>> converterType) {
        ApiEncrypt annotation = methodParameter.getMethodAnnotation(ApiEncrypt.class);
        return annotation != null && annotation.requestDecrypt();
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
        Type targetType, Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        String cipher = new String(inputMessage.getBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (cipher.isEmpty()) {
            return inputMessage;
        }
        String plain = cryptoProvider.decrypt(cipher);
        byte[] plainBytes = plain.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = inputMessage.getHeaders();
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(plainBytes);
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
        Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
        Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }
}
