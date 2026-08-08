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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import me.zhyd.oauth.request.AuthRequest;

/**
 * 默认 OAuth 授权请求注册表。
 *
 * <p>使用不可变 Map 快照与原子 copy-on-write 更新，读操作无锁且始终读取完整快照。
 *
 * @author wenbin
 * @since 2026-08-08
 */
public class DefaultSocialRequestRegistry implements SocialRequestRegistry {

    private final AtomicReference<Map<String, AuthRequest>> requests;

    public DefaultSocialRequestRegistry() {
        this(List.of());
    }

    public DefaultSocialRequestRegistry(List<AuthRequestProvider> providers) {
        Objects.requireNonNull(providers, "授权请求提供者列表不能为空");
        Map<String, AuthRequest> initialized = new HashMap<>();
        for (AuthRequestProvider provider : providers) {
            if (provider == null) {
                throw new IllegalArgumentException("授权请求提供者不能为空");
            }
            String source = normalize(provider.getSource());
            AuthRequest request = requireRequest(provider.getAuthRequest());
            if (initialized.putIfAbsent(source, request) != null) {
                throw new IllegalArgumentException("重复的第三方登录平台：" + source);
            }
        }
        this.requests = new AtomicReference<>(Map.copyOf(initialized));
    }

    @Override
    public void register(String source, AuthRequest request) {
        String normalizedSource = normalize(source);
        AuthRequest requiredRequest = requireRequest(request);
        requests.updateAndGet(current -> {
            Map<String, AuthRequest> updated = new HashMap<>(current);
            updated.put(normalizedSource, requiredRequest);
            return Map.copyOf(updated);
        });
    }

    @Override
    public AuthRequest remove(String source) {
        String normalizedSource = normalize(source);
        AtomicReference<AuthRequest> removed = new AtomicReference<>();
        requests.updateAndGet(current -> {
            AuthRequest existing = current.get(normalizedSource);
            removed.set(existing);
            if (existing == null) {
                return current;
            }
            Map<String, AuthRequest> updated = new HashMap<>(current);
            updated.remove(normalizedSource);
            return Map.copyOf(updated);
        });
        return removed.get();
    }

    @Override
    public AuthRequest require(String source) {
        String normalizedSource = normalize(source);
        AuthRequest request = requests.get().get(normalizedSource);
        if (request == null) {
            throw new SocialException("未配置第三方登录平台：" + source);
        }
        return request;
    }

    @Override
    public Set<String> sources() {
        return requests.get().keySet();
    }

    private static String normalize(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("第三方登录平台标识不能为空");
        }
        return source.trim().toLowerCase(Locale.ROOT);
    }

    private static AuthRequest requireRequest(AuthRequest request) {
        return Objects.requireNonNull(request, "授权请求不能为空");
    }
}
