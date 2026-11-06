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
package cn.ypbin.starter.tools.autoconfigure;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * tools 模块级配置。
 *
 * <p>只承载需要跨能力共享的字段（当前是客户端 IP 解析）；限流开关等仍由各自的
 * {@code ypbin.tools.rate-limit.*} 承载，不在此重复声明。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
@ConfigurationProperties(prefix = ToolsProperties.PREFIX)
public class ToolsProperties {

    public static final String PREFIX = "ypbin.tools";

    /**
     * 可信代理（精确 IP 或 CIDR）。
     *
     * <p>非空时 {@code RequestUtils.getClientIp(true)} 改为「从右往左跳过可信代理、取第一个不可信地址」
     * 的多级解析，与 core/sign/log 的 {@code ClientIpResolver} 同口径；为空时保持历史上的
     * 「只取最右一跳」语义（兼容多级代理未配置前的行为）。</p>
     */
    private List<String> trustedProxies = List.of();

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    }
}
