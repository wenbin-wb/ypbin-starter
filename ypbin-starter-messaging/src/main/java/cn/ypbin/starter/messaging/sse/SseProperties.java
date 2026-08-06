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
package cn.ypbin.starter.messaging.sse;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSE 配置项。
 *
 * @author wenbin
 * @since 2026-07-31
 */
@ConfigurationProperties(prefix = "ypbin.sse")
public class SseProperties {

    /** 是否启用 SSE，默认关闭（需显式开启） */
    private boolean enabled = false;

    /** 是否注册内置订阅端点 {@code /ypbin/sse/subscribe} */
    private boolean registerEndpoint = true;

    /** 内置订阅端点路径 */
    private String path = "/ypbin/sse/subscribe";

    /** 一次性订阅票据签发端点路径（Header 令牌鉴权场景：先换票再用 ticket 订阅） */
    private String ticketPath = "/ypbin/sse/ticket";

    /** 一次性订阅票据有效期（秒），换票后应尽快用于订阅 */
    private long ticketTtlSeconds = 30L;

    /**
     * 连接超时（毫秒），默认 0 不超时。
     *
     * <p>0 表示长连接不设总超时，由心跳（{@link #getHeartbeatIntervalSeconds()}）负责保活与死连接检测，
     * 适合通知中心等长连接场景（前端已有重连兜底）。配有限值则作为安全网，到点由容器回收连接
     * （回收时全局异常处理器已静默化，不产生 ERROR 噪音）。</p>
     */
    private long timeout = 0L;

    /**
     * 心跳间隔（秒），默认 30。定期向连接发送 {@code : ping} 注释帧，保活中间代理并尽早暴露死连接
     * （发送失败即回收）。0 表示关闭心跳。
     */
    private long heartbeatIntervalSeconds = 30L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRegisterEndpoint() {
        return registerEndpoint;
    }

    public void setRegisterEndpoint(boolean registerEndpoint) {
        this.registerEndpoint = registerEndpoint;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTicketPath() {
        return ticketPath;
    }

    public void setTicketPath(String ticketPath) {
        this.ticketPath = ticketPath;
    }

    public long getTicketTtlSeconds() {
        return ticketTtlSeconds;
    }

    public void setTicketTtlSeconds(long ticketTtlSeconds) {
        this.ticketTtlSeconds = ticketTtlSeconds;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public long getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    public void setHeartbeatIntervalSeconds(long heartbeatIntervalSeconds) {
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }
}
