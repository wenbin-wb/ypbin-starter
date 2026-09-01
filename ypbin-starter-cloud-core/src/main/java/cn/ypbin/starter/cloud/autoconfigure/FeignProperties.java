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
package cn.ypbin.starter.cloud.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feign 增强配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = FeignProperties.PREFIX)
public class FeignProperties {

    public static final String PREFIX = "ypbin.cloud.feign";

    /** 是否启用 Feign 增强（请求头透传等），默认开启 */
    private boolean enabled = true;

    /** 是否启用统一错误解码，默认开启 */
    private boolean errorDecoderEnabled = true;

    /** 是否默认开启 OpenFeign circuitbreaker，默认开启 */
    private boolean circuitbreakerEnabled = true;

    /**
     * 需要透传到下游服务的请求头名单（大小写不敏感）。
     * 默认透传认证、链路追踪与身份头（身份头由可信网关统一清洗/签发，二次 RPC 时保证
     * 下游仍能识别调用者身份；若需自定义可在配置中覆盖）。
     */
    private List<String> propagateHeaders = new ArrayList<>(List.of(
        "Authorization", "X-Request-Id", "X-Trace-Id",
        "X-User-Id", "X-User-Name", "X-Tenant-Id", "X-Dept-Id", "X-Roles"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isErrorDecoderEnabled() {
        return errorDecoderEnabled;
    }

    public void setErrorDecoderEnabled(boolean errorDecoderEnabled) {
        this.errorDecoderEnabled = errorDecoderEnabled;
    }

    public boolean isCircuitbreakerEnabled() {
        return circuitbreakerEnabled;
    }

    public void setCircuitbreakerEnabled(boolean circuitbreakerEnabled) {
        this.circuitbreakerEnabled = circuitbreakerEnabled;
    }

    public List<String> getPropagateHeaders() {
        return propagateHeaders;
    }

    public void setPropagateHeaders(List<String> propagateHeaders) {
        this.propagateHeaders = propagateHeaders;
    }
}
