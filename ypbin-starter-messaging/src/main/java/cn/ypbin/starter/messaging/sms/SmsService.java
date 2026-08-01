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
package cn.ypbin.starter.messaging.sms;

import java.util.List;
import java.util.Map;

/**
 * 短信发送服务。
 *
 * <p>基于短信聚合框架 sms4j 的轻封装：屏蔽阿里云/腾讯云等厂商差异，统一以模板 + 变量方式发送。
 * 厂商与密钥配置走 sms4j 原生配置（{@code sms:} 前缀，配置文件或由业务方实现 sms4j 的 SmsReadConfig
 * 从数据库动态读取），本服务只提供友好的发送门面。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface SmsService {

    /**
     * 是否已配置可用的短信厂商。
     *
     * @return 是否就绪
     */
    boolean isConfigured();

    /**
     * 按模板变量顺序发送短信（使用默认厂商配置的模板）。
     *
     * @param phone   手机号
     * @param message 模板变量值（单变量场景）
     * @return 是否发送成功
     */
    boolean send(String phone, String message);

    /**
     * 按模板变量映射发送短信（使用默认厂商）。
     *
     * @param phone     手机号
     * @param variables 模板变量键值对
     * @return 是否发送成功
     */
    boolean send(String phone, Map<String, String> variables);

    /**
     * 指定模板 ID 发送短信。
     *
     * @param phone      手机号
     * @param templateId 模板 ID
     * @param variables  模板变量键值对
     * @return 是否发送成功
     */
    boolean sendByTemplate(String phone, String templateId, Map<String, String> variables);

    /**
     * 使用指定厂商配置（configId）发送短信。
     *
     * @param configId   sms4j 中配置的厂商标识
     * @param phone      手机号
     * @param templateId 模板 ID
     * @param variables  模板变量键值对
     * @return 是否发送成功
     */
    boolean sendByConfig(String configId, String phone, String templateId, Map<String, String> variables);

    /**
     * 群发短信。
     *
     * @param phones    手机号列表
     * @param variables 模板变量键值对
     * @return 是否发送成功
     */
    boolean sendBatch(List<String> phones, Map<String, String> variables);
}
