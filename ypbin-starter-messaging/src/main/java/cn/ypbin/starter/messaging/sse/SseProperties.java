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
package cn.ypbin.starter.messaging.sse;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSE 配置项。
 *
 * @author wenbin
 * @since 2026-07-31
 */
@ConfigurationProperties(prefix = "ypbin.sse")
public class SseProperties {

    /** 是否启用 SSE，默认关闭（需显式开启） */
    private boolean enabled = false;

    /** 是否注册内置订阅端点 {@code /ypbin/sse/subscribe} */
    private boolean registerEndpoint = true;

    /** 内置订阅端点路径 */
    private String path = "/ypbin/sse/subscribe";

    /** 连接超时（毫秒），0 表示不超时（不建议）；到期后客户端自动重连 */
    private long timeout = 300_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRegisterEndpoint() {
        return registerEndpoint;
    }

    public void setRegisterEndpoint(boolean registerEndpoint) {
        this.registerEndpoint = registerEndpoint;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
}
