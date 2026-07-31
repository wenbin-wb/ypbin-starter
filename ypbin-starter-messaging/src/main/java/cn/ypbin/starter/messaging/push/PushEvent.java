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
package cn.ypbin.starter.messaging.push;

import java.io.Serial;
import java.io.Serializable;

/**
 * 推送事件。
 *
 * <p>统一封装一次推送的事件名与载荷。事件名供前端区分业务类型（如 {@code unread-count}、
 * {@code login-status}、{@code dashboard-refresh}），载荷为任意可序列化对象。</p>
 *
 * @param event 事件名
 * @param data  载荷
 * @author wenbin
 * @since 2026-07-31
 */
public record PushEvent(String event, Object data) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造推送事件。
     *
     * @param event 事件名
     * @param data  载荷
     * @return 事件
     */
    public static PushEvent of(String event, Object data) {
        return new PushEvent(event, data);
    }
}
