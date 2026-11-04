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
package cn.ypbin.starter.core.blocklist;

/**
 * 访问黑名单规则的匹配维度。
 *
 * @author wenbin
 * @since 2026-11-04
 */
public enum BlocklistMatchType {

    /** 精确匹配单个 IP。 */
    IP,

    /** 匹配 CIDR 网段，如 {@code 10.0.0.0/8}。 */
    CIDR,

    /** User-Agent 包含指定关键字（大小写不敏感）。 */
    USER_AGENT,

    /** 请求路径前缀，如 {@code /system/open-api}。 */
    PATH
}
