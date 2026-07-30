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
import cn.ypbin.starter.social.core.SocialService;
import java.util.List;
import me.zhyd.oauth.request.AuthRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 第三方登录自动配置。
 *
 * <p>仅在引入 JustAuth 且容器中存在至少一个 {@link AuthRequestProvider}（业务方为具体平台
 * 配置的授权请求）时装配 {@link SocialService}。本模块不预设任何平台配置，因为各平台的
 * appId / secret / 回调地址均由业务方持有。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(AuthRequest.class)
@ConditionalOnProperty(prefix = "ypbin.social", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SocialAutoConfiguration {

    @Bean
    @ConditionalOnBean(AuthRequestProvider.class)
    @ConditionalOnMissingBean
    public SocialService socialService(List<AuthRequestProvider> providers) {
        return new SocialService(providers);
    }
}
