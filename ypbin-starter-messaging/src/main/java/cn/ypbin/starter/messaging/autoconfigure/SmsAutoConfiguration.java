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

import cn.ypbin.starter.messaging.sms.DefaultSmsService;
import cn.ypbin.starter.messaging.sms.SmsService;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 短信自动配置。
 *
 * <p>仅当类路径存在 sms4j（{@link SmsFactory}，即引入 sms4j-spring-boot-starter 与某厂商依赖）时装配
 * {@link SmsService}。厂商密钥/模板等配置由 sms4j 自身管理（{@code sms:} 前缀，或业务方实现 SmsReadConfig
 * 从数据库动态读取），本模块只提供统一发送门面。可通过 {@code ypbin.sms.enabled=false} 关停。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@AutoConfiguration
@ConditionalOnClass(SmsFactory.class)
@ConditionalOnProperty(prefix = "ypbin.sms", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SmsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SmsService smsService() {
        return new DefaultSmsService();
    }
}
