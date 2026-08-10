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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

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
}
