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
package cn.ypbin.starter.log.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.starter.log.autoconfigure.AccessLogProperties;
import java.util.List;
import java.util.stream.Collectors;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link AccessLogAspect} 单元测试：分块日志格式、敏感头掩码、异常块、排除路径、参数序列化。
 *
 * @author wenbin
 * @since 2026-08-06
 */
class AccessLogAspectTest {

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        Logger accessLogger = (Logger) LoggerFactory.getLogger("ypbin.access");
        appender = new ListAppender<>();
        appender.start();
        accessLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        RequestContextHolder.resetRequestAttributes();
        Logger accessLogger = (Logger) LoggerFactory.getLogger("ypbin.access");
        accessLogger.detachAppender(appender);
    }

    private String capturedLog() {
        return appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.joining("\n"));
    }

    private void setRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private AccessLogAspect aspect() {
        return new AccessLogAspect(new ObjectMapper(), new AccessLogProperties());
    }

    private ProceedingJoinPoint point() throws Throwable {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[] {"current", "size"});
        doReturn(AccessLogAspectTest.class).when(signature).getDeclaringType();
        when(signature.getName()).thenReturn("list");
        when(point.getArgs()).thenReturn(new Object[] {1, 10});
        when(point.proceed()).thenReturn("result");
        return point;
    }

    @Test
    void around_shouldLogRequestAndResponseBlocks() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("content-type", "application/json");
        setRequest(request);

        aspect().around(point());

        String log = capturedLog();
        assertThat(log).contains("================  Request Start  ================");
        assertThat(log).contains("===Handler===  AccessLogAspectTest.list");
        assertThat(log).contains("===> GET: /orders Parameters: {\"current\":1,\"size\":10}");
        assertThat(log).contains("===Headers===");
        assertThat(log).containsIgnoringCase("content-type: application/json");
        assertThat(log).contains("===IP===  10.0.0.1");
        assertThat(log).contains("================   Request End   ================");
        assertThat(log).contains("================  Response Start  ================");
        assertThat(log).contains("===Result===  \"result\"");
        assertThat(log).containsPattern("<=== GET: /orders \\(\\d+ ms\\)");
        assertThat(log).contains("================   Response End   ================");
    }

    @Test
    void around_shouldMaskSensitiveHeaders() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.addHeader("Authorization", "Bearer secret-token");
        request.addHeader("X-Tenant-Id", "1001");
        setRequest(request);

        aspect().around(point());

        String log = capturedLog();
        assertThat(log).contains("  Authorization: ******");
        assertThat(log).contains("  X-Tenant-Id: 1001");
        assertThat(log).doesNotContain("Bearer secret-token");
    }

    @Test
    void around_shouldLogExceptionBlock() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        setRequest(request);
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[0]);
        doReturn(AccessLogAspectTest.class).when(signature).getDeclaringType();
        when(signature.getName()).thenReturn("fail");
        when(point.getArgs()).thenReturn(new Object[0]);
        when(point.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThat(catchThrowable(() -> aspect().around(point)))
            .isInstanceOf(IllegalStateException.class);

        assertThat(capturedLog()).contains("===Result===  exception: boom");
    }

    @Test
    void around_shouldSkipExcludedPath() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health/check");
        setRequest(request);
        AccessLogProperties properties = new AccessLogProperties();
        properties.setExcludePathPatterns(List.of("/health/**"));
        AccessLogAspect aspect = new AccessLogAspect(new ObjectMapper(), properties);

        aspect.around(point());

        assertThat(capturedLog()).isEmpty();
    }

    @Test
    void around_shouldProceedWithoutWebContext() throws Throwable {
        RequestContextHolder.resetRequestAttributes();
        ProceedingJoinPoint point = point();

        Object result = aspect().around(point);

        assertThat(result).isEqualTo("result");
        assertThat(capturedLog()).isEmpty();
    }

    @Test
    void buildParams_shouldZipParameterNames() {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[] {"current", "size"});
        when(point.getArgs()).thenReturn(new Object[] {1, 10});

        assertThat(aspect().buildParams(point)).isEqualTo("{\"current\":1,\"size\":10}");
    }

    @Test
    void buildParams_shouldFallbackWithoutNames() {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(null);
        when(point.getArgs()).thenReturn(new Object[] {"single"});

        assertThat(aspect().buildParams(point)).isEqualTo("\"single\"");
    }

    @Test
    void buildParams_shouldFilterNonLoggableArgs() {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[] {"req", "name"});
        when(point.getArgs()).thenReturn(new Object[] {new MockHttpServletRequest(), "tom"});

        assertThat(aspect().buildParams(point)).isEqualTo("{\"name\":\"tom\"}");
    }

    @Test
    void resolveIp_shouldPreferForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.2");

        assertThat(AccessLogAspect.resolveIp(request)).isEqualTo("203.0.113.5");
    }

    @Test
    void resolveIp_shouldFallbackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        assertThat(AccessLogAspect.resolveIp(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void around_shouldSummarizeByteArrayResult() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/download");
        setRequest(request);
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[0]);
        doReturn(AccessLogAspectTest.class).when(signature).getDeclaringType();
        when(signature.getName()).thenReturn("download");
        when(point.getArgs()).thenReturn(new Object[0]);
        when(point.proceed()).thenReturn(new byte[] {1, 2, 3});

        aspect().around(point);

        assertThat(capturedLog()).contains("===Result===  <byte[3]>");
    }

    @Test
    void around_shouldSummarizeInputStreamResult() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/download");
        setRequest(request);
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[0]);
        doReturn(AccessLogAspectTest.class).when(signature).getDeclaringType();
        when(signature.getName()).thenReturn("download");
        when(point.getArgs()).thenReturn(new Object[0]);
        when(point.proceed()).thenReturn(new java.io.ByteArrayInputStream(new byte[] {1}));

        aspect().around(point);

        assertThat(capturedLog()).contains("===Result===  <InputStream>");
    }

    @Test
    void around_shouldTruncateOversizedResult() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/big");
        setRequest(request);
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[0]);
        doReturn(AccessLogAspectTest.class).when(signature).getDeclaringType();
        when(signature.getName()).thenReturn("big");
        when(point.getArgs()).thenReturn(new Object[0]);
        when(point.proceed()).thenReturn("x".repeat(3000));

        aspect().around(point);

        assertThat(capturedLog()).contains("...(truncated, total 3002 chars)");
    }

    static class LoginRequest {
        public String username;
        @cn.ypbin.starter.log.annotation.LogMask
        public String password;
    }

    @Test
    void around_shouldMaskAnnotatedFieldInResult() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        setRequest(request);
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[0]);
        doReturn(AccessLogAspectTest.class).when(signature).getDeclaringType();
        when(signature.getName()).thenReturn("login");
        when(point.getArgs()).thenReturn(new Object[0]);
        LoginRequest body = new LoginRequest();
        body.username = "tom";
        body.password = "s3cret";
        when(point.proceed()).thenReturn(body);

        ObjectMapper maskingMapper = new ObjectMapper().rebuild()
            .addModule(new cn.ypbin.starter.log.support.LogMaskModule())
            .build();
        new AccessLogAspect(maskingMapper, new AccessLogProperties()).around(point);

        assertThat(capturedLog()).contains("\"password\":\"******\"");
        assertThat(capturedLog()).doesNotContain("s3cret");
    }
}
