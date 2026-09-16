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

import cn.ypbin.starter.core.util.LogSanitizer;
import cn.ypbin.starter.tracking.support.BoundedEventQueue;
import cn.ypbin.starter.tracking.support.TrackCounters;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 埋点采集门面：业务代码、切面与适配器侧唯一的写入入口。
 *
 * <p><strong>契约：本类的任何方法都不会向调用方抛出异常。</strong> 埋点是旁路能力，
 * 采集侧故障不允许中断业务事务；出错只记录计数与完整堆栈（不静默吞掉）。</p>
 *
 * <p><strong>未登记事件码在此处统一拒绝</strong>：无论事件来自采集端点、{@code @Tracked} 切面
 * 还是 IoT 适配器装饰器，都在唯一写入口做一次登记校验——把校验放在入口而不是各调用方，
 * 才能保证「目录里没有的事件码永远不会进入存储」这一不变量。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
public class TrackRecorder {

    private static final Logger log = LoggerFactory.getLogger(TrackRecorder.class);

    private final BoundedEventQueue queue;

    private final TrackCounters counters;

    private final TrackingEventCatalog catalog;

    /**
     * 创建采集门面。
     *
     * @param queue    有界队列
     * @param counters 计数器
     * @param catalog  事件目录（用于统一拒绝未登记事件码）
     */
    public TrackRecorder(BoundedEventQueue queue, TrackCounters counters, TrackingEventCatalog catalog) {
        this.queue = queue;
        this.counters = counters;
        this.catalog = catalog;
    }

    /**
     * 记录一条事件。
     *
     * @param event 事件
     */
    public void record(TrackEvent event) {
        record(List.of(event));
    }

    /**
     * 批量记录事件；队列满时按「丢新」处理并计入丢弃计数。
     *
     * @param events 事件列表
     * @return 实际入队条数
     */
    public int record(List<TrackEvent> events) {
        if (events.isEmpty()) {
            return 0;
        }
        try {
            int accepted = 0;
            int unregistered = 0;
            @Nullable String sampleCode = null;
            for (TrackEvent event : events) {
                if (!catalog.isRegistered(event.eventCode())) {
                    unregistered++;
                    if (sampleCode == null) {
                        sampleCode = event.eventCode();
                    }
                    continue;
                }
                if (queue.offer(event)) {
                    accepted++;
                }
            }
            if (unregistered > 0) {
                counters.rejected(TrackRejectionReason.UNREGISTERED, unregistered);
                log.warn("[ypbin-starter] {} tracking event(s) rejected: code not registered in catalog, e.g. {}",
                    unregistered, LogSanitizer.sanitize(sampleCode));
            }
            int dropped = events.size() - accepted - unregistered;
            counters.accepted(accepted);
            if (dropped > 0) {
                counters.droppedOnQueueFull(dropped);
                log.warn("[ypbin-starter] tracking queue is full, {} of {} event(s) dropped.", dropped, events.size());
            }
            return accepted;
        } catch (RuntimeException ex) {
            // 门面契约：绝不向上抛；但也不静默——必须留完整堆栈
            log.error("[ypbin-starter] failed to enqueue tracking event(s), {} dropped.", events.size(), ex);
            counters.droppedOnQueueFull(events.size());
            return 0;
        }
    }
}
