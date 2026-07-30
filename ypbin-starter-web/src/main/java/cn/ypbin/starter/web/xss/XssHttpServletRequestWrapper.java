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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XSS 请求包装器。
 *
 * <p>对请求参数（Query / 表单）逐一做 HTML 转义，拦截 {@code <script>} 等注入脚本。
 * 转义而非删除，尽量保留原始语义，避免误伤正常内容。JSON 请求体的清洗由 Jackson
 * 反序列化层的转义策略负责，此处专注 Servlet 参数。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        return XssCleaner.clean(super.getParameter(name));
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        String[] cleaned = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            cleaned[i] = XssCleaner.clean(values[i]);
        }
        return cleaned;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> origin = super.getParameterMap();
        Map<String, String[]> cleaned = new LinkedHashMap<>(origin.size());
        origin.forEach((key, values) -> {
            String[] cleanValues = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                cleanValues[i] = XssCleaner.clean(values[i]);
            }
            cleaned.put(key, cleanValues);
        });
        return cleaned;
    }

    @Override
    public String getHeader(String name) {
        return XssCleaner.clean(super.getHeader(name));
    }
}
