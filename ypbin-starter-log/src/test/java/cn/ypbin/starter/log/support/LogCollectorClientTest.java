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
package cn.ypbin.starter.log.support;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.log.core.LogClientProvider;
import cn.ypbin.starter.log.core.LogClientProvider.LogClientInfo;
import cn.ypbin.starter.log.enums.Include;
import cn.ypbin.starter.log.model.LogRecord;
import java.util.EnumSet;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link LogCollector} 客户端信息采集测试。
 *
 * @author wenbin
 * @since 2026-08-01
 */
class LogCollectorClientTest {

    @Test
    void shouldCollectClientInfoWhenIncluded() {
        LogClientProvider clientProvider = () -> Optional.of(new LogClientInfo("web-admin", "WEB", "ACCOUNT"));
        LogCollector collector = new LogCollector(Optional::empty, clientProvider, new ObjectMapper());
        LogRecord record = new LogRecord();

        collector.collect(record, EnumSet.of(Include.CLIENT), null, null, null);

        assertThat(record.getClientId()).isEqualTo("web-admin");
        assertThat(record.getClientType()).isEqualTo("WEB");
        assertThat(record.getAuthType()).isEqualTo("ACCOUNT");
    }

    @Test
    void shouldSkipClientInfoWhenNotIncluded() {
        LogClientProvider clientProvider = () -> Optional.of(new LogClientInfo("web-admin", "WEB", "ACCOUNT"));
        LogCollector collector = new LogCollector(Optional::empty, clientProvider, new ObjectMapper());
        LogRecord record = new LogRecord();

        collector.collect(record, EnumSet.noneOf(Include.class), null, null, null);

        assertThat(record.getClientId()).isNull();
        assertThat(record.getClientType()).isNull();
        assertThat(record.getAuthType()).isNull();
    }
}
