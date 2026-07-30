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
package cn.ypbin.starter.web.xss;

import java.util.regex.Pattern;

/**
 * XSS 内容清洗工具。
 *
 * <p>移除脚本注入相关的危险内容（{@code <script>}、{@code javascript:}、事件属性等），
 * 保留正常文本。采用"移除危险片段"而非"整体 HTML 转义"，避免把正常内容中的
 * {@code < >} 也转义掉造成误伤。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class XssCleaner {

    private static final Pattern[] PATTERNS = {
        // <script>...</script>
        Pattern.compile("<script>(.*?)</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        // 单独的 <script ...> 或 </script>
        Pattern.compile("</?script[^>]*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        // src='...' / src="..."
        Pattern.compile("src[\r\n]*=[\r\n]*\\'(.*?)\\'", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("src[\r\n]*=[\r\n]*\\\"(.*?)\\\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        // eval(...) / expression(...)
        Pattern.compile("eval\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("expression\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        // javascript: / vbscript:
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
        // on事件属性，如 onclick= onload=
        Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
    };

    private XssCleaner() {
    }

    /**
     * 清洗字符串中的 XSS 危险内容。
     *
     * @param value 原始值
     * @return 清洗后的值；入参为 {@code null} 时返回 {@code null}
     */
    public static String clean(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String result = value;
        for (Pattern pattern : PATTERNS) {
            result = pattern.matcher(result).replaceAll("");
        }
        return result;
    }
}
