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
 * 邮件配置来源扩展点。
 *
 * <p>starter 默认提供配置文件版实现（读 {@code ypbin.mail.*}）。业务系统若把 SMTP 配置做成后台可配置
 * （如存配置中心/数据库表），实现本接口返回动态配置即可覆盖，配置变更后下次发送即时生效，无需重启。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@FunctionalInterface
public interface MailConfigProvider {

    /**
     * 获取当前生效的邮件配置。
     *
     * @return 邮件配置
     */
    MailConfig getConfig();
}
