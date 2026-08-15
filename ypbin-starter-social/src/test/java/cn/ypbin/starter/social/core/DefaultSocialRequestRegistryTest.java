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
package cn.ypbin.starter.social.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import me.zhyd.oauth.request.AuthRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * {@link DefaultSocialRequestRegistry} 测试。
 *
 * @author wenbin
 * @since 2026-08-08
 */
class DefaultSocialRequestRegistryTest {

    @Test
    void shouldNormalizeSourceAndExposeImmutableSnapshot() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            AuthRequest request = Mockito.mock(AuthRequest.class);
            DefaultSocialRequestRegistry registry = new DefaultSocialRequestRegistry();

            registry.register(" GITHUB ", request);

            assertThat(registry.require("github")).isSameAs(request);
            assertThat(registry.sources()).containsExactly("github");
            assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> registry.sources().remove("github"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void shouldReplaceAndRemoveRequestDynamically() {
        AuthRequest first = Mockito.mock(AuthRequest.class);
        AuthRequest second = Mockito.mock(AuthRequest.class);
        DefaultSocialRequestRegistry registry = new DefaultSocialRequestRegistry();

        registry.register("github", first);
        registry.register("GITHUB", second);

        assertThat(registry.require("github")).isSameAs(second);
        assertThat(registry.remove(" GitHub ")).isSameAs(second);
        assertThat(registry.remove("github")).isNull();
        assertThat(registry.sources()).isEmpty();
        assertThatExceptionOfType(SocialException.class)
            .isThrownBy(() -> registry.require("github"))
            .withMessage("未配置第三方登录平台：github");
    }

    @Test
    void shouldRejectInvalidRegistrationAndDuplicateInitialization() {
        AuthRequest request = Mockito.mock(AuthRequest.class);
        AuthRequestProvider first = provider("GitHub", request);
        AuthRequestProvider duplicate = provider(" github ", Mockito.mock(AuthRequest.class));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DefaultSocialRequestRegistry(List.of(first, duplicate)))
            .withMessage("重复的第三方登录平台：github");
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DefaultSocialRequestRegistry().register(" ", request));
        assertThatNullPointerException()
            .isThrownBy(() -> new DefaultSocialRequestRegistry().register("github", null))
            .withMessage("授权请求不能为空");
    }

    @Test
    void shouldPreserveConcurrentRegistrations() throws InterruptedException {
        int count = 20;
        DefaultSocialRequestRegistry registry = new DefaultSocialRequestRegistry();
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (int i = 0; i < count; i++) {
                int index = i;
                executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    registry.register("source-" + index, Mockito.mock(AuthRequest.class));
                    return null;
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            assertThat(registry.sources()).hasSize(count);
        } finally {
            executor.shutdownNow();
        }
    }

    private static AuthRequestProvider provider(String source, AuthRequest request) {
        return new AuthRequestProvider() {
            @Override
            public String getSource() {
                return source;
            }

            @Override
            public AuthRequest getAuthRequest() {
                return request;
            }
        };
    }
}
