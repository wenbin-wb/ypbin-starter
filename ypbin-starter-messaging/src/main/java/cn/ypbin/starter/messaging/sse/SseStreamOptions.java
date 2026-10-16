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

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * {@link SseStreamBridge} 的流式桥接选项（不可变，用 {@link #builder()} 构造）。
 *
 * <p>所有字段都有明确默认值，且<strong>不提供任何 setter</strong>：选项对象在一次流式响应内会被多个
 * Reactor 线程读取，可变对象会引入发布竞态。需要不同取值时用 {@code builder} 另建实例。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
public final class SseStreamOptions {

    /** 默认的连接总超时（毫秒）：0 表示不设总超时，由客户端断开/错误回调触发释放。 */
    public static final long DEFAULT_TIMEOUT_MILLIS = 0L;

    /**
     * 默认的正常帧事件名：<strong>空串，表示不写 {@code event:} 行、只发裸 {@code data:} 帧</strong>。
     *
     * <p>这是为了<strong>逐字节复刻</strong> IoT 既有实现的线上协议（{@code emitter.send(token)} 等价于
     * {@code event().data(token)}），前端解析器按「无事件名即正常 token」处理，改默认值会改变对外协议。</p>
     */
    public static final String DEFAULT_EVENT_NAME = "";

    /** 默认的错误帧事件名：{@code error}（前端据此把该帧交给 onError 而非当作 token）。 */
    public static final String DEFAULT_ERROR_EVENT_NAME = "error";

    /** 默认的错误帧文案前缀。 */
    public static final String DEFAULT_ERROR_PREFIX = "对话出错：";

    /** 默认是否传播调用线程的上下文快照（见 {@link SseStreamBridge} 类注释）。 */
    public static final boolean DEFAULT_PROPAGATE_CONTEXT = true;

    /** 默认是否在出错时先发 error 事件帧再 complete（关闭后仅 complete）。 */
    public static final boolean DEFAULT_ERROR_EVENT_ENABLED = true;

    private final long timeoutMillis;

    private final String eventName;

    private final String errorEventName;

    private final String errorPrefix;

    private final boolean propagateContext;

    private final boolean errorEventEnabled;

    private SseStreamOptions(long timeoutMillis, String eventName, String errorEventName,
                             String errorPrefix, boolean propagateContext, boolean errorEventEnabled) {
        this.timeoutMillis = timeoutMillis;
        this.eventName = eventName;
        this.errorEventName = errorEventName;
        this.errorPrefix = errorPrefix;
        this.propagateContext = propagateContext;
        this.errorEventEnabled = errorEventEnabled;
    }

    /**
     * 默认选项：不设总超时、正常帧不带事件名、错误事件名 {@code error}、错误前缀 {@code 对话出错：}、
     * 开启上下文传播、开启错误事件帧。
     *
     * @return 默认选项
     */
    public static SseStreamOptions defaults() {
        return builder().build();
    }

    /**
     * 构造器。
     *
     * @return 新的构造器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 连接总超时（毫秒）。
     *
     * @return 超时毫秒数，0 表示不设总超时
     */
    public long timeoutMillis() {
        return timeoutMillis;
    }

    /**
     * 正常 token 的事件名；空串表示裸 {@code data:} 帧（默认，兼容既有调用方）。
     *
     * @return 事件名（可能为空串，非 {@code null}）
     */
    public String eventName() {
        return eventName;
    }

    /**
     * 错误事件的事件名。
     *
     * @return 错误事件名（非空）
     */
    public String errorEventName() {
        return errorEventName;
    }

    /**
     * 错误帧文案前缀（最终文案 = 前缀 + 根因消息）。
     *
     * @return 前缀（可能为空串，非 {@code null}）
     */
    public String errorPrefix() {
        return errorPrefix;
    }

    /**
     * 是否传播调用线程的上下文快照。
     *
     * @return 开启返回 true
     */
    public boolean propagateContext() {
        return propagateContext;
    }

    /**
     * 是否在出错时先发一帧错误事件，再 {@code complete()}。
     *
     * @return 开启返回 true
     */
    public boolean errorEventEnabled() {
        return errorEventEnabled;
    }

    /**
     * {@link SseStreamOptions} 构造器。
     *
     * @author wenbin
     * @since 2026-10-16
     */
    public static final class Builder {

        private long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;

        private String eventName = DEFAULT_EVENT_NAME;

        private String errorEventName = DEFAULT_ERROR_EVENT_NAME;

        private String errorPrefix = DEFAULT_ERROR_PREFIX;

        private boolean propagateContext = DEFAULT_PROPAGATE_CONTEXT;

        private boolean errorEventEnabled = DEFAULT_ERROR_EVENT_ENABLED;

        private Builder() {
        }

        /**
         * 连接总超时；{@code null} 或非正值表示不设总超时。
         *
         * @param timeout 超时（可为 {@code null}）
         * @return 本构造器
         */
        public Builder timeout(@Nullable Duration timeout) {
            this.timeoutMillis = timeout == null ? DEFAULT_TIMEOUT_MILLIS : Math.max(0L, timeout.toMillis());
            return this;
        }

        /**
         * 连接总超时（毫秒）；非正值表示不设总超时。
         *
         * @param timeoutMillis 超时毫秒数
         * @return 本构造器
         */
        public Builder timeoutMillis(long timeoutMillis) {
            this.timeoutMillis = Math.max(0L, timeoutMillis);
            return this;
        }

        /**
         * 正常 token 的事件名；传 {@code null} 或空串表示发裸 {@code data:} 帧（默认行为）。
         *
         * @param eventName 事件名（可为 {@code null}）
         * @return 本构造器
         */
        public Builder eventName(@Nullable String eventName) {
            this.eventName = eventName == null ? DEFAULT_EVENT_NAME : eventName;
            return this;
        }

        /**
         * 错误事件的事件名。
         *
         * @param errorEventName 事件名（非空白）
         * @return 本构造器
         */
        public Builder errorEventName(String errorEventName) {
            this.errorEventName = requireText(errorEventName, "errorEventName");
            return this;
        }

        /**
         * 错误帧文案前缀；传 {@code null} 表示不加前缀。
         *
         * @param errorPrefix 前缀（可为 {@code null}）
         * @return 本构造器
         */
        public Builder errorPrefix(@Nullable String errorPrefix) {
            this.errorPrefix = errorPrefix == null ? "" : errorPrefix;
            return this;
        }

        /**
         * 是否传播调用线程的上下文快照。
         *
         * @param propagateContext 开启传 true
         * @return 本构造器
         */
        public Builder propagateContext(boolean propagateContext) {
            this.propagateContext = propagateContext;
            return this;
        }

        /**
         * 是否在出错时先发错误事件帧。
         *
         * @param errorEventEnabled 开启传 true
         * @return 本构造器
         */
        public Builder errorEventEnabled(boolean errorEventEnabled) {
            this.errorEventEnabled = errorEventEnabled;
            return this;
        }

        /**
         * 构造不可变选项。
         *
         * @return 选项
         */
        public SseStreamOptions build() {
            return new SseStreamOptions(timeoutMillis, eventName, errorEventName,
                errorPrefix, propagateContext, errorEventEnabled);
        }

        private static String requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " 不能为空（SSE 事件名缺失会让客户端无法区分帧类型）");
            }
            return value;
        }
    }
}
