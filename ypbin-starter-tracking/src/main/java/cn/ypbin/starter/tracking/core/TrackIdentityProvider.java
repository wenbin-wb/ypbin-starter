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
package cn.ypbin.starter.tracking.core;

import org.jspecify.annotations.Nullable;

/**
 * 登录身份提供者（扩展点）。
 *
 * <p>埋点属于「理解用户」的能力，用户与租户是最基本的分组维度；但这两个值只能在<strong>请求线程</strong>上
 * 取到（消费者线程上没有登录会话，也没有租户上下文）。因此由宿主提供本接口的实现，
 * 采集侧在请求线程上取值并随事件带下去。</p>
 *
 * <p>本模块<strong>刻意不依赖</strong>任何身份实现（单体走 Sa-Token 会话、微服务走网关身份头），
 * 默认实现返回空值——宿主若不提供，事件仍会被正常采集，只是缺少用户/租户维度。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
public interface TrackIdentityProvider {

    /** 缺省实现：不提供身份维度。 */
    TrackIdentityProvider NONE = new TrackIdentityProvider() {
        @Override
        public @Nullable Long userId() {
            return null;
        }

        @Override
        public @Nullable Long tenantId() {
            return null;
        }
    };

    /**
     * 当前登录用户 ID。
     *
     * @return 用户 ID；未登录时为 {@code null}
     */
    @Nullable Long userId();

    /**
     * 当前租户 ID。
     *
     * @return 租户 ID；无租户上下文时为 {@code null}
     */
    @Nullable Long tenantId();
}
