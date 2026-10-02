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

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.sign.autoconfigure.SignProperties;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link SignChecker} 新增维度校验测试：灰度模式、IP 白名单、作用域、配额、哈希密钥。
 *
 * <p>重点覆盖"新增分支 + 边界 + 对抗用例"，并确保<b>既有行为不退化</b>。</p>
 *
 * @author wenbin
 * @since 2026-10-05
 */
class SignCheckerExtensionsTest {

    private static final String SECRET = "sk-xxx";

    private static SignProperties baseProperties() {
        SignProperties properties = new SignProperties();
        properties.setReplayProtect(false);
        return properties;
    }

    private static SignChecker checker(SignProperties properties, SignApp app) {
        return new SignChecker(properties, (key, ttl) -> true, new ObjectMapper(),
            accessKey -> Optional.ofNullable(app));
    }

    private static SignChecker checker(SignProperties properties, SignApp app, SignAppVerifier verifier) {
        return new SignChecker(properties, (key, ttl) -> true, new ObjectMapper(),
            accessKey -> Optional.ofNullable(app), verifier);
    }

    private static MockHttpServletRequest signedRequest(String accessKey) {
        return signedRequest(accessKey, SECRET);
    }

    private static MockHttpServletRequest signedRequest(String accessKey, String secret) {
        Map<String, String> signed = SignClient.sign(Map.of("orderNo", "A100"), accessKey, secret,
            SignAlgorithm.HMAC_SHA256);
        MockHttpServletRequest request = new MockHttpServletRequest();
        signed.forEach(request::addParameter);
        return request;
    }

    // ==================== 灰度模式 signMode ====================

    @Test
    void requiredModeShouldRejectCompletelyUnsignedRequest() {
        SignProperties properties = baseProperties();
        properties.setSignMode(SignProperties.SignMode.REQUIRED);
        SignChecker checker = checker(properties, new SignApp("ak-001", SECRET));

        MockHttpServletRequest request = new MockHttpServletRequest();
        SignResult result = checker.check(request);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("缺少签名参数");
    }

    @Test
    void optionalModeShouldAllowCompletelyUnsignedRequest() {
        // OPTIONAL 灰度：四件套全无 ⇒ 视为既有未签名请求，放行
        SignProperties properties = baseProperties();
        properties.setSignMode(SignProperties.SignMode.OPTIONAL);
        SignChecker checker = checker(properties, new SignApp("ak-001", SECRET));

        MockHttpServletRequest request = new MockHttpServletRequest();
        SignResult result = checker.check(request);

        assertThat(result.success()).isTrue();
    }

    @Test
    void optionalModeShouldStillVerifyWhenSignaturePresent() {
        // OPTIONAL 下带了完整签名 ⇒ 必须真实校验，签名错要拒绝
        SignProperties properties = baseProperties();
        properties.setSignMode(SignProperties.SignMode.OPTIONAL);
        SignChecker checker = checker(properties, new SignApp("ak-001", SECRET));

        MockHttpServletRequest request = signedRequest("ak-001");
        assertThat(checker.check(request).success()).isTrue();

        MockHttpServletRequest bad = signedRequest("ak-001");
        bad.setParameter("sign", "DEADBEEF");
        assertThat(checker.check(bad).success()).isFalse();
    }

