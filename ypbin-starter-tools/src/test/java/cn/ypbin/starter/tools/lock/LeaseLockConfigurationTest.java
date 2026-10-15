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
package cn.ypbin.starter.tools.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.tools.autoconfigure.LeaseLockAutoConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 租约锁的**启动自检**单测。
 *
 * <p>这些配置错误（续期间隔 ≥ TTL、TTL 非正、key-prefix 空）在运行期只会表现为「偶尔两个副本
 * 同时执行」——随机、难复现、且不会报错。唯一能可靠抓住它们的时机是启动，故逐条钉死。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
class LeaseLockConfigurationTest {

    @Test
    @DisplayName("默认配置必须自检通过（默认值本身就是合法的，避免开箱即错）")
    void defaultsShouldPassSelfCheck() {
        assertThatCode(() -> new LeaseLockAutoConfiguration(new LeaseLockProperties()).afterPropertiesSet())
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("renew-interval >= ttl 必须拒绝（否则锁在第一次续期前就过期，多副本并发执行）")
    void renewIntervalMustBeSmallerThanTtl() {
        LeaseLockProperties properties = new LeaseLockProperties();
        properties.setTtl(Duration.ofSeconds(10));
        properties.setRenewInterval(Duration.ofSeconds(10));

        assertThatThrownBy(() -> new LeaseLockAutoConfiguration(properties).afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("renew-interval")
            .hasMessageContaining("必须小于");

        // 大于 TTL 同样拒绝（不只是相等）
        properties.setRenewInterval(Duration.ofSeconds(11));
        assertThatThrownBy(() -> new LeaseLockAutoConfiguration(properties).afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("必须小于");
    }

    @Test
    @DisplayName("ttl <= 0 必须拒绝（零/负租约等于没有锁）")
    void ttlMustBePositive() {
        LeaseLockProperties properties = new LeaseLockProperties();
        properties.setTtl(Duration.ZERO);

        assertThatThrownBy(() -> new LeaseLockAutoConfiguration(properties).afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ttl 必须为正数");

        properties.setTtl(Duration.ofSeconds(-1));
        assertThatThrownBy(() -> new LeaseLockAutoConfiguration(properties).afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ttl 必须为正数");

        properties.setTtl(null);
        assertThatThrownBy(() -> new LeaseLockAutoConfiguration(properties).afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ttl 必须为正数");
    }

    @Test
    @DisplayName("renew-interval <= 0 必须拒绝")
    void renewIntervalMustBePositive() {
        LeaseLockProperties properties = new LeaseLockProperties();
        properties.setRenewInterval(Duration.ZERO);

        assertThatThrownBy(() -> new LeaseLockAutoConfiguration(properties).afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("renew-interval 必须为正数");
    }

    @Test
    @DisplayName("key-prefix 为空必须拒绝（空前缀无法在存储里识别本框架的锁）")
    void keyPrefixMustNotBeBlank() {
        LeaseLockProperties properties = new LeaseLockProperties();
        properties.setKeyPrefix("   ");

        assertThatThrownBy(() -> new LeaseLockAutoConfiguration(properties).afterPropertiesSet())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("key-prefix 不能为空");
    }

    @Test
    @DisplayName("enabled=false 时跳过校验（显式关闭不该被配置值卡住启动）")
    void disabledShouldSkipValidation() {
        LeaseLockProperties properties = new LeaseLockProperties();
        properties.setEnabled(false);
        properties.setTtl(Duration.ZERO);
        properties.setRenewInterval(Duration.ofSeconds(10));

        assertThatCode(() -> new LeaseLockAutoConfiguration(properties).afterPropertiesSet())
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("配置默认值：与文档/元数据一致（防默认值漂移）")
    void defaultsShouldMatchDocumentedValues() {
        LeaseLockProperties properties = new LeaseLockProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getKeyPrefix()).isEqualTo("ypbin:lease-lock:");
        assertThat(properties.getTtl()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getRenewInterval()).isEqualTo(Duration.ofSeconds(20));
        assertThat(properties.isFailOpen()).isTrue();
        assertThat(properties.getMetricPrefix())
            .as("指标前缀默认不得含宿主应用域名（本库通用，由使用方按需覆盖）")
            .isEqualTo("ypbin.lock");
    }
}
