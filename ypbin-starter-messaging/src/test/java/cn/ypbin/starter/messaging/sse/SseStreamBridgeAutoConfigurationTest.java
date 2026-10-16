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
package cn.ypbin.starter.messaging.sse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

/**
 * {@link SseStreamBridgeAutoConfiguration} 装配测试。
 *
 * <p>锁定两点：① 正常（servlet + Reactor 在 classpath）时注册 {@link SseStreamBridge} Bean；②
 * {@code reactor-core} 缺席时<strong>整块不装配</strong>——不能因为可选依赖缺失就抛
 * {@code NoClassDefFoundError} 把宿主启动带崩，也不能装配出一个调用即炸的 Bean。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class SseStreamBridgeAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SseStreamBridgeAutoConfiguration.class));

    @Test
    @DisplayName("servlet + Reactor：注册 SseStreamBridge Bean")
    void registersBridgeBean() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SseStreamBridge.class);
        });
    }

    @Test
    @DisplayName("★ 隐藏 reactor-core：整块配置不装配，且上下文正常启动（不抛 NoClassDefFoundError）")
    void skipsWhenReactorMissing() {
        runner.withClassLoader(new FilteredClassLoader(Flux.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(SseStreamBridge.class);
            });
    }

    @Test
    @DisplayName("非 servlet（reactive/无 Web）环境：不装配（桥接依赖 Spring MVC 的 SseEmitter）")
    void skipsWhenNotServletWebApplication() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SseStreamBridgeAutoConfiguration.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(SseStreamBridge.class);
            });
    }

    @Test
    @DisplayName("宿主自定义同类型 Bean 时让位（@ConditionalOnMissingBean 覆盖语义）")
    void backsOffWhenHostProvidesOwnBridge() {
        runner.withUserConfiguration(CustomBridgeConfig.class)
            .run(context -> {
                assertThat(context).hasSingleBean(SseStreamBridge.class);
                assertThat(context.getBean(SseStreamBridge.class)).isSameAs(CustomBridgeConfig.CUSTOM);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomBridgeConfig {

        static final SseStreamBridge CUSTOM = new SseStreamBridge();

        @Bean
        SseStreamBridge customSseStreamBridge() {
            return CUSTOM;
        }
    }
}
