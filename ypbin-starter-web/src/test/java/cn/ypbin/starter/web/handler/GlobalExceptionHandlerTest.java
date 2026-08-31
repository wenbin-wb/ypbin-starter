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
package cn.ypbin.starter.web.handler;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.web.autoconfigure.WebDefaultsEnvironmentPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.support.StandardServletEnvironment;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理器与 Web 默认环境后置处理测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void businessExceptionShouldReturnBusinessCode() {
        R<Void> result = handler.handleBusinessException(new BusinessException("业务失败"), request);
        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.BUSINESS_ERROR.getCode());
        assertThat(result.getMessage()).isEqualTo("业务失败");
    }

    @Test
    void bindExceptionShouldReturnBadRequest() {
        BindException bind = new BindException(new Object(), "target");
        bind.addError(new FieldError("target", "name", "名称不能为空"));
        R<Void> result = handler.handleBindException(bind);
        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.BAD_REQUEST.getCode());
        assertThat(result.getMessage()).contains("名称不能为空");
    }

    @Test
    void methodNotSupportedShouldReturn405() {
        R<Void> result = handler.handleMethodNotSupported(
            new HttpRequestMethodNotSupportedException("GET"));
        assertThat(result.getCode()).isEqualTo(405);
    }

    @Test
    void notFoundShouldReturn404() {
        R<Void> result = handler.handleNotFound(
            new NoHandlerFoundException("GET", "/x", null), request);
        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void unexpectedExceptionShouldReturn500() {
        R<Void> result = handler.handleException(new IllegalStateException("boom"), request);
        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    void webDefaultsShouldInjectProperties() {
        WebDefaultsEnvironmentPostProcessor processor = new WebDefaultsEnvironmentPostProcessor();
        assertThat(processor.getOrder()).isEqualTo(org.springframework.core.Ordered.LOWEST_PRECEDENCE);
        StandardServletEnvironment env = new StandardServletEnvironment();
        processor.postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.web.resources.add-mappings")).isEqualTo("false");
        assertThat(env.getProperty("spring.threads.virtual.enabled")).isEqualTo("true");
    }

    @Test
    void webDefaultsShouldNotOverrideExistingSource() {
        WebDefaultsEnvironmentPostProcessor processor = new WebDefaultsEnvironmentPostProcessor();
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.threads.virtual.enabled", "false");
        processor.postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.threads.virtual.enabled")).isEqualTo("false");
    }
}
