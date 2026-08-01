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
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * 邮件发送服务。
 *
 * <p>SMTP 配置来自 {@link MailConfigProvider}（可配置文件、也可业务方从数据库读取），支持后台动态调整：
 * 内部按配置指纹缓存 {@link JavaMailSender}，配置变化时自动重建，无需重启。提供纯文本、HTML、带附件与
 * 测试发送四种方式，发件人默认取当前配置的 from/username。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class MailService {

    private final MailConfigProvider configProvider;

    /** 缓存的 sender 与其对应配置指纹（配置变化即重建） */
    private volatile JavaMailSenderImpl cachedSender;
    private volatile String cachedFingerprint;

    public MailService(MailConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    /**
     * 当前邮件配置是否可用（至少配置了 host 与 username）。
     *
     * @return 是否可用
     */
    public boolean isConfigured() {
        return configProvider.getConfig().isConfigured();
    }

    /**
     * 发送纯文本邮件。
     *
     * @param to      收件人
     * @param subject 主题
     * @param content 正文
     */
    public void sendText(String to, String subject, String content) {
        MailConfig config = requireConfig();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(config.resolveFrom());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        sender(config).send(message);
    }

    /**
     * 发送 HTML 邮件。
     *
     * @param to      收件人
     * @param subject 主题
     * @param html    HTML 正文
     */
    public void sendHtml(String to, String subject, String html) {
        MailConfig config = requireConfig();
        try {
            JavaMailSender sender = sender(config);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, config.getDefaultEncoding());
            applyFrom(helper, config);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
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
        MailConfig config = requireConfig();
        try {
            JavaMailSender sender = sender(config);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, config.getDefaultEncoding());
            applyFrom(helper, config);
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
            sender.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("附件邮件发送失败：" + subject, e);
        }
    }

    /**
     * 发送测试邮件，用于后台"保存 SMTP 配置前先测一封"。发送失败抛出异常，异常信息含失败原因。
     *
     * @param to 收件人
     */
    public void sendTest(String to) {
        sendText(to, "邮件配置测试", "这是一封测试邮件，收到说明邮件配置正确。");
    }

    private MailConfig requireConfig() {
        MailConfig config = configProvider.getConfig();
        if (config == null || !config.isConfigured()) {
            throw new IllegalStateException("邮件未配置或配置不完整（缺少 host/username）");
        }
        return config;
    }

    private void applyFrom(MimeMessageHelper helper, MailConfig config) throws Exception {
        String from = config.resolveFrom();
        if (config.getFromName() != null && !config.getFromName().isBlank()) {
            try {
                helper.setFrom(from, config.getFromName());
                return;
            } catch (UnsupportedEncodingException ignored) {
                // 显示名编码失败时退化为纯地址
            }
        }
        helper.setFrom(from);
    }

    /**
     * 按当前配置获取 sender：指纹一致复用缓存，变化则重建。
     */
    private JavaMailSender sender(MailConfig config) {
        String fingerprint = config.fingerprint();
        JavaMailSenderImpl sender = cachedSender;
        if (sender != null && fingerprint.equals(cachedFingerprint)) {
            return sender;
        }
        synchronized (this) {
            if (cachedSender != null && fingerprint.equals(cachedFingerprint)) {
                return cachedSender;
            }
            JavaMailSenderImpl built = build(config);
            cachedSender = built;
            cachedFingerprint = fingerprint;
            return built;
        }
    }

    private JavaMailSenderImpl build(MailConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getHost());
        sender.setPort(config.getPort());
        sender.setUsername(config.getUsername());
        sender.setPassword(config.getPassword());
        sender.setProtocol(config.getProtocol());
        sender.setDefaultEncoding(config.getDefaultEncoding());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        String timeout = String.valueOf(config.getTimeout());
        props.put("mail.smtp.connectiontimeout", timeout);
        props.put("mail.smtp.timeout", timeout);
        props.put("mail.smtp.writetimeout", timeout);
        if (config.isSslEnabled()) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        if (config.isStarttlsEnabled()) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        return sender;
    }
}
