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
package cn.ypbin.starter.license.autoconfigure;

import cn.ypbin.starter.job.core.JobHandler;
import cn.ypbin.starter.license.aspect.LicenseCheckAspect;
import cn.ypbin.starter.license.core.LicenseManager;
import cn.ypbin.starter.license.exception.LicenseErrorCode;
import cn.ypbin.starter.license.exception.LicenseException;
import cn.ypbin.starter.license.extension.FileLicenseStore;
import cn.ypbin.starter.license.extension.HttpRemoteVerifyProvider;
import cn.ypbin.starter.license.extension.LicenseStore;
import cn.ypbin.starter.license.extension.RemoteVerifyProvider;
import cn.ypbin.starter.license.integration.LicenseLoginVerifier;
import cn.ypbin.starter.license.integration.OnlineVerifyJob;
import cn.ypbin.starter.security.core.LoginVerifyProvider;
import cn.ypbin.starter.sign.core.SignClient;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * License 授权自动配置。
 *
 * <p>仅当 {@code ypbin.license.enabled=true}（默认）时装配。装配授权串存储（默认本地文件）、授权状态机、
 * {@code @LicenseCheck} 切面，并在启动阶段加载并校验授权（含机器指纹校验），实现启动即锁定非法环境。</p>
 *
 * <p>对 security、job、sign 的集成置于条件装配的嵌套配置中：仅当对应模块在 classpath 时才装配登录回验、
 * 定期联机校验、HTTP 联机校验参考实现，缺失时静默跳过该项集成但不影响其余授权能力（这是「能力可选」而非
 * 「问题掩盖」）。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ypbin.license", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LicenseProperties.class)
public class LicenseAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LicenseAutoConfiguration.class);

    /**
     * 授权串存储：默认基于本地文件。业务方提供自定义 {@link LicenseStore} 即可改为数据库/配置中心等来源。
     */
    @Bean
    @ConditionalOnMissingBean
    public LicenseStore licenseStore(LicenseProperties properties) {
        return new FileLicenseStore(Path.of(properties.getLocation()));
    }

    /**
     * 授权状态机：启动阶段加载并校验授权。
     *
     * <p>公钥/密钥缺失即抛异常暴露配置错误；授权文件缺失时依据 {@code allowStartupWithoutLicense}
     * 决定启动失败还是以非法不可用状态启动。绝不静默以「已授权」蒙混。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public LicenseManager licenseManager(LicenseProperties properties, LicenseStore licenseStore) {
        if (!StringUtils.hasText(properties.getPublicKey()) || !StringUtils.hasText(properties.getSecretKey())) {
            throw new LicenseException(LicenseErrorCode.LICENSE_CORRUPTED,
                "缺少授权校验密钥：请配置 ypbin.license.public-key 与 ypbin.license.secret-key");
        }
        LicenseManager manager = new LicenseManager(properties.getPublicKey(), properties.getSecretKey(),
            properties.isFingerprintEnabled());
        String authCode = licenseStore.load();
        if (!StringUtils.hasText(authCode)) {
            if (!properties.isAllowStartupWithoutLicense()) {
                throw new LicenseException(LicenseErrorCode.LICENSE_MISSING,
                    "未检测到授权文件且未允许无授权启动：请放置授权文件或设置 "
                        + "ypbin.license.allow-startup-without-license=true");
            }
            log.warn("[ypbin-starter] 未检测到授权文件，已以「非法不可用」状态启动，受保护能力将被拦截。");
            return manager;
        }
        manager.load(authCode);
        return manager;
    }

    /**
     * {@code @LicenseCheck} 校验切面。
     */
    @Bean
    @ConditionalOnMissingBean
    public LicenseCheckAspect licenseCheckAspect(LicenseManager licenseManager,
        ObjectProvider<RemoteVerifyProvider> remoteVerifyProviders) {
        return new LicenseCheckAspect(licenseManager, remoteVerifyProviders.orderedStream().toList());
    }

    /**
     * 联机校验 HTTP 参考实现（依赖 sign 模块的签名客户端，按 sign 是否在 classpath 条件装配，
     * 见 {@link RemoteVerifyConfiguration}）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(SignClient.class)
    static class RemoteVerifyConfiguration {

        /**
         * 联机校验 HTTP 参考实现：仅当 sign 模块在 classpath 且配置了 {@code ypbin.license.online.base-url}
         * 时装配，业务侧自定义 {@link RemoteVerifyProvider} 时自动退位。
         *
         * <p>装配即要求开放应用 AK/SK 齐备，缺失直接抛异常暴露配置错误——否则每次联机校验都会因签名无法
         * 通过而持续被拦，静默配置缺失等于线上反复误拦。类级 {@code @ConditionalOnClass(SignClient.class)}：
         * sign 为可选依赖，缺它时不装配本实现（也不内省引用其类型的 {@code @Bean} 方法签名）。</p>
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "ypbin.license.online", name = "base-url")
        public HttpRemoteVerifyProvider httpRemoteVerifyProvider(LicenseProperties properties) {
            String accessKey = properties.getOnline().getAccessKey();
            String secretKey = properties.getOnline().getSecretKey();
            if (!StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey)) {
                throw new LicenseException(LicenseErrorCode.LICENSE_REMOTE_REJECTED,
                    "联机校验缺少开放应用密钥：请配置 ypbin.license.online.access-key 与 secret-key"
                        + "（在签发端「开放应用管理」为消费端应用签发）");
            }
            return new HttpRemoteVerifyProvider(properties.getOnline().getBaseUrl(),
                accessKey, secretKey, properties.getOnline().getTimeout(),
                properties.getOnline().getCacheSeconds(), properties.getOnline().getFailOpenCacheSeconds(),
                properties.getOnline().getFailOpenThreshold(), properties.getOnline().getFailOpenBackoffSeconds(),
                properties.getOnline().getFailurePolicy());
        }
    }

    /**
     * security 集成：登录回验。仅当 classpath 存在 security 的 {@link LoginVerifyProvider} 时装配。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(LoginVerifyProvider.class)
    static class SecurityIntegrationConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public LicenseLoginVerifier licenseLoginVerifier(LicenseManager licenseManager,
            ObjectProvider<RemoteVerifyProvider> remoteVerifyProviders) {
            log.info("[ypbin-starter] 已启用登录回验：每次登录成功后回验当前授权。");
            return new LicenseLoginVerifier(licenseManager, remoteVerifyProviders.orderedStream().toList());
        }
    }

    /**
     * job 集成：定期联机校验。仅当 classpath 存在 job 的 {@link JobHandler} 时装配。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JobHandler.class)
    static class JobIntegrationConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public OnlineVerifyJob onlineVerifyJob(LicenseManager licenseManager,
            ObjectProvider<RemoteVerifyProvider> remoteVerifyProviders) {
            log.info("[ypbin-starter] 已注册定期联机校验任务，执行器标识：licenseOnlineVerify。");
            return new OnlineVerifyJob(licenseManager, remoteVerifyProviders.orderedStream().toList());
        }
    }
}
