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
package cn.ypbin.starter.crud.controller;

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
 * {@link BaseController} 辅助方法测试。
 *
 * @author wenbin
 * @since 2026-08-01
 */
class BaseControllerHelperTest {

    @RestController
    @RequestMapping("/helpers")
    static class HelperController extends BaseController {

        @GetMapping
        R<Map<String, Object>> helper() {
            return data(Map.of(
                "path", path(),
                "method", method(),
                "header", header("X-Test"),
                "param", param("q"),
                "ip", ip(),
                "login", isLogin(),
                "userIdPresent", userId().isPresent()
            ));
        }

        @PostMapping("/file")
        R<Map<String, Object>> upload() {
            MultipartFile file = file("file");
            return ok(Map.of(
                "fileName", file == null ? "" : file.getOriginalFilename(),
                "count", files("file").size()
            ));
        }

        @GetMapping("/status")
        R<Void> statusResult() {
            return status(Boolean.parseBoolean(param("ok", "false")));
        }
    }

    @Test
    void helperShouldReadRequestAndReturnData() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HelperController()).build();

        mockMvc.perform(get("/helpers?q=abc")
                .header("X-Test", "yes")
                .header("X-Forwarded-For", "10.0.0.1, 10.0.0.2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.path").value("/helpers"))
            .andExpect(jsonPath("$.data.method").value("GET"))
            .andExpect(jsonPath("$.data.header").value("yes"))
            .andExpect(jsonPath("$.data.param").value("abc"))
            .andExpect(jsonPath("$.data.ip").value("10.0.0.1"))
            .andExpect(jsonPath("$.data.login").value(false))
            .andExpect(jsonPath("$.data.userIdPresent").value(false));
    }

    @Test
    void helperShouldReadMultipartFiles() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HelperController()).build();
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/helpers/file").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.fileName").value("a.txt"))
            .andExpect(jsonPath("$.data.count").value(1));
    }

    @Test
    void statusShouldReturnSuccessOrFailure() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HelperController()).build();

        mockMvc.perform(get("/helpers/status?ok=true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get("/helpers/status?ok=false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false));
    }
}
