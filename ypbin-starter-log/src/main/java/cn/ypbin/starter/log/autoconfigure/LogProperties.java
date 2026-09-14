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

import cn.ypbin.starter.log.enums.Include;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志模块配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = LogProperties.PREFIX)
// 字段由 Spring Boot 在对象构造后绑定（@ConfigurationProperties），构造器结束时必然为 null；
// NullAway 的「字段未初始化」在此属框架装配语义，故按类抑制并在此说明原因
@SuppressWarnings("NullAway.Init")
public class LogProperties {

    public static final String PREFIX = "ypbin.log";

    /** 是否启用操作日志，默认开启 */
    private boolean enabled = true;

    /** 全局默认采集项，为空时使用 {@link Include#defaultIncludes()} */
    private Set<Include> includes;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<Include> getIncludes() {
        return includes;
    }

    public void setIncludes(Set<Include> includes) {
        this.includes = includes;
    }
}
