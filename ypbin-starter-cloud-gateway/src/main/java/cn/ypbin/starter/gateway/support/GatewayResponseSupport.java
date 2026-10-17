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
package cn.ypbin.starter.gateway.support;

import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;

/**
 * 网关统一响应信封的共享构造入口。
 *
 * <p><strong>为什么必须集中</strong>：网关的 404 有两个来源——未知路由（
 * {@code GatewayExceptionHandler}，HTTP 200）与内部端点阻断（{@code InternalPathBlockGlobalFilter}，
 * HTTP 404）。issue #96 要求调用方<b>无法据响应判断某个内部端点是否存在</b>，其前提就是两者响应体
 * 除动态字段 {@code timestamp} 外完全一致。若各自硬编码文案，任何一次单侧改动都会让「内部路径被阻断」
 * 与「路由不存在」重新变得可区分（等于对外泄露内部 API 面），而且这种漂移不会触发任何编译错误。
 * 故 code / message / success 三者在这里只定义一次，两个来源都经 {@link #notFound()} 构造。</p>
 *
 * <p><strong>差异说明（仅 {@code timestamp}）</strong>：{@code R} 的时间戳是「该信封实例构造那一刻」的
 * {@code LocalDateTime.now()}，两个来源的取值必然不同，且无法在不引入人为固定值的前提下统一。关键是
 * <b>取值口径必须一致</b>：两侧都必须在<b>每次请求</b>时生成信封，使时间戳总是「本次请求时刻」。若某一侧
 * （如内部路径阻断过滤器）为了省一次序列化而预生成响应体，它的时间戳就被冻结成启动时刻、连续多次拒绝
 * 返回同一个值，调用方据此即可与未知路由（每次都是当前时刻）区分开——那是比「文案不同」更隐蔽的
 * 可区分信号，故不得预生成。</p>
 *
 * <p>HTTP 状态码刻意不在这里统一：未知路由沿用既有约定 HTTP 200（错误类型由 {@code R.code} 区分），
 * 内部端点阻断保留 HTTP 404（issue 明确要求），本类只负责响应体。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
public final class GatewayResponseSupport {

    /**
     * 网关 404 统一文案。
     *
     * <p>单一来源：{@code GatewayExceptionHandler} 的两处 404 与
     * {@code InternalPathBlockGlobalFilter} 的信封都引用本常量，改动即同时生效。</p>
     */
    public static final String NOT_FOUND_MESSAGE = "接口不存在";

    private GatewayResponseSupport() {
    }

    /**
     * 构造网关 404 统一信封。
     *
     * <p>返回新实例（承载各自的 {@code timestamp}），但字段取值与结构完全一致；两个 404 来源
     * 都必须经本方法构造，不得自行拼装 code/message，且必须在<b>每次请求</b>时调用（不得缓存
     * 序列化结果，否则时间戳被冻结、可被用于区分两个来源）。</p>
     *
     * @return 404 信封（{@code code}=404、{@code message}= {@link #NOT_FOUND_MESSAGE}、
     *         {@code data}=null、{@code success}=false）
     */
    public static R<Void> notFound() {
        return R.fail(GlobalErrorCode.NOT_FOUND.getCode(), NOT_FOUND_MESSAGE);
    }
}
