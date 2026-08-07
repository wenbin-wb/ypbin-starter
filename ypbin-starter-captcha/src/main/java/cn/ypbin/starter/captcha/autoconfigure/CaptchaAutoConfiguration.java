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

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.spring.autoconfiguration.ImageCaptchaAutoConfiguration;
import cn.ypbin.starter.captcha.core.CaptchaService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 验证码自动配置。
 *
 * <p>在 tianai-captcha 已自动装配 {@link ImageCaptchaApplication} 的基础上，提供一个便捷的
 * {@link CaptchaService} 薄封装，并由 {@link CaptchaResourceInitializer} 幂等加载默认模板与背景图。
 * 仅在 tianai 核心存在且 {@code ypbin.captcha.enabled=true}（默认开启）时生效。自定义图片资源、
 * 缓存（本地/Redis）、二次校验等能力通过 tianai 自身的 {@code aj.captcha.*} / {@code captcha.*}
 * 配置项调整。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration(after = ImageCaptchaAutoConfiguration.class)
@ConditionalOnClass(ImageCaptchaApplication.class)
@ConditionalOnProperty(prefix = "ypbin.captcha", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CaptchaProperties.class)
public class CaptchaAutoConfiguration {

    @Bean
    @ConditionalOnBean(ImageCaptchaApplication.class)
    @ConditionalOnMissingBean
    public CaptchaService captchaService(ImageCaptchaApplication application) {
        return new CaptchaService(application);
    }

    @Bean(initMethod = "init")
    @ConditionalOnBean(ImageCaptchaApplication.class)
    @ConditionalOnMissingBean
    public CaptchaResourceInitializer captchaResourceInitializer(ImageCaptchaApplication application,
        CaptchaProperties properties) {
        return new CaptchaResourceInitializer(application, properties);
    }
}
