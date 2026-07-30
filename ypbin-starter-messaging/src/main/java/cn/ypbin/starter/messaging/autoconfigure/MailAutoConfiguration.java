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

import cn.ypbin.starter.messaging.mail.MailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 邮件自动配置。
 *
 * <p>仅当存在 {@link JavaMailSender}（即业务方配置了 spring.mail.*）时装配 {@link MailService}。
 * 发件人默认取 {@code spring.mail.username}，可被业务方自定义 Bean 覆盖。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration(after = MailSenderAutoConfiguration.class)
@ConditionalOnClass(JavaMailSender.class)
@ConditionalOnBean(JavaMailSender.class)
public class MailAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MailService mailService(JavaMailSender mailSender,
        @Value("${spring.mail.username:}") String defaultFrom) {
        return new MailService(mailSender, defaultFrom);
    }
}
