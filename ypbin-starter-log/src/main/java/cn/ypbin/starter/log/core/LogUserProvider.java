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
package cn.ypbin.starter.log.core;

import java.util.Optional;

/**
 * 操作人提供者扩展点。
 *
 * <p>日志采集时通过本接口获取当前操作人 ID。log 模块不直接依赖 security 模块，
 * 由业务方（或 security 模块）提供实现完成解耦。未提供实现时使用返回空的默认实现。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@FunctionalInterface
public interface LogUserProvider {

    /**
     * 获取当前操作人 ID。
     *
     * @return 操作人 ID，未登录或无上下文时返回 {@link Optional#empty()}
     */
    Optional<Long> getCurrentUserId();
}
