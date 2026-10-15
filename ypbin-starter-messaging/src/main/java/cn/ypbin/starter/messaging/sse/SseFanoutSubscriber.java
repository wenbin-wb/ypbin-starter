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

import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

/**
 * SSE 扇出帧的 Redis 订阅者：把其它实例发布的事件投给<strong>本实例</strong>的连接。
 *
 * <p>三道显式拒绝，均<strong>不静默</strong>：</p>
 * <ol>
 *   <li><strong>自广播回环</strong>：帧内实例标识等于本实例 ⇒ 直接丢弃（本地连接已由管理器投递过），
 *       按 debug 记录，不产生 WARN 噪音；</li>
 *   <li><strong>协议格式非法</strong>：长度前缀缺失/越界、kind 与 flag 不一致等 ⇒ 丢弃 + WARN（滚动升级
 *       窗口内旧版本帧、或被污染的消息都会走这里）；</li>
 *   <li><strong>codec 版本不一致</strong>：帧的 {@code codecId} 与本实例不同 ⇒ 丢弃 + WARN。跨版本帧
 *       无法安全解码，宁可显式丢弃并留下可查日志，也不猜测类型。</li>
 * </ol>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public class SseFanoutSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(SseFanoutSubscriber.class);

    private final SseEmitterManager manager;

    private final String instanceId;

    private final SseFanoutCodec codec;

    public SseFanoutSubscriber(SseEmitterManager manager, String instanceId, SseFanoutCodec codec) {
        this.manager = manager;
        this.instanceId = instanceId;
        this.codec = codec;
    }

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        String frame = new String(message.getBody(), StandardCharsets.UTF_8);
        SseFanoutProtocol.Decoded decoded = SseFanoutProtocol.decode(frame);
        if (decoded == null) {
            log.warn("[ypbin-starter] 收到无法解析的 SSE 扇出帧，已丢弃：channel={}",
                new String(message.getChannel(), StandardCharsets.UTF_8));
            return;
        }
        if (instanceId.equals(decoded.fromInstanceId())) {
            // 自广播回环：本实例发布时已就地投递，这里再投会重复
            log.debug("[ypbin-starter] 忽略本实例发布的 SSE 扇出帧：event={}", decoded.message().event());
            return;
        }
        if (!codec.id().equals(decoded.message().codecId())) {
            log.warn("[ypbin-starter] SSE 扇出帧 codec 版本不一致，已丢弃（本实例 codec={}，帧 codec={}）：event={}",
                codec.id(), decoded.message().codecId(), decoded.message().event());
            return;
        }
        Object payload = codec.decode(decoded.message().payloadText());
        manager.deliverLocal(decoded.message(), payload);
    }
}
