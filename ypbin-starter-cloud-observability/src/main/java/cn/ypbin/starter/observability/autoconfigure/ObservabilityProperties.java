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
package cn.ypbin.starter.observability.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 可观测性配置项。
 *
 * @author wenbin
 * @since 2026-07-31
 */
@ConfigurationProperties(prefix = "ypbin.observability")
public class ObservabilityProperties {

    /** 是否启用可观测性（X-Request-Id 与 MDC 关联），默认开启 */
    private boolean enabled = true;

    /** 请求 ID 请求头名称，与网关 RequestId 保持一致 */
    private String requestIdHeader = "X-Request-Id";

    /** 写入 MDC 的键名，日志 pattern 可引用 */
    private String mdcKey = "requestId";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRequestIdHeader() {
        return requestIdHeader;
    }

    public void setRequestIdHeader(String requestIdHeader) {
        this.requestIdHeader = requestIdHeader;
    }

    public String getMdcKey() {
        return mdcKey;
    }

    public void setMdcKey(String mdcKey) {
        this.mdcKey = mdcKey;
    }
}
