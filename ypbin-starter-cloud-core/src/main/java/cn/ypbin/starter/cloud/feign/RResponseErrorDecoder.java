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
package cn.ypbin.starter.cloud.feign;

import cn.ypbin.starter.cloud.exception.FeignRemoteException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.util.StringUtils;

/**
 * 统一响应体 Feign 错误解码器。
 *
 * <p>当下游返回非 2xx 时，优先尝试按 {@link R} 解析响应体；若是 ypbin 统一错误结构，保留下游
 * 业务码与提示。无法解析时回退到 Feign 默认异常，避免吞掉底层诊断信息。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class RResponseErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    private final ErrorDecoder delegate = new Default();

    public RResponseErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        String body = readBody(response);
        if (!StringUtils.hasText(body)) {
            return delegate.decode(methodKey, response);
        }
        R<?> result = parseResult(body);
        if (result == null) {
            return delegate.decode(methodKey, rebuildResponse(response, body));
        }
        String message = StringUtils.hasText(result.getMessage())
            ? result.getMessage()
            : GlobalErrorCode.INTERNAL_ERROR.getMessage();
        return new FeignRemoteException(result.getCode(), message, response.status(), methodKey);
    }

    private R<?> parseResult(String body) {
        try {
            return objectMapper.readValue(body, R.class);
        } catch (IOException ignored) {
            return null;
        }
    }

    private String readBody(Response response) {
        if (response == null || response.body() == null) {
            return null;
        }
        try (InputStream inputStream = response.body().asInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return null;
        }
    }

    private Response rebuildResponse(Response response, String body) {
        return response.toBuilder()
            .body(body, StandardCharsets.UTF_8)
            .build();
    }
}
