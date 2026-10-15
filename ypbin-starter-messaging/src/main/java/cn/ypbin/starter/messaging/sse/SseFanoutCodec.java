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

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * SSE 跨实例扇出的载荷编解码扩展点。
 *
 * <p>messaging 模块刻意<strong>不引入 Jackson</strong>（它只经可选的 web 依赖传递，消费者不该依赖）：
 * 默认实现 {@link TextSseFanoutCodec} 用零依赖的纯文本协议覆盖 null/布尔/数字/字符串等标量；
 * 结构化对象由业务注册本类型的 Bean（IoT 的实时事件就属于这一类）。</p>
 *
 * <p><strong>不静默降级</strong>：{@link #encode(Object)} 返回空表示「本 codec 无法承载该载荷」，
 * 发布方会记一条 WARN 并只投本实例（跨实例部分丢失是显式的），而不是把对象 {@code toString()}
 * 成 {@code [object Object]} 类的垃圾帧投出去。</p>
 *
 * <p><strong>滚动升级</strong>：{@link #id()} 会写进扇出帧，订阅方遇到 id 与自身不一致的帧即丢弃并
 * WARN（跨 codec 版本的帧无法安全解码，宁可显式丢弃也不猜）。因此 id 变化应视为协议变更。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
public interface SseFanoutCodec {

    /** 本 codec 的协议标识（写进扇出帧，供对端识别版本），不得为空或含 {@code ':'}。 */
    String id();

    /**
     * 编码载荷。
     *
     * @param payload 待编码载荷（可为 {@code null}）
     * @return 编码后的文本；本 codec 不支持该类型时返回 {@link Optional#empty()}
     */
    Optional<String> encode(Object payload);

    /**
     * 解码载荷（与 {@link #encode(Object)} 对称）。
     *
     * @param text 编码文本（帧已按长度前缀切出，可含任意字符）
     * @return 还原后的载荷；编码结果为 {@code null} 时返回 {@code null}
     */
    @Nullable
    Object decode(String text);
}
