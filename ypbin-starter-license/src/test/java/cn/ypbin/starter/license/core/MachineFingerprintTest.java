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
package cn.ypbin.starter.license.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link MachineFingerprint} 指纹采集单元测试。
 *
 * @author wenbin
 * @since 2026-08-05
 */
class MachineFingerprintTest {

    @Test
    void current_shouldBeStableAcrossCalls() {
        String first = MachineFingerprint.current();
        String second = MachineFingerprint.current();

        // 同一机器多次采集应稳定，且为 SM3 的 64 位十六进制
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
    }
}
