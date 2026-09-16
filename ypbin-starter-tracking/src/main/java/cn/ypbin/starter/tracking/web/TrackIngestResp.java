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

import java.util.Map;

/**
 * 采集端点的上报结果。
 *
 * <p>刻意逐项返回计数而不是「成功/失败」二值：客户端需要知道哪些事件没被接收，
 * 否则会盲目重试并放大流量。{@code dropped} 是队列满导致丢弃的条数（埋点允许丢弃，
 * 但必须让调用方与运维都看得见）。</p>
 *
 * @param received 本次请求携带的事件数
 * @param accepted 被接收入队的事件数
 * @param rejected 因校验失败被拒绝的事件数
 * @param dropped  因队列满被丢弃的事件数
 * @param reasons  拒绝原因到条数的映射（不可变；无拒绝时为空）
 * @author wenbin
 * @since 2026-09-15
 */
public record TrackIngestResp(int received, int accepted, int rejected, int dropped, Map<String, Integer> reasons) {
}
