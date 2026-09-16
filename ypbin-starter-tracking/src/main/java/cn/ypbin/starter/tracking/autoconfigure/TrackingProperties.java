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
package cn.ypbin.starter.tracking.autoconfigure;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 埋点模块配置项。
 *
 * <p><strong>默认全部关闭</strong>：采集端点是一个匿名可写入口（新增攻击面），
 * 因此本模块不采用「引入即生效」的默认值，必须由宿主显式开启。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
@ConfigurationProperties(prefix = TrackingProperties.PREFIX)
public class TrackingProperties {

    public static final String PREFIX = "ypbin.tracking";

    /** 是否启用埋点能力（装配 TrackEventSink 等内核 Bean），默认关闭 */
    private boolean enabled = false;

    /**
     * 是否启用采集端点，默认关闭。
     *
     * <p>即使 {@link #enabled} 已开启，端点仍需本开关显式打开：微服务下多个服务共用本模块，
     * 端点默认开启会让 {@code /tracking/ingest} 散布到每一个服务上。</p>
     */
    private boolean ingestEnabled = false;

    /** 采集端点路径（网关前带服务短名，如 {@code /system/tracking/ingest}） */
    private String path = "/tracking/ingest";

    /** 有界队列容量，满即丢弃新事件并计数（严禁无界队列） */
    private int queueCapacity = 10000;

    /** 单批落库条数 */
    private int batchSize = 200;

    /** 定时刷新间隔（毫秒） */
    private long flushIntervalMs = 1000L;

    /** 落点写入失败后、重试前的退避毫秒数；重试一次仍失败即整批丢弃并计数（不无限重试） */
    private long sinkRetryBackoffMs = 100L;

    /** 单个请求允许携带的最大事件数，超出部分拒绝并计数 */
    private int maxEventsPerRequest = 50;

    /** 单个事件属性的最大字节数（UTF-8），超出即拒绝该事件 */
    private int maxPayloadBytes = 8192;

    /**
     * 单个请求体最大字节数，超出即整体拒绝。
     *
     * <p>作用不只是防大包：采集端点与业务接口<strong>共用同一个 Servlet 线程池</strong>，
     * 限制请求体大小是避免匿名流量挤占业务线程的手段之一。</p>
     */
    private int maxRequestBytes = 262144;

    /** 应用标识；请求体未提供 appId 时使用，仍为空则该维度不写 */
    private String appId = "";

    /** 服务端采样率（0 表示不采样即全部丢弃，1 表示全量保留） */
    private double sampleRate = 1.0D;

    /** 是否对客户端 IP 做截断脱敏（IPv4 保留 /24、IPv6 保留 /64），默认开启 */
    private boolean anonymizeIp = true;

    /** 允许的上报来源白名单；为空表示不校验来源（仅作降噪，不构成安全边界） */
    private List<String> allowedOrigins = List.of();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isIngestEnabled() {
        return ingestEnabled;
    }

    public void setIngestEnabled(boolean ingestEnabled) {
        this.ingestEnabled = ingestEnabled;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getFlushIntervalMs() {
        return flushIntervalMs;
    }

    public void setFlushIntervalMs(long flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }

    public long getSinkRetryBackoffMs() {
        return sinkRetryBackoffMs;
    }

    public void setSinkRetryBackoffMs(long sinkRetryBackoffMs) {
        this.sinkRetryBackoffMs = sinkRetryBackoffMs;
    }

    public int getMaxEventsPerRequest() {
        return maxEventsPerRequest;
    }

    public void setMaxEventsPerRequest(int maxEventsPerRequest) {
        this.maxEventsPerRequest = maxEventsPerRequest;
    }

    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    public void setMaxPayloadBytes(int maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
    }

    public int getMaxRequestBytes() {
        return maxRequestBytes;
    }

    public void setMaxRequestBytes(int maxRequestBytes) {
        this.maxRequestBytes = maxRequestBytes;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public double getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(double sampleRate) {
        this.sampleRate = sampleRate;
    }

    public boolean isAnonymizeIp() {
        return anonymizeIp;
    }

    public void setAnonymizeIp(boolean anonymizeIp) {
        this.anonymizeIp = anonymizeIp;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
