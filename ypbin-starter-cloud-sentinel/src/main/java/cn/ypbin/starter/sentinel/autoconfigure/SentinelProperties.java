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
package cn.ypbin.starter.sentinel.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sentinel 增强配置项。
 *
 * @author wenbin
 * @since 2026-07-31
 */
@ConfigurationProperties(prefix = "ypbin.cloud.sentinel")
public class SentinelProperties {

    /** 是否启用被限流/降级时的统一 R 响应，默认开启 */
    private boolean enabled = true;

    /** 被限流时的提示信息 */
    private String blockMessage = "请求过于频繁，请稍后重试";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBlockMessage() {
        return blockMessage;
    }

    public void setBlockMessage(String blockMessage) {
        this.blockMessage = blockMessage;
    }
}
