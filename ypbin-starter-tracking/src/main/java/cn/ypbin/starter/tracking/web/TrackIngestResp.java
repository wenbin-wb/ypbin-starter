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
 * <p>逐项返回计数而不是「成功/失败」二值：客户端需要知道哪些事件没被接收，否则会盲目重试并放大流量。</p>
 *
 * <p><strong>三个计数满足</strong> {@code received = accepted + rejected + dropped}；属性级问题
 * <strong>不计入</strong> {@code rejected}——它们对应的事件是被正常接收的，只是丢了个别属性，
 * 单独放在 {@code attributeIssues} 里，避免出现「received=1 / accepted=1 / rejected=2」这种自相矛盾的结果。</p>
 *
 * @param received        本次请求携带的事件数
 * @param accepted        被接收入队的事件数
 * @param rejected        因事件级问题未被接收的事件数
 * @param dropped         因队列满被丢弃的事件数（埋点允许丢弃，但必须让调用方看得见）
 * @param reasons         事件级原因到条数的映射（不可变；无则为空）
 * @param attributeIssues 属性级问题到条数的映射（不可变；无则为空）
 * @author wenbin
 * @since 2026-09-15
 */
public record TrackIngestResp(int received, int accepted, int rejected, int dropped,
                              Map<String, Integer> reasons, Map<String, Integer> attributeIssues) {
}
