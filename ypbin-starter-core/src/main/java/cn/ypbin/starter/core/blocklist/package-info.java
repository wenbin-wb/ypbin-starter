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

/**
 * 访问黑名单核心模型、匹配器与存储端口。
 *
 * <p>本包不依赖 Servlet/Gateway/Redis，Servlet 与 WebFlux 两侧共用同一套匹配语义。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
@NullMarked
package cn.ypbin.starter.core.blocklist;

import org.jspecify.annotations.NullMarked;
