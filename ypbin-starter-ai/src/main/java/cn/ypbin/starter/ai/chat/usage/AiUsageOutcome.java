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
package cn.ypbin.starter.ai.chat.usage;

/**
 * 一次 AI 对话调用的终局结果。
 *
 * <p>用于让 {@link AiUsageListener} 区分「正常完成 / 上游或链路失败 / 调用方取消」三类事件：
 * 取消（如 SSE 客户端断开、下游 {@code dispose()}）**不会**走到完成或异常回调，
 * 若不单独上报就会出现「有请求、无用量记录」的黑洞。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
public enum AiUsageOutcome {

    /** 正常完成（流式已 onComplete） */
    SUCCESS("success", "成功"),

    /** 失败（上游异常/超时/回调链异常，流式以 onError 终止） */
    FAILURE("failure", "失败"),

    /** 调用方主动取消（SSE 断开等，流式以 onCancel 终止） */
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String desc;

    AiUsageOutcome(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 获取枚举编码（对外存库/传输一律用 code，禁用 {@code ordinal()}）。
     *
     * @return 编码
     */
    public String code() {
        return code;
    }

    /**
     * 获取枚举描述。
     *
     * @return 描述
     */
    public String desc() {
        return desc;
    }
}
