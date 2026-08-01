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

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 邮件（SMTP）配置。
 *
 * <p>描述发送邮件所需的 SMTP 参数。starter 只定义运行时模型与动态构建逻辑，配置来源由
 * {@link MailConfigProvider} 决定：可来自配置文件，也可由业务系统从数据库读取，支持后台可视化调整。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class MailConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** SMTP 服务器地址 */
    private String host;

    /** SMTP 端口 */
    private int port = 465;

    /** 账号 */
    private String username;

    /** 密码/授权码 */
    private String password;

    /** 发件人邮箱，为空时取 {@link #username} */
    private String from;

    /** 发件人显示名，可空 */
    private String fromName;

    /** 协议，默认 smtp */
    private String protocol = "smtp";

    /** 是否启用 SSL */
    private boolean sslEnabled = true;

    /** 是否启用 STARTTLS */
    private boolean starttlsEnabled = false;

    /** 编码 */
    private String defaultEncoding = "UTF-8";

    /** 连接/读取超时（毫秒） */
    private int timeout = 10000;

    /**
     * 配置是否可用（至少有 host 与 username）。
     *
     * @return 是否已配置
     */
    public boolean isConfigured() {
        return host != null && !host.isBlank() && username != null && !username.isBlank();
    }

    /**
     * 有效发件人：显式 from 优先，否则取 username。
     *
     * @return 发件人邮箱
     */
    public String resolveFrom() {
        return (from != null && !from.isBlank()) ? from : username;
    }

    /**
     * 配置指纹：用于判断配置是否变化以决定是否重建 sender。
     *
     * @return 指纹
     */
    public String fingerprint() {
        return String.join("|",
            String.valueOf(host), String.valueOf(port), String.valueOf(username),
            String.valueOf(password), String.valueOf(from), String.valueOf(fromName),
            String.valueOf(protocol), String.valueOf(sslEnabled), String.valueOf(starttlsEnabled),
            String.valueOf(defaultEncoding), String.valueOf(timeout));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MailConfig other)) {
            return false;
        }
        return fingerprint().equals(other.fingerprint());
    }

    @Override
    public int hashCode() {
        return Objects.hash(fingerprint());
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public boolean isStarttlsEnabled() {
        return starttlsEnabled;
    }

    public void setStarttlsEnabled(boolean starttlsEnabled) {
        this.starttlsEnabled = starttlsEnabled;
    }

    public String getDefaultEncoding() {
        return defaultEncoding;
    }

    public void setDefaultEncoding(String defaultEncoding) {
        this.defaultEncoding = defaultEncoding;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
