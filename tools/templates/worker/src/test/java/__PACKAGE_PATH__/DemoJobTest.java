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

import __PACKAGE__.job.DemoJob;
import __PACKAGE__.job.DemoTaskService;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * 示例任务测试：断言调度入口已正确标注（结构）与业务逻辑正确（行为）。
 *
 * <p>不直接调用 {@code execute()}：它会提交异步任务，需要 Spring 容器提供执行器；
 * 单元测试聚焦纯逻辑与契约，异步链路由集成测试覆盖。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
class DemoJobTest {

    @Test
    void executeShouldBeRegisteredAsXxlJob() throws NoSuchMethodException {
        Method method = DemoJob.class.getMethod("execute");

        XxlJob annotation = method.getAnnotation(XxlJob.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("demoJob");
    }

    @Test
    void taskNameShouldBeStable() {
        assertThat(new DemoTaskService().taskName()).isEqualTo("__ARTIFACT_ID__-demo");
    }
}
