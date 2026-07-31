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
package cn.ypbin.starter.sentinel.handler;

import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

/**
 * Web 接口被 Sentinel 限流/降级时的统一响应处理器。
 *
 * <p>把 Sentinel 默认的纯文本拒绝响应替换为项目统一的 {@link R} JSON。遵循项目约定：HTTP 状态返回
 * 200，错误类型由 {@link R#getCode()} 区分（此处为 {@link GlobalErrorCode#TOO_MANY_REQUESTS}）。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class RBlockExceptionHandler implements BlockExceptionHandler {

    private final ObjectMapper objectMapper;

    private final String blockMessage;

    public RBlockExceptionHandler(ObjectMapper objectMapper, String blockMessage) {
        this.objectMapper = objectMapper;
        this.blockMessage = blockMessage;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, String resourceName,
                       BlockException e) throws Exception {
        R<Void> body = R.fail(GlobalErrorCode.TOO_MANY_REQUESTS.getCode(), blockMessage);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
