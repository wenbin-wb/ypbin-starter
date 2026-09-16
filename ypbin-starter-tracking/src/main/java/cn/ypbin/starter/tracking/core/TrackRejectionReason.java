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
 * 埋点事件被拒绝或被裁剪的原因。
 *
 * <p>采集端点是匿名可写入口，问题必须<strong>可解释、可聚合</strong>：既回给调用方（便于前端自查），
 * 也进入计数器（便于运维发现异常）。因此用有限枚举而不是自由文本——自由文本会带来标签基数爆炸。</p>
 *
 * <p>每条原因带 {@link Level}：<strong>事件级</strong>表示该事件没有被接收，<strong>属性级</strong>表示事件
 * 仍被接收、只是丢了个别属性。二者必须分开统计，否则会出现「received=1 / accepted=1 / rejected=2」这种
 * 自相矛盾的结果。级别由枚举自己声明，采集侧据此路由，调用点无需各自判断。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
public enum TrackRejectionReason {

    /** 事件码未登记在事件目录中（事件级） */
    UNREGISTERED("unregistered", "事件码未登记在事件目录", Level.EVENT),

    /** 缺少必填字段（事件级） */
    MISSING_REQUIRED_FIELD("missingRequiredField", "缺少必填字段", Level.EVENT),

    /** 客户端事件时间不可解析（事件级） */
    INVALID_EVENT_TIME("invalidEventTime", "客户端事件时间不可解析", Level.EVENT),

    /** 属性体积超过单事件上限（事件级） */
    PAYLOAD_TOO_LARGE("payloadTooLarge", "属性体积超过单事件上限", Level.EVENT),

    /** 单请求事件数超过上限（事件级） */
    OVER_REQUEST_LIMIT("overRequestLimit", "单请求事件数超过上限", Level.EVENT),

    /** 请求体超过单请求上限（事件级，由体积闸门判定） */
    REQUEST_TOO_LARGE("requestTooLarge", "请求体超过单请求上限", Level.EVENT),

    /** 属性名不在该事件的白名单内（属性级：属性被丢弃，事件仍被接收） */
    PAYLOAD_KEY_NOT_ALLOWED("payloadKeyNotAllowed", "属性名不在该事件的白名单内", Level.ATTRIBUTE),

    /** 属性类型与目录声明不符（属性级：属性被丢弃，事件仍被接收） */
    PAYLOAD_TYPE_MISMATCH("payloadTypeMismatch", "属性类型与目录声明不符", Level.ATTRIBUTE);

    private final String code;

    private final String desc;

    private final Level level;

    TrackRejectionReason(String code, String desc, Level level) {
        this.code = code;
        this.desc = desc;
        this.level = level;
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

    /**
     * 影响级别。
     *
     * @return 级别
     */
    public Level getLevel() {
        return level;
    }

    /**
     * 原因的影响级别。
     */
    public enum Level {

        /** 事件未被接收 */
        EVENT,

        /** 事件仍被接收，仅个别属性被丢弃 */
        ATTRIBUTE
    }
}
