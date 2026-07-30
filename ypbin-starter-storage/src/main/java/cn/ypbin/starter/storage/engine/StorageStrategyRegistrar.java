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
package cn.ypbin.starter.storage.engine;

import cn.ypbin.starter.storage.strategy.StorageStrategy;
import java.util.List;

/**
 * 存储策略贡献者。
 *
 * <p>每种存储后端（本地、对象存储等）提供一个 registrar，向路由器贡献一组策略。
 * 相比让路由器直接注入 {@code List<List<StorageStrategy>>} 这种晦涩的嵌套泛型，
 * 用接口列表注入更清晰、可扩展：业务方新增后端只需再提供一个 registrar bean。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@FunctionalInterface
public interface StorageStrategyRegistrar {

    /**
     * 贡献的存储策略列表。
     *
     * @return 策略列表
     */
    List<StorageStrategy> strategies();
}
