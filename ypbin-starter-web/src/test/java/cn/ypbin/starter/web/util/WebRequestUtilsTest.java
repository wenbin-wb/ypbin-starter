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
package cn.ypbin.starter.web.util;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.ypbin.starter.core.model.R;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@link WebRequestUtils} 请求上下文读取测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class WebRequestUtilsTest {

    @RestController
    @RequestMapping("/request-utils")
    static class ProbeController {

        @GetMapping
        R<Map<String, Object>> probe() {
            return R.ok(Map.of(
                "path", WebRequestUtils.path(),
                "method", WebRequestUtils.method(),
                "header", WebRequestUtils.header("X-Test"),
                "headerWithDefault", WebRequestUtils.header("X-Missing", "fallback"),
                "param", WebRequestUtils.param("q"),
                "paramWithDefault", WebRequestUtils.param("empty", "dft"),
                "ip", WebRequestUtils.ip()
            ));
        }

        @PostMapping("/file")
        R<Map<String, Object>> upload() {
            MultipartFile file = WebRequestUtils.file("file");
            return R.ok(Map.of(
                "fileName", file == null ? "" : file.getOriginalFilename(),
                "count", WebRequestUtils.files("file").size()
            ));
        }
    }

    @Test
    void shouldReadRequestContext() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController()).build();

        mockMvc.perform(get("/request-utils?q=abc")
                .header("X-Test", "yes")
                .header("X-Forwarded-For", "10.0.0.1, 10.0.0.2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.path").value("/request-utils"))
            .andExpect(jsonPath("$.data.method").value("GET"))
            .andExpect(jsonPath("$.data.header").value("yes"))
            .andExpect(jsonPath("$.data.headerWithDefault").value("fallback"))
            .andExpect(jsonPath("$.data.param").value("abc"))
            .andExpect(jsonPath("$.data.paramWithDefault").value("dft"))
            .andExpect(jsonPath("$.data.ip").value("10.0.0.1"));
    }

    @Test
    void shouldReadMultipartFiles() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController()).build();
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/request-utils/file").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.fileName").value("a.txt"))
            .andExpect(jsonPath("$.data.count").value(1));
    }
}
