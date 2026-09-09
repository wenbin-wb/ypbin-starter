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

    /** 可缓存请求体上限的最大允许值（64MB），防止误配过大使探测溢出或内存无界 */
    public static final long MAX_BODY_BYTES_LIMIT = 64L * 1024 * 1024;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getMaxBodyBytes() {
        return maxBodyBytes;
    }

    /**
     * 装配期校验缓存上限：非法配置在启动即失败（fail-fast），避免运行期首次请求才暴露，
     * 或上限逼近 {@code Long.MAX_VALUE} 时探测长度溢出导致请求体被静默读空。
     *
     * @param maxBodyBytes 允许缓存的最大字节数（(0, {@link #MAX_BODY_BYTES_LIMIT}]）
     */
    public void setMaxBodyBytes(long maxBodyBytes) {
        if (maxBodyBytes <= 0 || maxBodyBytes > MAX_BODY_BYTES_LIMIT) {
            throw new IllegalArgumentException("ypbin.web.repeatable-read.max-body-bytes 必须在 (0, "
                + MAX_BODY_BYTES_LIMIT + "] 字节范围内，当前为 " + maxBodyBytes);
        }
        this.maxBodyBytes = maxBodyBytes;
    }
}
