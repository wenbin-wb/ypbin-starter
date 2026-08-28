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
package cn.ypbin.starter.gateway.handler;

import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关统一异常处理器。
 *
 * <p>将 Gateway/WebFlux 层未捕获异常统一转换为 {@link R} JSON 响应，避免向客户端暴露
 * Spring WebFlux 默认错误结构或 HTML 错误页。遵循项目约定：HTTP 状态统一 200，错误类型由
 * {@link R#getCode()} 区分。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@Order(-2)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GatewayExceptionHandler(ObjectMapper objectMapper) {
        if (objectMapper != null) {
            this.objectMapper = objectMapper;
            this.objectMapper.findAndRegisterModules();
        } else {
            this.objectMapper = new ObjectMapper().findAndRegisterModules();
        }
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        R<Void> body = resolveBody(ex);
        if (body.getCode() >= GlobalErrorCode.INTERNAL_ERROR.getCode()) {
            log.error("[网关异常] {}", exchange.getRequest().getURI(), ex);
        } else {
            log.warn("[网关异常] {} -> {}", exchange.getRequest().getURI(), ex.getMessage());
        }
        byte[] bytes = toJsonBytes(body);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    R<Void> resolveBody(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ResponseStatusException statusException) {
                return resolveByStatusCode(statusException.getStatusCode().value(), statusException.getReason());
            }
            if (current instanceof ErrorResponse errorResponse) {
                String reason = errorResponse.getBody() != null ? errorResponse.getBody().getDetail() : null;
                return resolveByStatusCode(errorResponse.getStatusCode().value(), reason);
            }
            if (current.getCause() == null || current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        String msg = ex.getMessage();
        if (msg != null && (msg.contains("404 NOT_FOUND") || msg.contains("No static resource") || msg.contains("Not Found"))) {
            return R.fail(GlobalErrorCode.NOT_FOUND.getCode(), "接口不存在");
        }
        return R.fail(GlobalErrorCode.INTERNAL_ERROR);
    }

    private R<Void> resolveByStatusCode(int statusCode, String reason) {
        if (statusCode == 404) {
            return R.fail(GlobalErrorCode.NOT_FOUND.getCode(), "接口不存在");
        }
        if (statusCode == 401) {
            return R.fail(GlobalErrorCode.UNAUTHORIZED);
        }
        if (statusCode == 403) {
            return R.fail(GlobalErrorCode.FORBIDDEN);
        }
        if (statusCode >= 400 && statusCode < 500) {
            return R.fail(GlobalErrorCode.BAD_REQUEST.getCode(), reason != null ? reason : "请求参数错误");
        }
        return R.fail(GlobalErrorCode.INTERNAL_ERROR);
    }

    private byte[] toJsonBytes(R<Void> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            log.error("[网关异常] 响应序列化异常", e);
            String json = String.format("{\"code\":%d,\"message\":\"%s\",\"success\":%s}",
                body.getCode(), body.getMessage() != null ? body.getMessage() : "系统内部错误", body.isSuccess());
            return json.getBytes(StandardCharsets.UTF_8);
        }
    }
}
