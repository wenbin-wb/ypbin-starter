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

import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import java.lang.reflect.Field;
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

    /** xxl-job-core 3.5.0 中 {@code XxlJobExecutor#logPath} 的默认值。 */
    private static final String UPSTREAM_DEFAULT_LOG_PATH = "/data/applogs/xxl-job/jobhandler";

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

    /** 3.5.0 起 AccessToken 不可为空：空 token 不得进入 XxlJobExecutor，须在装配期失败并给出可操作提示。 */
    @Test
    void missingAccessTokenFailsFast() {
        contextRunner
            .withPropertyValues(
                "ypbin.xxl-job.enabled=true",
                "ypbin.xxl-job.admin-addresses=http://localhost:8080/xxl-job-admin",
                "ypbin.xxl-job.appname=ypbin-system")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessageContaining("access-token");
                assertThat(context.getStartupFailure()).hasMessageContaining("3.5.0");
            });
    }

    @Test
    void mapsPropertiesOntoExecutor() {
        XxlJobProperties properties = new XxlJobProperties();
        properties.setEnabled(true);
        properties.setAdminAddresses("http://localhost:8080/xxl-job-admin");
        properties.setAccessToken("tok");
        properties.setAppname("ypbin-system");
        properties.setAddress("http://10.0.0.1:9999/");
        properties.setIp("127.0.0.1");
        properties.setPort(9999);
        properties.setLogPath("/tmp/xxl-log");
        properties.setLogRetentionDays(15);
        properties.setGlueEnabled(false);

        XxlJobAutoConfiguration auto = new XxlJobAutoConfiguration();
        XxlJobSpringExecutor executor = auto.xxlJobExecutor(properties);

        assertThat(executor).isNotNull();
        assertThat(executor.getPort()).isEqualTo(9999);
        assertThat(executor.getAppname()).isEqualTo("ypbin-system");
        assertThat(executor.getAddress()).isEqualTo("http://10.0.0.1:9999/");
        assertThat(executor.getGlueEnabled()).isFalse();
        assertThat(readPrivateField(executor, "logPath")).isEqualTo("/tmp/xxl-log");
    }

    /**
     * 未配置 log-path 时必须让 xxl-job-core 的默认值生效：3.5.0 的
     * {@code XxlJobFileAppender#initLogPath} 对空路径直接抛错，装配期若把 null 回填给执行器，
     * 会在执行器 start() 时才炸；这里用反射读取执行器生效值作回归保护。
     */
    @Test
    void blankLogPathKeepsUpstreamDefault() throws Exception {
        XxlJobProperties properties = new XxlJobProperties();
        properties.setEnabled(true);
        properties.setAdminAddresses("http://localhost:8080/xxl-job-admin");
        properties.setAccessToken("tok");
        properties.setAppname("ypbin-system");

        XxlJobAutoConfiguration auto = new XxlJobAutoConfiguration();
        XxlJobSpringExecutor executor = auto.xxlJobExecutor(properties);

        assertThat(readPrivateField(executor, "logPath")).isEqualTo(UPSTREAM_DEFAULT_LOG_PATH);
        // GLUE 开关默认开启；AccessToken 默认空串（但装配期必须显式配置，空串会被上一条用例拦截）
        assertThat(executor.getGlueEnabled()).isTrue();
        assertThat(new XxlJobProperties().isGlueEnabled()).isTrue();
        assertThat(new XxlJobProperties().getAccessToken()).isEmpty();
    }

    private static Object readPrivateField(XxlJobSpringExecutor executor, String name) {
        try {
            Field field = XxlJobExecutor.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(executor);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("读取 XxlJobExecutor#" + name + " 失败", e);
        }
    }
}
