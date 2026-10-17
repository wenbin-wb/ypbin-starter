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
package cn.ypbin.starter.security.internal;

import cn.ypbin.starter.core.util.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * 内部调用凭证校验核心 —— 安全不变量的<b>单一定义点</b>。
 *
 * <p>{@link InternalTokenGuard}（RAW_401）与 {@link InternalTokenGuardInterceptor}（ENVELOPE）
 * 两种拒绝语义共用本类，避免"两份逐字重复实现"各自漂移（本能力上收的初衷）：</p>
 * <ul>
 *     <li><b>fail-closed</b>：凭证未配置（{@code null}/空白）一律返回
 *     {@link Verdict#MISSING_CONFIG}，由调用方拒绝，绝不静默放行；</li>
 *     <li><b>常量时间比较</b>：{@link MessageDigest#isEqual(byte[], byte[])}（JDK 21 的
 *     {@code @implNote} 明确「examines all bytes of digesta；耗时只取决于 {@code digesta} 的长度，
 *     与 {@code digestb} 的长度和两者内容无关」），不得改回 {@code String.equals}；</li>
 *     <li><b>日志不含凭证值</b>：只记 URI 与"是否携带头"的布尔量。</li>
 * </ul>
 *
 * <p>比较两侧都以 UTF-8 编码为字节数组：{@code configuredToken} 作为 {@code digesta}（长度固定，
 * 由部署配置决定），呈递值作为 {@code digestb}，符合该 API 的常量时间前提。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
final class InternalTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(InternalTokenVerifier.class);

    /** 凭证未配置时的固定提示（指向本组件自己的配置键，不含任何输入值） */
    static final String MISSING_CONFIG_MESSAGE =
        "内部调用凭证未配置，请先配置 " + InternalTokenProperties.PREFIX + ".token";

    /** 凭证校验失败的固定提示（不含任何输入值） */
    static final String MISMATCH_MESSAGE = "内部调用凭证校验失败";

    /** 校验结论 */
    enum Verdict {
        /** 凭证匹配，放行 */
        MATCH,
        /** 服务端未配置凭证：fail-closed，拒绝 */
        MISSING_CONFIG,
        /** 呈递凭证缺失或不匹配：拒绝 */
        MISMATCH
    }

    /** 期望凭证；空串表示未配置（不持有 {@code null}，避免空值传播） */
    private final String configuredToken;

    /** 凭证请求头名（构造期已归一化，保证非空） */
    private final String headerName;

    /**
     * 构造校验器。
     *
     * @param configuredToken 配置的凭证，可为 {@code null}
     * @param headerName      凭证请求头名，空白时回退默认头名（避免"读不到头"退化为全量拒绝）
     */
    InternalTokenVerifier(@Nullable String configuredToken, @Nullable String headerName) {
        this.configuredToken = configuredToken == null ? "" : configuredToken;
        this.headerName = StringUtils.hasText(headerName)
            ? headerName : InternalTokenProperties.DEFAULT_HEADER_NAME;
    }

    /**
     * 校验当前请求是否持有正确的内部调用凭证。
     *
     * <p>本方法同时负责失败日志（单一定义点，保证两种语义的日志口径与"不含凭证值"约束一致）。</p>
     *
     * @param request 当前请求
     * @return 校验结论，绝不为 {@code null}
     */
    Verdict verify(HttpServletRequest request) {
        if (!StringUtils.hasText(configuredToken)) {
            // 不静默放行：没配凭证就是没配信任边界（fail-closed）
            log.error("[ypbin-starter] 内部调用凭证未配置（{}），已拒绝内部端点请求：uri={}",
                InternalTokenProperties.PREFIX + ".token",
                LogSanitizer.sanitize(request.getRequestURI()));
            return Verdict.MISSING_CONFIG;
        }
        String presented = request.getHeader(headerName);
        if (presented == null || !MessageDigest.isEqual(
            configuredToken.getBytes(StandardCharsets.UTF_8),
            presented.getBytes(StandardCharsets.UTF_8))) {
            // 只记"是否携带头"，绝不记录呈递的凭证值
            log.warn("[ypbin-starter] 内部调用凭证校验失败，已拒绝：uri={} 是否携带头={}",
                LogSanitizer.sanitize(request.getRequestURI()), presented != null);
            return Verdict.MISMATCH;
        }
        return Verdict.MATCH;
    }

    /**
     * 取某结论对应的固定提示文案。
     *
     * @param verdict 校验结论（{@link Verdict#MATCH} 不应调用本方法）
     * @return 固定文案（不含任何输入值）
     */
    static String messageOf(Verdict verdict) {
        return verdict == Verdict.MISSING_CONFIG ? MISSING_CONFIG_MESSAGE : MISMATCH_MESSAGE;
    }
}
