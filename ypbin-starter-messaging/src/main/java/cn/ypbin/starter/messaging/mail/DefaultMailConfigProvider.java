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
package cn.ypbin.starter.messaging.mail;

/**
 * 配置文件版邮件配置来源。
 *
 * <p>读取 {@code ypbin.mail.*} 绑定的 {@link MailConfig}。业务系统把 SMTP 配置做成后台可配置时，
 * 实现自定义 {@link MailConfigProvider} 覆盖本实现即可。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class DefaultMailConfigProvider implements MailConfigProvider {

    private final MailConfig config;

    public DefaultMailConfigProvider(MailConfig config) {
        this.config = config;
    }

    @Override
    public MailConfig getConfig() {
        return config;
    }
}
