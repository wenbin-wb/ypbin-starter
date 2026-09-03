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
package cn.ypbin.starter.cloud.feign.support;

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.model.R;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Feign 统一响应处理工具。
 *
 * <p>微服务间 Feign 返回统一 {@link R}，调用侧需要反复处理：
 * 响应为 null、业务失败、数据为 null 三种情况。本类把这些判断收敛为几个明确语义的方法，
 * 避免各业务模块重复手写且出现“失败当空数据/静默降级”的不一致。</p>
 *
 * @author wenbin
 * @since 2026-09-03
 */
public final class FeignResponses {

    private FeignResponses() {
    }

    /**
     * 从 Feign 响应中安全取数据；失败时抛出业务异常。
     *
     * @param response    Feign 响应，允许为 null
     * @param errorMessage 远程失败时使用的错误提示
     * @return 业务数据
     */
    public static <T> T dataOrThrow(R<T> response, String errorMessage) {
        if (response == null || !response.isSuccess()) {
            throw new BusinessException(errorMessage);
        }
        return response.getData();
    }

    /**
     * 从 Feign 响应中取数据；失败时使用降级值。
     *
     * <p>注意：只适用于“业务允许降级默认值”的读取场景，不允许把远程失败伪装成成功空数据。</p>
     *
     * @param response Feign 响应
     * @param fallback 降级值
     * @return 业务数据或降级值
     */
    public static <T> T dataOrElse(R<T> response, T fallback) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            return fallback;
        }
        return response.getData();
    }

    /**
     * 从 Feign 响应中取 Optional 数据；失败时返回 {@link Optional#empty()}。
     */
    public static <T> Optional<T> optionalData(R<T> response) {
        return Optional.ofNullable(dataOrElse(response, null));
    }

    /**
     * 检查 Feign 响应是否成功且数据非空。
     */
    public static <T> boolean isSuccessWithData(R<T> response) {
        return response != null && response.isSuccess() && response.getData() != null;
    }

    /**
     * 校验 Feign 响应成功；失败时抛出远程不可用异常，避免调用方把失败当空数据处理。
     */
    public static <T> R<T> ensureSuccess(R<T> response) {
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException("远程服务暂不可用，请稍后重试");
        }
        return response;
    }

    /**
     * 对 Feign 响应执行数据消费，失败时抛出异常。
     */
    public static <T> T supplyOrThrow(Supplier<R<T>> supplier, String errorMessage) {
        return dataOrThrow(supplier.get(), errorMessage);
    }
}
