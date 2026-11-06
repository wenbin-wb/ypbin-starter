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
package cn.ypbin.starter.log.support;

import java.util.Locale;
import java.util.Set;

/**
 * 日志敏感字段名判定。
 *
 * <p>用于操作日志的查询参数、请求头与请求/响应体兜底脱敏：即使业务 DTO 忘记标注
 * {@code @LogMask}，字段名命中凭据语义时也不会把明文写进日志或上报到远端。</p>
 *
 * <p>判定按字段名包含关系（忽略大小写），宁可多掩码不可漏掩码；需要保留明文的非常规字段，
 * 请改名避开这些关键字。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
public final class SensitiveFieldNames {

    /** 敏感字段名关键字（小写）。 */
    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
        "password", "passwd", "pwd", "token", "authorization", "cookie",
        "secret", "apikey", "api_key", "accesskey", "access_key",
        "privatekey", "private_key", "credential", "sessionid", "session_id"
    );

    private SensitiveFieldNames() {
    }

    /**
     * 判断字段名/参数名/请求头名是否属于敏感信息。
     *
     * @param name 字段名、参数名或请求头名（可为 {@code null}）
     * @return 命中任一敏感关键字返回 {@code true}
     */
    public static boolean matches(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String normalized = name.toLowerCase(Locale.ROOT).replace("-", "_");
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
