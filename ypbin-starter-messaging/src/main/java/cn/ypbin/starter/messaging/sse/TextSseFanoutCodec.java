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

import java.math.BigDecimal;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * 默认的零依赖纯文本扇出载荷编解码器。
 *
 * <p>帧内载荷格式为「单字符类型标签 + 文本」，标签含义：{@code n}=null、{@code b1}/{@code b0}=布尔、
 * {@code d<数字>}={@link BigDecimal}（原样十进制，不经过二进制浮点）、{@code s<文本>}=字符串、
 * {@code c<字符>}=字符。枚举按名字编成字符串（跨实例无类型信息，刻意不猜枚举类型）。</p>
 *
 * <p><strong>数字的协议边界（不静默降级）</strong>：{@code d<数字>} 分支要求文本能被
 * {@link BigDecimal} 解析。{@link Double#NaN}、正负 {@link Double#POSITIVE_INFINITY} 等非有限值，
 * 以及 {@link Float} 的同类值，都无法用十进制字面量表达；{@link #encode(Object)} 对它们一律返回
 * {@link Optional#empty()}（即「本 codec 承载不了」），由发布方记 WARN 并只投本实例。判据不是维护一张
 * 「哪些 {@link Number} 子类可能非有限」的清单，而是直接问「这个十进制文本 decode 端能否还原」，故
 * {@code Double.NaN}/{@code Float.NaN}/{@code ±Infinity} 乃至 {@code DoubleAdder} 之类
 * {@code toString()} 非十进制字面量的 {@code Number} 子类都被同一判据挡住——<strong>encode 产出的帧
 * 一定可被 decode 还原</strong>是结构性成立的，而不是靠两侧各写一份类型判断。{@link BigDecimal}
 * 自身无法表示 NaN/无穷，天然通过。</p>
 *
 * <p><strong>解码的容错（不抛异常）</strong>：{@code d<文本>} 的文本无法解析时
 * （典型：滚动升级窗口内旧版本对端把 {@code NaN}/{@code Infinity} 直接编进了帧）
 * <strong>绝不抛异常</strong>，而是按与未知标签相同的「保留原文」策略返回去掉标签后的文本——单条坏帧
 * 不得让订阅线程出错，也不应把整帧丢掉（原文对排查更有用）。</p>
 *
 * <p>对象/数组等结构化载荷<strong>不支持</strong>，{@link #encode(Object)} 返回空，由发布方记 WARN；
 * 需要它们的业务注册自己的 {@link SseFanoutCodec}。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public class TextSseFanoutCodec implements SseFanoutCodec {

    /** 与 {@link SseFanoutCodec#id()} 对应的协议标识；改动帧格式时必须同步改这里。 */
    public static final String ID = "text-v1";

    /** 帧内类型标签：null。 */
    private static final char TAG_NULL = 'n';

    /** 帧内类型标签：布尔（体见 {@link #BOOLEAN_TRUE_BODY}/{@link #BOOLEAN_FALSE_BODY}）。 */
    private static final char TAG_BOOLEAN = 'b';

    /** 帧内类型标签：数字（体须是 {@link BigDecimal} 可解析的十进制文本）。 */
    private static final char TAG_NUMBER = 'd';

    /** 帧内类型标签：字符串（枚举亦按名字编到这里）。 */
    private static final char TAG_STRING = 's';

    /** 帧内类型标签：字符。 */
    private static final char TAG_CHAR = 'c';

    /** 布尔真值体。 */
    private static final String BOOLEAN_TRUE_BODY = "1";

    /** 布尔假值体。 */
    private static final String BOOLEAN_FALSE_BODY = "0";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<String> encode(Object payload) {
        if (payload == null) {
            return Optional.of(String.valueOf(TAG_NULL));
        }
        if (payload instanceof Boolean value) {
            return Optional.of(TAG_BOOLEAN + (value ? BOOLEAN_TRUE_BODY : BOOLEAN_FALSE_BODY));
        }
        if (payload instanceof Number value) {
            String text = value.toString();
            // 无法用十进制字面量表达的数字（NaN/±Infinity 等）不产出无法解码的帧，显式返回「承载不了」
            return isDecimalText(text) ? Optional.of(TAG_NUMBER + text) : Optional.empty();
        }
        if (payload instanceof CharSequence value) {
            return Optional.of(TAG_STRING + value.toString());
        }
        if (payload instanceof Character value) {
            return Optional.of(TAG_CHAR + value.toString());
        }
        if (payload instanceof Enum<?> value) {
            return Optional.of(TAG_STRING + value.name());
        }
        return Optional.empty();
    }

    @Override
    @Nullable
    public Object decode(String text) {
        if (text.isEmpty()) {
            // 空载荷：按原样返回，不抛异常（对端 codec 未来可能用空串表达别的语义）
            return "";
        }
        char tag = text.charAt(0);
        String body = text.substring(1);
        return switch (tag) {
            case TAG_NULL -> null;
            case TAG_BOOLEAN -> BOOLEAN_TRUE_BODY.equals(body);
            case TAG_NUMBER -> decodeDecimal(body);
            case TAG_CHAR -> body.isEmpty() ? "" : body.charAt(0);
            // TAG_STRING 与未知标签：按字符串返回（未知标签说明协议版本不一致，但保留原文比丢数据更可诊断）
            default -> body;
        };
    }

    /**
     * 数字的十进制文本能否被 {@code d<数字>} 协议承载。
     *
     * <p>判据与 {@link #decodeDecimal(String)} 的还原方式<strong>同源</strong>（都用 {@link BigDecimal}
     * 解析），故「encode 产出的帧一定能被 decode 还原」不是靠两侧清单保持一致，而是同一个解析器给出的
     * 结论。{@link BigDecimal} 自身无法表示 NaN/无穷，无需特殊处理。</p>
     *
     * @param text 数字的 {@link Object#toString()} 结果
     * @return 可被协议承载返回 {@code true}
     */
    private static boolean isDecimalText(String text) {
        try {
            new BigDecimal(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 还原 {@code d<数字>} 的体文本。
     *
     * <p>解析失败<strong>不抛异常</strong>：按与未知标签相同的「保留原文」策略返回体文本，交业务侧判断。</p>
     *
     * @param body 标签之后的文本
     * @return {@link BigDecimal}；无法解析时为体文本原文
     */
    private static Object decodeDecimal(String body) {
        try {
            return new BigDecimal(body);
        } catch (NumberFormatException e) {
            return body;
        }
    }
}
