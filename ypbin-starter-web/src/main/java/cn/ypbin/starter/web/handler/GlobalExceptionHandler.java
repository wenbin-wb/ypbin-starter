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

import cn.ypbin.starter.core.exception.BaseException;
import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 *
 * <p>将框架异常、参数校验异常与未捕获异常统一转换为 {@link R} 响应体，
 * 保证对外错误格式一致。业务异常记 warn，未知异常记 error 以便排查。</p>
 *
 * <p>约定：所有异常统一返回 HTTP 200，由 {@link R#getCode()} 中的业务码区分成功与
 * 各类错误，便于前端在单一响应回调中集中处理，无需在 error 回调里另行解析。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常：可预期，返回 200 + 业务码，仅告警。
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("[业务异常] {} -> {}", request.getRequestURI(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 框架其它异常。
     */
    @ExceptionHandler(BaseException.class)
    public R<Void> handleBaseException(BaseException e, HttpServletRequest request) {
        log.warn("[框架异常] {} -> {}", request.getRequestURI(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * {@code @RequestBody} 参数校验失败。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(GlobalExceptionHandler::formatFieldError)
            .collect(Collectors.joining("; "));
        return R.fail(GlobalErrorCode.BAD_REQUEST.getCode(), msg);
    }

    /**
     * 表单/普通对象绑定校验失败。
     */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        String msg = e.getFieldErrors().stream()
            .map(GlobalExceptionHandler::formatFieldError)
            .collect(Collectors.joining("; "));
        return R.fail(GlobalErrorCode.BAD_REQUEST.getCode(), msg);
    }

    /**
     * 请求方法不支持。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return R.fail(405, "不支持的请求方法：" + e.getMethod());
    }

    /**
     * 接口不存在（无匹配的处理器）。
     *
     * <p>纯 JSON REST 服务下，替代 Spring Boot 默认返回的 HTML Whitelabel 错误页，
     * 保证 404 也走统一的 {@link R} 结构，前端可正常解析。需配合
     * {@code spring.mvc.throw-exception-if-no-handler-found=true} 生效（本模块已默认开启）。</p>
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public R<Void> handleNotFound(Exception e, HttpServletRequest request) {
        log.warn("[接口不存在] {}", request.getRequestURI());
        return R.fail(GlobalErrorCode.NOT_FOUND.getCode(), "接口不存在");
    }

    /**
     * SSE 长连接异步超时：属预期内的连接回收（非业务异常），记一行 WARN 即可。
     *
     * <p>返回 void 不写响应体——连接已达超时被容器回收，响应 Content-Type 已是 {@code text/event-stream}，
     * 若返回 {@link R} 会因无转换器抛 {@code HttpMessageNotWritableException} 产生二次噪音。</p>
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncRequestTimeout(AsyncRequestTimeoutException e, HttpServletRequest request) {
        log.warn("[SSE 超时回收] {} -> {}", request.getRequestURI(), e.getMessage());
    }

    /**
     * 兜底：未预期的系统异常。
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("[系统异常] {} ", request.getRequestURI(), e);
        return R.fail(GlobalErrorCode.INTERNAL_ERROR);
    }

    private static String formatFieldError(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}
