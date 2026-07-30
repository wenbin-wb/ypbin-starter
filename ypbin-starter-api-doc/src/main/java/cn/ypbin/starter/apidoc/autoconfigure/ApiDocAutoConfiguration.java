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
package cn.ypbin.starter.apidoc.autoconfigure;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * API 文档自动配置。
 *
 * <p>基于配置项构建 {@link OpenAPI} 元信息 Bean。仅在 SpringDoc 存在且
 * {@code ypbin.api-doc.enabled=true} 时生效，业务方可提供自定义 OpenAPI Bean 覆盖。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnProperty(prefix = "ypbin.api-doc", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ApiDocProperties.class)
public class ApiDocAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ApiDocAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI ypbinOpenAPI(ApiDocProperties properties) {
        Info info = new Info()
            .title(properties.getTitle())
            .description(properties.getDescription())
            .version(properties.getVersion());

        ApiDocProperties.Contact contact = properties.getContact();
        if (StringUtils.hasText(contact.getName()) || StringUtils.hasText(contact.getEmail())) {
            info.contact(new Contact()
                .name(contact.getName())
                .email(contact.getEmail())
                .url(contact.getUrl()));
        }

        ApiDocProperties.License license = properties.getLicense();
        if (StringUtils.hasText(license.getName())) {
            info.license(new License()
                .name(license.getName())
                .url(license.getUrl()));
        }

        log.debug("[ypbin-starter] OpenAPI info configured, title={}.", properties.getTitle());
        return new OpenAPI().info(info);
    }
}
