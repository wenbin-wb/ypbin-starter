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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link SignGenerator} / {@link SignClient} 签名一致性单元测试。
 *
 * @author wenbin
 * @since 2026-07-30
 */
class SignGeneratorTest {

    private Map<String, String> params() {
        Map<String, String> p = new HashMap<>();
        p.put("accessKey", "ak-001");
        p.put("orderNo", "A100");
        p.put("amount", "99.5");
        p.put("timestamp", "1700000000");
        p.put("nonce", "abc123");
        return p;
    }

    @Test
    void hmac_sameParams_sameSign() {
        String s1 = SignGenerator.generate(params(), "secret", SignAlgorithm.HMAC_SHA256);
        String s2 = SignGenerator.generate(params(), "secret", SignAlgorithm.HMAC_SHA256);
        assertThat(s1).isEqualTo(s2).matches("[0-9A-F]+");
    }

    @Test
    void hmac_orderIndependent() {
        // 参数插入顺序不同，签名应一致（内部按键字典序）
        Map<String, String> ordered = new LinkedHashMap<>();
        ordered.put("nonce", "abc123");
        ordered.put("amount", "99.5");
        ordered.put("accessKey", "ak-001");
        ordered.put("timestamp", "1700000000");
        ordered.put("orderNo", "A100");
        assertThat(SignGenerator.generate(ordered, "secret", SignAlgorithm.HMAC_SHA256))
            .isEqualTo(SignGenerator.generate(params(), "secret", SignAlgorithm.HMAC_SHA256));
    }

    @Test
    void differentSecret_differentSign() {
        assertThat(SignGenerator.generate(params(), "secretA", SignAlgorithm.HMAC_SHA256))
            .isNotEqualTo(SignGenerator.generate(params(), "secretB", SignAlgorithm.HMAC_SHA256));
    }

    @Test
    void md5AndHmac_produceDifferentSign() {
        assertThat(SignGenerator.generate(params(), "secret", SignAlgorithm.MD5))
            .isNotEqualTo(SignGenerator.generate(params(), "secret", SignAlgorithm.HMAC_SHA256));
    }

    @Test
    void signClient_producesVerifiableSign() {
        // 客户端生成 -> 服务端同算法重算应一致（模拟验签）
        Map<String, String> biz = new HashMap<>();
        biz.put("orderNo", "A100");
        Map<String, String> signed = SignClient.sign(biz, "ak-001", "secret", SignAlgorithm.HMAC_SHA256);

        String clientSign = signed.get("sign");
        assertThat(clientSign).isNotBlank();
        assertThat(signed).containsKeys("accessKey", "timestamp", "nonce", "sign", "orderNo");

        // 服务端：去掉 sign 后用同密钥同算法重算
        Map<String, String> toVerify = new HashMap<>(signed);
        toVerify.remove("sign");
        String serverSign = SignGenerator.generate(toVerify, "secret", SignAlgorithm.HMAC_SHA256);
        assertThat(serverSign).isEqualTo(clientSign);
    }
}
