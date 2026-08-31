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
package cn.ypbin.starter.data;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.data.autoconfigure.DataAutoConfiguration;
import cn.ypbin.starter.data.autoconfigure.DataProperties;
import cn.ypbin.starter.data.core.AuditorProvider;
import cn.ypbin.starter.data.core.EntityStatus;
import cn.ypbin.starter.data.handler.DefaultMetaObjectHandler;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 数据模块自动装配与审计填充测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class DataAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DataAutoConfiguration.class));

    @Test
    void shouldWireCoreBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(MetaObjectHandler.class);
            assertThat(context).hasSingleBean(AuditorProvider.class);
            assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
        });
    }

    @Test
    void shouldProvideDefaultAuditor() {
        runner.run(context -> {
            AuditorProvider provider = context.getBean(AuditorProvider.class);
            assertThat(provider.getCurrentAuditor()).isEmpty();
        });
    }

    @Test
    void shouldBuildMetaObjectHandler() {
        DataAutoConfiguration config = new DataAutoConfiguration();
        MetaObjectHandler handler = config.metaObjectHandler(() -> java.util.Optional.of(7L));
        assertThat(handler).isInstanceOf(DefaultMetaObjectHandler.class);
    }

    @Test
    void entityStatusShouldCarryCodes() {
        assertThat(EntityStatus.ENABLED.getCode()).isEqualTo(1);
        assertThat(EntityStatus.DISABLED.getCode()).isEqualTo(0);
    }

    @Test
    void dataPropertiesShouldReflectEncryptDefaults() {
        DataProperties props = new DataProperties();
        assertThat(props.getEncrypt()).isNotNull();
    }

}
