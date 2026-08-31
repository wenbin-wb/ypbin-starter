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
package cn.ypbin.starter.data.core;

/**
 * 实体通用状态。
 *
 * <p>{@link BaseEntity#status} 字段的取值约定：数据库与接口一律存/传 {@code code}，业务判断用
 * 本枚举的 {@code getCode()} 比较，禁止裸写 {@code 0/1}。</p>
 *
 * @author wenbin
 * @since 2026-08-31
 */
public enum EntityStatus {

    /** 启用 */
    ENABLED(1, "启用"),

    /** 禁用 */
    DISABLED(0, "禁用");

    private final Integer code;
    private final String desc;

    EntityStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
