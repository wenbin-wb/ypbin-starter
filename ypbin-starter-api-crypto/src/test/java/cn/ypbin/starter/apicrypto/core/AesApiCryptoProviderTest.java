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
package cn.ypbin.starter.apicrypto.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@link AesApiCryptoProvider} 单元测试：AES-GCM 加解密往返、随机 IV、错误暴露。
 *
 * @author wenbin
 * @since 2026-08-05
 */
class AesApiCryptoProviderTest {

    /** 16 字节 AES 密钥 */
    private static final String KEY = "1234567890abcdef";

    private final AesApiCryptoProvider provider = new AesApiCryptoProvider(KEY);

    @Test
    void encryptThenDecrypt_shouldRoundTrip() {
        String plain = "{\"userId\":\"123\",\"amount\":99.5}";

        String cipher = provider.encrypt(plain);
        String decrypted = provider.decrypt(cipher);

        assertThat(cipher).isNotEqualTo(plain);
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    void encrypt_shouldRoundTripUnicodeAndEmpty() {
        assertThat(provider.decrypt(provider.encrypt("中文内容与 emoji 🎉"))).isEqualTo("中文内容与 emoji 🎉");
        assertThat(provider.decrypt(provider.encrypt(""))).isEmpty();
    }

    @Test
    void encrypt_shouldUseRandomIvSoSameInputYieldsDifferentCipher() {
        String plain = "same-plaintext";

        String c1 = provider.encrypt(plain);
        String c2 = provider.encrypt(plain);

        // 随机 IV 前置 → 两次密文不同，但都能解回同一明文
        assertThat(c1).isNotEqualTo(c2);
        assertThat(provider.decrypt(c1)).isEqualTo(plain);
        assertThat(provider.decrypt(c2)).isEqualTo(plain);
    }

    @Test
    void decrypt_withWrongKey_shouldThrowNotSilentlyFail() {
        String cipher = provider.encrypt("secret");
        AesApiCryptoProvider otherKey = new AesApiCryptoProvider("fedcba0987654321");

        // 错误密钥必须暴露异常，而非返回错误明文或 null（不静默降级）
        assertThatThrownBy(() -> otherKey.decrypt(cipher))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("解密失败");
    }

    @Test
    void decrypt_withMalformedCipher_shouldThrow() {
        assertThatThrownBy(() -> provider.decrypt("not-a-valid-base64-cipher!!!"))
            .isInstanceOf(IllegalStateException.class);
    }
}
