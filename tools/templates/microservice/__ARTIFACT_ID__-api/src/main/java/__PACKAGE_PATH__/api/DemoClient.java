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
package __PACKAGE__.api;

import cn.ypbin.starter.core.model.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 示例服务契约：调用方依赖本接口即可，无需感知服务实现。
 *
 * <p>服务名与 {@code spring.application.name} 一致，由注册中心解析为实例地址。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
@FeignClient(name = "__ARTIFACT_ID__-service", path = "/demo")
public interface DemoClient {

    /**
     * 按 ID 查询示例数据。
     *
     * @param id 主键
     * @return 统一响应体
     */
    @GetMapping("/{id}")
    R<DemoDto> getById(@PathVariable("id") Long id);
}
