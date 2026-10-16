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

import cn.ypbin.starter.core.exception.BaseException;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.web.autoconfigure.WebDefaultsEnvironmentPostProcessor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.support.StandardServletEnvironment;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
    void messageNotReadableShouldReturnBadRequest() {
        HttpMessageNotReadableException e = new HttpMessageNotReadableException(
            "JSON parse error", new MockHttpInputMessage(new byte[0]));

        R<Void> result = handler.handleMessageNotReadable(e, request);

        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.BAD_REQUEST.getCode());
        assertThat(result.getMessage()).contains("请求体");
    }

    @Test
    void argumentTypeMismatchShouldReturnBadRequest() throws NoSuchMethodException {
        MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
            "abc", Long.class, "orderId", sampleParameter(), null);

        R<Void> result = handler.handleArgumentTypeMismatch(e, request);

        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.BAD_REQUEST.getCode());
        assertThat(result.getMessage()).isEqualTo("请求参数类型不正确：orderId");
    }

    @Test
    void argumentTypeMismatchWithoutParameterNameShouldStillReturnBadRequest() throws NoSuchMethodException {
        MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
            "abc", Long.class, null, sampleParameter(), null);

        R<Void> result = handler.handleArgumentTypeMismatch(e, request);

        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.BAD_REQUEST.getCode());
        assertThat(result.getMessage()).isEqualTo("请求参数类型不正确");
    }

    @Test
    void missingParameterShouldReturnBadRequest() {
        MissingServletRequestParameterException e =
            new MissingServletRequestParameterException("orderId", "Long");

        R<Void> result = handler.handleMissingParameter(e, request);

        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.BAD_REQUEST.getCode());
        assertThat(result.getMessage()).isEqualTo("缺少必填参数：orderId");
    }

    @Test
    void maxUploadSizeExceededShouldReturnPayloadTooLarge() {
        R<Void> result = handler.handleMaxUploadSizeExceeded(
            new MaxUploadSizeExceededException(1024L), request);

        // 413 与 Spring 7 自身的 getStatusCode() 口径一致（而非 400），同时不得是 500
        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.PAYLOAD_TOO_LARGE.getCode());
        assertThat(result.getMessage()).contains("超出大小限制");
    }

    /** 仅用于构造 {@link MethodParameter} 的桩方法（不参与测试执行）。 */
    @SuppressWarnings("unused")
    private void sampleHandler(String orderId) {
        // 桩方法
    }

    private static MethodParameter sampleParameter() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("sampleHandler", String.class);
        return new MethodParameter(method, 0);
    }

    @Test
    void notFoundShouldReturn404() {
        // 签名刻意不含异常参数：404 场景的异常对象无诊断价值，省掉可避免「未使用参数」
        R<Void> result = handler.handleNotFound(request);
        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void unexpectedExceptionShouldReturn500() {
        R<Void> result = handler.handleException(new IllegalStateException("boom"), request);
        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    void wrappedBusinessExceptionShouldKeepItsCodeAndMessage() {
        // 模拟 MyBatis 把业务异常包成 MyBatisSystemException 的情形
        BusinessException root = new BusinessException("缺少租户上下文，请使用 @TenantIgnore");
        Exception wrapped = new IllegalStateException("### Error querying database", root);

        R<Void> result = handler.handleException(wrapped, request);

        assertThat(result.getCode()).isEqualTo(root.getCode());
        assertThat(result.getMessage()).isEqualTo(root.getMessage());
    }

    @Test
    void wrappedBaseExceptionShouldKeepItsCode() {
        BaseException root = new BaseException(GlobalErrorCode.FORBIDDEN, "禁止访问");
        Exception wrapped = new RuntimeException("outer", new RuntimeException("mid", root));

        R<Void> result = handler.handleException(wrapped, request);

        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.FORBIDDEN.getCode());
        assertThat(result.getMessage()).isEqualTo("禁止访问");
    }

    @Test
    void overlyDeepCauseChainShouldFallBackToInternalError() {
        // 超过解包深度上限（10 层）时不继续下探，避免超深异常链带来的额外开销
        Throwable current = new BusinessException("深层业务异常");
        for (int i = 0; i < 12; i++) {
            current = new IllegalStateException("wrap-" + i, current);
        }

        R<Void> result = handler.handleException((Exception) current, request);

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
