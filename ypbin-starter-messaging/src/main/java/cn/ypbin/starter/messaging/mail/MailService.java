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

import jakarta.mail.internet.MimeMessage;
import java.io.File;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * 邮件发送服务。
 *
 * <p>对 Spring {@link JavaMailSender} 的轻封装，提供纯文本、HTML、带附件三种常用发送方式。
 * 发件人默认取配置的 {@code spring.mail.username}，也可显式指定。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class MailService {

    private final JavaMailSender mailSender;
    private final String defaultFrom;

    public MailService(JavaMailSender mailSender, String defaultFrom) {
        this.mailSender = mailSender;
        this.defaultFrom = defaultFrom;
    }

    /**
     * 发送纯文本邮件。
     *
     * @param to      收件人
     * @param subject 主题
     * @param content 正文
     */
    public void sendText(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(defaultFrom);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
    }

    /**
     * 发送 HTML 邮件。
     *
     * @param to      收件人
     * @param subject 主题
     * @param html    HTML 正文
     */
    public void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(defaultFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("HTML 邮件发送失败：" + subject, e);
        }
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
    public void sendWithAttachments(String to, String subject, String content, boolean html,
        File... attachments) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(defaultFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, html);
            if (attachments != null) {
                for (File attachment : attachments) {
                    if (attachment != null) {
                        helper.addAttachment(attachment.getName(), new FileSystemResource(attachment));
                    }
                }
            }
            mailSender.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("附件邮件发送失败：" + subject, e);
        }
    }
}
