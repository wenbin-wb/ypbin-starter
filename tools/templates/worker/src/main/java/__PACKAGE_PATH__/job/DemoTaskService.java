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
package __PACKAGE__.job;

import org.springframework.stereotype.Service;

/**
 * 示例任务业务逻辑（与调度框架解耦，便于单元测试）。
 *
 * @author wenbin
 * @since 2026-09-13
 */
@Service
public class DemoTaskService {

    /**
     * 任务标识。
     *
     * @return 任务标识
     */
    public String taskName() {
        return "__ARTIFACT_ID__-demo";
    }
}
