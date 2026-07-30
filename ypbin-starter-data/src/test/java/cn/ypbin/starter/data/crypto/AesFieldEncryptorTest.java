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
package cn.ypbin.starter.data.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link AesFieldEncryptor} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-30
 */
class AesFieldEncryptorTest {

    private final FieldEncryptor encryptor = new AesFieldEncryptor("1234567890abcdef");

    @Test
    void encryptThenDecrypt_shouldReturnOriginal() {
        String plain = "13800008000";
        String cipher = encryptor.encrypt(plain);
        assertThat(cipher).isNotEqualTo(plain);
        assertThat(encryptor.decrypt(cipher)).isEqualTo(plain);
    }

    @Test
    void encrypt_sameInput_shouldProduceDifferentCipher_dueToRandomIv() {
        String plain = "hello";
        assertThat(encryptor.encrypt(plain)).isNotEqualTo(encryptor.encrypt(plain));
    }

    @Test
    void nullInput_shouldReturnNull() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.decrypt(null)).isNull();
    }

    @Test
    void decrypt_supportsUnicode() {
        String plain = "身份证：110101199001011234";
        assertThat(encryptor.decrypt(encryptor.encrypt(plain))).isEqualTo(plain);
    }
}
