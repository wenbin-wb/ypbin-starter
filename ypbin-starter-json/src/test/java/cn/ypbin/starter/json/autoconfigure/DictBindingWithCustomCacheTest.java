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
package cn.ypbin.starter.json.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.json.dict.DictCache;
import cn.ypbin.starter.json.dict.DictItem;
import cn.ypbin.starter.json.dict.DictUtils;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 宿主覆盖 {@link DictCache} 时 {@link DictUtils} 仍须绑定到宿主实现。
 *
 * <p>回归点：绑定原先写在默认 {@code dictCache} Bean 的创建方法里，而该方法带
 * {@code @ConditionalOnMissingBean}——宿主一覆盖默认 Bean 就不创建，绑定永不执行，
 * {@code @DictText} 静默失效且零日志。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
class DictBindingWithCustomCacheTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));

    @Test
    @DisplayName("宿主提供 DictCache 时 DictUtils 绑定到宿主实现")
    void bindsCustomDictCache() {
        runner.withUserConfiguration(CustomDictCacheConfiguration.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(DictCache.class);
                assertThat(DictUtils.isReady()).isTrue();
                assertThat(DictUtils.translate("sex", "1")).isEqualTo("自定义");
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDictCacheConfiguration {

        @Bean
        DictCache customDictCache() {
            return new DictCache(type -> List.of(new DictItem("1", "自定义")), 60, 10);
        }
    }
}
