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
package cn.ypbin.starter.web.autoconfigure;

import cn.ypbin.starter.web.request.RepeatableReadRequestWrapper;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 可重复读请求配置项。
 *
 * @author wenbin
 * @since 2026-09-09
 */
@ConfigurationProperties(prefix = RepeatableReadProperties.PREFIX)
public class RepeatableReadProperties {

    public static final String PREFIX = "ypbin.web.repeatable-read";

    /** 是否启用可重复读请求过滤器，默认关闭（签名等需要重复读 body 的能力依赖它，需显式开启） */
    private boolean enabled = false;

    /** 单请求可缓存请求体的最大字节数，默认 10MB；超限请求体拒绝缓存并中止读取（防止超大请求体占满内存） */
    private long maxBodyBytes = RepeatableReadRequestWrapper.DEFAULT_MAX_BODY_BYTES;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getMaxBodyBytes() {
        return maxBodyBytes;
    }

    public void setMaxBodyBytes(long maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }
}
