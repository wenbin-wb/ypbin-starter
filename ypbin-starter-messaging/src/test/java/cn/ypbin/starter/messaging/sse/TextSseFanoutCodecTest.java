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

    /**
     * 非有限数字无法用 {@code d<十进制>} 表达：encode 必须返回空（显式降级），而不是产出
     * {@code dNaN}/{@code dInfinity} 这种两侧 decode 都会失败的帧。
     *
     * <p>修前实测：{@code encode(Double.NaN)} 得到 {@code dNaN}，任何 decode 都抛
     * {@link NumberFormatException}；订阅线程会因单帧异常受影响。本用例钉死「承载不了就返回空」。</p>
     */
    @Test
    void nonFiniteNumbersAreRejectedInsteadOfProducingUndecodableFrames() {
        assertThat(codec.encode(Double.NaN)).isEmpty();
        assertThat(codec.encode(Double.POSITIVE_INFINITY)).isEmpty();
        assertThat(codec.encode(Double.NEGATIVE_INFINITY)).isEmpty();
        assertThat(codec.encode(Float.NaN)).isEmpty();
        assertThat(codec.encode(Float.POSITIVE_INFINITY)).isEmpty();
        assertThat(codec.encode(Float.NEGATIVE_INFINITY)).isEmpty();
        // 判据是「toString 能否被 BigDecimal 解析」，故非 Double/Float 的 Number 子类同样被挡住
        // （典型：DoubleAdder 累加过 NaN，toString() 返回 "NaN"）
        assertThat(codec.encode(new NonFiniteNumber())).isEmpty();
    }

    /** 有限数字（含极端值、科学计数法、超大 BigDecimal）必须照常可承载，不能因上面的守卫误伤。 */
    @Test
    void finiteNumbersRemainEncodable() {
        assertThat(codec.encode(Double.MIN_VALUE)).contains("d" + Double.MIN_VALUE);
        assertThat(codec.encode(Double.MAX_VALUE)).contains("d" + Double.MAX_VALUE);
        assertThat(codec.encode(Float.MIN_VALUE)).contains("d" + Float.MIN_VALUE);
        assertThat(codec.encode(new BigDecimal("1E+1000"))).contains("d1E+1000");
        java.math.BigInteger huge = new java.math.BigInteger("9".repeat(100));
        assertThat(codec.encode(huge)).contains("d" + huge);
    }

    /**
     * {@code d<文本>} 无法解析时<strong>不得抛异常</strong>：按与未知标签相同的「保留原文」策略返回
     * 去掉标签后的文本（滚动升级窗口内，旧版本对端可能把 NaN/无穷直接编进了帧）。
     */
    @Test
    void unparseableNumberTextDecodesToRawTextWithoutThrowing() {
        assertThat(codec.decode("dNaN")).isEqualTo("NaN");
        assertThat(codec.decode("dInfinity")).isEqualTo("Infinity");
        assertThat(codec.decode("d-Infinity")).isEqualTo("-Infinity");
        assertThat(codec.decode("d")).isEqualTo("");
        assertThat(codec.decode("dnot-a-number")).isEqualTo("not-a-number");
        // 合法数字仍还原为 BigDecimal（不只是「不抛」）
        assertThat(codec.decode("d1E+3")).isEqualTo(new BigDecimal("1E+3"));
    }

    @Test
    void codecIdMatchesProtocolConstant() {
        assertThat(codec.id()).isEqualTo(TextSseFanoutCodec.ID);
        assertThat(codec.id()).doesNotContain(":");
    }

    /** 非有限的自定义 {@link Number} 子类（如累加过 NaN 的 DoubleAdder）：toString 不是十进制字面量。 */
    private static final class NonFiniteNumber extends Number {

        @Override
        public int intValue() {
            return 0;
        }

        @Override
        public long longValue() {
            return 0L;
        }

        @Override
        public float floatValue() {
            return Float.NaN;
        }

        @Override
        public double doubleValue() {
            return Double.NaN;
        }

        @Override
        public String toString() {
            return "NaN";
        }
    }

    private enum AnEnum {
        A,
        B
    }
}
