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
import java.util.Map;
import java.util.Optional;
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
}
