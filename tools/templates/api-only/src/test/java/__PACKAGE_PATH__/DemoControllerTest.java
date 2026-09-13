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

package __PACKAGE__;

import static org.assertj.core.api.Assertions.assertThat;

import __PACKAGE__.controller.DemoController;
import cn.ypbin.starter.core.model.R;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 示例接口单元测试。
 *
 * @author wenbin
 * @since 2026-09-13
 */
class DemoControllerTest {

    @Test
    void pingShouldReturnUnifiedResponse() {
        R<Map<String, String>> result = new DemoController().ping();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("status", "up");
    }
}
