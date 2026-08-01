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
package cn.ypbin.starter.job.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 定时任务配置项。
 *
 * @author wenbin
 * @since 2026-08-01
 */
@ConfigurationProperties(prefix = "ypbin.job")
public class JobProperties {

    /** 是否启用定时任务调度，默认开启 */
    private boolean enabled = true;

    /** 调度线程池大小 */
    private int poolSize = 4;

    /** 线程名前缀 */
    private String threadNamePrefix = "ypbin-job-";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }
}
