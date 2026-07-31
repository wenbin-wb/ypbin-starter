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
 * {@link Sm4Utils} 多模式单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class Sm4UtilsTest {

    @Test
    void ecbBase64RoundTrip() {
        byte[] key = Sm4Utils.generateKey();
        String cipher = Sm4Utils.encrypt("国密数据", key);

        assertThat(Sm4Utils.decrypt(cipher, key)).isEqualTo("国密数据");
    }

    @Test
    void cbcRoundTrip() {
        byte[] key = Sm4Utils.generateKey();
        byte[] iv = Sm4Utils.generateIv(16);
        byte[] plain = "cbc-data".getBytes(StandardCharsets.UTF_8);

        byte[] cipher = Sm4Utils.encryptCbc(plain, key, iv);
        assertThat(Sm4Utils.decryptCbc(cipher, key, iv)).isEqualTo(plain);
    }

    @Test
    void gcmRoundTrip() {
        byte[] key = Sm4Utils.generateKey();
        byte[] plain = "gcm-data".getBytes(StandardCharsets.UTF_8);

        byte[] cipher = Sm4Utils.encryptGcm(plain, key);
        assertThat(Sm4Utils.decryptGcm(cipher, key)).isEqualTo(plain);
    }

    @Test
    void hexRoundTrip() {
        byte[] key = Sm4Utils.generateKey();
        String hex = Sm4Utils.encryptHex("hex-明文", key);

        assertThat(Sm4Utils.decryptHex(hex, key)).isEqualTo("hex-明文");
    }
}
