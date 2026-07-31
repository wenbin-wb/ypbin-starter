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
package cn.ypbin.starter.tools.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.tools.crypto.Sm2Utils.KeyPairBase64;
import org.junit.jupiter.api.Test;

/**
 * {@link Sm2Utils} 加解密与签名验签单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class Sm2UtilsTest {

    @Test
    void encryptDecryptRoundTrip() {
        KeyPairBase64 kp = Sm2Utils.generateKeyPair();
        String cipher = Sm2Utils.encrypt("非对称明文", kp.publicKey());

        assertThat(Sm2Utils.decrypt(cipher, kp.privateKey())).isEqualTo("非对称明文");
    }

    @Test
    void signAndVerify_shouldPassForValidSignature() {
        KeyPairBase64 kp = Sm2Utils.generateKeyPair();
        String sign = Sm2Utils.sign("待签名数据", kp.privateKey());

        assertThat(Sm2Utils.verify("待签名数据", sign, kp.publicKey())).isTrue();
    }

    @Test
    void verify_shouldFailForTamperedData() {
        KeyPairBase64 kp = Sm2Utils.generateKeyPair();
        String sign = Sm2Utils.sign("原始数据", kp.privateKey());

        assertThat(Sm2Utils.verify("被篡改的数据", sign, kp.publicKey())).isFalse();
    }
}
