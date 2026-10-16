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
package cn.ypbin.starter.core.diagnostic;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Starter 自省与诊断配置项（{@code ypbin.core.diagnostics.*}）。
 *
 * @author wenbin
 * @since 2026-10-17
 */
@ConfigurationProperties(prefix = StarterDiagnosticsProperties.PREFIX)
public class StarterDiagnosticsProperties {

    /** 配置前缀（单一来源） */
    public static final String PREFIX = "ypbin.core.diagnostics";

    /**
     * 应用就绪时是否打印单行「已激活 starter 模块」自省清单。
     *
     * <p>默认开启：只输出一行 INFO，能让接入方在启动日志里直接确认"到底装上了哪些 starter"，
     * 是排查"模块引了但没生效"这类问题的第一手线索；需要安静启动的宿主可显式置为 {@code false}。</p>
     */
    private boolean startupSummaryEnabled = true;

    public boolean isStartupSummaryEnabled() {
        return startupSummaryEnabled;
    }

    public void setStartupSummaryEnabled(boolean startupSummaryEnabled) {
        this.startupSummaryEnabled = startupSummaryEnabled;
    }
}
