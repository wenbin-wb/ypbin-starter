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

import cn.ypbin.starter.social.core.AuthRequestProvider;
import cn.ypbin.starter.social.core.DefaultSocialRequestRegistry;
import cn.ypbin.starter.social.core.SocialRequestRegistry;
import cn.ypbin.starter.social.core.SocialService;
import java.util.List;
import me.zhyd.oauth.request.AuthRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 第三方登录自动配置。
 *
 * <p>引入 JustAuth 且能力开关开启时，始终装配可覆盖的 {@link SocialRequestRegistry} 与
 * {@link SocialService}。业务方可通过 {@link AuthRequestProvider} 提供初始化平台，也可在运行时动态注册。
 *
 * @author wenbin
 * @since 2026-08-08
 */
@AutoConfiguration
@ConditionalOnClass(AuthRequest.class)
@ConditionalOnProperty(prefix = "ypbin.social", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SocialAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SocialRequestRegistry socialRequestRegistry(List<AuthRequestProvider> providers) {
        return new DefaultSocialRequestRegistry(providers);
    }

    @Bean
    @ConditionalOnMissingBean
    public SocialService socialService(SocialRequestRegistry registry) {
        return new SocialService(registry);
    }
}
