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

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * 日志字段脱敏工具：把不可信（用户可控）的值安全地写进日志。
 *
 * <p><strong>为什么需要</strong>：把用户可控的原始值直接拼进日志会造成
 * <em>日志注入（log injection / log forging）</em>——攻击者用换行符在日志里伪造出额外的日志行，
 * 让审计与告警被污染（例如伪造一条「登录成功」）。典型入口是 URL、请求头、查询参数、文件名、
 * 检索词、AccessKey 等。</p>
 *
 * <p>本工具做两件事：① 把换行/制表/控制字符替换为 {@code _}，杜绝跨行伪造；
 * ② 限制单字段长度，避免超长输入把日志撑爆。</p>
 *
 * <p>用法：{@code log.warn("[x] key={}", LogSanitizer.sanitize(accessKey));}</p>
 *
 * <p>注意：{@link #sanitize} 只解决「日志注入」，不解决「敏感信息落日志」——后者请勿记录原始凭据
 * （可只记 ID 或长度）；会话令牌等长凭据用 {@link #maskToken} 只保留前缀。</p>
 *
 * @author wenbin
 * @since 2026-09-14
 */
public final class LogSanitizer {

    /** 单条日志字段的最大长度；超长即截断（防止日志膨胀与磁盘打满） */
    private static final int MAX_LENGTH = 500;

    /** 截断后缀 */
    private static final String TRUNCATED_SUFFIX = "...(truncated)";

    /** 换行、制表与 C0/C1 控制字符：这些是日志注入的载体，统一替换 */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\r\\n\\t\\u0000-\\u001f\\u007f]");

    /** 令牌脱敏后保留的可见前缀长度：够定位是哪条会话，又不足以复用 */
    private static final int TOKEN_VISIBLE_PREFIX = 8;

    private LogSanitizer() {
    }

    /**
     * 脱敏任意对象用于日志输出。
     *
     * @param value 原始值（可为 {@code null}）
     * @return 可安全写入日志的文本；{@code null} 入参返回字面量 {@code "null"}
     */
    public static String sanitize(@Nullable Object value) {
        if (value == null) {
            return "null";
        }
        String text = CONTROL_CHARS.matcher(String.valueOf(value)).replaceAll("_");
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH) + TRUNCATED_SUFFIX;
    }

    /**
     * 会话令牌脱敏：只保留前 {@value #TOKEN_VISIBLE_PREFIX} 位，其余以 {@code ***} 代替。
     *
     * <p>用于「必须记录是哪次会话、但绝不能落下可复用凭据」的日志（如登录回验失败后回收会话、
     * Sa-Token 未认证异常）。默认日志里的 token 一律走本方法，不要在调用点各写一份截断逻辑。</p>
     *
     * @param token 原始令牌（可为 {@code null}）
     * @return 脱敏文本；{@code null} 或长度不足以截断时返回 {@code "***"}
     */
    public static String maskToken(@Nullable String token) {
        if (token == null || token.length() <= TOKEN_VISIBLE_PREFIX) {
            return "***";
        }
        return token.substring(0, TOKEN_VISIBLE_PREFIX) + "***";
    }
}
