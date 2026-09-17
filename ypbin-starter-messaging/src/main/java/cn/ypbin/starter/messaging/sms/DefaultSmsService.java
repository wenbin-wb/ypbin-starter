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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 sms4j 的短信发送服务默认实现。
 *
 * <p>委托 {@link SmsFactory} 获取短信实例发送。厂商/密钥/模板等配置由 sms4j 管理（配置文件或
 * 业务方实现 sms4j 的 SmsReadConfig 动态读取）。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class DefaultSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSmsService.class);

    @Override
    public boolean isConfigured() {
        try {
            return SmsFactory.getSmsBlend() != null;
        } catch (Exception e) {
            // 配置读取本身异常时按「未配置」返回是接口契约（调用方据此提示未配置），但必须留痕：
            // 否则真实故障（如 sms4j 配置损坏）会被误读成「没配短信」，无从定位
            log.warn("[ypbin-starter] 读取短信配置失败，本次按「未配置」处理，短信不会发送", e);
            return false;
        }
    }

    @Override
    public boolean send(String phone, String message) {
        SmsResponse response = requireBlend().sendMessage(phone, message);
        return isOk(response);
    }

    @Override
    public boolean send(String phone, Map<String, String> variables) {
        SmsResponse response = requireBlend().sendMessage(phone, toLinked(variables));
        return isOk(response);
    }

    @Override
    public boolean sendByTemplate(String phone, String templateId, Map<String, String> variables) {
        SmsResponse response = requireBlend().sendMessage(phone, templateId, toLinked(variables));
        return isOk(response);
    }

    @Override
    public boolean sendByConfig(String configId, String phone, String templateId, Map<String, String> variables) {
        SmsBlend blend = SmsFactory.getSmsBlend(configId);
        if (blend == null) {
            throw new IllegalStateException("短信厂商配置不存在：" + configId);
        }
        SmsResponse response = blend.sendMessage(phone, templateId, toLinked(variables));
        return isOk(response);
    }

    @Override
    public boolean sendBatch(List<String> phones, Map<String, String> variables) {
        SmsResponse response = requireBlend().massTexting(phones, toLinked(variables).toString());
        return isOk(response);
    }

    private SmsBlend requireBlend() {
        SmsBlend blend = SmsFactory.getSmsBlend();
        if (blend == null) {
            throw new IllegalStateException("短信未配置：请配置 sms4j 厂商（sms.blends.*）或提供 SmsReadConfig");
        }
        return blend;
    }

    private LinkedHashMap<String, String> toLinked(Map<String, String> variables) {
        return (variables == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(variables);
    }

    private boolean isOk(SmsResponse response) {
        return response != null && response.isSuccess();
    }
}
