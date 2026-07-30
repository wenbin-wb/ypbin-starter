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
package cn.ypbin.starter.messaging.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQTT 配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = "ypbin.mqtt")
public class MqttProperties {

    /** 是否启用 MQTT，默认关闭（需显式开启） */
    private boolean enabled = false;

    /** Broker 地址，如 tcp://127.0.0.1:1883 */
    private String url;

    /** 客户端 ID（为空则自动生成） */
    private String clientId;

    /** 用户名 */
    private String username;

    /** 密码 */
    private String password;

    /** 连接超时（秒） */
    private int connectionTimeout = 10;

    /** 心跳间隔（秒） */
    private int keepAliveInterval = 60;

    /** 是否清除会话（false 时 broker 保留会话与离线消息，配合 QoS≥1 实现可靠投递） */
    private boolean cleanSession = true;

    /** 默认发布 QoS（0/1/2） */
    private int defaultQos = 1;

    /** 是否自动重连 */
    private boolean automaticReconnect = true;

    /** 自动重连的最大间隔（毫秒），指数退避的上限 */
    private int maxReconnectDelay = 30_000;

    /** 最大未确认消息数（QoS1/2 高吞吐时提高，0 表示不限制在途窗口） */
    private int maxInflight = 10;

    /**
     * 消息持久化目录。配置后用文件持久化（进程重启后 QoS1/2 未确认消息不丢），
     * 为空则用内存持久化（重启丢失）。
     */
    private String persistenceDir;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
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

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getKeepAliveInterval() {
        return keepAliveInterval;
    }

    public void setKeepAliveInterval(int keepAliveInterval) {
        this.keepAliveInterval = keepAliveInterval;
    }

    public boolean isCleanSession() {
        return cleanSession;
    }

    public void setCleanSession(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }

    public int getDefaultQos() {
        return defaultQos;
    }

    public void setDefaultQos(int defaultQos) {
        this.defaultQos = defaultQos;
    }

    public boolean isAutomaticReconnect() {
        return automaticReconnect;
    }

    public void setAutomaticReconnect(boolean automaticReconnect) {
        this.automaticReconnect = automaticReconnect;
    }

    public int getMaxReconnectDelay() {
        return maxReconnectDelay;
    }

    public void setMaxReconnectDelay(int maxReconnectDelay) {
        this.maxReconnectDelay = maxReconnectDelay;
    }

    public int getMaxInflight() {
        return maxInflight;
    }

    public void setMaxInflight(int maxInflight) {
        this.maxInflight = maxInflight;
    }

    public String getPersistenceDir() {
        return persistenceDir;
    }

    public void setPersistenceDir(String persistenceDir) {
        this.persistenceDir = persistenceDir;
    }
}
