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

import cn.ypbin.starter.log.enums.Include;
import cn.ypbin.starter.log.model.LogRecord;
import java.util.EnumSet;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link LogCollector} 敏感参数与请求头脱敏测试。
 *
 * @author wenbin
 * @since 2026-11-06
 */
class LogCollectorSensitiveTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void masksSensitiveQueryParamsAndHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addParameter("token", "tok-123");
        request.addParameter("password", "s3cret");
        request.addParameter("name", "tom");
        request.addHeader("Authorization", "Bearer abc");
        request.addHeader("Cookie", "SESSION=xyz");
        request.addHeader("X-Trace-Id", "trace-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        ObjectMapper mapper = new ObjectMapper().rebuild().addModule(new LogMaskModule()).build();
        LogCollector collector = new LogCollector(Optional::empty, Optional::empty, ip -> null, mapper);
        LogRecord record = new LogRecord();

        collector.collect(record, EnumSet.of(Include.REQUEST_PARAM, Include.REQUEST_HEADERS), null, null, null);

        assertThat(record.getRequestParam())
            .contains("token=******")
            .contains("password=******")
            .contains("name=tom")
            .doesNotContain("tok-123")
            .doesNotContain("s3cret");
        assertThat(record.getRequestHeaders())
            .containsEntry("Authorization", "******")
            .containsEntry("Cookie", "******")
            .containsEntry("X-Trace-Id", "trace-1");
    }
}
