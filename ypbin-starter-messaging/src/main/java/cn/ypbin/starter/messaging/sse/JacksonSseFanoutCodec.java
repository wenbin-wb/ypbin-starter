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
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

/**
 * 基于 Jackson 的结构化载荷扇出编解码器：在 {@link TextSseFanoutCodec} 的标量能力之上，
 * 用 JSON 承载 {@code Map}/{@code Collection}/POJO/{@code record} 等对象。
 *
 * <p><strong>帧内格式</strong>：与该 codec 同族的 {@code text-v1} 一样是「单字符类型标签 + 文本」，
 * 但多出一个结构化分支 {@code j}：</p>
 * <ul>
 *   <li>标量（{@code null}/布尔/数字/字符串/字符/枚举）<strong>逐字节沿用 text 协议</strong>
 *       （{@code n}/{@code b0}/{@code b1}/{@code d<数字>}/{@code s<文本>}/{@code c<字符>}），
 *       实现上直接委托 {@link TextSseFanoutCodec}，两处语义不会漂移：若把 {@code "abc"} 交给 Jackson
 *       会编成带引号的 {@code "abc"}，投递内容形态与今天不同，属对外行为变化，故刻意不做；</li>
 *   <li>结构化载荷编成 {@code j<JSON 文本>}，{@link #decode(String)} 的 {@code j} 分支返回
 *       <strong>JSON 文本字符串本身</strong>；投递方把它作为 SSE 的 {@code data:} 原文发出，
 *       业务侧仍能 {@code JSON.parse} 成对象（不二次转义、不带额外引号）。</li>
 * </ul>
 *
 * <p><strong>不静默降级</strong>：Jackson 序列化失败（如自引用环、getter 抛异常）时
 * {@link #encode(Object)} 返回 {@link Optional#empty()}，由 {@code SseEmitterManager} 按既有语义记
 * WARN 并只投本实例；绝不 {@code toString()} 成垃圾帧，也不把半截 JSON 投出去。</p>
 *
 * <p><strong>滚动升级（重要）</strong>：{@link #id()} 为 {@value #ID}，与 {@code text-v1} 不同；而
 * {@code SseFanoutSubscriber} 会丢弃 codecId 与自身不一致的帧。因此<strong>混版本窗口内，两个方向的
 * 跨实例扇出会全部失效</strong>（不只是结构化载荷：新节点发的标量帧以 {@code json-v1} 打标，旧节点不认；
 * 旧节点的 {@code text-v1} 帧新节点也不认），日志里会有对应的 WARN。这不是本 codec 引入的新机制，
 * 而是 {@link SseFanoutCodec} 既有协议约束（跨 codec 版本无法安全解码，不猜类型）——好在它<em>不串码、
 * 不投出错误内容</em>。运维上要么把本 codec 视为协议变更、整批滚动并接受窗口内的跨实例丢失，
 * 要么在滚动期间显式注册一个 {@code TextSseFanoutCodec} Bean 钉住 {@code text-v1}（宿主自定义 codec
 * 优先于自动装配），升级完成后再放开。</p>
 *
 * <p><strong>敏感数据提示</strong>：JSON 是<strong>明文</strong>，会原样落到 Redis Pub/Sub 频道、
 * 缓存与订阅方日志/抓包里。对象里若有手机号、身份证、令牌等敏感字段，应由业务侧在进入本 codec 之前
 * 完成脱敏或加密，本类不做任何字段裁剪（它不认识业务模型，猜着裁剪只会静默丢数据）。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
public class JacksonSseFanoutCodec implements SseFanoutCodec {

    /** 与 {@link SseFanoutCodec#id()} 对应的协议标识；改动帧格式时必须同步改这里。 */
    public static final String ID = "json-v1";

    /**
     * 结构化载荷的帧内前缀：{@code 'j'} + JSON 文本。
     *
     * <p>{@code text-v1} 已占用 {@code n/b/d/s/c}，{@code j} 与之不冲突；包级可见以便同包用例断言帧格式。</p>
     */
    static final char JSON_PREFIX = 'j';

    private static final Logger log = LoggerFactory.getLogger(JacksonSseFanoutCodec.class);

    /**
     * 标量分支的实现来源。持有 {@link TextSseFanoutCodec} 实例而不是复制一份类型判断：
     * 复制出来的两份清单一旦漂移，同一载荷经两个 codec 就会产出不同帧内容（对外行为变化）。
     */
    private static final TextSseFanoutCodec SCALARS = new TextSseFanoutCodec();

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper 结构化载荷的序列化器（通常即容器里 Boot 自动配置的那个，保留宿主的
     *                     {@code spring.jackson.*} 定制：命名策略、时间格式、模块等）
     */
    public JacksonSseFanoutCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Optional<String> encode(Object payload) {
        // 标量优先走 text 协议：Jackson 会把字符串编成带引号的 JSON，直接交给它会改变投递内容形态
        Optional<String> scalar = SCALARS.encode(payload);
        if (scalar.isPresent()) {
            return scalar;
        }
        try {
            return Optional.of(JSON_PREFIX + objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            // 捕获 Exception 而不只是 JacksonException：独立验收实测，载荷自身抛出的非 Jackson 运行时异常
            // （典型：懒加载集合在 session 关闭后 iterator()/entrySet() 抛 LazyInitializationException）
            // 会从 writeValueAsString 里原样逃逸。而 SseEmitterManager.publishFanout 的 codec.encode 调用
            // 在 try/catch 之外，异常会反向抛回业务发布线程 ⇒ 必须在这里收敛。
            // 返回空是接口约定的「本 codec 承载不了」：发布方会记 WARN 并只投本实例（跨实例部分丢失是显式的）。
            // 这里补一条 debug 级完整堆栈，便于定位是哪个载荷序列化失败，同时不给正常流量制造 WARN 噪音。
            log.debug("[ypbin-starter] SSE 载荷无法用 JSON 承载，按不支持处理（发布方将只投本实例）：payloadType={}",
                payload.getClass().getName(), e);
            return Optional.empty();
        }
    }

    @Override
    @Nullable
    public Object decode(String text) {
        if (!text.isEmpty() && text.charAt(0) == JSON_PREFIX) {
            // 'j' 之后就是 JSON 文本原文：原样还原为字符串，由投递方作为 data: 原文发出。
            // 这里不重新解析 JSON——能走到本分支说明帧的 codecId 已与本 codec 一致，
            // 再解析一次只会多一种失败语义，且投递端并不需要 Java 对象。
            return text.substring(1);
        }
        // 标量（含未知标签、空串）与 text-v1 完全同构
        return SCALARS.decode(text);
    }
}
