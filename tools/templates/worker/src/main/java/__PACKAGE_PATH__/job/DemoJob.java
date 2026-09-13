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

import cn.ypbin.starter.async.util.AsyncUtils;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 示例任务：演示 XXL-JOB 调度入口 + 异步执行 + 业务逻辑下沉到 Service。
 *
 * <p>需在配置中开启 {@code ypbin.xxl-job.enabled=true} 并指定调度中心地址后才会被调度；
 * 未开启时本类仅作为普通 Bean 存在，不影响启动。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
@Component
@RequiredArgsConstructor
public class DemoJob {

    private static final Logger log = LoggerFactory.getLogger(DemoJob.class);

    private final DemoTaskService taskService;

    /**
     * XXL-JOB 任务入口：提交异步执行，避免占用调度线程。
     */
    @XxlJob("demoJob")
    public void execute() {
        String name = taskService.taskName();
        log.info("[{}] demoJob 开始执行", name);
        AsyncUtils.run(() -> log.info("[{}] demoJob 异步阶段完成", name));
    }
}
