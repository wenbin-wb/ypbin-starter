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
package cn.ypbin.starter.tools.limiter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 限流配置项。
 *
 * <p>仅承载 {@code byIp} 限流的 IP 取值策略；能力开关（enabled/distributed）仍由
 * 自动配置上的条件注解控制，不在本类重复声明，避免默认值漂移。</p>
 *
 * @author wenbin
 * @since 2026-09-09
 */
@ConfigurationProperties(prefix = RateLimitProperties.PREFIX)
public class RateLimitProperties {

    public static final String PREFIX = "ypbin.tools.rate-limit";

    /** 是否信任转发头（X-Forwarded-For/X-Real-IP 等）解析 {@code byIp} 限流的客户端 IP，默认 false：只取真实对端地址 */
    private boolean trustForwarded = false;

    public boolean isTrustForwarded() {
        return trustForwarded;
    }

    public void setTrustForwarded(boolean trustForwarded) {
        this.trustForwarded = trustForwarded;
    }
}
