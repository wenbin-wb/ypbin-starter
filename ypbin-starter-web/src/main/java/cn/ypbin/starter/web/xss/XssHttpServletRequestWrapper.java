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
 * <p>对请求参数（Query / 表单）与请求头逐一调用 {@link XssCleaner#clean}：按危险片段黑名单
 * <strong>删除</strong>{@code <script>}、{@code javascript:}、事件属性等内容（并非整体 HTML 转义）。
 * 未覆盖的方法（如 {@code getInputStream}/{@code getReader}）保持透传。</p>
 *
 * <p><strong>范围边界：JSON 请求体不在本过滤器清洗范围。</strong>本包装器不读取/改写请求体；
 * Jackson 反序列化层也未接入字符串清洗器——JSON body 中的脚本内容会原样进入业务层。需要清洗
 * JSON 字符串字段时，请接入自定义的 Jackson {@code String} 反序列化清洗器（本模块当前未内置），
 * 或由业务层对字段做白名单校验。</p>
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
