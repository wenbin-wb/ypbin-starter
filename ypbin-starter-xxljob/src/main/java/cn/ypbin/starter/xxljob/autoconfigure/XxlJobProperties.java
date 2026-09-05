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
 * @author wenbin
 * @since 2026-09-05
 */
@ConfigurationProperties(prefix = "ypbin.xxl-job")
public class XxlJobProperties {

    /** 是否启用 XXL-JOB 执行器 */
    private boolean enabled;

    /** 调度中心地址（多个逗号分隔），如 http://localhost:8080/xxl-job-admin */
    private String adminAddresses;

    /** 执行器通讯 Token（与 admin 端保持一致，为空则不做校验） */
    private String accessToken = "";

    /** 执行器名称（AppName），admin 端按此注册与路由 */
    private String appname;

    /** 执行器注册地址（为空时自动注册本机 IP） */
    private String address;

    /** 执行器 IP（为空自动获取） */
    private String ip;

    /** 执行器端口（执行器与 admin 通讯用，默认 9999） */
    private int port = 9999;

    /** 执行器日志保存路径（为空使用默认临时目录） */
    private String logPath;

    /** 执行器日志保存天数（默认 30） */
    private int logRetentionDays = 30;

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
}
