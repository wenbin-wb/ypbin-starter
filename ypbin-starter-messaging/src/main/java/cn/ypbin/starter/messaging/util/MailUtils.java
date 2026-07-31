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
import cn.ypbin.starter.messaging.mail.MailService;
import java.io.File;

/**
 * 邮件静态工具。
 *
 * <p>面向非 Spring 托管场景（异步任务、工具方法、静态上下文等）提供邮件发送，内部委托容器中的
 * {@link MailService} 单例。首次调用时经 {@link SpringUtils} 懒获取并缓存该 Bean 引用。</p>
 *
 * <p>Spring 托管组件仍应优先直接注入 {@link MailService}。发件人取配置的 {@code spring.mail.username}。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public final class MailUtils {

    private static volatile MailService service;

    private MailUtils() {
    }

    /**
     * 懒获取容器中的 {@link MailService} Bean（双重检查，线程安全）。
     *
     * @return 邮件服务实例
     */
    private static MailService service() {
        if (service == null) {
            synchronized (MailUtils.class) {
                if (service == null) {
                    service = SpringUtils.getBean(MailService.class);
                }
            }
        }
        return service;
    }

    /**
     * 发送纯文本邮件。
     *
     * @param to      收件人
     * @param subject 主题
     * @param content 正文
     */
    public static void sendText(String to, String subject, String content) {
        service().sendText(to, subject, content);
    }

    /**
     * 发送 HTML 邮件。
     *
     * @param to      收件人
     * @param subject 主题
     * @param html    HTML 正文
     */
    public static void sendHtml(String to, String subject, String html) {
        service().sendHtml(to, subject, html);
    }

    /**
     * 发送带附件的邮件。
     *
     * @param to          收件人
     * @param subject     主题
     * @param content     正文
     * @param html        正文是否为 HTML
     * @param attachments 附件文件
     */
    public static void sendWithAttachments(String to, String subject, String content, boolean html,
        File... attachments) {
        service().sendWithAttachments(to, subject, content, html, attachments);
    }
}
