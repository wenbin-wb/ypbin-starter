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
 * <p>messaging 模块对 Jackson 只有 <strong>optional 依赖</strong>（不向宿主传递）：默认实现
 * {@link TextSseFanoutCodec} 用零依赖的纯文本协议覆盖 null/布尔/数字/字符串等标量；类路径存在
 * Jackson 3 且容器里有 {@code ObjectMapper} Bean 时自动装配 {@link JacksonSseFanoutCodec} 以承载
 * {@code Map}/POJO/{@code record} 等结构化对象；宿主自定义本类型的 Bean 永远优先。</p>
 *
 * <p><strong>不静默降级</strong>：{@link #encode(Object)} 返回空表示「本 codec 无法承载该载荷」，
 * 发布方会记一条 WARN 并只投本实例（跨实例部分丢失是显式的），而不是把对象 {@code toString()}
 * 成 {@code [object Object]} 类的垃圾帧投出去。默认实现 {@link TextSseFanoutCodec} 对无法用十进制
 * 字面量表达的数字（{@code Double.NaN}、正负 {@code Infinity}、{@code Float.NaN} 等）同样返回空：
 * 承载不了就不产出没人能解码的帧。</p>
 *
 * <p><strong>解码不得抛异常</strong>：{@link #decode(String)} 对无法识别的帧内容（未知标签、非法数字
 * 文本等）必须收敛为「保留原文」的字符串结果，而不是抛 {@link RuntimeException}——调用它的
 * {@link SseFanoutSubscriber} 位于 Redis 订阅线程，一条坏帧不得让订阅线程或连接出错。</p>
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
     * <p>实现<strong>不得</strong>因内容无法识别而抛运行时异常：按「保留原文」返回文本即可（见类注释），
     * 调用方位于 Redis 订阅线程，坏帧只能让这一帧被丢弃。</p>
     *
     * @param text 编码文本（帧已按长度前缀切出，可含任意字符）
     * @return 还原后的载荷；编码结果为 {@code null} 时返回 {@code null}
     */
    @Nullable
    Object decode(String text);
}
