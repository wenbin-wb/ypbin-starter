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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link JacksonSseFanoutCodec} 用例：标量与 {@link TextSseFanoutCodec} 逐项一致、结构化载荷编成规范 JSON、
 * 不可序列化载荷显式返回空（不静默降级）、特殊字符经扇出帧协议不破坏。
 *
 * @author wenbin
 * @since 2026-10-17
 */
class JacksonSseFanoutCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final JacksonSseFanoutCodec codec = new JacksonSseFanoutCodec(objectMapper);

    private final TextSseFanoutCodec textCodec = new TextSseFanoutCodec();

    /**
     * 标量语义必须与 {@code text-v1} <strong>逐项一致</strong>：一旦 Jackson 把字符串编成带引号的 JSON
     * 或把数字编成二进制浮点，跨实例投递内容形态就变了（对外行为变化）。故这里逐项比对两个 codec 的
     * 帧文本与解码结果，而不是各自断言。
     */
    @Test
    void scalarsRoundTripExactlyLikeTextCodec() {
        // Arrays.asList 允许 null（List.of 不允许），覆盖 SSE 的「事件名 + 空数据」语义
        List<Object> scalars = Arrays.asList(null, true, false, 25, 25L, new BigDecimal("25.0"), 25.5d,
            "OFF", "a:b|c\nd\"e\\f", "中文😀", 'x', AnEnum.B);

        for (Object value : scalars) {
            Optional<String> jacksonFrame = codec.encode(value);
            assertThat(jacksonFrame).as("encode(%s) 必须与 text-v1 一致", value).isEqualTo(textCodec.encode(value));
            assertThat(jacksonFrame).as("标量必须被承载").isPresent();
            String frame = jacksonFrame.orElseThrow();
            assertThat(frame).as("标量不得落到 JSON 分支").doesNotStartWith(String.valueOf(JacksonSseFanoutCodec.JSON_PREFIX));
            assertThat(codec.decode(frame)).as("decode(%s)", frame).isEqualTo(textCodec.decode(frame));
        }
    }

    /** 显式钉住解码类型：布尔/数字/字符串/字符的还原形态与 Text codec 相同。 */
    @Test
    void scalarsDecodeToSameJavaTypesAsTextCodec() {
        assertThat(codec.decode("n")).isNull();
        assertThat(codec.decode("b1")).isEqualTo(true);
        assertThat(codec.decode("b0")).isEqualTo(false);
        assertThat(codec.decode("d25.0")).isInstanceOf(BigDecimal.class).isEqualTo(new BigDecimal("25.0"));
        assertThat(codec.decode("sOFF")).isInstanceOf(String.class).isEqualTo("OFF");
        assertThat(codec.decode("cx")).isInstanceOf(Character.class).isEqualTo('x');
        // 空帧与未知标签沿用 text-v1 的「保留原文」策略
        assertThat(codec.decode("")).isEqualTo("");
        assertThat(codec.decode("zraw")).isEqualTo("raw");
    }

    /**
     * 非有限数字（NaN/±Infinity）与 {@link TextSseFanoutCodec} 一致地返回空，<strong>不得</strong>落到
     * JSON 分支被 Jackson 写成带引号的 {@code "NaN"}：那会让「标量语义与 text-v1 逐字节一致」这条对外
     * 契约漂移，且 JSON 本身没有 NaN/无穷的标准表示。
     */
    @Test
    void nonFiniteNumbersAreRejectedExactlyLikeTextCodec() {
        for (Object value : List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThat(codec.encode(value)).as("encode(%s)", value).isEmpty();
            assertThat(codec.encode(value)).isEqualTo(textCodec.encode(value));
        }
    }

    /** {@code d<文本>} 无法解析时与 text-v1 一致地保留原文、不抛异常（委托实现，两侧同源）。 */
    @Test
    void unparseableNumberFrameDecodesExactlyLikeTextCodec() {
        for (String frame : List.of("dNaN", "dInfinity", "d-Infinity", "d", "dnot-a-number")) {
            assertThat(codec.decode(frame)).as("decode(%s)", frame).isEqualTo(textCodec.decode(frame));
        }
        assertThat(codec.decode("dNaN")).isEqualTo("NaN");
    }

    /** 结构化载荷（含嵌套 Map/List、自定义 record）编成 {@code j<JSON>}，解码得到合法 JSON 文本。 */
    @Test
    void structuredPayloadRoundTripsAsCanonicalJsonText() {
        Reading reading = new Reading("d1", 25, List.of("a|b", "换行\n行", "emoji😀", "引号\"反斜杠\\"));
        Optional<String> encoded = codec.encode(reading);
        assertThat(encoded).isPresent();

        String frame = encoded.orElseThrow();
        assertThat(frame).startsWith(String.valueOf(JacksonSseFanoutCodec.JSON_PREFIX));
        String json = frame.substring(1);
        assertThat(objectMapper.readTree(json)).isEqualTo(objectMapper.readTree(
            "{\"deviceId\":\"d1\",\"value\":25,\"tags\":[\"a|b\",\"换行\\n行\",\"emoji😀\",\"引号\\\"反斜杠\\\\\"]}"));

        Object decoded = codec.decode(frame);
        assertThat(decoded).isInstanceOf(String.class);
        assertThat(objectMapper.readTree((String) decoded)).isEqualTo(objectMapper.readTree(json));

        // 嵌套 Map/List 同样走 JSON 分支（业务不必为每个模型注册 codec）
        Map<String, Object> nested = Map.of("list", List.of(Map.of("n", 1)), "flag", true);
        String nestedFrame = codec.encode(nested).orElseThrow();
        assertThat(nestedFrame).startsWith(String.valueOf(JacksonSseFanoutCodec.JSON_PREFIX));
        assertThat(objectMapper.readTree(nestedFrame.substring(1)))
            .isEqualTo(objectMapper.readTree("{\"list\":[{\"n\":1}],\"flag\":true}"));
    }

    /**
     * 不可序列化的载荷必须返回空，交由发布方记 WARN 并只投本实例；不得 {@code toString()} 成垃圾帧。
     *
     * <p>失败形态已实测（Jackson 3.1.5）：自引用环抛 {@code InvalidDefinitionException}
     * （{@code JacksonException} 子类，Jackson 3 里为<strong>非受检</strong>异常），getter 抛异常时包成
     * {@code DatabindException}；两者都必须被收敛为 {@link Optional#empty()}。</p>
     */
    @Test
    void unserializablePayloadReturnsEmptyInsteadOfGarbageFrame() {
        assertThat(codec.encode(new SelfReferencing())).isEmpty();
        assertThat(codec.encode(new ThrowingGetter())).isEmpty();
    }

    /**
     * 载荷<b>自身</b>抛出的非 Jackson 运行时异常同样必须收敛为 {@link Optional#empty()}。
     *
     * <p>独立验收实测：{@code Map.entrySet()} / {@code Iterable.iterator()} 抛出的异常会从
     * {@code writeValueAsString} 原样逃逸（并非 {@code JacksonException}），而
     * {@code SseEmitterManager.publishFanout} 的 {@code codec.encode(...)} 调用在 try/catch 之外，
     * 异常会反向抛回业务发布线程（典型触发：懒加载集合在 session 关闭后 {@code iterator()} 抛
     * {@code LazyInitializationException}）。</p>
     */
    @Test
    void payloadThrowingRuntimeExceptionDuringSerializationReturnsEmpty() {
        assertThat(codec.encode(new ExplodingEntrySetMap())).isEmpty();
        assertThat(codec.encode(new ExplodingIterable())).isEmpty();
    }

    /**
     * 帧协议对任意字符安全的证据：{@link SseFanoutProtocol} 的每个变长字段都是「十进制长度 + ':' + 原文」，
     * 且编码端用 {@link String#length()}（UTF-16 码元数）、解码端也按同一计数切分，故 {@code |}、{@code :}、
     * 换行、以及需要代理对的 emoji 都不会截错或丢字符。这里让 codec 产出的 JSON 里带上全部这些字符，
     * 再走一遍真实的帧编解码。
     */
    @Test
    void trickyCharactersSurviveFrameProtocolRoundTrip() {
        String tricky = "a|b\nc:d\"e\\f 中文😀";
        // 帧里承载的是 codec 的完整输出（含 'j' 前缀），与生产发布路径一致
        String payloadText = codec.encode(Map.of("text", tricky)).orElseThrow();
        assertThat(payloadText).startsWith(String.valueOf(JacksonSseFanoutCodec.JSON_PREFIX));

        SseFanoutMessage message =
            new SseFanoutMessage("latest", "tenant|:7\n中文😀", JacksonSseFanoutCodec.ID, payloadText);
        String frame = SseFanoutProtocol.encode(message, "inst|:1\n😀");
        SseFanoutProtocol.Decoded decoded = SseFanoutProtocol.decode(frame);

        assertThat(decoded).isNotNull();
        assertThat(decoded.message()).isEqualTo(message);
        assertThat(decoded.fromInstanceId()).isEqualTo("inst|:1\n😀");
        assertThat(decoded.message().payloadText()).isEqualTo(payloadText);

        Object payload = codec.decode(decoded.message().payloadText());
        assertThat(payload).isInstanceOf(String.class);
        assertThat(objectMapper.readTree((String) payload).get("text").asString()).isEqualTo(tricky);
    }

    @Test
    void codecIdMatchesProtocolConstantAndIsFrameSafe() {
        assertThat(codec.id()).isEqualTo(JacksonSseFanoutCodec.ID).isEqualTo("json-v1");
        assertThat(codec.id()).doesNotContain(":");
        assertThat(codec.id()).isNotEqualTo(TextSseFanoutCodec.ID);
    }

    @Test
    void nullObjectMapperIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new JacksonSseFanoutCodec(null)).isInstanceOf(NullPointerException.class);
    }

    /** 自引用环：Jackson 3 默认「直接自引用」即失败（不是无限递归）。字段必须 public，否则 Jackson 看不见。 */
    private static final class SelfReferencing {

        public final SelfReferencing self = this;
    }

    /** getter 抛异常：序列化失败必须被收敛为「不支持」，而不是把异常抛给发布方线程。 */
    private static final class ThrowingGetter {

        public String getBoom() {
            throw new IllegalStateException("boom-getter");
        }
    }

    /** 业务自定义 record（issue #97 的典型载荷形态）。 */
    private record Reading(String deviceId, int value, List<String> tags) {
    }

    /** entrySet() 抛运行时异常的自定义 Map（Jackson 序列化 Map 时会调用 entrySet()）。 */
    private static final class ExplodingEntrySetMap extends AbstractMap<String, String> {

        @Override
        public Set<Entry<String, String>> entrySet() {
            throw new IllegalStateException("boom-entrySet");
        }
    }

    /** iterator() 抛运行时异常的自定义 Iterable。 */
    private static final class ExplodingIterable implements Iterable<String> {

        @Override
        public Iterator<String> iterator() {
            throw new IllegalStateException("boom-iterator");
        }
    }

    private enum AnEnum {
        A,
        B
    }
}
