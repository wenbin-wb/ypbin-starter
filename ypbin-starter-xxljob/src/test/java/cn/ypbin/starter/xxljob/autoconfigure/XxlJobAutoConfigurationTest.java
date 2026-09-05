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
 * <p>注：不在此处完整启动 {@link XxlJobSpringExecutor}（SmartInitializingSingleton 会拉起
 * Netty server，CI 沙箱网络受限易失败）；配置齐全的成功分支通过
 * {@link XxlJobAutoConfiguration#xxlJobExecutor} 直接调用验证属性映射。</p>
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
    void mapsPropertiesOntoExecutor() {
        XxlJobProperties properties = new XxlJobProperties();
        properties.setEnabled(true);
        properties.setAdminAddresses("http://localhost:8080/xxl-job-admin");
        properties.setAccessToken("tok");
        properties.setAppname("ypbin-system");
        properties.setIp("127.0.0.1");
        properties.setPort(9999);
        properties.setLogPath("/tmp/xxl-log");
        properties.setLogRetentionDays(15);

        XxlJobAutoConfiguration auto = new XxlJobAutoConfiguration();
        XxlJobSpringExecutor executor = auto.xxlJobExecutor(properties);

        assertThat(executor).isNotNull();
        assertThat(executor.getPort()).isEqualTo(9999);
        assertThat(executor.getAppname()).isEqualTo("ypbin-system");
        assertThat(executor.getAddress()).isNull();
    }
}
