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

import org.junit.jupiter.api.Test;

/**
 * 国密 SM2 / SM4 往返测试。
 *
 * @author wenbin
 * @since 2026-07-30
 */
class SmCryptoTest {

    @Test
    void sm4_encryptThenDecrypt_returnsOriginal() {
        String key = "1234567890abcdef";
        String plain = "国密SM4测试-hello123";
        String cipher = Sm4Utils.encrypt(plain, key);
        assertThat(cipher).isNotEqualTo(plain);
        assertThat(Sm4Utils.decrypt(cipher, key)).isEqualTo(plain);
    }

    @Test
    void sm2_encryptThenDecrypt_returnsOriginal() {
        Sm2Utils.KeyPairBase64 keyPair = Sm2Utils.generateKeyPair();
        String plain = "国密SM2测试-secret";
        String cipher = Sm2Utils.encrypt(plain, keyPair.publicKey());
        assertThat(cipher).isNotEqualTo(plain);
        assertThat(Sm2Utils.decrypt(cipher, keyPair.privateKey())).isEqualTo(plain);
    }

    @Test
    void sm2_generateKeyPair_producesNonBlankKeys() {
        Sm2Utils.KeyPairBase64 keyPair = Sm2Utils.generateKeyPair();
        assertThat(keyPair.publicKey()).isNotBlank();
        assertThat(keyPair.privateKey()).isNotBlank();
    }
}
