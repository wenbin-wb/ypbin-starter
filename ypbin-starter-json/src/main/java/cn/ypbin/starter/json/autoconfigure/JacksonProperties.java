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
package cn.ypbin.starter.json.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JSON 模块配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = "ypbin.json")
public class JacksonProperties {

    /** 是否启用统一 Jackson 定制，默认开启 */
    private boolean enabled = true;

    /**
     * 是否将 Long / BigInteger / BigDecimal 序列化为字符串，规避前端 JS 大数精度丢失。
     * 默认开启。
     */
    private boolean writeBigNumberAsString = true;

    /** 日期时间格式 */
    private String dateTimeFormat = "yyyy-MM-dd HH:mm:ss";

    /** 日期格式 */
    private String dateFormat = "yyyy-MM-dd";

    /** 时间格式 */
    private String timeFormat = "HH:mm:ss";

    /** 引用翻译（@RefText）配置 */
    private RefText refText = new RefText();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isWriteBigNumberAsString() {
        return writeBigNumberAsString;
    }

    public void setWriteBigNumberAsString(boolean writeBigNumberAsString) {
        this.writeBigNumberAsString = writeBigNumberAsString;
    }

    public String getDateTimeFormat() {
        return dateTimeFormat;
    }

    public void setDateTimeFormat(String dateTimeFormat) {
        this.dateTimeFormat = dateTimeFormat;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public String getTimeFormat() {
        return timeFormat;
    }

    public void setTimeFormat(String timeFormat) {
        this.timeFormat = timeFormat;
    }

    public RefText getRefText() {
        return refText;
    }

    public void setRefText(RefText refText) {
        this.refText = refText;
    }

    /**
     * 引用翻译缓存配置。
     */
    public static class RefText {

        /** 翻译结果缓存有效期（秒），默认 5 分钟 */
        private long ttlSeconds = 300L;

        /** 缓存容量上限（条），超出触发清理，仍满则不再写入，默认 1 万 */
        private int maxSize = 10000;

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }
    }
}
