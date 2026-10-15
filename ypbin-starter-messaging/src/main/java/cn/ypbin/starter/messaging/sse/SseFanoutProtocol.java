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

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * SSE 跨实例扇出帧的<strong>纯文本长度前缀协议</strong>（零依赖，刻意不引入 Jackson）。
 *
 * <p><strong>帧格式</strong>：每个可变长字段都是「十进制长度 + {@code ':'} + 原文」，因此任何字段都能
 * 含 {@code ':'}、换行或任意 Unicode 字符而不产生歧义：</p>
 * <pre>
 * &lt;kind&gt;&lt;codecIdLen&gt;:&lt;codecId&gt;&lt;instanceIdLen&gt;:&lt;instanceId&gt;&lt;audienceFlag&gt;&lt;audienceLen&gt;:&lt;audience&gt;&lt;payloadLen&gt;:&lt;payload&gt;&lt;eventLen&gt;:&lt;event&gt;
 * </pre>
 * <ul>
 *   <li>{@code kind}：{@code U}=按 audience 扇出、{@code B}=广播（单字符，非长度前缀）；</li>
 *   <li>{@code audienceFlag}：{@code 1}=有 audience、{@code 0}=广播（此时 audience 段为空串）；</li>
 *   <li>{@code instanceId} 用<strong>长度前缀</strong>定界（照抄同仓
 *       {@code RedisCacheInvalidationPublisher} 的做法），避免 {@code instanceId|key} 这类拼接
 *       在标识含 {@code '|'} 时被截错；订阅方据此丢弃「自己发的帧」，杜绝自广播回环；</li>
 *   <li>{@code codecId} 让订阅方识别「对端 codec 版本不同」并显式丢弃 + WARN（滚动升级窗口），
 *       不猜测、不静默。</li>
 * </ul>
 *
 * <p>解析失败（缺字段、长度非数字/越界、非法 kind 或 flag、尾随字符）一律返回 {@code null}：
 * 调用方按非法消息丢弃并记日志，绝不因一条坏帧让订阅线程抛错，也不把坏数据投给连接。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
final class SseFanoutProtocol {

    /** 帧 kind：按 audience 扇出。 */
    private static final char KIND_AUDIENCE = 'U';

    /** 帧 kind：广播。 */
    private static final char KIND_BROADCAST = 'B';

    private static final char AUDIENCE_PRESENT = '1';

    private static final char AUDIENCE_ABSENT = '0';

    private SseFanoutProtocol() {
    }

    /**
     * 编码一帧。
     *
     * @param message    扇出消息
     * @param instanceId 发布实例标识
     * @return 帧文本
     */
    static String encode(SseFanoutMessage message, String instanceId) {
        boolean broadcast = message.broadcast();
        StringBuilder sb = new StringBuilder(64);
        sb.append(broadcast ? KIND_BROADCAST : KIND_AUDIENCE);
        appendField(sb, message.codecId());
        appendField(sb, instanceId);
        if (broadcast) {
            sb.append(AUDIENCE_ABSENT);
            appendField(sb, "");
        } else {
            sb.append(AUDIENCE_PRESENT);
            // broadcast() 为 false 时 audience 必非空（记录构造器已保证 null 只代表广播）
            appendField(sb, Objects.requireNonNull(message.audience(), "非广播消息的 audience 不能为空"));
        }
        appendField(sb, message.payloadText());
        appendField(sb, message.event());
        return sb.toString();
    }

    /**
     * 解码一帧。
     *
     * @param frame 帧文本
     * @return 解码结果（消息 + 发布实例标识）；格式非法时返回 {@code null}
     */
    @Nullable
    static Decoded decode(String frame) {
        Cursor cursor = new Cursor(frame);
        Character kind = cursor.nextChar();
        if (kind == null || (kind != KIND_AUDIENCE && kind != KIND_BROADCAST)) {
            return null;
        }
        String codecId = cursor.nextField();
        String instanceId = cursor.nextField();
        Character audienceFlag = cursor.nextChar();
        String audienceField = cursor.nextField();
        String payloadText = cursor.nextField();
        String event = cursor.nextField();
        if (codecId == null || instanceId == null || audienceFlag == null
            || audienceField == null || payloadText == null || event == null
            || (audienceFlag != AUDIENCE_PRESENT && audienceFlag != AUDIENCE_ABSENT)
            || !cursor.exhausted()) {
            return null;
        }
        boolean hasAudience = audienceFlag == AUDIENCE_PRESENT;
        // kind 与 flag 必须一致：否则说明帧被破坏或版本不匹配
        if (hasAudience != (kind == KIND_AUDIENCE)) {
            return null;
        }
        String audience = hasAudience ? audienceField : null;
        return new Decoded(new SseFanoutMessage(event, audience, codecId, payloadText), instanceId);
    }

    /** 解码结果：消息 + 发布实例标识（后者用于自广播回环判定）。 */
    record Decoded(SseFanoutMessage message, String fromInstanceId) {
    }

    /** 追加「长度:原文」字段。 */
    private static void appendField(StringBuilder sb, String value) {
        sb.append(value.length()).append(':').append(value);
    }

    /** 单次解析游标：所有越界/格式错误都收敛为 {@code null}，调用方无需处理异常。 */
    private static final class Cursor {

        private final String text;

        private int index;

        Cursor(String text) {
            this.text = text;
        }

        @Nullable
        Character nextChar() {
            if (index >= text.length()) {
                return null;
            }
            return text.charAt(index++);
        }

        /**
         * 读取一个「长度:原文」字段。
         *
         * @return 字段原文；缺分隔符、长度非数字或越界时返回 {@code null}
         */
        @Nullable
        String nextField() {
            int sep = text.indexOf(':', index);
            if (sep < 0) {
                return null;
            }
            final int length;
            try {
                length = Integer.parseInt(text.substring(index, sep));
            } catch (NumberFormatException e) {
                return null;
            }
            if (length < 0) {
                return null;
            }
            int start = sep + 1;
            // 用 long 累加防 int 溢出：起点 + 长度溢出为负会绕过越界检查
            long end = (long) start + length;
            if (end > text.length()) {
                return null;
            }
            index = (int) end;
            return text.substring(start, index);
        }

        boolean exhausted() {
            return index == text.length();
        }
    }
}
