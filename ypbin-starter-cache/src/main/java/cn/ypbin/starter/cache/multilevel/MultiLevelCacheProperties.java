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
package cn.ypbin.starter.cache.multilevel;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多级缓存配置项。
 *
 * @author wenbin
 * @since 2026-07-31
 */
@ConfigurationProperties(prefix = MultiLevelCacheProperties.PREFIX)
public class MultiLevelCacheProperties {

    public static final String PREFIX = "ypbin.cache.multi-level";

    /** 是否启用多级缓存（L1 本地 + L2 Redis），默认关闭 */
    private boolean enabled = false;

    /** L1 本地缓存最大条目数 */
    private long localMaxSize = 10_000L;

    /** L1 本地缓存写后过期秒数（应小于 L2 TTL，作为最终一致兜底） */
    private long localExpireSeconds = 300L;

    /** 是否开启跨实例失效广播（多副本部署需开启，单体可关） */
    private boolean invalidationBroadcast = true;

    /** 失效广播频道名 */
    private String invalidationChannel = "ypbin:cache:invalidation";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getLocalMaxSize() {
        return localMaxSize;
    }

    public void setLocalMaxSize(long localMaxSize) {
        this.localMaxSize = localMaxSize;
    }

    public long getLocalExpireSeconds() {
        return localExpireSeconds;
    }

    public void setLocalExpireSeconds(long localExpireSeconds) {
        this.localExpireSeconds = localExpireSeconds;
    }

    public boolean isInvalidationBroadcast() {
        return invalidationBroadcast;
    }

    public void setInvalidationBroadcast(boolean invalidationBroadcast) {
        this.invalidationBroadcast = invalidationBroadcast;
    }

    public String getInvalidationChannel() {
        return invalidationChannel;
    }

    public void setInvalidationChannel(String invalidationChannel) {
        this.invalidationChannel = invalidationChannel;
    }
}
