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
package cn.ypbin.starter.messaging.util;

import cn.ypbin.starter.core.util.SpringUtils;
import cn.ypbin.starter.messaging.sms.SmsService;
import java.util.List;
import java.util.Map;

/**
 * 短信静态工具。
 *
 * <p>面向非 Spring 托管场景（异步任务、工具方法、静态上下文等）提供短信发送，内部委托容器中的
 * {@link SmsService} 单例。首次调用时经 {@link SpringUtils} 懒获取并缓存该 Bean 引用。</p>
 *
 * <p>Spring 托管组件仍应优先直接注入 {@link SmsService}。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public final class SmsUtils {

    private static volatile SmsService service;

    private SmsUtils() {
    }

    private static SmsService service() {
        if (service == null) {
            synchronized (SmsUtils.class) {
                if (service == null) {
                    service = SpringUtils.getBean(SmsService.class);
                }
            }
        }
        return service;
    }

    /**
     * 按单变量发送短信。
     *
     * @param phone   手机号
     * @param message 模板变量值
     * @return 是否成功
     */
    public static boolean send(String phone, String message) {
        return service().send(phone, message);
    }

    /**
     * 按模板变量映射发送短信。
     *
     * @param phone     手机号
     * @param variables 模板变量
     * @return 是否成功
     */
    public static boolean send(String phone, Map<String, String> variables) {
        return service().send(phone, variables);
    }

    /**
     * 指定模板 ID 发送短信。
     *
     * @param phone      手机号
     * @param templateId 模板 ID
     * @param variables  模板变量
     * @return 是否成功
     */
    public static boolean sendByTemplate(String phone, String templateId, Map<String, String> variables) {
        return service().sendByTemplate(phone, templateId, variables);
    }

    /**
     * 群发短信。
     *
     * @param phones    手机号列表
     * @param variables 模板变量
     * @return 是否成功
     */
    public static boolean sendBatch(List<String> phones, Map<String, String> variables) {
        return service().sendBatch(phones, variables);
    }
}
