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
package cn.ypbin.starter.data.autoconfigure;

import com.baomidou.mybatisplus.annotation.DbType;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * 数据模块配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = DataProperties.PREFIX)
public class DataProperties {

    public static final String PREFIX = "ypbin.data";

    /** 是否启用数据增强，默认开启 */
    private boolean enabled = true;

    /** 数据库类型，用于分页方言，默认 MySQL */
    private DbType dbType = DbType.MYSQL;

    /** 单页最大条数限制，防止恶意超大分页，-1 表示不限制 */
    private long maxLimit = 500L;

    /** 溢出总页数后是否进行处理（跳回首页） */
    private boolean overflow = false;

    /** 字段加密配置 */
    @NestedConfigurationProperty
    private Encrypt encrypt = new Encrypt();

    /** BaseEnum 与数据库的映射配置 */
    @NestedConfigurationProperty
    private BaseEnum baseEnum = new BaseEnum();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public DbType getDbType() {
        return dbType;
    }

    public void setDbType(DbType dbType) {
        this.dbType = dbType;
    }

    public long getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(long maxLimit) {
        this.maxLimit = maxLimit;
    }

    public boolean isOverflow() {
        return overflow;
    }

    public void setOverflow(boolean overflow) {
        this.overflow = overflow;
    }

    public Encrypt getEncrypt() {
        return encrypt;
    }

    public void setEncrypt(Encrypt encrypt) {
        this.encrypt = encrypt;
    }

    public BaseEnum getBaseEnum() {
        return baseEnum;
    }

    public void setBaseEnum(BaseEnum baseEnum) {
        this.baseEnum = baseEnum;
    }

    /**
     * {@code BaseEnum} 的 MyBatis 集成配置。
     *
     * <p><b>默认开启</b>：{@code BaseEnum} 的接口注释即约定「{@code value} 用于存储」，
     * 实现该接口的枚举本就应该按 {@code value} 落库。保持可关闭是为了给需要先回归既有
     * 表数据的宿主一个逃生开关。</p>
     */
    public static class BaseEnum {

        /** 配置前缀。 */
        public static final String PREFIX = "ypbin.data.base-enum";

        /**
         * 是否启用「实现 {@code BaseEnum} 的枚举按 value 落库/回填」，默认开启。
         *
         * <p>关闭后这些枚举退回 MyBatis-Plus 默认枚举处理（{@code name()}），与升级前一致。</p>
         */
        private boolean enabled = true;

        /**
         * 扫描 {@code BaseEnum} 枚举实现的根包，默认 {@code cn.ypbin}。
         *
         * <p>为什么需要显式配置：MyBatis 的类型处理器注册是「具体类型 → 处理器」的显式映射，
         * 无法按接口自动匹配，而实体字段声明的又是具体枚举类，因此必须知道候选类型集合。
         * 默认值覆盖 starter 自身与遵循 {@code cn.ypbin} 约定的业务代码；业务包名不同时务必显式配置，
         * 否则枚举会静默退回 {@code name()} 存储（不是报错，但数据形态不符预期）。</p>
         */
        private Set<String> scanPackages = new LinkedHashSet<>(Set.of("cn.ypbin"));

        /**
         * 「零命中诊断」开关，默认开启。
         *
         * <p>开启时，若 {@code scan-packages} 扫到 0 个 {@code BaseEnum} 实现，会再做一次
         * <b>带硬上界</b>的全 classpath 探测（{@code BaseEnumScanner#scanAll(long)}），用于区分
         * 「宿主没用该能力」（INFO）与「有实现但配错扫描范围」（WARN）。</p>
         *
         * <p><b>什么情况该关</b>：宿主明确不使用该能力、且对启动期 classpath 遍历极其敏感
         * （低内存、超大依赖树）。2026-10-20 的真实事故是：某宿主 {@code -Xmx384m} 下该诊断读到
         * groovy 巨类导致 {@code OutOfMemoryError}——那次之后诊断本身已加上界与三方包跳过，
         * 默认开启是安全的；但保留本开关以便极端环境彻底规避。</p>
         */
        private boolean emptyScanDiagnostic = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEmptyScanDiagnostic() {
            return emptyScanDiagnostic;
        }

        public void setEmptyScanDiagnostic(boolean emptyScanDiagnostic) {
            this.emptyScanDiagnostic = emptyScanDiagnostic;
        }

        public Set<String> getScanPackages() {
            return scanPackages;
        }

        public void setScanPackages(Set<String> scanPackages) {
            this.scanPackages = scanPackages;
        }
    }

    /**
     * 字段加密配置。
     */
    public static class Encrypt {

        /** 配置前缀。 */
        public static final String PREFIX = "ypbin.data.encrypt";

        /** AES 密钥，长度需为 16/24/32 字节。配置后才装配默认字段加密器 */
        // 由 Spring Boot 在对象构造后绑定（@ConfigurationProperties），构造器结束时必然为 null
        @SuppressWarnings("NullAway.Init")
        private String key;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }
}
