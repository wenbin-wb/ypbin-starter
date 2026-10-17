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
 * <p><strong>codec 与发布侧同源（结构上不可能不一致）</strong>：本类<strong>不持有</strong>自己的
 * codec，而是始终用 {@link SseEmitterManager#getCodec()}。发布时写进帧的 {@code codecId} 来自管理器的
 * 同一个 {@code final} codec 字段，订阅侧比对与解码也用同一个实例，故「管理器与订阅者 codec 不一致 ⇒
 * 跨实例帧被整批丢弃」不再可能发生：宿主即使自定义 {@link SseEmitterManager}（其 codec 与容器里是否
 * 另有 {@link SseFanoutCodec} Bean 无关），两侧也自动一致。</p>
 *
 * <p>四道显式拒绝，均<strong>不静默</strong>：</p>
 * <ol>
 *   <li><strong>帧无法解析</strong>：长度前缀缺失/越界、kind 与 flag 不一致等 ⇒ 丢弃 + WARN（滚动升级
 *       窗口内旧版本帧、或被污染的消息都会走这里）；</li>
 *   <li><strong>自广播回环</strong>：帧内实例标识等于本实例 ⇒ 直接丢弃（本地连接已由管理器投递过），
 *       按 debug 记录，不产生 WARN 噪音；</li>
 *   <li><strong>codec 版本不一致</strong>：帧的 {@code codecId} 与本实例不同 ⇒ 丢弃 + WARN。跨版本帧
 *       无法安全解码，宁可显式丢弃并留下可查日志，也不猜测类型；</li>
 *   <li><strong>单帧处理异常</strong>：解析/解码/投递任一环节抛 {@link Exception} ⇒ 丢弃<em>该帧</em> +
 *       WARN（带完整堆栈）。单条坏帧不得让订阅线程出错，也不得影响后续帧；中断语义（
 *       {@link InterruptedException}）恢复中断标志位后照常丢弃该帧，不被静默吞掉。</li>
 * </ol>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public class SseFanoutSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(SseFanoutSubscriber.class);

    private final SseEmitterManager manager;

    private final String instanceId;

    /**
     * @param manager    本实例连接管理器（同时是发布侧与订阅侧<strong>唯一</strong>的 codec 来源）
     * @param instanceId 本实例的扇出标识（与 {@link SseFanoutBridge#instanceId()} 同一来源）
     */
    public SseFanoutSubscriber(SseEmitterManager manager, String instanceId) {
        this.manager = manager;
        this.instanceId = instanceId;
    }

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        // 频道名先取一次：catch 块本身也要用它，不能等到异常处理时再解引用（那样坏帧会变成 NPE 逃逸）
        String channel = channelOf(message);
        byte[] body = message.getBody();
        if (body == null) {
            // Spring Data Redis 的实际实现不会给出 null body；此处仅为防御第三方/测试替身，
            // 避免 new String(null, ...) 抛 NPE 后连「哪一帧坏了」都记录不下来
            log.warn("[ypbin-starter] 收到 body 为 null 的 SSE 扇出消息，已丢弃：channel={}", channel);
            return;
        }
        try {
            handle(new String(body, StandardCharsets.UTF_8), channel);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                // 中断/关闭语义必须保留：恢复标志位供上层（监听容器/线程池）感知，而不是被这里吞掉
                Thread.currentThread().interrupt();
            }
            // 单帧异常绝不能让订阅线程或连接出错：记完整堆栈后丢弃该帧，后续帧照常处理
            log.warn("[ypbin-starter] 处理 SSE 扇出帧失败，已丢弃该帧：channel={}", channel, e);
        }
    }

    /**
     * 取消息频道名（仅用于日志）。
     *
     * <p>订阅消息理应带频道，但日志语句本身绝不能成为异常来源：{@code getChannel()} 返回
     * {@code null} 时退化为占位符，而不是在 WARN 里再抛一次 NPE 把异常顶出订阅线程。</p>
     *
     * @param message 原始消息
     * @return 频道名；无法取得时为占位符
     */
    private static String channelOf(Message message) {
        byte[] raw = message.getChannel();
        return raw == null ? "<unknown>" : new String(raw, StandardCharsets.UTF_8);
    }

    /**
     * 解析并把一帧投给本实例连接；任何非法/不可解码的帧都只记录并返回，不抛出。
     *
     * @param frame   帧文本
     * @param channel 频道名（仅用于日志）
     */
    private void handle(String frame, String channel) {
        SseFanoutProtocol.Decoded decoded = SseFanoutProtocol.decode(frame);
        if (decoded == null) {
            log.warn("[ypbin-starter] 收到无法解析的 SSE 扇出帧，已丢弃：channel={}", channel);
            return;
        }
        if (instanceId.equals(decoded.fromInstanceId())) {
            // 自广播回环：本实例发布时已就地投递，这里再投会重复
            log.debug("[ypbin-starter] 忽略本实例发布的 SSE 扇出帧：event={}", decoded.message().event());
            return;
        }
        // codec 取管理器里那个 final 字段（构造时注入、无 setter），与发布侧结构上同源
        SseFanoutCodec codec = manager.getCodec();
        if (!codec.id().equals(decoded.message().codecId())) {
            log.warn("[ypbin-starter] SSE 扇出帧 codec 版本不一致，已丢弃（本实例 codec={}，帧 codec={}）：event={}",
                codec.id(), decoded.message().codecId(), decoded.message().event());
            return;
        }
        manager.deliverLocal(decoded.message(), codec.decode(decoded.message().payloadText()));
    }
}
