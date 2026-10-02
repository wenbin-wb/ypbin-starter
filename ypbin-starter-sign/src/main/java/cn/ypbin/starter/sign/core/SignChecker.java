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
package cn.ypbin.starter.sign.core;

import cn.ypbin.starter.core.util.LogSanitizer;
import cn.ypbin.starter.sign.autoconfigure.SignProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

/**
 * 签名校验器。
 *
 * <p>校验流程：提取四件套（accessKey/timestamp/nonce/sign）→ 校验非空 → 查应用（校验启用/未过期）→ 校验时间戳超时 →
 * 可选 nonce 防重放 → 收集参与签名的参数（Query + JSON Body，排除 sign 与配置的排除项）→
 * 服务端按应用密钥与算法重算签名 → 与客户端签名比对。请求体经 web 模块的可重复读包装，
 * 读取后不影响 Controller 再次读取。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class SignChecker {

    private static final Logger log = LoggerFactory.getLogger(SignChecker.class);

    private static final String ACCESS_KEY = "accessKey";
    private static final String TIMESTAMP = "timestamp";
    private static final String NONCE = "nonce";
    private static final String SIGN = "sign";
    private static final String JSON_TYPE = "application/json";

    /** 明文密钥的参数名（库内只存哈希时，第三方经此传入明文以参与验签）。 */
    private static final String SECRET_PARAM = "secret";

    /** 明文密钥的请求头名（形态 {@code accessKeyId:secret}，与既有开放 API 接入方式一致）。 */
    private static final String API_KEY_HEADER = "X-Api-Key";

    /** 允许的未来时钟偏移（秒）：容忍客户端与服务端的小幅时钟不同步，但不接受明显来自未来的时间戳 */
    private static final long CLOCK_SKEW_SECONDS = 5L;

    private final SignProperties properties;
    private final NonceStore nonceStore;
    private final ObjectMapper objectMapper;
    private final SignAppProvider appProvider;
    private final SignAppVerifier appVerifier;

    public SignChecker(SignProperties properties, NonceStore nonceStore, ObjectMapper objectMapper,
        SignAppProvider appProvider) {
        this(properties, nonceStore, objectMapper, appProvider, SignAppVerifier.DEFAULT);
    }

    public SignChecker(SignProperties properties, NonceStore nonceStore, ObjectMapper objectMapper,
        SignAppProvider appProvider, SignAppVerifier appVerifier) {
        this.properties = properties;
        this.nonceStore = nonceStore;
        // 专用副本并强制按 key 排序：嵌套对象拍平为字符串时输出确定、与配置无关，
        // 避免共享 mapper 的 key 顺序波动导致验签时对时错
        ObjectMapper base = (objectMapper != null) ? objectMapper : new ObjectMapper();
        this.objectMapper = base.rebuild()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .build();
        this.appProvider = appProvider;
        this.appVerifier = (appVerifier != null) ? appVerifier : SignAppVerifier.DEFAULT;
    }

    /**
     * 校验请求签名。
     *
     * @param request HTTP 请求
     * @return 校验结果
     */
    public SignResult check(HttpServletRequest request) {
        String accessKey = request.getParameter(ACCESS_KEY);
        String timestamp = request.getParameter(TIMESTAMP);
        String nonce = request.getParameter(NONCE);
        String sign = request.getParameter(SIGN);

        if (properties.getSignMode() == SignProperties.SignMode.OPTIONAL
            && nonePresent(request, ACCESS_KEY, TIMESTAMP, NONCE, SIGN)) {
            // OPTIONAL 灰度：四个签名参数**都不存在**才视为"未启用签名的既有请求"而放行。
            //
            // 🔴 判据是"参数是否存在"，**不是**"值是否为空白"：独立复核实测发现，
            // 若用 isBlank()，客户端发 `timestamp=\u3000`（全角空格）会被 isBlank 判为空白
            // ⇒ 落进放行分支，构成**降级绕过**（NBSP/ZWSP 又恰好不算空白，行为还依赖字符集细节）。
            // 改为 presence 判定后，该面消失：只要带了这个参数名，就必须走完整校验。
            return SignResult.ok("");
        }
        if (isBlank(accessKey) || isBlank(timestamp) || isBlank(nonce) || isBlank(sign)) {
            // 部分携带一律拒绝（fail-closed）：若这里放行，攻击者可故意只带部分参数
            // 落进上面的 OPTIONAL 分支，形成**降级绕过**。
            return SignResult.fail("缺少签名参数");
        }

        SignApp app = appProvider.findByAccessKey(accessKey).orElse(null);
        if (app == null) {
            return SignResult.fail("应用不存在");
        }
        if (!app.isEnabled()) {
            return SignResult.fail("应用已禁用");
        }
        if (app.isExpired()) {
            return SignResult.fail("应用已过期");
        }

        long now = System.currentTimeMillis() / 1000;
        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return SignResult.fail("时间戳格式错误");
        }
        // 过去方向按 timeout 判过期；未来方向只容忍小幅时钟偏移（防"未来时间戳"扩大重放窗口）。
        // 用「比较」而非「相减」：requestTime 完全由请求方提供，相减在极值（如 Long.MIN_VALUE）下会溢出，
        // 溢出后 behind/ahead 可能同时不满足条件，等于绕过有效期校验。
        long lowerBound = now - properties.getTimeout();
        long upperBound = now + CLOCK_SKEW_SECONDS;
        if (requestTime < lowerBound || requestTime > upperBound) {
            return SignResult.fail("签名已过期");
        }
        long delta = requestTime - now;
        if (delta > CLOCK_SKEW_SECONDS || delta < -properties.getTimeout()) {
            return SignResult.fail("签名已过期");
        }

        Map<String, String> params = collectParams(request);
        String secret = resolveSecret(request, app);
        if (secret.isEmpty()) {
            // 无可用密钥（库内只有哈希且未通过业务校验）⇒ 按验签失败处理。
            // 刻意在此**提前返回**而**不是**交给 SignGenerator：HMAC 的密钥为空会抛
            // IllegalArgumentException（"Empty key"），那是 500 级异常而非预期的 401 语义；
            // 且异常路径会绕过"恒定时间比较"，把密钥错误与签名错误区分开（可被用于枚举探测）。
            log.warn("[ypbin-starter] 无可用签名密钥（哈希校验未通过或密钥缺失）accessKey={}",
                LogSanitizer.sanitize(accessKey));
            return SignResult.fail("签名验证失败");
        }
        String expected = SignGenerator.generate(params, secret, properties.getAlgorithm());
        // 恒定时间比较，避免逐字符短路带来的时序侧信道；不记录明文签名，防泄漏
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), sign.getBytes(StandardCharsets.UTF_8))) {
            log.warn("[ypbin-starter] 签名验证失败 accessKey={}", LogSanitizer.sanitize(accessKey));
            return SignResult.fail("签名验证失败");
        }

        // nonce 防重放：**刻意放在签名比对通过之后**占用。
        // 若在验签前占用（本类早期实现如此），攻击者可用**无效签名**批量预占 nonce 键，
        // 导致合法请求的 nonce 被误判为"已使用"而拒绝 —— 一种低成本的拒绝服务面。
        // 与 iot 侧 OpenApiKeyService 的口径保持一致。
        if (properties.isReplayProtect()) {
            String nonceKey = "ypbin:sign:nonce:" + accessKey + ":" + nonce;
            // nonce 存活必须覆盖时间戳的整个有效期末尾（requestTime + timeout）。
            // 固定 timeout+1 在时间戳偏未来时会早于时间戳失效前过期，留出重放真空期，
            // 故按请求时间戳动态计算 TTL。上面校验已保证该值落在 [1, 2*timeout+1]，不会为负。
            long ttlSeconds = properties.getTimeout() + delta + 1;
            if (!nonceStore.tryUse(nonceKey, Duration.ofSeconds(ttlSeconds))) {
                return SignResult.fail("请求重复（nonce 已使用）");
            }
        }

        // 以下为**验签通过之后**的维度校验。顺序是刻意的：
        // ① 未认证请求不得进入任何计数/配额逻辑（否则等于免费的拒绝服务面）；
        // ② IP/租户属于"身份约束"，先于配额；③ 配额最后。
        SignResult ipResult = checkIpWhitelist(request, app);
        if (ipResult != null) {
            return ipResult;
        }
        SignResult scopeResult = checkScopes(app);
        if (scopeResult != null) {
            return scopeResult;
        }
        SignResult quotaResult = checkQuota(app);
        if (quotaResult != null) {
            return quotaResult;
        }
        return SignResult.ok(accessKey);
    }

    /**
     * 解析参与签名计算的密钥：明文优先，其次走业务提供的哈希校验回调。
     *
     * <p>返回空串表示"无可用密钥" ⇒ 调用方按验签失败处理（fail-closed，不静默放行）。</p>
     *
     * <p><b>明文来源</b>：优先取 {@link SignApp#getSecretKey()}（配置/库内明文）；
     * 为空时（"库内只存哈希"场景）从请求中取第三方送来的明文密钥，
     * 依次尝试参数 {@code secret} 与 {@code X-Api-Key} 头
     * （后者的形态为 {@code accessKeyId:secret}，与既有开放 API 接入方式一致）。</p>
     */
    private String resolveSecret(HttpServletRequest request, SignApp app) {
        String plain = app.getSecretKey();
        if (plain != null && !plain.isBlank()) {
            return plain;
        }
        // 库内只存哈希的场景：校验由业务回调判定，starter 不假设哈希算法与 pepper。
        // 匹配通过后再用请求里的明文重算签名（保证与客户端口径一致）。
        String rawSecret = extractRawSecret(request);
        try {
            if (appVerifier.matchesSecret(app, rawSecret)) {
                return (rawSecret == null) ? "" : rawSecret.trim();
            }
        } catch (RuntimeException ex) {
            // 不吞异常：按拒绝处理，但记录完整堆栈（实现出错不等于放行）
            log.error("[ypbin-starter] 密钥哈希校验实现抛出异常，按拒绝处理 accessKey={}",
                LogSanitizer.sanitize(app.getAccessKey()), ex);
        }
        return "";
    }

    /**
     * 从请求中取第三方送来的明文密钥：先查参数 {@code secret}，再查 {@code X-Api-Key} 头。
     *
     * <p>返回值<b>绝不进日志</b>（明文凭据红线）。</p>
     */
    @Nullable
    private String extractRawSecret(HttpServletRequest request) {
        String fromParam = request.getParameter(SECRET_PARAM);
        if (!isBlank(fromParam)) {
            return fromParam;
        }
        String header = request.getHeader(API_KEY_HEADER);
        if (isBlank(header)) {
            return null;
        }
        // 形态 accessKeyId:secret；无冒号则整体视为 secret（宽松兼容），
        // 取冒号**之后**全部内容，避免 secret 内部含冒号时被截断
        int sep = header.indexOf(':');
        if (sep < 0) {
            return header;
        }
        String secret = header.substring(sep + 1).trim();
        return secret.isEmpty() ? null : secret;
    }

    /**
     * IP 白名单校验（未配置白名单时返回 {@code null} 表示放行）。
     */
    @Nullable
    private SignResult checkIpWhitelist(HttpServletRequest request, SignApp app) {
        if (!app.hasIpWhitelist()) {
            return null;
        }
        // 白名单条目非法必须可见：否则"配了却不生效"只能靠猜
        List<String> invalid = IpWhitelist.invalidEntries(app.getIpWhitelist());
        if (!invalid.isEmpty()) {
            log.warn("[ypbin-starter] IP 白名单含无法解析的条目（已忽略，不匹配任何地址）accessKey={}, invalid={}",
                LogSanitizer.sanitize(app.getAccessKey()), invalid);
        }
        String clientIp = resolveClientIp(request);
        if (!IpWhitelist.matches(app.getIpWhitelist(), clientIp)) {
            log.warn("[ypbin-starter] 来源 IP 不在白名单 accessKey={}, ip={}",
                LogSanitizer.sanitize(app.getAccessKey()), clientIp);
            return SignResult.fail("来源地址不在允许范围");
        }
        return null;
    }

    /**
     * 解析客户端来源地址。
     *
     * <p><b>刻意默认不信任 {@code X-Forwarded-For}</b>：该头可被客户端伪造，只有在
     * 前面确有可信代理时才可信。故由配置 {@code ypbin.sign.trust-forwarded-header} 显式开启，
     * 开启后取 XFF <b>最左</b>值（原始客户端）。未开启时用 {@code getRemoteAddr()}。</p>
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (properties.isTrustForwardedHeader()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 作用域校验（应用未声明作用域时返回 {@code null} 表示放行 = 旧行为）。
     */
    @Nullable
    private SignResult checkScopes(SignApp app) {
        List<String> scopes = app.getScopes();
        if (scopes.isEmpty()) {
            return null;
        }
        try {
            if (!appVerifier.scopesAllowed(app, scopes)) {
                log.warn("[ypbin-starter] 作用域校验未通过 accessKey={}, scopes={}",
                    LogSanitizer.sanitize(app.getAccessKey()), scopes);
                return SignResult.fail("作用域不被允许");
            }
        } catch (RuntimeException ex) {
            log.error("[ypbin-starter] 作用域校验实现抛出异常，按拒绝处理 accessKey={}",
                LogSanitizer.sanitize(app.getAccessKey()), ex);
            return SignResult.fail("作用域校验失败");
        }
        return null;
    }

    /**
     * 配额/限流判定（应用未配配额时返回 {@code null} 表示放行）。
     */
    @Nullable
    private SignResult checkQuota(SignApp app) {
        if (!app.hasQuota()) {
            return null;
        }
        try {
            SignAppVerifier.QuotaDecision decision = appVerifier.checkQuota(app);
            if (decision != null && !decision.allowed()) {
                String message = decision.message();
                return SignResult.fail(isBlank(message) ? "请求过于频繁或超过配额" : message);
            }
        } catch (RuntimeException ex) {
            // 配额判定的存储（如 Redis）异常：**按拒绝处理**。
            // 与"限流可用性"相比，超配额放行的代价是资源被打满，故此处刻意选择 fail-closed。
            log.error("[ypbin-starter] 配额判定实现抛出异常，按拒绝处理 accessKey={}",
                LogSanitizer.sanitize(app.getAccessKey()), ex);
            return SignResult.fail("配额判定失败");
        }
        return null;
    }

    /**
     * 判定这些参数名是否**一个都不存在**于请求中（query 或表单）。
     *
     * <p>刻意按"参数是否存在"而非"值是否空白"判定：空白值（尤其全角空格等
     * {@code isBlank()} 边界字符）若被当作"未携带"，即可被用来降级绕过签名校验。</p>
     *
     * @param request 请求
     * @param names   参数名
     * @return 一个都不存在返回 {@code true}
     */
    private boolean nonePresent(HttpServletRequest request, String... names) {
        for (String name : names) {
            if (request.getParameter(name) != null || request.getParameterValues(name) != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * 收集参与签名的参数：Query/表单参数 + JSON body 顶层字段，排除 sign 与配置排除项。
     */
    private Map<String, String> collectParams(HttpServletRequest request) {
        List<String> skip = new ArrayList<>();
        skip.add(SIGN);
        skip.addAll(properties.getSkipParamNames());

        Map<String, String> params = new HashMap<>(16);
        Enumeration<String> names = request.getParameterNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (skip.contains(name)) {
                continue;
            }
            String value = request.getParameter(name);
            if (value != null && !value.isEmpty()) {
                params.put(name, value);
            }
        }

        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().contains(JSON_TYPE)) {
            mergeJsonBody(request, skip, params);
        }
        return params;
    }

    @SuppressWarnings("unchecked")
    private void mergeJsonBody(HttpServletRequest request, List<String> skip, Map<String, String> params) {
        try {
            String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                return;
            }
            Map<String, Object> json = objectMapper.readValue(body, Map.class);
            for (Map.Entry<String, Object> entry : json.entrySet()) {
                Object value = entry.getValue();
                if (skip.contains(entry.getKey()) || value == null) {
                    continue;
                }
                // JDK 21 switch 类型模式：基本 JSON 标量直接 valueOf，复杂结构序列化
                String strVal = switch (value) {
                    case String s -> s;
                    case Number n -> String.valueOf(n);
                    case Boolean b -> String.valueOf(b);
                    default -> objectMapper.writeValueAsString(value);
                };
                params.put(entry.getKey(), strVal);
            }
        } catch (Exception e) {
            log.warn("[ypbin-starter] 解析 JSON 请求体用于签名失败，本次验签将缺少 JSON 参数: {}", e.getMessage(), e);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
