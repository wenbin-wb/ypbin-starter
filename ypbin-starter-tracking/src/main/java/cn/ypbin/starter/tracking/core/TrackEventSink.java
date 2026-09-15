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
package cn.ypbin.starter.tracking.core;

import java.util.List;

/**
 * 埋点事件持久化扩展点。
 *
 * <p>采集链路只负责把事件投入有界队列，落库由本接口的实现决定（数据库 / 消息队列 / 文件）。
 * 默认实现 {@code LoggingTrackEventSink} 仅打印到应用日志，宿主覆盖本 Bean 即可落库。</p>
 *
 * <p><strong>调用语义</strong>：本接口在采集侧<strong>消费者线程</strong>上被调用，与业务请求线程隔离，
 * 因此允许阻塞（批量写库）；但实现应保持幂等——同一 {@code eventId} 重复写入不得产生重复数据行。</p>
 *
 * <p><strong>异常语义</strong>：实现抛出的异常由采集链路捕获、计入失败计数并按批丢弃，
 * <strong>不会</strong>影响业务请求；但这只是兜底，实现不应依赖它来掩盖错误
 * （禁止静默吞异常，失败必须记录完整堆栈）。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
@FunctionalInterface
public interface TrackEventSink {

    /**
     * 批量写入事件。
     *
     * @param events 待写入事件（调用方保证非空；实现无需再判空，但可按需短路）
     */
    void write(List<TrackEvent> events);
}
