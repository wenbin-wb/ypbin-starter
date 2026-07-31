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
package cn.ypbin.starter.loadbalancer.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 版本灰度负载均衡配置项。
 *
 * @author wenbin
 * @since 2026-07-31
 */
@ConfigurationProperties(prefix = LoadBalancerProperties.PREFIX)
public class LoadBalancerProperties {

    public static final String PREFIX = "ypbin.cloud.loadbalancer";

    /** 是否启用版本灰度负载均衡，默认开启 */
    private boolean enabled = true;

    /** 当前服务实例版本；配置后可自动写入 Nacos metadata */
    private String version;

    /** 请求头中的灰度版本名，按顺序取第一个非空值 */
    private List<String> versionHeaders = new ArrayList<>(List.of("X-Version", "version"));

    /** 服务实例 metadata 中保存版本的 key */
    private String metadataKey = "version";

    /** 服务实例 metadata 中保存权重的 key */
    private String weightMetadataKey = "weight";

    /** metadata 未配置或配置非法时使用的默认权重 */
    private int defaultWeight = 1;

    /** 灰度版本无匹配实例时是否回退到正式实例 */
    private boolean fallbackToStable = true;

    /** 无版本请求是否优先选择未标记版本的正式实例 */
    private boolean preferStableWithoutVersion = true;

    /** 优先 IP 通配列表，例如 10.20.0.8*、10.20.0.* */
    private List<String> priorIpPatterns = new ArrayList<>();

    /** 是否把当前服务版本写入 Nacos discovery metadata */
    private boolean registerNacosMetadata = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<String> getVersionHeaders() {
        return versionHeaders;
    }

    public void setVersionHeaders(List<String> versionHeaders) {
        this.versionHeaders = versionHeaders;
    }

    public String getMetadataKey() {
        return metadataKey;
    }

    public void setMetadataKey(String metadataKey) {
        this.metadataKey = metadataKey;
    }

    public String getWeightMetadataKey() {
        return weightMetadataKey;
    }

    public void setWeightMetadataKey(String weightMetadataKey) {
        this.weightMetadataKey = weightMetadataKey;
    }

    public int getDefaultWeight() {
        return defaultWeight;
    }

    public void setDefaultWeight(int defaultWeight) {
        this.defaultWeight = defaultWeight;
    }

    public boolean isFallbackToStable() {
        return fallbackToStable;
    }

    public void setFallbackToStable(boolean fallbackToStable) {
        this.fallbackToStable = fallbackToStable;
    }

    public boolean isPreferStableWithoutVersion() {
        return preferStableWithoutVersion;
    }

    public void setPreferStableWithoutVersion(boolean preferStableWithoutVersion) {
        this.preferStableWithoutVersion = preferStableWithoutVersion;
    }

    public List<String> getPriorIpPatterns() {
        return priorIpPatterns;
    }

    public void setPriorIpPatterns(List<String> priorIpPatterns) {
        this.priorIpPatterns = priorIpPatterns;
    }

    public boolean isRegisterNacosMetadata() {
        return registerNacosMetadata;
    }

    public void setRegisterNacosMetadata(boolean registerNacosMetadata) {
        this.registerNacosMetadata = registerNacosMetadata;
    }
}
