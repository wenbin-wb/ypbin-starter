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

/**
 * 埋点事件被拒绝的原因。
 *
 * <p>采集端点是匿名可写入口，拒绝必须<strong>可解释、可聚合</strong>：既回给调用方（便于前端自查），
 * 也进入计数器（便于运维发现异常）。因此用有限枚举而不是自由文本——自由文本会带来标签基数爆炸。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
public enum TrackRejectionReason {

    /** 事件码未登记在事件目录中 */
    UNREGISTERED("unregistered", "事件码未登记在事件目录"),

    /** 属性名不在该事件的白名单内 */
    PAYLOAD_KEY_NOT_ALLOWED("payloadKeyNotAllowed", "属性名不在该事件的白名单内"),

    /** 属性类型与目录声明不符 */
    PAYLOAD_TYPE_MISMATCH("payloadTypeMismatch", "属性类型与目录声明不符"),

    /** 属性体积超过单事件上限 */
    PAYLOAD_TOO_LARGE("payloadTooLarge", "属性体积超过单事件上限"),

    /** 请求体超过单请求上限 */
    REQUEST_TOO_LARGE("requestTooLarge", "请求体超过单请求上限"),

    /** 客户端事件时间不可解析 */
    INVALID_EVENT_TIME("invalidEventTime", "客户端事件时间不可解析"),

    /** 缺少必填字段 */
    MISSING_REQUIRED_FIELD("missingRequiredField", "缺少必填字段"),

    /** 单请求事件数超过上限 */
    OVER_REQUEST_LIMIT("overRequestLimit", "单请求事件数超过上限");

    private final String code;

    private final String desc;

    TrackRejectionReason(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 稳定编码（对外与计数使用，不随枚举顺序变化）。
     *
     * @return 编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 中文说明。
     *
     * @return 说明
     */
    public String getDesc() {
        return desc;
    }
}
