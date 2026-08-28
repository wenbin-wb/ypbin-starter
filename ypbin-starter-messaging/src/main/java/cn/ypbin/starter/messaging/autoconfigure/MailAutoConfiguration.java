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
package cn.ypbin.starter.messaging.autoconfigure;

import cn.ypbin.starter.messaging.mail.DefaultMailConfigProvider;
import cn.ypbin.starter.messaging.mail.MailConfig;
import cn.ypbin.starter.messaging.mail.MailConfigProvider;
import cn.ypbin.starter.messaging.mail.MailService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 邮件自动配置。
 *
 * <p>SMTP 配置默认绑定 {@code ypbin.mail.*}（配置文件版 {@link MailConfigProvider}），
 * 业务方提供自定义 {@link MailConfigProvider}（如从数据库读）即可覆盖，实现后台动态配置、不重启生效。
 * {@link MailService} 按配置指纹缓存并按需重建底层 sender。</p>
 *
 * <p>仅需类路径存在 {@link JavaMailSender}（引入 spring-boot-starter-mail 即满足），不再要求业务方
 * 预先配置 {@code spring.mail.*} 生成 sender Bean。可通过 {@code ypbin.mail.enabled=false} 关停。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(JavaMailSender.class)
@ConditionalOnProperty(prefix = "ypbin.mail", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MailAutoConfiguration {

    /** 邮件配置前缀 */
    private static final String MAIL_PREFIX = "ypbin.mail";

    /**
     * 邮件配置，绑定 {@code ypbin.mail.*}。
     */
    @Bean
    @ConfigurationProperties(prefix = MAIL_PREFIX)
    @ConditionalOnMissingBean
    public MailConfig mailConfig() {
        return new MailConfig();
    }

    /**
     * 默认配置文件版邮件配置来源。业务方提供自定义实现即可从数据库接管。
     */
    @Bean
    @ConditionalOnMissingBean
    public MailConfigProvider mailConfigProvider(MailConfig mailConfig) {
        return new DefaultMailConfigProvider(mailConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public MailService mailService(MailConfigProvider mailConfigProvider) {
        return new MailService(mailConfigProvider);
    }
}
