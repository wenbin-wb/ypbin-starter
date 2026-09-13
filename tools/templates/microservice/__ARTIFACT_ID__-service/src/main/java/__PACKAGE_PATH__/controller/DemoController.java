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

import __PACKAGE__.api.DemoDto;
import cn.ypbin.starter.core.model.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 示例服务实现。
 *
 * @author wenbin
 * @since 2026-09-13
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

    /**
     * 按 ID 查询示例数据。
     *
     * @param id 主键
     * @return 统一响应体
     */
    @GetMapping("/{id}")
    public R<DemoDto> getById(@PathVariable("id") Long id) {
        DemoDto dto = new DemoDto();
        dto.setId(id);
        dto.setName("demo-" + id);
        return R.ok(dto);
    }
}
