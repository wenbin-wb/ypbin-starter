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
package cn.ypbin.starter.xxljob.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * {@link XxlJobAutoConfiguration} 装配测试。
 *
 * @author wenbin
 * @since 2026-09-05
 */
class XxlJobAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(XxlJobAutoConfiguration.class));

    @Test
    void disabledByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(XxlJobSpringExecutor.class));
    }

    @Test
    void missingAdminAddressFailsFast() {
        contextRunner
            .withPropertyValues("ypbin.xxl-job.enabled=true", "ypbin.xxl-job.appname=ypbin-system")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessageContaining("admin-addresses");
            });
    }

    @Test
    void missingAppnameFailsFast() {
        contextRunner
            .withPropertyValues("ypbin.xxl-job.enabled=true", "ypbin.xxl-job.admin-addresses=http://localhost:8080/xxl-job-admin")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessageContaining("appname");
            });
    }

    @Test
    void createsExecutorWhenConfigured() {
        contextRunner
            .withPropertyValues(
                "ypbin.xxl-job.enabled=true",
                "ypbin.xxl-job.admin-addresses=http://localhost:8080/xxl-job-admin",
                "ypbin.xxl-job.appname=ypbin-system",
                "ypbin.xxl-job.port=9999")
            .run(context -> {
                XxlJobSpringExecutor executor = context.getBean(XxlJobSpringExecutor.class);
                assertThat(executor).isNotNull();
                assertThat(executor.getPort()).isEqualTo(9999);
            });
    }
}
