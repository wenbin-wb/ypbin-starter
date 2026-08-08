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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * {@link SocialService} 测试。
 *
 * @author wenbin
 * @since 2026-08-08
 */
class SocialServiceTest {

    @Test
    void shouldResolveRequestDynamicallyFromRegistry() {
        SocialRequestRegistry registry = Mockito.mock(SocialRequestRegistry.class);
        AuthRequest first = Mockito.mock(AuthRequest.class);
        AuthRequest second = Mockito.mock(AuthRequest.class);
        when(registry.require("github")).thenReturn(first, second);
        when(first.authorize(anyString())).thenReturn("first");
        when(second.authorize(anyString())).thenReturn("second");
        SocialService service = new SocialService(registry);

        assertThat(service.authorizeUrl("github")).isEqualTo("first");
        assertThat(service.authorizeUrl("github")).isEqualTo("second");
        verify(registry, Mockito.times(2)).require("github");
    }

    @Test
    void shouldKeepProviderListConstructorCompatible() {
        AuthRequest request = Mockito.mock(AuthRequest.class);
        when(request.authorize(anyString())).thenReturn("url");
        SocialService service = new SocialService(List.of(provider("GitHub", request)));

        assertThat(service.authorizeUrl("github")).isEqualTo("url");
        assertThat(service.sources()).containsExactly("github");
    }

    @Test
    void shouldReturnUserAndExposeLoginFailure() {
        SocialRequestRegistry registry = new DefaultSocialRequestRegistry();
        AuthRequest request = Mockito.mock(AuthRequest.class);
        AuthCallback callback = new AuthCallback();
        AuthUser user = new AuthUser();
        AuthResponse<AuthUser> success = AuthResponse.<AuthUser>builder().code(2000).data(user).build();
        AuthResponse<AuthUser> failure = AuthResponse.<AuthUser>builder().code(5000).msg("拒绝授权").build();
        registry.register("github", request);
        SocialService service = new SocialService(registry);
        when(request.login(callback)).thenReturn(success, failure);

        assertThat(service.login("github", callback)).isSameAs(user);
        assertThatExceptionOfType(SocialException.class)
            .isThrownBy(() -> service.login("github", callback))
            .withMessage("第三方登录失败：拒绝授权");
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
