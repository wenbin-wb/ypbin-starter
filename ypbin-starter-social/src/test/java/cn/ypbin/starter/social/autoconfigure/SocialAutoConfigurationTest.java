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
package cn.ypbin.starter.social.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.social.core.AuthRequestProvider;
import cn.ypbin.starter.social.core.DefaultSocialRequestRegistry;
import cn.ypbin.starter.social.core.SocialRequestRegistry;
import cn.ypbin.starter.social.core.SocialService;
import me.zhyd.oauth.request.AuthRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link SocialAutoConfiguration} 装配测试。
 *
 * @author wenbin
 * @since 2026-08-08
 */
class SocialAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SocialAutoConfiguration.class));

    @Test
    void shouldRegisterRegistryAndServiceWithoutProvider() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SocialRequestRegistry.class);
            assertThat(context).hasSingleBean(SocialService.class);
            assertThat(context.getBean(SocialRequestRegistry.class)).isInstanceOf(DefaultSocialRequestRegistry.class);
            assertThat(context.getBean(SocialService.class).sources()).isEmpty();
        });
    }

    @Test
    void shouldInitializeRegistryFromProviders() {
        runner.withUserConfiguration(ProviderConfiguration.class)
            .run(context -> assertThat(context.getBean(SocialService.class).sources()).containsExactly("github"));
    }

    @Test
    void shouldBackOffForCustomBeans() {
        runner.withUserConfiguration(CustomBeansConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(SocialRequestRegistry.class);
                assertThat(context).hasSingleBean(SocialService.class);
                assertThat(context.getBean(SocialRequestRegistry.class))
                    .isSameAs(CustomBeansConfiguration.REGISTRY);
                assertThat(context.getBean(SocialService.class)).isSameAs(CustomBeansConfiguration.SERVICE);
            });
    }

    @Test
    void shouldBackOffWhenDisabled() {
        runner.withPropertyValues("ypbin.social.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(SocialRequestRegistry.class);
                assertThat(context).doesNotHaveBean(SocialService.class);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class ProviderConfiguration {

        @Bean
        AuthRequestProvider authRequestProvider() {
            return new AuthRequestProvider() {
                @Override
                public String getSource() {
                    return "GitHub";
                }

                @Override
                public AuthRequest getAuthRequest() {
                    return Mockito.mock(AuthRequest.class);
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomBeansConfiguration {

        private static final SocialRequestRegistry REGISTRY = Mockito.mock(SocialRequestRegistry.class);
        private static final SocialService SERVICE = Mockito.mock(SocialService.class);

        @Bean
        SocialRequestRegistry customSocialRequestRegistry() {
            return REGISTRY;
        }

        @Bean
        SocialService customSocialService() {
            return SERVICE;
        }
    }
}
