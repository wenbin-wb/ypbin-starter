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
package cn.ypbin.starter.tracking.support;

import cn.ypbin.starter.tracking.core.TrackRejectionReason;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 埋点链路计数器。
 *
 * <p>埋点的语义是「允许丢弃」，因此<strong>丢弃与拒绝必须是可观测的</strong>——没有计数就没人知道数据丢了多少。
 * 计数器由采集端点、队列与消费者共同累加，宿主可读取快照接入自己的监控体系（埋点自身的健康度属于技术指标，
 * 不属于埋点）。</p>
 *
 * <p><strong>事件级拒绝与属性级问题分开计数</strong>：前者表示事件未被接收，后者表示事件被正常接收、
 * 只是丢了个别属性；混在一起会让「接收数 + 拒绝数」的账算不平。</p>
 *
 * <p>刻意用 {@link LongAdder} 而不是加锁计数：累加发生在请求线程与消费者线程上，
 * 需要无争用、非阻塞的实现。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
public class TrackCounters {

    private final LongAdder accepted = new LongAdder();

    private final LongAdder droppedOnQueueFull = new LongAdder();

    private final LongAdder flushed = new LongAdder();

    private final LongAdder flushFailed = new LongAdder();

    /** 事件级拒绝计数；构造期不预填，仅在写入时按需创建（并发安全） */
    private final ConcurrentMap<TrackRejectionReason, LongAdder> rejected = new ConcurrentHashMap<>();

    /** 属性级问题计数 */
    private final ConcurrentMap<TrackRejectionReason, LongAdder> attributeIssues = new ConcurrentHashMap<>();

    /** 记录一批已接收入队的事件数。 */
    public void accepted(long count) {
        if (count > 0L) {
            accepted.add(count);
        }
    }

    /** 记录因队列满被丢弃的事件数。 */
    public void droppedOnQueueFull(long count) {
        if (count > 0L) {
            droppedOnQueueFull.add(count);
        }
    }

    /**
     * 记录因<strong>事件级</strong>原因未被接收的事件数。
     *
     * @param reason 拒绝原因（应为事件级）
     * @param count  条数
     */
    public void rejected(TrackRejectionReason reason, long count) {
        if (count > 0L) {
            rejected.computeIfAbsent(reason, key -> new LongAdder()).add(count);
        }
    }

    /**
     * 记录<strong>属性级</strong>问题条数（事件本身仍被接收）。
     *
     * @param reason 问题原因（应为属性级）
     * @param count  条数
     */
    public void attributeIssue(TrackRejectionReason reason, long count) {
        if (count > 0L) {
            attributeIssues.computeIfAbsent(reason, key -> new LongAdder()).add(count);
        }
    }

    /** 记录成功写入落点的事件数。 */
    public void flushed(long count) {
        if (count > 0L) {
            flushed.add(count);
        }
    }

    /** 记录重试后仍写入失败而被丢弃的事件数。 */
    public void flushFailed(long count) {
        if (count > 0L) {
            flushFailed.add(count);
        }
    }

    /**
     * 取当前计数快照。
     *
     * <p>快照不是同一时点的全局一致视图（各计数独立累加），用于监控与排查足够；不要把
     * {@code accepted} 与 {@code rejected} 的差值当成精确的账。</p>
     *
     * @return 快照（原因只包含计数大于 0 的项，便于直接打点）
     */
    public Snapshot snapshot() {
        Map<String, Long> rejectedByReason = new LinkedHashMap<>();
        long rejectedTotal = 0L;
        List<Map.Entry<TrackRejectionReason, LongAdder>> ordered = new ArrayList<>(rejected.entrySet());
        ordered.sort(Comparator.comparing(entry -> entry.getKey().getCode()));
        for (Map.Entry<TrackRejectionReason, LongAdder> entry : ordered) {
            long value = entry.getValue().sum();
            rejectedTotal += value;
            if (value > 0L) {
                rejectedByReason.put(entry.getKey().getCode(), value);
            }
        }
        Map<String, Long> attributeIssuesByReason = new LinkedHashMap<>();
        long attributeIssueTotal = 0L;
        List<Map.Entry<TrackRejectionReason, LongAdder>> orderedIssues = new ArrayList<>(attributeIssues.entrySet());
        orderedIssues.sort(Comparator.comparing(entry -> entry.getKey().getCode()));
        for (Map.Entry<TrackRejectionReason, LongAdder> entry : orderedIssues) {
            long value = entry.getValue().sum();
            attributeIssueTotal += value;
            if (value > 0L) {
                attributeIssuesByReason.put(entry.getKey().getCode(), value);
            }
        }
        return new Snapshot(accepted.sum(), droppedOnQueueFull.sum(), flushed.sum(), flushFailed.sum(),
            rejectedTotal, Map.copyOf(rejectedByReason), attributeIssueTotal,
            Map.copyOf(attributeIssuesByReason));
    }

    /**
     * 计数器快照。
     *
     * @param accepted                  已接收入队
     * @param droppedOnQueueFull        队列满丢弃
     * @param flushed                   已写入落点
     * @param flushFailed               写入最终失败
     * @param rejectedTotal             事件级拒绝总数
     * @param rejectedByReason          事件级拒绝按原因分桶（不可变）
     * @param attributeIssueTotal       属性级问题总数（事件仍被接收）
     * @param attributeIssuesByReason   属性级问题按原因分桶（不可变）
     * @author wenbin
     * @since 2026-09-15
     */
    public record Snapshot(long accepted, long droppedOnQueueFull, long flushed, long flushFailed,
                           long rejectedTotal, Map<String, Long> rejectedByReason,
                           long attributeIssueTotal, Map<String, Long> attributeIssuesByReason) {
    }
}
