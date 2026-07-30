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
package cn.ypbin.starter.captcha.autoconfigure;

import cn.ypbin.starter.captcha.core.CaptchaService;
import cn.ypbin.starter.captcha.core.CaptchaStore;
import cn.ypbin.starter.captcha.core.InMemoryCaptchaStore;
import com.wf.captcha.base.Captcha;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 验证码自动配置。
 *
 * <p>装配验证码存储（默认内存，可被 Redis 实现覆盖）与验证码服务。仅在 easy-captcha 存在且
 * {@code ypbin.captcha.enabled=true}（默认开启）时生效。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(Captcha.class)
@ConditionalOnProperty(prefix = "ypbin.captcha", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CaptchaProperties.class)
public class CaptchaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CaptchaStore captchaStore() {
        return new InMemoryCaptchaStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public CaptchaService captchaService(CaptchaStore store, CaptchaProperties properties) {
        return new CaptchaService(store, properties);
    }
}
