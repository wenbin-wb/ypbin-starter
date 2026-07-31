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
package cn.ypbin.starter.cloud.feign.fallback;

import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.util.StringUtils;

/**
 * 返回 {@link R} 的 Feign fallback 工厂基类。
 *
 * <p>本类只提供统一失败响应构造，不尝试对任意 Feign 接口做动态代理兜底，避免非 {@code R}
 * 返回值被静默吞错。业务方继承后在 {@link #create(Throwable)} 中返回自己的 Feign 接口匿名实现，
 * 每个方法调用 {@link #fail(Throwable)} 或 {@link #fail(Throwable, String)} 即可。</p>
 *
 * @param <T> Feign 客户端接口类型
 * @author wenbin
 * @since 2026-07-31
 */
public abstract class RFeignFallbackFactory<T> implements FallbackFactory<T> {

    protected <D> R<D> fail(Throwable cause) {
        return fail(cause, null);
    }

    protected <D> R<D> fail(Throwable cause, String defaultMessage) {
        if (cause instanceof FeignFallbackException fallbackException) {
            return R.fail(fallbackException.getCode(), fallbackException.getMessage());
        }
        String message = resolveMessage(cause, defaultMessage);
        return R.fail(GlobalErrorCode.INTERNAL_ERROR.getCode(), message);
    }

    private String resolveMessage(Throwable cause, String defaultMessage) {
        if (StringUtils.hasText(defaultMessage)) {
            return defaultMessage;
        }
        if (cause != null && StringUtils.hasText(cause.getMessage())) {
            return cause.getMessage();
        }
        return "远程服务暂不可用，请稍后重试";
    }
}
