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

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * {@link AesUtils} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class AesUtilsTest {

    @Test
    void stringRoundTrip_shouldRecoverPlainText() {
        byte[] key = AesUtils.generateKey(256);
        String cipher = AesUtils.encrypt("hello 中文", key);

        assertThat(AesUtils.decrypt(cipher, key)).isEqualTo("hello 中文");
    }

    @Test
    void bytesRoundTrip_shouldRecoverPlainBytes() {
        byte[] key = AesUtils.generateKey(128);
        byte[] plain = "binary-data".getBytes(StandardCharsets.UTF_8);

        byte[] cipher = AesUtils.encryptBytes(plain, key);
        assertThat(AesUtils.decryptBytes(cipher, key)).isEqualTo(plain);
    }

    @Test
    void randomIv_shouldProduceDifferentCipherForSamePlainText() {
        byte[] key = AesUtils.generateKey(256);

        // GCM 每次随机 IV，同一明文两次密文不同
        assertThat(AesUtils.encrypt("same", key)).isNotEqualTo(AesUtils.encrypt("same", key));
    }

    @Test
    void generateKeyBase64AndDecode_shouldBeUsable() {
        String base64Key = AesUtils.generateKeyBase64(256);
        byte[] key = AesUtils.decodeKey(base64Key);

        String cipher = AesUtils.encrypt("data", key);
        assertThat(AesUtils.decrypt(cipher, key)).isEqualTo("data");
    }

    @Test
    void deriveKey_sameSaltSamePassword_shouldDeriveSameKey() {
        byte[] salt = AesUtils.generateSalt(16);
        byte[] k1 = AesUtils.deriveKey("passphrase", salt, 256);
        byte[] k2 = AesUtils.deriveKey("passphrase", salt, 256);

        assertThat(k1).isEqualTo(k2);
        // 派生密钥可用于加解密
        String cipher = AesUtils.encrypt("secret", k1);
        assertThat(AesUtils.decrypt(cipher, k2)).isEqualTo("secret");
    }
}
