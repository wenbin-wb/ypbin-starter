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
import cn.ypbin.starter.social.core.InMemorySocialStateStore;
import cn.ypbin.starter.social.core.SocialRequestRegistry;
import cn.ypbin.starter.social.core.SocialService;
import cn.ypbin.starter.social.core.SocialStateStore;
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
@ConditionalOnProperty(prefix = SocialAutoConfiguration.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class SocialAutoConfiguration {

    /**
     * 配置前缀。
     *
     * <p>本能力只有装配开关、没有绑定字段，故前缀常量就近定义在本自动配置类内。
     */
    public static final String PREFIX = "ypbin.social";

    @Bean
    @ConditionalOnMissingBean
    public SocialRequestRegistry socialRequestRegistry(List<AuthRequestProvider> providers) {
        return new DefaultSocialRequestRegistry(providers);
    }

    /**
     * OAuth state 存储：默认进程内实现，多实例无会话粘滞时由宿主提供共享实现（如 Redis）覆盖。
     */
    @Bean
    @ConditionalOnMissingBean
    public SocialStateStore socialStateStore() {
        return new InMemorySocialStateStore();
    }

    /**
     * 旧签名：保留二进制兼容，实际装配由 {@link #socialService(SocialRequestRegistry, SocialStateStore)} 完成。
     *
     * @param registry 授权请求注册表
     * @return 第三方登录服务（使用新建的进程内 state 存储，非容器 Bean）
     * @deprecated 请使用带 {@link SocialStateStore} 的重载（本方法不再注册 Bean）
     */
    @Deprecated
    public SocialService socialService(SocialRequestRegistry registry) {
        return new SocialService(registry);
    }

    /**
     * 第三方登录服务。
     *
     * @param registry   授权请求注册表
     * @param stateStore state 存储（发起授权时登记，回调时一次性校验）
     * @return 第三方登录服务
     */
    @Bean(name = "socialService")
    @ConditionalOnMissingBean
    public SocialService socialService(SocialRequestRegistry registry, SocialStateStore stateStore) {
        return new SocialService(registry, stateStore);
    }
}
