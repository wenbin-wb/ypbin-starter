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
package cn.ypbin.starter.messaging.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@link MailService} 与 {@link MailConfig} 单元测试。
 *
 * @author wenbin
 * @since 2026-08-01
 */
class MailServiceTest {

    private MailConfig config(String host, String username, String from) {
        MailConfig config = new MailConfig();
        config.setHost(host);
        config.setUsername(username);
        config.setFrom(from);
        config.setPassword("secret");
        return config;
    }

    @Test
    void isConfiguredReflectsProvider() {
        MailConfig config = config("smtp.qq.com", "a@qq.com", null);
        MailService service = new MailService(() -> config);
        assertThat(service.isConfigured()).isTrue();

        MailService empty = new MailService(MailConfig::new);
        assertThat(empty.isConfigured()).isFalse();
    }

    @Test
    void sendShouldThrowWhenNotConfigured() {
        MailService service = new MailService(MailConfig::new);
        assertThatThrownBy(() -> service.sendText("x@y.com", "s", "c"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("邮件未配置");
    }

    @Test
    void resolveFromPrefersExplicitFrom() {
        assertThat(config("h", "u@qq.com", "noreply@qq.com").resolveFrom()).isEqualTo("noreply@qq.com");
        assertThat(config("h", "u@qq.com", null).resolveFrom()).isEqualTo("u@qq.com");
        assertThat(config("h", "u@qq.com", "  ").resolveFrom()).isEqualTo("u@qq.com");
    }

    @Test
    void fingerprintChangesWithConfig() {
        MailConfig a = config("smtp.qq.com", "a@qq.com", null);
        MailConfig b = config("smtp.qq.com", "a@qq.com", null);
        assertThat(a.fingerprint()).isEqualTo(b.fingerprint());

        b.setPassword("changed");
        assertThat(a.fingerprint()).isNotEqualTo(b.fingerprint());
    }

    @Test
    void providerReturningNewConfigTakesEffect() {
        // 模拟后台动态改配置：provider 每次返回可变引用
        MailConfig[] holder = {config("smtp.qq.com", "a@qq.com", null)};
        MailService service = new MailService(() -> holder[0]);
        assertThat(service.isConfigured()).isTrue();

        holder[0] = new MailConfig(); // 配置被清空
        assertThat(service.isConfigured()).isFalse();
    }
}
