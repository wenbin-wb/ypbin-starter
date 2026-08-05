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
 * {@link Sm3Utils} 摘要与 HMAC 单元测试。
 *
 * @author wenbin
 * @since 2026-08-05
 */
class Sm3UtilsTest {

    @Test
    void digestHex_shouldBeStableAndFixedLength() {
        String first = Sm3Utils.digestHex("机器特征");
        String second = Sm3Utils.digestHex("机器特征");

        assertThat(first).isEqualTo(second);
        // SM3 输出 256 位 = 32 字节 = 64 位十六进制
        assertThat(first).hasSize(64);
    }

    @Test
    void digestHex_shouldDifferForDifferentInput() {
        assertThat(Sm3Utils.digestHex("A")).isNotEqualTo(Sm3Utils.digestHex("B"));
    }

    @Test
    void hmacHex_shouldBeStableForSameKey() {
        byte[] key = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

        assertThat(Sm3Utils.hmacHex("载荷", key)).isEqualTo(Sm3Utils.hmacHex("载荷", key));
        assertThat(Sm3Utils.hmacHex("载荷", key)).hasSize(64);
    }
}
