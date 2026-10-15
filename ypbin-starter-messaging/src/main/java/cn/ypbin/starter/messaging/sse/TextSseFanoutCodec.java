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
 * <p>对象/数组等结构化载荷<strong>不支持</strong>，{@link #encode(Object)} 返回空，由发布方记 WARN；
 * 需要它们的业务注册自己的 {@link SseFanoutCodec}。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public class TextSseFanoutCodec implements SseFanoutCodec {

    /** 与 {@link SseFanoutCodec#id()} 对应的协议标识；改动帧格式时必须同步改这里。 */
    public static final String ID = "text-v1";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<String> encode(Object payload) {
        if (payload == null) {
            return Optional.of("n");
        }
        if (payload instanceof Boolean value) {
            return Optional.of(value ? "b1" : "b0");
        }
        if (payload instanceof Number value) {
            return Optional.of("d" + value);
        }
        if (payload instanceof CharSequence value) {
            return Optional.of("s" + value);
        }
        if (payload instanceof Character value) {
            return Optional.of("c" + value);
        }
        if (payload instanceof Enum<?> value) {
            return Optional.of("s" + value.name());
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
            case 'n' -> null;
            case 'b' -> "1".equals(body);
            case 'd' -> new BigDecimal(body);
            case 'c' -> body.isEmpty() ? "" : body.charAt(0);
            // 's' 与未知标签：按字符串返回（未知标签说明协议版本不一致，但保留原文比丢数据更可诊断）
            default -> body;
        };
    }
}
