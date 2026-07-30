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
package cn.ypbin.starter.log.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 日志采集包含项。
 *
 * <p>控制单条日志记录的采集粒度。全局配置默认集合，{@code @Log} 可在方法/类上按需
 * 追加（includes）或删减（excludes），避免记录敏感或冗余信息。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public enum Include {

    /** 请求头 */
    REQUEST_HEADERS,

    /** 请求体 */
    REQUEST_BODY,

    /** 请求参数 */
    REQUEST_PARAM,

    /** 响应头 */
    RESPONSE_HEADERS,

    /** 响应体 */
    RESPONSE_BODY,

    /** 客户端 IP */
    IP,

    /** 浏览器 */
    BROWSER,

    /** 操作系统 */
    OS;

    private static final Set<Include> DEFAULT_INCLUDES = Collections.unmodifiableSet(
        EnumSet.of(REQUEST_PARAM, IP));

    /**
     * 默认采集集合：请求参数 + IP。
     *
     * <p>默认不采集请求/响应体，避免大报文与敏感信息意外落库，需要时显式开启。</p>
     *
     * @return 默认包含项
     */
    public static Set<Include> defaultIncludes() {
        return DEFAULT_INCLUDES;
    }
}
