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
@ConfigurationProperties(prefix = JacksonProperties.PREFIX)
public class JacksonProperties {

    public static final String PREFIX = "ypbin.json";

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

    /** 字典翻译（@DictText / DictUtils）配置 */
    private Dict dict = new Dict();

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

    public Dict getDict() {
        return dict;
    }

    public void setDict(Dict dict) {
        this.dict = dict;
    }

    /**
     * 引用翻译缓存配置。
     */
    public static class RefText {

        /** 翻译结果缓存有效期（秒），默认 5 分钟 */
        private long ttlSeconds = 300L;

        /** 缓存容量上限（条），超出触发清理，仍满则不再写入，默认 1 万 */
        private int maxSize = 10000;

        /** 是否自动预加载（拦截响应体在序列化前批量翻译，业务无需手动 preload），默认开启 */
        private boolean autoResolve = true;

        public boolean isAutoResolve() {
            return autoResolve;
        }

        public void setAutoResolve(boolean autoResolve) {
            this.autoResolve = autoResolve;
        }

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

    /**
     * 字典缓存配置。
     */
    public static class Dict {

        /**
         * 字典缓存有效期（秒），默认 5 分钟。多实例部署下，单个实例的字典文案最长陈旧时间即为该值
         * （refresh 只清当前 JVM 缓存）。设为 0 或负数表示关闭字典缓存（每次读取都回源），
         * 与 ref-text.ttl-seconds 的 0 值语义一致；本模块不提供「永不过期」开关，需要长缓存请给一个大值。
         */
        // 字面量写法与 RefText 一致：引用跨类常量会让配置元数据无法解析出 defaultValue
        private long ttlSeconds = 300L;

        /**
         * 缓存容量上限（不同字典类型数），默认 1 万。超出时先清理过期条目，仍满则淘汰最早到期的一条，
         * 保证新字典类型仍可缓存。设为 0 或负数表示关闭字典缓存（每次读取都回源）。
         */
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
