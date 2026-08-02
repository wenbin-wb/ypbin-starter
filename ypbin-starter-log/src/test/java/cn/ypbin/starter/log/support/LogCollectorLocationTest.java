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

import cn.ypbin.starter.log.core.IpLocationResolver;
import cn.ypbin.starter.log.enums.Include;
import cn.ypbin.starter.log.model.LogRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumSet;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link LogCollector} IP 归属地采集测试。
 *
 * @author wenbin
 * @since 2026-08-01
 */
class LogCollectorLocationTest {

    private void bindRequest(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldFillLocationWhenResolverProvided() {
        bindRequest("1.2.3.4");
        IpLocationResolver resolver = ip -> "广东省深圳市";
        LogCollector collector = new LogCollector(Optional::empty, Optional::empty, resolver, new ObjectMapper());
        LogRecord record = new LogRecord();

        collector.collect(record, EnumSet.of(Include.IP), null, null, null);

        assertThat(record.getIp()).isEqualTo("1.2.3.4");
        assertThat(record.getLocation()).isEqualTo("广东省深圳市");
    }

    @Test
    void shouldLeaveLocationNullWithDefaultResolver() {
        bindRequest("1.2.3.4");
        // 默认解析器返回 null（未接入 IP 库）
        LogCollector collector = new LogCollector(Optional::empty, Optional::empty, ip -> null, new ObjectMapper());
        LogRecord record = new LogRecord();

        collector.collect(record, EnumSet.of(Include.IP), null, null, null);

        assertThat(record.getIp()).isEqualTo("1.2.3.4");
        assertThat(record.getLocation()).isNull();
    }

    @Test
    void resolverExceptionShouldNotBreakCollection() {
        bindRequest("1.2.3.4");
        IpLocationResolver faulty = ip -> {
            throw new RuntimeException("IP 库异常");
        };
        LogCollector collector = new LogCollector(Optional::empty, Optional::empty, faulty, new ObjectMapper());
        LogRecord record = new LogRecord();

        // 解析器抛异常不应影响其它字段采集
        collector.collect(record, EnumSet.of(Include.IP), null, null, null);

        assertThat(record.getIp()).isEqualTo("1.2.3.4");
        assertThat(record.getLocation()).isNull();
    }
}
