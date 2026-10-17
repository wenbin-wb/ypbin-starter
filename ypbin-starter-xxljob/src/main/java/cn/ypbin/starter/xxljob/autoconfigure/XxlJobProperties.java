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
package cn.ypbin.starter.xxljob.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * XXL-JOB 执行器配置。
 *
 * <p>前缀 {@code ypbin.xxl-job}，与 xxl-job-admin 控制台的执行器配置对应：
 * 服务启动后向调度中心（admin）注册本执行器，业务方法标注 {@code @XxlJob("handlerName")}
 * 即成为可被 admin 调度的任务。</p>
 *
 * <p>默认不启用（{@code enabled=false}），业务侧显式开启并配置 admin 地址后生效，
 * 避免误把所有引入本模块的服务都变成 xxl 执行器。</p>
 *
 * <p><strong>版本约束（xxl-job 3.5.0 起）</strong>：AccessToken 改为执行器（AppName）维度且不可为空，
 * 通讯协议亦有变化（新增 AppName 请求头、触发路径 {@code /run} 改为 {@code /trigger}），
 * 调度中心 admin 与执行器<strong>必须同版本成对升级</strong>，否则注册/调度在运行期失败。</p>
 *
 * @author wenbin
 * @since 2026-09-05
 */
@ConfigurationProperties(prefix = XxlJobProperties.PREFIX)
// 字段由 Spring Boot 在对象构造后绑定（@ConfigurationProperties），构造器结束时必然为 null；
// NullAway 的「字段未初始化」在此属框架装配语义，故按类抑制并在此说明原因
@SuppressWarnings("NullAway.Init")
public class XxlJobProperties {

    /** 配置前缀。 */
    public static final String PREFIX = "ypbin.xxl-job";

    /** 是否启用 XXL-JOB 执行器 */
    private boolean enabled;

    /** 调度中心地址（多个逗号分隔），如 http://localhost:8080/xxl-job-admin */
    private String adminAddresses;

    /**
     * 执行器通讯 Token。
     *
     * <p>3.5.0 起 AccessToken 由全局维度调整为执行器（AppName）维度且不可为空：
     * 必须与调度中心 admin 中该 AppName 对应的 {@code xxl_job_group.access_token} 完全一致，
     * 否则注册与回调都会被 admin 拒绝。admin 与执行器必须一并升级到 3.5.0。</p>
     */
    private String accessToken = "";

    /** 执行器名称（AppName），admin 端按此注册与路由 */
    private String appname;

    /** 执行器注册地址（为空时自动注册本机 IP） */
    private String address;

    /** 执行器 IP（为空自动获取） */
    private String ip;

    /** 执行器端口（执行器与 admin 通讯用，默认 9999） */
    private int port = 9999;

    /**
     * 执行器日志保存路径。
     *
     * <p>留空时沿用 xxl-job-core 默认值 {@code /data/applogs/xxl-job/jobhandler}；
     * 执行器启动时要求该值非空（空串会直接抛错），故本壳仅在配置了非空值时才回填给执行器。</p>
     */
    private String logPath;

    /** 执行器日志保存天数（默认 30） */
    private int logRetentionDays = 30;

    /**
     * 是否启用 GLUE（非 BEAN）模式任务（3.5.0 新增，执行器维度；默认 true）。
     *
     * <p>关闭后本执行器只执行 {@code @XxlJob} 注册的 BEAN 任务，不再加载/执行 GLUE 模式任务。</p>
     */
    private boolean glueEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAdminAddresses() {
        return adminAddresses;
    }

    public void setAdminAddresses(String adminAddresses) {
        this.adminAddresses = adminAddresses;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAppname() {
        return appname;
    }

    public void setAppname(String appname) {
        this.appname = appname;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    public int getLogRetentionDays() {
        return logRetentionDays;
    }

    public void setLogRetentionDays(int logRetentionDays) {
        this.logRetentionDays = logRetentionDays;
    }

    public boolean isGlueEnabled() {
        return glueEnabled;
    }

    public void setGlueEnabled(boolean glueEnabled) {
        this.glueEnabled = glueEnabled;
    }
}
