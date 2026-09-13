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

package __PACKAGE__.controller;

import cn.ypbin.starter.core.model.R;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 示例接口：演示统一响应体 R 与薄 Controller 写法。
 *
 * @author wenbin
 * @since 2026-09-13
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

    /**
     * 回声接口。
     *
     * @return 统一响应体
     */
    @GetMapping("/ping")
    public R<Map<String, String>> ping() {
        return R.ok(Map.of("app", "__APP_NAME__", "status", "up"));
    }
}
