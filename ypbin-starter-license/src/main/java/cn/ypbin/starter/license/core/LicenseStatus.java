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
package cn.ypbin.starter.license.core;

/**
 * License 授权状态。
 *
 * <p>刻画授权在生命周期中的三态：合法可用（正常）、非法可用（宽限期内，允许运行但需告警续期）、
 * 非法不可用（过期或校验失败，拒绝受保护能力）。可视化展示与拦截决策均基于此状态。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
public enum LicenseStatus {

    /** 合法可用：授权有效且在有效期内 */
    LEGAL(true, "合法可用"),

    /** 非法可用：已过期但处于宽限期内，允许继续运行并提示续期 */
    GRACE(true, "非法可用（宽限期）"),

    /** 非法不可用：未授权、校验失败或宽限期已耗尽，拒绝受保护能力 */
    ILLEGAL(false, "非法不可用");

    private final boolean usable;
    private final String description;

    LicenseStatus(boolean usable, String description) {
        this.usable = usable;
        this.description = description;
    }

    /**
     * 当前状态是否允许使用受保护能力。
     *
     * @return {@code true} 表示可用（含宽限期）
     */
    public boolean isUsable() {
        return usable;
    }

    /**
     * 状态中文描述。
     *
     * @return 描述文案
     */
    public String getDescription() {
        return description;
    }
}
