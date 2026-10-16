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
package cn.ypbin.starter.license.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.license.exception.LicenseErrorCode;
import cn.ypbin.starter.license.exception.LicenseException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.core.io.support.SpringFactoriesLoader;

/**
 * {@link LicenseStartupFailureAnalyzer} 测试：锁定「诊断器真的被 Boot 加载」与「Action 文案给出可执行
 * 的配置项」两件事。
 *
 * @author wenbin
 * @since 2026-10-17
 */
class LicenseStartupFailureAnalyzerTest {

    private final LicenseStartupFailureAnalyzer analyzer = new LicenseStartupFailureAnalyzer();

    @Test
    @DisplayName("★ 诊断器必须被 SpringFactoriesLoader 真实加载（注册文件写错即静默失效）")
    void analyzerIsRegisteredForBootLoading() {
        // 只取工厂类名而不实例化：Boot 自带的 NoSuchBeanDefinitionFailureAnalyzer 需要构造参数，
        // 直接 load(...) 会因实例化它而抛异常，测不到我们的注册项
        List<String> analyzerNames = SpringFactoriesLoader.loadFactoryNames(
            FailureAnalyzer.class, getClass().getClassLoader());

        assertThat(analyzerNames)
            .as("META-INF/spring.factories 的注册必须真实可加载，否则诊断器等于没写")
            .contains(LicenseStartupFailureAnalyzer.class.getName());
    }

    @Test
    @DisplayName("授权缺失：Action 指向 location / allow-startup-without-license")
    void missingLicenseActionPointsToLocation() {
        FailureAnalysis analysis = analyzer.analyze(
            new LicenseException(LicenseErrorCode.LICENSE_MISSING, "未检测到授权文件"));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription())
            .contains("License 授权启动校验失败")
            .contains(LicenseErrorCode.LICENSE_MISSING.name())
            .contains("未检测到授权文件");
        assertThat(analysis.getAction())
            .contains("ypbin.license.location")
            .contains("ypbin.license.allow-startup-without-license");
    }

    @Test
    @DisplayName("密钥缺失（CORRUPTED）：Action 指向 public-key / secret-key")
    void corruptedLicenseActionPointsToKeys() {
        FailureAnalysis analysis = analyzer.analyze(
            new LicenseException(LicenseErrorCode.LICENSE_CORRUPTED, "缺少授权校验密钥"));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getAction())
            .contains("ypbin.license.public-key")
            .contains("ypbin.license.secret-key");
    }

    @Test
    @DisplayName("联机校验被拒：Action 指向 online.* 配置")
    void remoteRejectedActionPointsToOnlineConfig() {
        FailureAnalysis analysis = analyzer.analyze(
            new LicenseException(LicenseErrorCode.LICENSE_REMOTE_REJECTED, "联机校验缺少开放应用密钥"));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getAction()).contains("ypbin.license.online");
    }

    @Test
    @DisplayName("非 License 异常不参与诊断（不得抢答其它启动失败）")
    void unrelatedFailureIsNotAnalyzed() {
        assertThat(analyzer.analyze(new IllegalStateException("别的启动失败"))).isNull();
    }
}
