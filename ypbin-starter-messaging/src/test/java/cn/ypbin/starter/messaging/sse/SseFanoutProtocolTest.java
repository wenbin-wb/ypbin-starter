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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link SseFanoutProtocol} 纯文本长度前缀协议的编解码用例。
 *
 * <p>重点覆盖「做错了看不出」的部分：字段含分隔符/换行时的定界、长度越界与非法 kind/flag 的拒绝、
 * 以及帧内实例标识的还原（自广播回环判定的依据）。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
class SseFanoutProtocolTest {

    private static final String INSTANCE = "inst-1";

    @Test
    void audienceFrameRoundTrip() {
        SseFanoutMessage message =
            new SseFanoutMessage("latest", "tenant:7", TextSseFanoutCodec.ID, "d25.0");
        String frame = SseFanoutProtocol.encode(message, INSTANCE);
        SseFanoutProtocol.Decoded decoded = SseFanoutProtocol.decode(frame);
        assertThat(decoded).isNotNull();
        assertThat(decoded.fromInstanceId()).isEqualTo(INSTANCE);
        assertThat(decoded.message()).isEqualTo(message);
        assertThat(decoded.message().broadcast()).isFalse();
    }

    @Test
    void broadcastFrameRoundTrip() {
        SseFanoutMessage message =
            new SseFanoutMessage("alert", null, TextSseFanoutCodec.ID, "sx");
        SseFanoutProtocol.Decoded decoded = SseFanoutProtocol.decode(SseFanoutProtocol.encode(message, INSTANCE));
        assertThat(decoded).isNotNull();
        assertThat(decoded.message()).isEqualTo(message);
        assertThat(decoded.message().broadcast()).isTrue();
    }

    @Test
    void fieldsWithSeparatorsAndNewlinesSurviveLengthPrefixing() {
        // audience/event/payload 全含 ':' 与换行：拼接式协议会在这里截错，长度前缀不会
        SseFanoutMessage message = new SseFanoutMessage(
            "evt:with:colons\nand\nnewlines", "tenant:7:a|b", TextSseFanoutCodec.ID, "s1:2\n3");
        SseFanoutProtocol.Decoded decoded = SseFanoutProtocol.decode(SseFanoutProtocol.encode(message, INSTANCE));
        assertThat(decoded).isNotNull();
        assertThat(decoded.message()).isEqualTo(message);
    }

    @Test
    void instanceIdWithSeparatorDoesNotBreakBoundary() {
        // 关键：instanceId 含 ':' 也必须能精确还原（长度前缀定界的意义所在）
        String instance = "inst:with:colons";
        String frame = SseFanoutProtocol.encode(
            new SseFanoutMessage("e", "a", TextSseFanoutCodec.ID, "sx"), instance);
        SseFanoutProtocol.Decoded decoded = SseFanoutProtocol.decode(frame);
        assertThat(decoded).isNotNull();
        assertThat(decoded.fromInstanceId()).isEqualTo(instance);
    }

    @Test
    void malformedFramesAreRejectedWithoutThrowing() {
        String validAudience = SseFanoutProtocol.encode(
            new SseFanoutMessage("evt", "aud", TextSseFanoutCodec.ID, "sx"), INSTANCE);
        String validBroadcast = SseFanoutProtocol.encode(
            new SseFanoutMessage("evt", null, TextSseFanoutCodec.ID, "sx"), INSTANCE);

        List<String> bad = new ArrayList<>(List.of(
            // 结构性非法
            "",
            "X" + validAudience.substring(1),   // 非法 kind（替换首字符）
            "U",                                // kind 之后再无内容
            validAudience + "X",                // 尾随字符（长度前缀必须恰好耗尽）
            validAudience.substring(0, validAudience.length() - 1), // 截断最后一个字段
            validAudience.replaceFirst("^U\\d+:", "Ux:"),          // 长度前缀非数字
            validAudience.replaceFirst("^U", "B"),                  // kind 与 audience flag 不一致
            validBroadcast.replaceFirst("^B", "U")                  // 同上，反向
        ));
        for (String frame : bad) {
            assertThat(SseFanoutProtocol.decode(frame))
                .as("非法帧必须被拒绝且不抛异常：%s", frame)
                .isNull();
        }
    }

    @Test
    void oversizedLengthIsRejectedNotOverflowed() {
        // int 溢出会绕过越界检查：这里断言仍然拒绝（长度声明远大于实际内容）
        assertThat(SseFanoutProtocol.decode("U4:code1:inst1:a2147483647:x0:e")).isNull();
    }

    @Test
    void codecIdIsCarriedVerbatim() {
        // codecId 必须原样往返：订阅方据此识别「对端 codec 版本不同」并丢弃
        SseFanoutMessage message = new SseFanoutMessage("e", "a", "text-v2", "sx");
        SseFanoutProtocol.Decoded decoded = SseFanoutProtocol.decode(SseFanoutProtocol.encode(message, INSTANCE));
        assertThat(decoded).isNotNull();
        assertThat(decoded.message().codecId()).isEqualTo("text-v2");
    }
}
