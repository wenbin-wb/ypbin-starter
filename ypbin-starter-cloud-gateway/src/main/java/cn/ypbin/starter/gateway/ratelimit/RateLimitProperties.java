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
package cn.ypbin.starter.gateway.ratelimit;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

/**
 * 属性驱动限流配置（看板反哺：ypbin-iot 开放 API 限流/配额的通用部分）。
 *
 * <p>工作模式：上游鉴权过滤器把"维度键 + 配额"放进 exchange attributes（见各 key 默认名），
 * 本过滤器只做固定窗口计数与 429 判定，不查库、不调业务。Redis 异常 fail-open（仅记日志），
 * 命中情况建议由调用方另配计数类指标观测（如 `*_rate_limited`）。</p>
 *
 * <p>本模块不用 Lombok（与 {@code GatewayProperties} 同口径）：显式 accessor。</p>
 *
 * @author wenbin
 * @since 2026-10-02
 */
@ConfigurationProperties(prefix = "ypbin.gateway.rate-limit")
public class RateLimitProperties {

    /** 是否启用（默认关闭：新过滤器不得在业务方不知情时激活）。 */
    private boolean enabled;

    /** 生效路径前缀（任一命中即限流；为空 = 全路径，危险，须显式配置）。 */
    private List<String> pathPrefixes = new ArrayList<>();

    /** 维度键的 attribute 名（如 API Key 场景放 accessKeyId）。 */
    private String keyAttribute = "rate-limit.key";

    /** QPS 配额的 attribute 名（缺席用默认）。 */
    private String qpsAttribute = "rate-limit.qps";

    /** 日配额的 attribute 名（缺席用默认）。 */
    private String quotaAttribute = "rate-limit.quota";

    /** QPS 计数 Redis key 前缀。 */
    private String qpsKeyPrefix = "ypbin:ratelimit:qps:";

    /** 日配额计数 Redis key 前缀。 */
    private String quotaKeyPrefix = "ypbin:ratelimit:quota:";

    /** 限流窗口秒数。 */
    private int windowSeconds = 1;

    /** 默认 QPS 配额。 */
    private int defaultQps = 10;

    /** 默认日配额（0 = 不限）。 */
    private int defaultQuota = 100000;

    /** 过滤器顺序（默认与既有鉴权链衔接：清洗 < 签发 < 鉴权 < 限流）。 */
    private int order = Ordered.HIGHEST_PRECEDENCE + 4;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getPathPrefixes() {
        return pathPrefixes;
    }

    public void setPathPrefixes(List<String> pathPrefixes) {
        this.pathPrefixes = pathPrefixes;
    }

    public String getKeyAttribute() {
        return keyAttribute;
    }

    public void setKeyAttribute(String keyAttribute) {
        this.keyAttribute = keyAttribute;
    }

    public String getQpsAttribute() {
        return qpsAttribute;
    }

    public void setQpsAttribute(String qpsAttribute) {
        this.qpsAttribute = qpsAttribute;
    }

    public String getQuotaAttribute() {
        return quotaAttribute;
    }

    public void setQuotaAttribute(String quotaAttribute) {
        this.quotaAttribute = quotaAttribute;
    }

    public String getQpsKeyPrefix() {
        return qpsKeyPrefix;
    }

    public void setQpsKeyPrefix(String qpsKeyPrefix) {
        this.qpsKeyPrefix = qpsKeyPrefix;
    }

    public String getQuotaKeyPrefix() {
        return quotaKeyPrefix;
    }

    public void setQuotaKeyPrefix(String quotaKeyPrefix) {
        this.quotaKeyPrefix = quotaKeyPrefix;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getDefaultQps() {
        return defaultQps;
    }

    public void setDefaultQps(int defaultQps) {
        this.defaultQps = defaultQps;
    }

    public int getDefaultQuota() {
        return defaultQuota;
    }

    public void setDefaultQuota(int defaultQuota) {
        this.defaultQuota = defaultQuota;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
