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
import cn.ypbin.starter.core.model.R;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.ObjectMapper;

/**
 * 响应体加密 Advice。
 *
 * <p>对标注 {@link ApiEncrypt} 且 {@code responseEncrypt=true} 的接口，将序列化后的响应体
 * 加密为 Base64 密文再返回。为保持统一响应结构，若返回体是 {@link R}，仅加密其 data 部分并
 * 原样保留 code/message；其它类型整体加密为字符串。</p>
 *
 * <p><strong>响应契约（客户端须按成败两种格式解析）：</strong></p>
 * <ul>
 *     <li><strong>成功路径</strong>：返回仍是统一 {@link R} 结构，但 {@code data} 已替换为
 *     密文 {@code String}（Base64），前端须先解密 data 再使用；非 {@link R} 返回体则整体加密为密文字符串。</li>
 *     <li><strong>失败路径</strong>：{@code data} 为 {@code null} 的 {@link R}（业务失败/异常由全局处理器产出）
 *     保持明文返回（未加密），前端按明文 {@link R} 解析 {@code code/message} 即可——失败分支不在本 Advice
 *     加密范围内。</li>
 * </ul>
 * <p>因此前端不应假设"标注了 {@code @ApiEncrypt} 的接口响应恒为密文"，需先按 {@code code} 判断成败，
 * 再决定解密还是直读明文。本类不改动上述既有行为，仅在此固化契约说明。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@RestControllerAdvice
public class ApiEncryptResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ApiCryptoProvider cryptoProvider;
    private final ObjectMapper objectMapper;

    public ApiEncryptResponseAdvice(ApiCryptoProvider cryptoProvider, ObjectMapper objectMapper) {
        this.cryptoProvider = cryptoProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType,
        Class<? extends HttpMessageConverter<?>> converterType) {
        ApiEncrypt annotation = returnType.getMethodAnnotation(ApiEncrypt.class);
        return annotation != null && annotation.responseEncrypt();
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
        Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
        ServerHttpResponse response) {
        try {
            if (body instanceof R<?> r) {
                // 仅加密 data，保留统一响应结构
                if (r.getData() != null) {
                    String dataJson = objectMapper.writeValueAsString(r.getData());
                    R<String> encrypted = R.ok(r.getMessage(), cryptoProvider.encrypt(dataJson));
                    encrypted.setCode(r.getCode());
                    encrypted.setSuccess(r.isSuccess());
                    return encrypted;
                }
                return body;
            }
            String json = objectMapper.writeValueAsString(body);
            return cryptoProvider.encrypt(json);
        } catch (Exception e) {
            throw new IllegalStateException("接口响应加密失败", e);
        }
    }
}
