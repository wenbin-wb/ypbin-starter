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
package cn.ypbin.starter.messaging.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * {@link TextSseFanoutCodec} 用例：标量类型无损往返、结构化对象显式拒绝（不静默降级）。
 *
 * @author wenbin
 * @since 2026-10-15
 */
class TextSseFanoutCodecTest {

    private final TextSseFanoutCodec codec = new TextSseFanoutCodec();

    @Test
    void scalarsRoundTripWithoutLoss() {
        assertThat(codec.encode(null)).contains("n");
        assertThat(codec.encode(true)).contains("b1");
        assertThat(codec.encode(false)).contains("b0");
        assertThat(codec.encode(25)).contains("d25");
        assertThat(codec.encode("OFF")).contains("sOFF");
        assertThat(codec.encode('x')).contains("cx");
        assertThat(codec.encode(AnEnum.B)).contains("sB");

        assertThat(codec.decode("n")).isNull();
        assertThat(codec.decode("b1")).isEqualTo(true);
        assertThat(codec.decode("b0")).isEqualTo(false);
        // 数字解码为 BigDecimal：不经二进制浮点，故 0.1+0.2 类误差不会出现
        assertThat(codec.decode("d25.0")).isEqualTo(new BigDecimal("25.0"));
        assertThat(codec.decode("sOFF")).isEqualTo("OFF");
        assertThat(codec.decode("cx")).isEqualTo('x');
    }

    @Test
    void structuredPayloadIsRejectedInsteadOfStringified() {
        // 关键纪律：不支持就必须返回空，让发布方记 WARN；不能 toString() 成垃圾帧投出去
        assertThat(codec.encode(new java.util.ArrayList<>(java.util.List.of(1)))).isEmpty();
        assertThat(codec.encode(java.util.Map.of("a", 1))).isEmpty();
    }

    @Test
    void codecIdMatchesProtocolConstant() {
        assertThat(codec.id()).isEqualTo(TextSseFanoutCodec.ID);
        assertThat(codec.id()).doesNotContain(":");
    }

    private enum AnEnum {
        A,
        B
    }
}
