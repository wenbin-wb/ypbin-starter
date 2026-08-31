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
package cn.ypbin.starter.job.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.job.core.JobManager;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

/**
 * 定时任务锁工厂与配置测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class JobSupportTest {

    @Test
    void lockFactoryShouldFallbackToNoop() {
        StaticApplicationContext ctx = new StaticApplicationContext();
        ctx.refresh();
        JobManager.JobLock lock = JobLockFactory.create(ctx);
        assertThat(lock).isNotNull();
        assertThat(lock.tryLock("job:1", "node-1", Duration.ofSeconds(10))).isTrue();
        assertThat(lock.unlock("job:1", "node-1")).isTrue();
    }

    @Test
    void lockFactoryShouldDelegateWhenLockServicePresent() {
        StaticApplicationContext ctx = new StaticApplicationContext();
        ctx.registerBean("lockService", Object.class, () -> new Object());
        ctx.refresh();
        JobManager.JobLock lock = JobLockFactory.create(ctx);
        assertThat(lock).isNotNull();
    }

    @Test
    void autoConfigurationShouldBuildBeans() {
        JobAutoConfiguration config = new JobAutoConfiguration();
        assertThat(config.jobExecutor()).isNotNull();
        assertThat(config.jobTaskScheduler(new JobProperties())).isNotNull();
        assertThat(config.jobExecutionListener()).isNotNull();
    }

    @Test
    void propertiesShouldExposeDefaults() {
        JobProperties props = new JobProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getPoolSize()).isEqualTo(4);
        assertThat(props.getThreadNamePrefix()).isEqualTo("ypbin-job-");
    }
}
