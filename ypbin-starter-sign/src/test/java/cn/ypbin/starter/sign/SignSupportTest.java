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
package cn.ypbin.starter.sign;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.sign.autoconfigure.SignProperties;
import cn.ypbin.starter.sign.core.DefaultSignAppProvider;
import cn.ypbin.starter.sign.core.InMemoryNonceStore;
import cn.ypbin.starter.sign.core.SignAlgorithm;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 签名模块配置与应用/Nonce 存储测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class SignSupportTest {

    @Test
    void propertiesShouldExposeDefaults() {
        SignProperties props = new SignProperties();
        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getAlgorithm()).isEqualTo(SignAlgorithm.HMAC_SHA256);
        assertThat(props.getTimeout()).isEqualTo(60L);
        assertThat(props.isReplayProtect()).isTrue();
    }

    @Test
    void appProviderShouldFindByAccessKey() {
        SignProperties props = new SignProperties();
        SignProperties.AppInfo info = new SignProperties.AppInfo();
        info.setAccessKey("ak-1");
        info.setSecretKey("sk-1");
        info.setAppName("demo");
        props.setApps(java.util.List.of(info));
        DefaultSignAppProvider provider = new DefaultSignAppProvider(props);

        assertThat(provider.findByAccessKey("ak-1")).isPresent();
        assertThat(provider.findByAccessKey("missing")).isEmpty();
    }

    @Test
    void inMemoryNonceShouldTrackUsage() {
        InMemoryNonceStore store = new InMemoryNonceStore();
        assertThat(store.tryUse("nonce-1", Duration.ofSeconds(30))).isTrue();
        assertThat(store.tryUse("nonce-1", Duration.ofSeconds(30))).isFalse();
        assertThat(store.tryUse("nonce-2", Duration.ofSeconds(30))).isTrue();
    }
}
