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
package cn.ypbin.starter.tracking.web;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 采集端点的批量上报请求。
 *
 * @param appId  应用标识；为空时取服务端配置的 {@code ypbin.tracking.app-id}
 * @param events 事件列表；超过 {@code max-events-per-request} 的部分会被拒绝并计数
 * @author wenbin
 * @since 2026-09-15
 */
public record TrackIngestReq(@Nullable String appId, @Nullable List<TrackIngestEvent> events) {
}
