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
package cn.ypbin.starter.core.util;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 请求链路 ID 工具。
 *
 * <p>链路 ID 会写入日志 MDC、响应头并透传到下游，因此来自客户端的值属不可信输入：
 * 必须限制长度并过滤控制字符，避免日志注入（CRLF/ANSI 转义伪造日志行）与超长值污染日志。
 * 不合法或缺失时统一生成新的链路 ID。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
public final class RequestIdUtils {

    /** 允许的最大长度：覆盖常见 trace/request id，超出视为不可信 */
    private static final int MAX_LENGTH = 128;

    private RequestIdUtils() {
    }

    /**
     * 生成一个不含连字符的随机链路 ID。
     *
     * @return 链路 ID
     */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 校验并返回可信的链路 ID；不合法时返回 {@code null} 由调用方生成新值。
     *
     * @param candidate 客户端传入的链路 ID
     * @return 合法链路 ID，或 {@code null}
     */
    @Nullable
    public static String sanitize(String candidate) {
        if (candidate == null) {
            return null;
        }
        String trimmed = candidate.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            // 仅允许可见 ASCII：阻断 CR/LF（日志注入）、控制字符与 ANSI 转义
            if (ch < 0x21 || ch > 0x7E) {
                return null;
            }
        }
        return trimmed;
    }

    /**
     * 返回可信链路 ID，缺失或不合法时生成新值。
     *
     * @param candidate 客户端传入的链路 ID
     * @return 可信链路 ID
     */
    public static String sanitizeOrGenerate(String candidate) {
        String sanitized = sanitize(candidate);
        return sanitized != null ? sanitized : generate();
    }
}
