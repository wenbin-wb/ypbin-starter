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
package cn.ypbin.starter.security.blocklist;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.core.blocklist.BlocklistProperties;
import cn.ypbin.starter.core.blocklist.InMemoryBlocklistStore;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 登录失败自动封禁计数入口用例。
 *
 * @author wenbin
 * @since 2026-11-04
 */
class BlocklistAutoBanRecorderTest {

    @Test
    @DisplayName("登录失败达到阈值后封禁 IP")
    void recordsUntilThreshold() {
        BlocklistProperties properties = new BlocklistProperties();
        properties.setEnabled(true);
        properties.getAutoBan().setThreshold(2);
        properties.getAutoBan().setWindowSeconds(60);
        properties.getAutoBan().setBanSeconds(60);
        InMemoryBlocklistStore store = new InMemoryBlocklistStore();
        BlocklistAutoBanRecorder recorder = new BlocklistAutoBanRecorder(properties, store);

        recorder.recordLoginFailure("1.2.3.4");
        assertThat(store.isBanned("1.2.3.4")).isFalse();
        recorder.recordLoginFailure("1.2.3.4");

        assertThat(store.isBanned("1.2.3.4")).isTrue();
    }

    @Test
    @DisplayName("exclude-ips 命中的地址不计数")
    void ignoresExcludedIp() {
        BlocklistProperties properties = new BlocklistProperties();
        properties.setEnabled(true);
        properties.setExcludeIps(List.of("10.0.0.0/8"));
        properties.getAutoBan().setThreshold(1);
        InMemoryBlocklistStore store = new InMemoryBlocklistStore();

        new BlocklistAutoBanRecorder(properties, store).recordLoginFailure("10.0.0.5");

        assertThat(store.isBanned("10.0.0.5")).isFalse();
    }

    @Test
    @DisplayName("未开启或 IP 非法时不计数")
    void ignoresDisabledAndInvalidIp() {
        BlocklistProperties properties = new BlocklistProperties();
        properties.setEnabled(false);
        InMemoryBlocklistStore disabledStore = new InMemoryBlocklistStore();
        new BlocklistAutoBanRecorder(properties, disabledStore).recordLoginFailure("1.2.3.4");
        assertThat(disabledStore.isBanned("1.2.3.4")).isFalse();

        properties.setEnabled(true);
        properties.getAutoBan().setThreshold(1);
        InMemoryBlocklistStore invalidIpStore = new InMemoryBlocklistStore();
        new BlocklistAutoBanRecorder(properties, invalidIpStore).recordLoginFailure("not-an-ip");
        assertThat(invalidIpStore.isBanned("not-an-ip")).isFalse();
    }
}
