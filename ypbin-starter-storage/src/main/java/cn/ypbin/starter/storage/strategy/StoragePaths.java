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
package cn.ypbin.starter.storage.strategy;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 存储路径工具。
 *
 * @author wenbin
 * @since 2026-07-30
 */
final class StoragePaths {

    private StoragePaths() {
    }

    /**
     * 对存储路径做 URL 编码：按 {@code /} 分段编码，保留路径分隔符。
     *
     * <p>{@link URLEncoder} 面向 application/x-www-form-urlencoded，会把空格编码为
     * {@code +}、且不编码 {@code +} 本身，直接用于 URL 路径会出错。这里逐段编码后
     * 将 {@code +} 修正为 {@code %20}、还原 {@code ~}，得到符合 URL path 规范的结果。</p>
     *
     * @param path 原始路径（可能含中文、空格、加号等）
     * @return 编码后的路径
     */
    static String encodePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String[] segments = path.split("/", -1);
        StringBuilder sb = new StringBuilder(path.length() + 16);
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(encodeSegment(segments[i]));
        }
        return sb.toString();
    }

    private static String encodeSegment(String segment) {
        if (segment.isEmpty()) {
            return "";
        }
        return URLEncoder.encode(segment, StandardCharsets.UTF_8)
            .replace("+", "%20")
            .replace("%7E", "~")
            .replace("*", "%2A");
    }
}
