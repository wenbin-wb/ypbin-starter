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
package cn.ypbin.starter.log.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 全量访问日志配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = AccessLogProperties.PREFIX)
public class AccessLogProperties {

    public static final String PREFIX = "ypbin.log.access";

    /** 是否启用全量访问日志切面，默认关闭（与 @Log 注解版互补，按需开启） */
    private boolean enabled = false;

    /** 排除路径（静态资源、健康检查等） */
    private List<String> excludePathPatterns = new ArrayList<>();

    /** 敏感请求头关键字（头名小写包含即掩码值），默认掩码授权/会话相关头 */
    private List<String> maskHeaders = new ArrayList<>(List.of("authorization", "cookie", "token"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getExcludePathPatterns() {
        return excludePathPatterns;
    }

    public void setExcludePathPatterns(List<String> excludePathPatterns) {
        this.excludePathPatterns = excludePathPatterns;
    }

    public List<String> getMaskHeaders() {
        return maskHeaders;
    }

    public void setMaskHeaders(List<String> maskHeaders) {
        this.maskHeaders = maskHeaders;
    }
}