    @Test
    void optionalModeShouldRejectPartiallyCarriedSignature() {
        // 🔴 降级绕过对抗：只带部分签名参数，不得落进"全无"放行分支
        SignProperties properties = baseProperties();
        properties.setSignMode(SignProperties.SignMode.OPTIONAL);
        SignChecker checker = checker(properties, new SignApp("ak-001", SECRET));

        String[][] partials = {
            {"timestamp"},
            {"nonce"},
            {"sign"},
            {"accessKey"},
            {"accessKey", "timestamp"},
            {"accessKey", "timestamp", "nonce"},
            {"timestamp", "nonce", "sign"},
        };
        for (String[] present : partials) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            for (String name : present) {
                request.addParameter(name, "whatever");
            }
            SignResult result = checker.check(request);
            assertThat(result.success())
                .as("部分携带签名参数必须拒绝（fail-closed），携带=%s", String.join(",", present))
                .isFalse();
        }
    }

    @Test
    void optionalModeShouldTreatBlankValuesAsPresent() {
        // 🔴 安全语义（由独立复核纠正）：判据是"参数**是否存在**"，不是"值是否空白"。
        //
        // 早期实现用 isBlank() 判定"全无"，实测存在降级绕过：
        //   - `timestamp="\u3000"`（全角空格）被 isBlank() 判为空白 ⇒ 落进放行分支；
        //   - 而 NBSP(\u00A0)/ZWSP(\u200B) 又不算空白 ⇒ 行为还依赖字符集细节。
        // 改为 presence 判定后：只要带了参数名就必须走完整校验，该面消失。
        SignProperties properties = baseProperties();
        properties.setSignMode(SignProperties.SignMode.OPTIONAL);
        SignChecker checker = checker(properties, new SignApp("ak-001", SECRET));

        // 带参数但值为空白 ⇒ 视为"意图签名"，必须走严格校验 ⇒ 拒绝（不是放行）
        MockHttpServletRequest allBlank = new MockHttpServletRequest();
        allBlank.addParameter("accessKey", "  ");
        allBlank.addParameter("timestamp", "");
        allBlank.addParameter("nonce", "\t");
        allBlank.addParameter("sign", " ");
        assertThat(checker.check(allBlank).success())
            .as("参数存在（哪怕值为空白）即意图签名，必须校验而非放行")
            .isFalse();

        // 全角空格：曾可绕过 isBlank 判定的降级载荷
        MockHttpServletRequest fullWidth = new MockHttpServletRequest();
        fullWidth.addParameter("accessKey", "ak-001");
        fullWidth.addParameter("timestamp", "\u3000");
        fullWidth.addParameter("nonce", "\u3000");
        fullWidth.addParameter("sign", "\u3000");
        assertThat(checker.check(fullWidth).success())
            .as("全角空格不得被当作\"未携带\"而降级")
            .isFalse();

        // 真正一个参数都不带 ⇒ 才落入"未启用签名"分支放行
        assertThat(checker.check(new MockHttpServletRequest()).success())
            .as("四个参数名都不存在时才放行")
            .isTrue();
    }

    // ==================== IP 白名单 ====================

    @Test
    void shouldRejectIpOutsideWhitelist() {
        SignApp app = new SignApp("ak-001", SECRET);
        app.setIpWhitelist("10.0.0.0/8");
        SignChecker checker = checker(baseProperties(), app);

        MockHttpServletRequest request = signedRequest("ak-001");
        request.setRemoteAddr("192.168.1.5");

        SignResult result = checker.check(request);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("来源地址不在允许范围");
    }

    @Test
    void shouldAllowIpInsideWhitelist() {
        SignApp app = new SignApp("ak-001", SECRET);
        app.setIpWhitelist("10.0.0.0/8");
        SignChecker checker = checker(baseProperties(), app);

        MockHttpServletRequest request = signedRequest("ak-001");
        request.setRemoteAddr("10.1.2.3");

        assertThat(checker.check(request).success()).isTrue();
    }

    @Test
    void shouldIgnoreForwardedHeaderByDefault() {
        // 默认不信任 XFF：伪造该头不能让白名单外的来源通过
        SignApp app = new SignApp("ak-001", SECRET);
        app.setIpWhitelist("10.0.0.0/8");
        SignChecker checker = checker(baseProperties(), app);

        MockHttpServletRequest request = signedRequest("ak-001");
        request.setRemoteAddr("192.168.1.5");
        request.addHeader("X-Forwarded-For", "10.1.2.3");

        assertThat(checker.check(request).success()).isFalse();
    }

    @Test
    void shouldHonorForwardedHeaderWhenExplicitlyTrusted() {
        SignProperties properties = baseProperties();
        properties.setTrustForwardedHeader(true);
        SignApp app = new SignApp("ak-001", SECRET);
        app.setIpWhitelist("10.0.0.0/8");
        SignChecker checker = checker(properties, app);

        MockHttpServletRequest request = signedRequest("ak-001");
        request.setRemoteAddr("192.168.1.5");
        request.addHeader("X-Forwarded-For", "10.1.2.3, 172.16.0.1");

        // 取 XFF 最左值（原始客户端）
        assertThat(checker.check(request).success()).isTrue();
    }

    @Test
    void shouldRejectWhenWhitelistConfiguredButRemoteAddrUnparseable() {
        SignApp app = new SignApp("ak-001", SECRET);
        app.setIpWhitelist("10.0.0.0/8");
        SignChecker checker = checker(baseProperties(), app);

        MockHttpServletRequest request = signedRequest("ak-001");
        request.setRemoteAddr("not-an-ip");

        // 无法判定来源 ⇒ 拒绝（fail-closed）
        assertThat(checker.check(request).success()).isFalse();
    }

    @Test
    void shouldNotApplyIpCheckWhenWhitelistAbsent() {
        // 未配白名单 ⇒ 旧行为（不限来源）
        SignApp app = new SignApp("ak-001", SECRET);
        SignChecker checker = checker(baseProperties(), app);

        MockHttpServletRequest request = signedRequest("ak-001");
        request.setRemoteAddr("8.8.8.8");

        assertThat(checker.check(request).success()).isTrue();
    }

    // ==================== 作用域 ====================

    @Test
    void shouldAllowWhenScopesAbsent() {
        // 未声明作用域 ⇒ 旧行为（不校验）
        SignApp app = new SignApp("ak-001", SECRET);
        SignChecker checker = checker(baseProperties(), app, SignAppVerifier.DEFAULT);
        assertThat(checker.check(signedRequest("ak-001")).success()).isTrue();
    }

    @Test
    void shouldRejectWhenScopeVerifierDenies() {
        SignApp app = new SignApp("ak-001", SECRET);
        app.setScopes(List.of("iot:debug:send"));
        SignAppVerifier denying = new SignAppVerifier() {
            @Override
            public boolean scopesAllowed(SignApp ignored, List<String> scopes) {
                return false;
            }
        };
        SignChecker checker = checker(baseProperties(), app, denying);

        SignResult result = checker.check(signedRequest("ak-001"));
        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("作用域不被允许");
    }

    @Test
    void shouldPassWhenScopeVerifierAllows() {
        SignApp app = new SignApp("ak-001", SECRET);
        app.setScopes(List.of("iot:device:list"));
        SignChecker checker = checker(baseProperties(), app, SignAppVerifier.DEFAULT);
        assertThat(checker.check(signedRequest("ak-001")).success()).isTrue();
    }

    @Test
    void shouldFailClosedWhenScopeVerifierThrows() {
        // 实现抛异常不得静默放行
        SignApp app = new SignApp("ak-001", SECRET);
        app.setScopes(List.of("iot:device:list"));
        SignAppVerifier throwing = new SignAppVerifier() {
            @Override
            public boolean scopesAllowed(SignApp ignored, List<String> scopes) {
                throw new IllegalStateException("boom");
            }
        };
        SignChecker checker = checker(baseProperties(), app, throwing);

        SignResult result = checker.check(signedRequest("ak-001"));
        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("作用域校验失败");
    }

    // ==================== 配额 ====================

    @Test
    void shouldNotInvokeQuotaWhenAppHasNoQuota() {
        AtomicInteger calls = new AtomicInteger();
        SignAppVerifier counting = new SignAppVerifier() {
            @Override
            public QuotaDecision checkQuota(SignApp app) {
                calls.incrementAndGet();
                return QuotaDecision.ok();
            }
        };
        SignApp app = new SignApp("ak-001", SECRET);
        SignChecker checker = checker(baseProperties(), app, counting);

        assertThat(checker.check(signedRequest("ak-001")).success()).isTrue();
        assertThat(calls.get()).isZero();
    }

    @Test
    void shouldRejectWhenQuotaExceeded() {
        SignApp app = new SignApp("ak-001", SECRET);
        app.setRateLimitQps(10);
        SignAppVerifier denying = new SignAppVerifier() {
            @Override
            public QuotaDecision checkQuota(SignApp ignored) {
                return QuotaDecision.reject("请求过于频繁");
            }
        };
        SignChecker checker = checker(baseProperties(), app, denying);

        SignResult result = checker.check(signedRequest("ak-001"));
        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("请求过于频繁");
    }

    @Test
    void shouldFailClosedWhenQuotaVerifierThrows() {
        SignApp app = new SignApp("ak-001", SECRET);
        app.setDailyQuota(100);
        SignAppVerifier throwing = new SignAppVerifier() {
            @Override
            public QuotaDecision checkQuota(SignApp ignored) {
                throw new IllegalStateException("redis down");
            }
        };
        SignChecker checker = checker(baseProperties(), app, throwing);

        SignResult result = checker.check(signedRequest("ak-001"));
        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("配额判定失败");
    }

    @Test
    void shouldNotConsumeQuotaWhenSignatureInvalid() {
        // 🔴 核心安全属性：配额只能在验签通过后被消费。
        // 攻击者用错误签名打请求，不得消耗配额（否则等于免费的 DoS 面）。
        AtomicInteger calls = new AtomicInteger();
        SignAppVerifier counting = new SignAppVerifier() {
            @Override
            public QuotaDecision checkQuota(SignApp app) {
                calls.incrementAndGet();
                return QuotaDecision.ok();
            }
        };
        SignApp app = new SignApp("ak-001", SECRET);
        app.setRateLimitQps(10);
        SignChecker checker = checker(baseProperties(), app, counting);

        MockHttpServletRequest bad = signedRequest("ak-001");
        bad.setParameter("sign", "DEADBEEF");
        SignResult result = checker.check(bad);

        assertThat(result.success()).isFalse();
        assertThat(calls.get()).as("验签失败时不得调用配额判定").isZero();
    }

    @Test
    void shouldNotConsumeQuotaWhenTimestampExpired() {
        AtomicInteger calls = new AtomicInteger();
        SignAppVerifier counting = new SignAppVerifier() {
            @Override
            public QuotaDecision checkQuota(SignApp app) {
                calls.incrementAndGet();
                return QuotaDecision.ok();
            }
        };
        SignApp app = new SignApp("ak-001", SECRET);
        app.setRateLimitQps(10);
        SignChecker checker = checker(baseProperties(), app, counting);

        Map<String, String> params = new HashMap<>();
        params.put("orderNo", "A100");
        params.put("accessKey", "ak-001");
        params.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000 - 99999));
        params.put("nonce", "n1");
        String sign = SignGenerator.generate(params, SECRET, SignAlgorithm.HMAC_SHA256);
        params.put("sign", sign);
        MockHttpServletRequest request = new MockHttpServletRequest();
        params.forEach(request::addParameter);

        assertThat(checker.check(request).success()).isFalse();
        assertThat(calls.get()).as("时间戳过期时不得调用配额判定").isZero();
    }

    @Test
    void shouldNotConsumeQuotaWhenAppDisabledOrExpired() {
        AtomicInteger calls = new AtomicInteger();
        SignAppVerifier counting = new SignAppVerifier() {
            @Override
            public QuotaDecision checkQuota(SignApp app) {
                calls.incrementAndGet();
                return QuotaDecision.ok();
            }
        };
        SignProperties properties = baseProperties();

        SignApp disabled = new SignApp("ak-001", SECRET);
        disabled.setEnabled(false);
        disabled.setRateLimitQps(10);
        assertThat(checker(properties, disabled, counting).check(signedRequest("ak-001")).success()).isFalse();
        assertThat(calls.get()).isZero();

        SignApp expired = new SignApp("ak-001", SECRET);
        expired.setExpireTime(LocalDateTime.now().minusDays(1));
        expired.setRateLimitQps(10);
        assertThat(checker(properties, expired, counting).check(signedRequest("ak-001")).success()).isFalse();
        assertThat(calls.get()).isZero();
    }

    @Test
    void shouldNotConsumeQuotaWhenIpRejected() {
        AtomicInteger calls = new AtomicInteger();
        SignAppVerifier counting = new SignAppVerifier() {
            @Override
            public QuotaDecision checkQuota(SignApp app) {
                calls.incrementAndGet();
                return QuotaDecision.ok();
            }
        };
        SignApp app = new SignApp("ak-001", SECRET);
        app.setRateLimitQps(10);
        app.setIpWhitelist("10.0.0.0/8");
        SignChecker checker = checker(baseProperties(), app, counting);

        MockHttpServletRequest request = signedRequest("ak-001");
        request.setRemoteAddr("192.168.1.5");

        assertThat(checker.check(request).success()).isFalse();
        assertThat(calls.get()).as("IP 被拒时不得调用配额判定").isZero();
    }

    // ==================== 哈希密钥（secretHash） ====================

    @Test
    void shouldVerifyViaHashWhenPlainSecretAbsent() {
        SignApp app = new SignApp();
        app.setAccessKey("ak-001");
        app.setSecretHash("hash-placeholder"); // 明文 secretKey 留空
        SignAppVerifier hashVerifier = new SignAppVerifier() {
            @Override
            public boolean matchesSecret(SignApp ignored, String rawSecret) {
                return SECRET.equals(rawSecret);
            }
        };
        SignChecker checker = checker(baseProperties(), app, hashVerifier);

        // 请求用明文 SECRET 签名，并经 X-Api-Key 头送明文；hash 校验通过后以该明文重算 ⇒ 应通过
        MockHttpServletRequest request = signedRequest("ak-001");
        request.addHeader("X-Api-Key", "ak-001:" + SECRET);
        assertThat(checker.check(request).success()).isTrue();
    }

    @Test
    void shouldRejectWhenHashVerifierDenies() {
        SignApp app = new SignApp();
        app.setAccessKey("ak-001");
        app.setSecretHash("hash-placeholder");
        SignAppVerifier denying = new SignAppVerifier() {
            @Override
            public boolean matchesSecret(SignApp ignored, String rawSecret) {
                return false;
            }
        };
        SignChecker checker = checker(baseProperties(), app, denying);

        MockHttpServletRequest request = signedRequest("ak-001");
        request.addHeader("X-Api-Key", "ak-001:" + SECRET);
        assertThat(checker.check(request).success()).isFalse();
    }

    @Test
    void shouldFailClosedWhenHashVerifierThrows() {
        SignApp app = new SignApp();
        app.setAccessKey("ak-001");
        app.setSecretHash("hash-placeholder");
        SignAppVerifier throwing = new SignAppVerifier() {
            @Override
            public boolean matchesSecret(SignApp ignored, String rawSecret) {
                throw new IllegalStateException("hash boom");
            }
        };
        SignChecker checker = checker(baseProperties(), app, throwing);

        // 不静默放行：返回空密钥 ⇒ 验签失败（且不得抛 500 级异常）
        MockHttpServletRequest request = signedRequest("ak-001");
        request.addHeader("X-Api-Key", "ak-001:" + SECRET);
        SignResult result = checker.check(request);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("签名验证失败");
    }

    @Test
    void shouldPreferPlainSecretOverHashPath() {
        // 明文存在时不应调用哈希回调（既有行为不退化）
        AtomicInteger hashCalls = new AtomicInteger();
        SignAppVerifier counting = new SignAppVerifier() {
            @Override
            public boolean matchesSecret(SignApp app, String rawSecret) {
                hashCalls.incrementAndGet();
                return false;
            }
        };
        SignChecker checker = checker(baseProperties(), new SignApp("ak-001", SECRET), counting);

        assertThat(checker.check(signedRequest("ak-001")).success()).isTrue();
        assertThat(hashCalls.get()).isZero();
    }

    // ==================== 组合与顺序 ====================

    @Test
    void shouldApplyIpBeforeScopeBeforeQuota() {
        // 顺序断言：IP 不过时，作用域与配额都不应被调用
        AtomicInteger scopeCalls = new AtomicInteger();
        AtomicInteger quotaCalls = new AtomicInteger();
        SignAppVerifier spy = new SignAppVerifier() {
            @Override
            public boolean scopesAllowed(SignApp app, List<String> scopes) {
                scopeCalls.incrementAndGet();
                return true;
            }

            @Override
            public QuotaDecision checkQuota(SignApp app) {
                quotaCalls.incrementAndGet();
                return QuotaDecision.ok();
            }
        };
        SignApp app = new SignApp("ak-001", SECRET);
        app.setIpWhitelist("10.0.0.0/8");
        app.setScopes(List.of("iot:device:list"));
        app.setRateLimitQps(10);
        SignChecker checker = checker(baseProperties(), app, spy);

        MockHttpServletRequest request = signedRequest("ak-001");
        request.setRemoteAddr("192.168.1.5");
        assertThat(checker.check(request).success()).isFalse();
        assertThat(scopeCalls.get()).isZero();
        assertThat(quotaCalls.get()).isZero();
    }

    @Test
    void shouldPassAllDimensionsWhenEverythingValid() {
        SignApp app = new SignApp("ak-001", SECRET);
        app.setTenantId(1L);
        app.setScopes(List.of("iot:device:list"));
        app.setRateLimitQps(10);
        app.setDailyQuota(1000);
        app.setIpWhitelist("10.0.0.0/8");
        SignChecker checker = checker(baseProperties(), app, SignAppVerifier.DEFAULT);

        MockHttpServletRequest request = signedRequest("ak-001");
        request.setRemoteAddr("10.1.2.3");

        SignResult result = checker.check(request);
        assertThat(result.success()).isTrue();
        assertThat(result.accessKey()).isEqualTo("ak-001");
    }

    @Test
    void defaultVerifierShouldBePermissive() {
        // DEFAULT 三件事全放行（= 既有版本行为）
        assertThat(SignAppVerifier.DEFAULT.matchesSecret(new SignApp("a", "b"), "b")).isFalse();
        assertThat(SignAppVerifier.DEFAULT.scopesAllowed(new SignApp("a", "b"), List.of("x"))).isTrue();
        assertThat(SignAppVerifier.DEFAULT.checkQuota(new SignApp("a", "b"))).isNull();
    }

    @Test
    void nullVerifierShouldFallBackToDefault() {
        // 构造器传入 null 不得 NPE
        SignChecker checker = new SignChecker(baseProperties(), (key, ttl) -> true, new ObjectMapper(),
            accessKey -> Optional.of(new SignApp("ak-001", SECRET)), null);
        assertThat(checker.check(signedRequest("ak-001")).success()).isTrue();
    }

    // ==================== 配置源字段映射（防"建了字段没接线"） ====================

    @Test
    void configProviderMustMapAllNewDimensions() {
        // 🔴 独立复核发现：DefaultSignAppProvider 早期只映射了 appName/expireTime/enabled，
        // 新增 6 字段全部丢失 ⇒ 从 ypbin.sign.apps 配置来的应用永远走不进三条校验路径
        // （表象是"字段建了没用"）。本用例锁死该映射，防回归。
        SignProperties.AppInfo info = new SignProperties.AppInfo();
        info.setAccessKey("ak-cfg");
        info.setSecretKey(SECRET);
        info.setAppName("配置应用");
        info.setTenantId(9L);
        info.setScopes(List.of("iot:device:list"));
        info.setRateLimitQps(7);
        info.setDailyQuota(1234);
        info.setIpWhitelist("10.0.0.0/8");

        SignProperties properties = baseProperties();
        properties.setApps(List.of(info));
        SignApp app = new DefaultSignAppProvider(properties).findByAccessKey("ak-cfg").orElseThrow();

        assertThat(app.getTenantId()).isEqualTo(9L);
        assertThat(app.getScopes()).containsExactly("iot:device:list");
        assertThat(app.getRateLimitQps()).isEqualTo(7);
        assertThat(app.getDailyQuota()).isEqualTo(1234);
        assertThat(app.getIpWhitelist()).isEqualTo("10.0.0.0/8");
    }

    @Test
    void configDrivenAppMustActuallyEnforceIpWhitelist() {
        // 端到端咬合：配置来的白名单必须真的生效（不是只映射了字段）
        SignProperties.AppInfo info = new SignProperties.AppInfo();
        info.setAccessKey("ak-cfg");
        info.setSecretKey(SECRET);
        info.setIpWhitelist("10.0.0.0/8");

        SignProperties properties = baseProperties();
        properties.setApps(List.of(info));
        SignChecker checker = new SignChecker(properties, (key, ttl) -> true, new ObjectMapper(),
            new DefaultSignAppProvider(properties));

        MockHttpServletRequest denied = signedRequest("ak-cfg");
        denied.setRemoteAddr("192.168.1.5");
        assertThat(checker.check(denied).success()).isFalse();

        MockHttpServletRequest allowed = signedRequest("ak-cfg");
        allowed.setRemoteAddr("10.9.9.9");
        assertThat(checker.check(allowed).success()).isTrue();
    }

    @Test
    void configDrivenAppMustActuallyInvokeQuotaVerifier() {
        // 配置来的配额必须真的触发扩展点回调
        AtomicInteger calls = new AtomicInteger();
        SignAppVerifier counting = new SignAppVerifier() {
            @Override
            public QuotaDecision checkQuota(SignApp app) {
                calls.incrementAndGet();
                return QuotaDecision.reject("超配额");
            }
        };
        SignProperties.AppInfo info = new SignProperties.AppInfo();
        info.setAccessKey("ak-cfg");
        info.setSecretKey(SECRET);
        info.setRateLimitQps(5);

        SignProperties properties = baseProperties();
        properties.setApps(List.of(info));
        SignChecker checker = new SignChecker(properties, (key, ttl) -> true, new ObjectMapper(),
            new DefaultSignAppProvider(properties), counting);

        SignResult result = checker.check(signedRequest("ak-cfg"));
        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("超配额");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void configAppWithoutNewFieldsKeepsOldBehavior() {
        // 兼容性回归：老配置（只有 accessKey/secretKey）行为完全不变
        SignProperties.AppInfo info = new SignProperties.AppInfo();
        info.setAccessKey("ak-old");
        info.setSecretKey(SECRET);
        SignProperties properties = baseProperties();
        properties.setApps(List.of(info));

        SignChecker checker = new SignChecker(properties, (key, ttl) -> true, new ObjectMapper(),
            new DefaultSignAppProvider(properties));
        MockHttpServletRequest request = signedRequest("ak-old");
        request.setRemoteAddr("8.8.8.8");
        assertThat(checker.check(request).success()).isTrue();
    }
}
