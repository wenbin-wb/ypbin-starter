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
 * 一条跨实例扇出消息（发布方 → Redis 频道 → 各实例订阅方）。
 *
 * <p><strong>载荷保持编码后原文，不在本类里解码</strong>：订阅方用<strong>自己的</strong>
 * {@link SseFanoutCodec} 解码，于是「对端 codec 版本不同」由 {@link #codecId} 显式承载——
 * 收到不认识的 codecId 就丢弃并 WARN，不去猜类型、也不静默投出垃圾帧。</p>
 *
 * @param event       事件名（对应 SSE 的 {@code event:} 行）
 * @param audience    扇出目标键；{@code null} 表示广播（对应 {@link SseEmitterManager#broadcast}）
 * @param codecId     载荷 codec 标识（见 {@link SseFanoutCodec#id()}）
 * @param payloadText codec 编码后的载荷文本
 * @author wenbin
 * @since 2026-10-15
 */
public record SseFanoutMessage(String event, @Nullable String audience, String codecId, String payloadText) {

    public SseFanoutMessage {
        Objects.requireNonNull(event, "event 不能为空");
        Objects.requireNonNull(codecId, "codecId 不能为空");
        Objects.requireNonNull(payloadText, "payloadText 不能为空");
    }

    /**
     * 是否为广播消息。
     *
     * @return 广播返回 true
     */
    public boolean broadcast() {
        return audience == null;
    }
}
