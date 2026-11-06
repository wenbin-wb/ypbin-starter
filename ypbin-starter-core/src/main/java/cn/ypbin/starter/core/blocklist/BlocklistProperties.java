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
package cn.ypbin.starter.core.blocklist;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

/**
 * 访问黑名单配置。
 *
 * <p><b>默认关闭</b>：黑名单会改变既有请求的可达性，必须由宿主显式
 * {@code ypbin.security.blocklist.enabled=true} 开启，避免升级 starter 后静默拦截线上流量。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
@ConfigurationProperties(prefix = BlocklistProperties.PREFIX)
public class BlocklistProperties {

    /** 配置前缀。 */
    public static final String PREFIX = "ypbin.security.blocklist";

    /** 是否启用访问黑名单。 */
    private boolean enabled;

    /** 过滤器顺序：早于 XSS（HIGHEST_PRECEDENCE + 100），晚于可重复读包装。 */
    private int order = Ordered.HIGHEST_PRECEDENCE + 50;

    /** 放行路径（永不拦截），避免健康检查/错误页被误伤。 */
    private List<String> excludePaths = new ArrayList<>(List.of(
        "/actuator/health", "/actuator/health/**", "/error"));

    /**
     * 放行 IP（精确 IP 或 CIDR）。
     *
     * <p>默认空：回环地址不再无条件放行——同机反代时 {@code getRemoteAddr()} 恒为
     * {@code 127.0.0.1}，若默认放行会让黑名单静默全放行。需要放行健康检查等来源时由宿主显式配置，
     * 并同时配置 {@link #trustedProxies} 以采信转发头。</p>
     */
    private List<String> excludeIps = new ArrayList<>();

    /**
     * 可信代理（精确 IP 或 CIDR）；只有直连地址命中时才采信 {@code X-Forwarded-For} /
     * {@code X-Real-IP}，取值口径为「从右往左跳过可信代理，取第一个不可信地址」。
     */
    private List<String> trustedProxies = new ArrayList<>();

    /** 静态规则（也可由管理端下发；二者合并生效）。 */
    private List<BlocklistRule> rules = new ArrayList<>();

    /** Servlet 与网关两侧共用的规则本地缓存秒数；管理端下发后最迟在该时间内生效。 */
    private int rulesCacheSeconds = 5;

    /** 命中黑名单时的响应文案。 */
    private String responseMessage = "访问已被限制";

    /** 自动封禁配置。 */
    private AutoBan autoBan = new AutoBan();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    public List<String> getExcludeIps() {
        return excludeIps;
    }

    public void setExcludeIps(List<String> excludeIps) {
        this.excludeIps = excludeIps;
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    public List<BlocklistRule> getRules() {
        return rules;
    }

    public void setRules(List<BlocklistRule> rules) {
        this.rules = rules;
    }

    public int getRulesCacheSeconds() {
        return rulesCacheSeconds;
    }

    public void setRulesCacheSeconds(int rulesCacheSeconds) {
        this.rulesCacheSeconds = rulesCacheSeconds;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage;
    }

    public AutoBan getAutoBan() {
        return autoBan;
    }

    public void setAutoBan(AutoBan autoBan) {
        this.autoBan = autoBan;
    }

    /**
     * 自动封禁配置：在窗口内累计到阈值后，把客户端 IP 临时封禁。
     *
     * <p><b>默认只统计真 429</b>（限流）。本仓业务响应恒 HTTP 200，登录失败是
     * {@code 200 + R.code=401}，过滤器读状态码看不到，须由业务失败点调用
     * {@code BlocklistAutoBanRecorder#recordLoginFailure}（{@code PasswordAttemptLimiter} 已自动接入）。
     * 宿主若确实在入口返回真 401/403，可通过 {@code status-codes} 显式加入，并用
     * {@code path-prefixes} 限定路径，避免过期 token 误封。</p>
     */
    public static class AutoBan {

        /** 是否启用自动封禁。 */
        private boolean enabled = true;

        /** 窗口内达到该次数即封禁。 */
        private int threshold = 20;

        /** 计数窗口秒数。 */
        private int windowSeconds = 60;

        /** 封禁时长秒数。 */
        private int banSeconds = 600;

        /** 内存存储的违规计数条目上限；超过后先清理过期项，仍满则本次不计数。 */
        private int maxViolationEntries = 10_000;

        /**
         * 是否允许自动封禁非公网地址，默认 {@code false}。
         *
         * <p>未配置 {@code trusted-proxies} 时，同机反向代理会让所有请求的直连地址都变成代理 IP；
         * 若继续按该地址计数并封禁，会把整站打成 403。默认只封公网单点地址，内网/回环/CGNAT
         * 等地址跳过并 WARN 一次；纯内网部署确需按内网 IP 封禁时显式打开。</p>
         */
        private boolean allowNonPublicIp;

        /** 参与计数的响应状态码；默认仅真 429。 */
        private List<Integer> statusCodes = new ArrayList<>(List.of(429));

        /** 非 429 状态码只在这些路径前缀上计数（默认登录类路径）。 */
        private List<String> pathPrefixes = new ArrayList<>(List.of(
            "/auth/login", "/auth/sms/login", "/auth/social/callback"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getThreshold() {
            return threshold;
        }

        public void setThreshold(int threshold) {
            this.threshold = threshold;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public int getBanSeconds() {
            return banSeconds;
        }

        public void setBanSeconds(int banSeconds) {
            this.banSeconds = banSeconds;
        }

        public int getMaxViolationEntries() {
            return maxViolationEntries;
        }

        public void setMaxViolationEntries(int maxViolationEntries) {
            this.maxViolationEntries = maxViolationEntries;
        }

        public boolean isAllowNonPublicIp() {
            return allowNonPublicIp;
        }

        public void setAllowNonPublicIp(boolean allowNonPublicIp) {
            this.allowNonPublicIp = allowNonPublicIp;
        }

        public List<Integer> getStatusCodes() {
            return statusCodes;
        }

        public void setStatusCodes(List<Integer> statusCodes) {
            this.statusCodes = statusCodes;
        }

        public List<String> getPathPrefixes() {
            return pathPrefixes;
        }

        public void setPathPrefixes(List<String> pathPrefixes) {
            this.pathPrefixes = pathPrefixes;
        }
    }
}
