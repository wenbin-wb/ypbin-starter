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

import cn.ypbin.starter.data.core.AuditorProvider;
import cn.ypbin.starter.data.core.InnerInterceptorProvider;
import cn.ypbin.starter.data.crypto.AesFieldEncryptor;
import cn.ypbin.starter.data.crypto.FieldEncryptor;
import cn.ypbin.starter.data.crypto.FieldEncryptorHolder;
import cn.ypbin.starter.data.enums.BaseEnumScanner;
import cn.ypbin.starter.data.enums.BaseEnumTypeHandlerRegistrar;
import cn.ypbin.starter.data.handler.DefaultMetaObjectHandler;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

/**
 * 数据访问自动配置。
 *
 * <p>装配 MyBatis-Plus 分页拦截器与审计字段自动填充。当业务方未提供
 * {@link AuditorProvider} 时使用返回空的默认实现，从而 data 模块可独立运行；
 * security 模块引入后可覆盖该 Bean 以对接实际登录用户。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@ConditionalOnProperty(prefix = DataProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DataProperties.class)
public class DataAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DataAutoConfiguration.class);

    /** 告警文案里展示的示例枚举数量上限（避免一个超长列表刷屏日志）。 */
    private static final long WARN_SAMPLE_LIMIT = 5L;

    /**
     * 分页拦截器贡献者。
     *
     * <p>以较大 order 排在最后，确保多租户 / 数据权限等扩展拦截器先于分页执行。</p>
     */
    @Bean
    @ConditionalOnMissingBean(name = "paginationInnerInterceptorProvider")
    public InnerInterceptorProvider paginationInnerInterceptorProvider(DataProperties properties) {
        return new InnerInterceptorProvider() {
            @Override
            public InnerInterceptor getInnerInterceptor() {
                PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(properties.getDbType());
                pagination.setMaxLimit(properties.getMaxLimit());
                pagination.setOverflow(properties.isOverflow());
                return pagination;
            }

            @Override
            public int getOrder() {
                return ORDER_PAGINATION;
            }
        };
    }

    /**
     * MyBatis-Plus 核心拦截器链：收集所有 {@link InnerInterceptorProvider}，按 order 升序装配。
     *
     * <p>顺序很关键：多租户 / 数据权限（小 order）必须先于分页（大 order）执行。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(List<InnerInterceptorProvider> providers) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        List<InnerInterceptorProvider> sorted = new ArrayList<>(providers);
        AnnotationAwareOrderComparator.sort(sorted);
        for (InnerInterceptorProvider provider : sorted) {
            interceptor.addInnerInterceptor(provider.getInnerInterceptor());
        }
        log.debug("[ypbin-starter] mybatis-plus interceptor assembled with {} inner interceptor(s).", sorted.size());
        return interceptor;
    }

    /**
     * 默认审计人提供者：无上下文时返回空，保证 data 模块可独立运行。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuditorProvider auditorProvider() {
        return Optional::empty;
    }

    /**
     * 审计字段自动填充处理器。
     */
    @Bean
    @ConditionalOnMissingBean
    public MetaObjectHandler metaObjectHandler(AuditorProvider auditorProvider) {
        return new DefaultMetaObjectHandler(auditorProvider);
    }

    /**
     * 默认字段加密器：仅当配置了 {@code ypbin.data.encrypt.key} 时装配。
     *
     * <p>装配后注入 {@link FieldEncryptorHolder}，供由 MyBatis 实例化的 EncryptTypeHandler 取用。
     * 业务方也可自行提供 {@link FieldEncryptor} Bean 覆盖（如接入国密/KMS）。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = DataProperties.Encrypt.PREFIX, name = "key")
    public FieldEncryptor fieldEncryptor(DataProperties properties) {
        String key = properties.getEncrypt().getKey();
        FieldEncryptor encryptor = new AesFieldEncryptor(key);
        FieldEncryptorHolder.setEncryptor(encryptor);
        log.debug("[ypbin-starter] field encryptor initialized.");
        return encryptor;
    }

    /**
     * {@code BaseEnum} 的 MyBatis 集成：让实现 {@link cn.ypbin.starter.core.enums.BaseEnum} 的枚举
     * 按 {@code value} 落库与回填，而不是按常量名或序号。
     *
     * <p><b>装配形态</b>：返回 MyBatis-Plus 的 {@link ConfigurationCustomizer} bean，由
     * {@code MybatisPlusAutoConfiguration} 收集后应用到每个 {@code MybatisConfiguration}。
     * 用 customizer 而不是在 starter 里直接改 {@code MybatisConfiguration}，是因为配置对象在业务
     * 容器启动时才由 MyBatis-Plus 创建，starter 不能假设它已经存在。</p>
     *
     * <p><b>扫描范围</b>：默认扫描 {@code cn.ypbin}（starter 自身与业务代码的公共根包），可用
     * {@code ypbin.data.base-enum.scan-packages} 覆盖。之所以必须扫描：MyBatis 的类型处理器注册是
     * 「具体类型 → 处理器」的显式映射，无法按接口自动匹配，实体字段声明的又是具体枚举类。</p>
     *
     * <p><b>不改变既有行为</b>：只注册「实现了 {@code BaseEnum} 的枚举」，未实现该接口的枚举继续走
     * MyBatis-Plus 的默认枚举处理（{@code name()} / {@code @EnumValue}），行为一个字节都不变。
     * 可用 {@code ypbin.data.base-enum.enabled=false} 关闭。</p>
     *
     * @param properties 数据访问配置
     * @return MyBatis 配置定制器
     */
    @Bean
    @ConditionalOnMissingBean(name = "baseEnumTypeHandlerCustomizer")
    @ConditionalOnProperty(prefix = DataProperties.BaseEnum.PREFIX, name = "enabled",
        havingValue = "true", matchIfMissing = true)
    public ConfigurationCustomizer baseEnumTypeHandlerCustomizer(DataProperties properties) {
        Set<String> scanPackages = properties.getBaseEnum().getScanPackages();
        return configuration -> {
            List<Class<?>> enumTypes = BaseEnumScanner.scan(scanPackages);
            if (enumTypes.isEmpty() && properties.getBaseEnum().isEmptyScanDiagnostic()) {
                // 全 classpath 探测只在这个分支发生一次，且内部有硬上界与三方包跳过
                // （2026-10-20 事故：无上界时读到 groovy 巨类在 -Xmx384m 下 OOM）
                reportEmptyScan(scanPackages, BaseEnumScanner.scanAll());
            }
            List<Class<?>> registered = BaseEnumTypeHandlerRegistrar.register(configuration, enumTypes);
            log.debug("[ypbin-starter] base-enum type handler registered for {} enum type(s): {}",
                registered.size(), registered.stream().map(Class::getSimpleName).toList());
        };
    }

    /**
     * 「扫描到 0 个枚举」时区分两种成因并给出正确级别的日志。
     *
     * <p><b>为什么要区分</b>：扫描 0 命中意味着所有 {@code BaseEnum} 会静默按 {@code name()} 落库，
     * 与本能力「按 value 存储」的契约不符，也与其它服务/前端按 value 约定的数据不一致——属静默降级。
     * 但「0 命中」有两种成因，混为一谈会制造噪音或放过真实故障：</p>
     * <ul>
     *   <li>classpath 上<b>没有</b>任何 {@code BaseEnum} 实现 ⇒ 宿主没用这能力，属正常，记 INFO；</li>
     *   <li>classpath 上<b>有</b>实现但不在 {@code scan-packages} 内 ⇒ 配置错误，记 WARN
     *       并给出可直接照做的修正项。</li>
     * </ul>
     *
     * <p><b>为什么把「全量清单」作为参数而不是在方法内扫描</b>：便于测试直接覆盖两种分支
     * （尤其「classpath 上确实没有实现」这一分支在测试 classpath 上无法自然构造——测试代码本身就带着
     * {@code BaseEnum} 实现）。生产调用方传入 {@link BaseEnumScanner#scanAll()} 的真实结果。</p>
     *
     * <p><b>成本控制</b>：全 classpath 扫描只在「scan 命中 0 个」这个分支上由调用方执行一次，
     * 正常宿主（能命中）不会付这份代价。</p>
     *
     * @param scanPackages 实际生效的扫描根包（用于告警文案）
     * @param allBaseEnums classpath 上全部 {@code BaseEnum} 实现（用于判别成因；空表示宿主没用该能力）
     */
    static void reportEmptyScan(Set<String> scanPackages, List<Class<?>> allBaseEnums) {
        if (allBaseEnums.isEmpty()) {
            // 宿主没有使用该能力：正常情况，不打扰
            log.info("[ypbin-starter] 未发现任何 BaseEnum 实现（scan-packages={}），"
                + "本能力不参与装配；如后续引入枚举请保持包名在扫描范围内。", scanPackages);
            return;
        }
        log.warn("[ypbin-starter] 存在 {} 个 BaseEnum 实现，但全部不在扫描范围内（scan-packages={}），"
                + "当前没有任何枚举按 value 落库——它们将静默退回 MyBatis 默认的 name() 存储，"
                + "与按 value 约定存储的数据不一致。请把业务包名加入 "
                + "ypbin.data.base-enum.scan-packages（当前实现示例：{}）。",
            allBaseEnums.size(), scanPackages,
            allBaseEnums.stream().map(Class::getName).limit(WARN_SAMPLE_LIMIT).toList());
    }

    /**
     * 兼容业务方自定义的 {@link FieldEncryptor} Bean：无论何种来源，都注入静态持有器。
     */
    @Bean
    public FieldEncryptorRegistrar fieldEncryptorRegistrar(ObjectProvider<FieldEncryptor> encryptorProvider) {
        return new FieldEncryptorRegistrar(encryptorProvider);
    }

    /**
     * 加密器注册器：把容器中的 FieldEncryptor（无论默认还是自定义）注入静态持有器。
     */
    static class FieldEncryptorRegistrar {
        FieldEncryptorRegistrar(ObjectProvider<FieldEncryptor> encryptorProvider) {
            FieldEncryptor encryptor = encryptorProvider.getIfAvailable();
            if (encryptor != null) {
                FieldEncryptorHolder.setEncryptor(encryptor);
            }
        }
    }
}
