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
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link SignChecker} 应用来源与状态校验测试。
 *
 * @author wenbin
 * @since 2026-08-01
 */
class SignCheckerTest {

    private static final String SECRET = "sk-xxx";

    private SignChecker checker(SignAppProvider provider) {
        SignProperties properties = new SignProperties();
        properties.setReplayProtect(false);
        return new SignChecker(properties, (key, ttl) -> true, new ObjectMapper(), provider);
    }

    private MockHttpServletRequest signedRequest(String accessKey) {
        Map<String, String> signed = SignClient.sign(Map.of("orderNo", "A100"), accessKey, SECRET,
            SignAlgorithm.HMAC_SHA256);
        MockHttpServletRequest request = new MockHttpServletRequest();
        signed.forEach(request::addParameter);
        return request;
    }

    @Test
    void shouldPassWithValidApp() {
        SignApp app = new SignApp("ak-001", SECRET);
        SignChecker checker = checker(accessKey -> Optional.of(app));

        SignResult result = checker.check(signedRequest("ak-001"));

        assertThat(result.success()).isTrue();
        assertThat(result.accessKey()).isEqualTo("ak-001");
    }

    @Test
    void shouldFailWhenAppNotFound() {
        SignChecker checker = checker(accessKey -> Optional.empty());

        SignResult result = checker.check(signedRequest("ak-001"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("应用不存在");
    }

    @Test
    void shouldFailWhenAppDisabled() {
        SignApp app = new SignApp("ak-001", SECRET);
        app.setEnabled(false);
        SignChecker checker = checker(accessKey -> Optional.of(app));

        SignResult result = checker.check(signedRequest("ak-001"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("应用已禁用");
    }

    @Test
    void shouldFailWhenAppExpired() {
        SignApp app = new SignApp("ak-001", SECRET);
        app.setExpireTime(LocalDateTime.now().minusDays(1));
        SignChecker checker = checker(accessKey -> Optional.of(app));

        SignResult result = checker.check(signedRequest("ak-001"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("应用已过期");
    }

    @Test
    void shouldFailWithWrongSecret() {
        SignApp app = new SignApp("ak-001", "another-secret");
        SignChecker checker = checker(accessKey -> Optional.of(app));

        SignResult result = checker.check(signedRequest("ak-001"));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("签名验证失败");
    }

    /** 开启防重放、带真实内存 nonce 存储的校验器 */
    private SignChecker replayChecker(SignAppProvider provider) {
        SignProperties properties = new SignProperties();
        properties.setReplayProtect(true);
        // 简单内存 nonce：首次 true，再次 false
        Set<String> used = new HashSet<>();
        NonceStore store = (key, ttl) -> used.add(key);
        return new SignChecker(properties, store, new ObjectMapper(), provider);
    }

    /** 按指定时间戳（秒）重新签名的请求 */
    private MockHttpServletRequest signedRequestAt(String accessKey, long timestampSeconds) {
        Map<String, String> biz = new HashMap<>();
        biz.put("orderNo", "A100");
        biz.put("accessKey", accessKey);
        biz.put("timestamp", String.valueOf(timestampSeconds));
        biz.put("nonce", "fixed-nonce-123");
        String sign = SignGenerator.generate(biz, SECRET, SignAlgorithm.HMAC_SHA256);
        biz.put("sign", sign);
        MockHttpServletRequest request = new MockHttpServletRequest();
        biz.forEach(request::addParameter);
        return request;
    }

    @Test
    void shouldRejectFutureTimestamp() {
        SignApp app = new SignApp("ak-001", SECRET);
        SignChecker checker = checker(accessKey -> Optional.of(app));
        // 未来 50 秒（超过 5 秒时钟偏移容忍）：防"时间旅行"扩大重放窗口
        long future = System.currentTimeMillis() / 1000 + 50;

        SignResult result = checker.check(signedRequestAt("ak-001", future));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("签名已过期");
    }

    @Test
    void shouldRejectReplayedNonce() {
        SignApp app = new SignApp("ak-001", SECRET);
        SignChecker checker = replayChecker(accessKey -> Optional.of(app));
        long now = System.currentTimeMillis() / 1000;

        // 同一 nonce 首次通过、二次被拒
        assertThat(checker.check(signedRequestAt("ak-001", now)).success()).isTrue();
        SignResult replay = checker.check(signedRequestAt("ak-001", now));
        assertThat(replay.success()).isFalse();
        assertThat(replay.message()).contains("重复");
    }

    // ------------------------------------------------------------------ JSON 请求体：fail-closed 回归

    @Test
    void shouldFailClosedWhenJsonBodyMalformed() {
        SignApp app = new SignApp("ak-001", SECRET);
        SignChecker checker = checker(accessKey -> Optional.of(app));
        // Query 参数上的签名完全合法，仅请求体是畸形 JSON：历史实现会只记一条 WARN 后按 Query 验签通过，
        // 而业务侧的容错解析仍可能读出字段 ⇒ 签名降级绕过。必须 fail-closed。
        MockHttpServletRequest request = signedRequest("ak-001");
        request.setContentType("application/json");
        request.setContent("{\"orderNo\": \"A100\", ".getBytes(StandardCharsets.UTF_8));

        SignResult result = checker.check(request);

        assertThat(result.success())
            .as("Content-Type 声明 JSON 且请求体畸形时必须拒绝，不得仅按 Query 参数验签通过")
            .isFalse();
        assertThat(result.message()).isEqualTo("JSON 请求体格式非法");
    }

    @Test
    void shouldRejectWhenJsonBodyCarriesUnsignedField() {
        SignApp app = new SignApp("ak-001", SECRET);
        SignChecker checker = checker(accessKey -> Optional.of(app));
        // 客户端只对 orderNo 签名，却在 body 里多塞一个字段：JSON 必须参与签名计算，多出字段即不匹配。
        // 本用例是「JSON 合并逻辑不得被旁路」的守卫：若有人把 mergeJsonBody 改成 no-op，它会失败。
        MockHttpServletRequest request = signedRequest("ak-001");
        request.setContentType("application/json;charset=UTF-8");
        request.setContent("{\"orderNo\":\"A100\",\"extra\":\"x\"}".getBytes(StandardCharsets.UTF_8));

        SignResult result = checker.check(request);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("签名验证失败");
    }

    @Test
    void shouldPassWhenJsonBodyMatchesSignedParams() {
        SignApp app = new SignApp("ak-001", SECRET);
        SignChecker checker = checker(accessKey -> Optional.of(app));
        MockHttpServletRequest request = signedRequest("ak-001");
        request.setContentType("application/json");
        request.setContent("{\"orderNo\":\"A100\"}".getBytes(StandardCharsets.UTF_8));

        SignResult result = checker.check(request);

        assertThat(result.success()).as("合法 JSON body 不应被 fail-closed 逻辑误伤").isTrue();
    }

    @Test
    void shouldPassWhenJsonContentTypeWithEmptyBody() {
        SignApp app = new SignApp("ak-001", SECRET);
        SignChecker checker = checker(accessKey -> Optional.of(app));
        // 空请求体两侧都读不到 body 字段，不存在降级空间，保持放行（与历史行为一致）
        MockHttpServletRequest request = signedRequest("ak-001");
        request.setContentType("application/json");
        request.setContent(new byte[0]);

        SignResult result = checker.check(request);

        assertThat(result.success()).as("空请求体不构成解析失败").isTrue();
    }

    // ---------------------------------------- 结构化 JSON 后缀（application/*+json）：与 Spring MVC 口径一致

    @Test
    void shouldRejectUnsignedFieldInStructuredJsonContentType() {
        // Spring MVC 默认 Jackson 转换器同时接受 application/json 与 application/*+json。
        // 若签名侧只认字面量 application/json，攻击者只改一个 Content-Type 头就能让 body 里的
        // 未签名字段绕过签名（业务侧依然读得到）—— 这正是必须堵死的降级绕过。
        SignApp app = new SignApp("ak-001", SECRET);
        SignChecker checker = checker(accessKey -> Optional.of(app));
        MockHttpServletRequest request = signedRequest("ak-001");
        request.setContentType("application/vnd.api+json");
        request.setContent("{\"extra\":\"x\"}".getBytes(StandardCharsets.UTF_8));

        SignResult result = checker.check(request);

        assertThat(result.success())
            .as("application/*+json 的请求体必须参与签名计算")
            .isFalse();
        assertThat(result.message()).isEqualTo("签名验证失败");
    }

    @Test
    void shouldFailClosedOnMalformedStructuredJsonBody() {
        SignApp app = new SignApp("ak-001", SECRET);
        SignChecker checker = checker(accessKey -> Optional.of(app));
        MockHttpServletRequest request = signedRequest("ak-001");
        request.setContentType("application/merge-patch+json");
        request.setContent("{\"orderNo\": ".getBytes(StandardCharsets.UTF_8));

        SignResult result = checker.check(request);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("JSON 请求体格式非法");
    }

    @Test
    void shouldPassWithStructuredJsonBodyMatchingSignature() {
        SignApp app = new SignApp("ak-001", SECRET);
        SignChecker checker = checker(accessKey -> Optional.of(app));
        MockHttpServletRequest request = signedRequest("ak-001");
        request.setContentType("application/vnd.api+json;charset=UTF-8");
        request.setContent("{\"orderNo\":\"A100\"}".getBytes(StandardCharsets.UTF_8));

        SignResult result = checker.check(request);

        assertThat(result.success()).as("结构化 JSON 后缀不应被误伤").isTrue();
    }

    @Test
    void shouldRejectJsonContentTypeUnderTurkishLocale() {
        // toLowerCase() 不带 Locale 时，tr-TR 会把 'I' 转成无点的 'ı'（'APPLICATION/JSON' → 'applıcatıon/json'），
        // 曾使 JSON 判定失效。这里显式钉住「默认 Locale 不影响判定」。
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            SignApp app = new SignApp("ak-001", SECRET);
            SignChecker checker = checker(accessKey -> Optional.of(app));
            MockHttpServletRequest request = signedRequest("ak-001");
            request.setContentType("APPLICATION/JSON");
            request.setContent("{\"extra\":\"x\"}".getBytes(StandardCharsets.UTF_8));

            SignResult result = checker.check(request);

            assertThat(result.success()).as("tr-TR 区域下 JSON 判定不得失效").isFalse();
        } finally {
            Locale.setDefault(original);
        }
    }
}
