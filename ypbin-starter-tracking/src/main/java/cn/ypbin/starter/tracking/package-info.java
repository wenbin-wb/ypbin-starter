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
 * ypbin-starter tracking 模块（埋点）。
 *
 * <p>职责边界（与相邻能力严格区分，不做第四套日志）：</p>
 * <ul>
 *   <li><strong>审计日志</strong>（{@code ypbin-starter-log}）：回答「谁对什么做了什么」，不可丢、主体必须已认证；</li>
 *   <li><strong>技术指标</strong>（actuator / Micrometer）：回答「系统是否健康」，用于报警；</li>
 *   <li><strong>埋点</strong>（本模块）：回答「用户怎么用、哪一步流失、哪个页面慢」，<strong>允许丢弃</strong>、主体可匿名。</li>
 * </ul>
 *
 * <p>本包及其子包标注 {@link org.jspecify.annotations.NullMarked}：<strong>未标注即为非空</strong>，
 * 可空的返回值、参数与字段必须显式标注 {@code @Nullable}。该约定由
 * {@code mvn -Pnullaway -pl <本模块> compile}（NullAway）在编译期校验，漏标即构建失败。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
@NullMarked
package cn.ypbin.starter.tracking;

import org.jspecify.annotations.NullMarked;
