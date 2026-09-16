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
package cn.ypbin.starter.json.dict;

import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字典缓存。
 *
 * <p>包装 {@link DictProvider}，按字典类型缓存字典项与 value→label 映射，避免每次翻译都回源。
 * 缓存策略与引用翻译缓存保持一致——带 TTL 过期与容量上限，配置项为
 * {@code ypbin.json.dict.ttl-seconds} 与 {@code ypbin.json.dict.max-size}：</p>
 * <ul>
 *     <li><b>TTL 过期</b>：条目写入时记账到期时间戳，读取时惰性判定（{@code now >= expireAt} 即视为过期）；
 *     默认 5 分钟，即<b>多实例部署下单个实例的字典文案最长陈旧时间</b>。</li>
 *     <li><b>容量上限</b>：缓存的不同字典类型数达到 {@code max-size} 时，先清理已过期条目，
 *     仍满则淘汰最早到期的一条（同一 TTL 下近似 FIFO），保证新字典类型依旧可被缓存。</li>
 *     <li><b>0 值即关闭缓存</b>：{@code ttl-seconds <= 0} 或 {@code max-size <= 0} 时完全不缓存
 *     （每次读取都回源，{@link #refresh()} 成为空操作）。这与引用翻译缓存
 *     {@code ypbin.json.ref-text.ttl-seconds}/{@code max-size} 的 0 值语义一致；
 *     <b>注意没有「永不过期」开关</b>——需要长缓存请给一个很大的 TTL，不要用 0（0 会让回源频率
 *     与请求量同阶）。</li>
 *     <li><b>同类型单飞</b>：同一 dictType 的装载走 {@code ConcurrentHashMap#compute}，
 *     并发下只回源一次；代价是同一桶内其它键的装载会被桶级锁阻塞，阻塞时长等于回源耗时
 *     （{@code ConcurrentHashMap} 的固有行为），故回源实现应保持轻量。</li>
 * </ul>
 *
 * <p><b>刷新语义（重要）</b>：{@link #refresh()} 与 {@link #refresh(String)} 只清空<b>当前 JVM</b> 的本地缓存，
 * 不会通知其它实例。多实例部署下，其它实例的字典文案陈旧时间取决于 TTL（或各实例自身的刷新调用），
 * 不存在「维护一次、全实例即时生效」。本模块不引入 Redis / 消息广播等外部依赖；若业务要求
 * 「改完即刻全实例生效」，需由业务方自行广播（如 MQ、配置中心推送），并在收到广播的实例上调用
 * {@code DictUtils.refresh(dictType)}。</p>
 *
 * <p>2026-09-16：新增 TTL 与容量上限（对齐引用翻译缓存），并明确 refresh 仅作用于当前 JVM。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class DictCache {

    /** 缓存值：字典项 + value→label 映射 + 到期时间戳 */
    private record Entry(List<DictItem> items, Map<String, String> labels, long expireAt) {
    }

    /** 默认 TTL（秒）：与配置项 {@code ypbin.json.dict.ttl-seconds} 默认值一致 */
    public static final long DEFAULT_TTL_SECONDS = 300L;

    /** 默认容量上限（不同字典类型数）：与配置项 {@code ypbin.json.dict.max-size} 默认值一致 */
    public static final int DEFAULT_MAX_SIZE = 10000;

    private final DictProvider provider;
    private final long ttlMillis;
    private final int maxSize;
    private final Clock clock;

    /** TTL 与容量上限均 > 0 才启用缓存；任一为 0/负数即整体关闭（与引用翻译缓存的 0 值语义一致） */
    private final boolean cachingEnabled;

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    /**
     * 使用默认 TTL 与容量上限构造（与配置项默认值一致）。
     *
     * @param provider 字典数据来源
     */
    public DictCache(DictProvider provider) {
        this(provider, DEFAULT_TTL_SECONDS, DEFAULT_MAX_SIZE);
    }

    /**
     * 指定 TTL 与容量上限构造（使用系统时钟）。
     *
     * @param provider   字典数据来源
     * @param ttlSeconds 缓存有效期（秒）；{@code <= 0} 表示关闭缓存（每次回源）
     * @param maxSize    容量上限（不同字典类型数）；{@code <= 0} 表示关闭缓存（每次回源）
     */
    public DictCache(DictProvider provider, long ttlSeconds, int maxSize) {
        this(provider, ttlSeconds, maxSize, Clock.systemUTC());
    }

    /**
     * 指定 TTL、容量上限与时钟构造（时钟可注入，便于对 TTL 边界做确定性测试）。
     *
     * @param provider   字典数据来源
     * @param ttlSeconds 缓存有效期（秒）；{@code <= 0} 表示关闭缓存（每次回源）
     * @param maxSize    容量上限（不同字典类型数）；{@code <= 0} 表示关闭缓存（每次回源）
     * @param clock      时钟
     */
    public DictCache(DictProvider provider, long ttlSeconds, int maxSize, Clock clock) {
        this.provider = provider;
        // 过大导致毫秒溢出时显式失败（Duration#toMillis 抛 ArithmeticException），不静默截断
        this.ttlMillis = ttlSeconds > 0 ? Duration.ofSeconds(ttlSeconds).toMillis() : 0L;
        this.maxSize = maxSize;
        this.cachingEnabled = this.ttlMillis > 0 && maxSize > 0;
        this.clock = clock;
    }

    /**
     * 获取字典项列表（带缓存，过期后自动重新装载）。
     *
     * @param dictType 字典类型
     * @return 字典项列表
     */
    public List<DictItem> getItems(String dictType) {
        if (dictType == null || dictType.isBlank()) {
            return List.of();
        }
        return resolve(dictType).items();
    }

    /**
     * 将字典值翻译为展示文本（带缓存，过期后自动重新装载）。
     *
     * @param dictType 字典类型
     * @param value    字典值（code）
     * @return 展示文本；无匹配时返回原值
     */
    public String translate(String dictType, String value) {
        if (dictType == null || dictType.isBlank() || value == null) {
            return value;
        }
        return resolve(dictType).labels().getOrDefault(value, value);
    }

    /**
     * 清空全部字典缓存。
     *
     * <p><b>只作用于当前 JVM</b>：其它实例的本地缓存不受影响，多实例下需依赖 TTL 兜底或外部广播，
     * 详见类级 Javadoc 的「刷新语义」。</p>
     */
    public void refresh() {
        cache.clear();
    }

    /**
     * 清空指定字典类型的缓存。
     *
     * <p><b>只作用于当前 JVM</b>：其它实例的本地缓存不受影响，多实例下需依赖 TTL 兜底或外部广播，
     * 详见类级 Javadoc 的「刷新语义」。</p>
     *
     * @param dictType 字典类型
     */
    public void refresh(String dictType) {
        if (dictType != null) {
            cache.remove(dictType);
        }
    }

    /**
     * 取缓存条目：命中且未过期直接返回，否则装载（同一 dictType 并发只回源一次）。
     */
    private Entry resolve(String dictType) {
        long now = clock.millis();
        if (!cachingEnabled) {
            // ttl 或 maxSize 为 0/负数：整体关闭缓存，每次读取都回源
            return load(dictType, now);
        }
        Entry cached = cache.get(dictType);
        if (cached != null && !isExpired(cached, now)) {
            return cached;
        }
        evictIfNeeded(dictType, now);
        return cache.compute(dictType, (key, existing) -> {
            long current = clock.millis();
            // 并发下其它线程可能已装载完成：不重复回源
            if (existing != null && !isExpired(existing, current)) {
                return existing;
            }
            return load(key, current);
        });
    }

    /**
     * 容量保护：达到上限且当前 dictType 未在缓存中时，先清理过期条目，仍满则淘汰最早到期的一条。
     *
     * <p>淘汰在 {@code compute} 之外完成——{@code ConcurrentHashMap} 不允许在映射函数内修改自身；
     * 因此并发下容量是<b>软约束</b>（极端并发可能短暂超过上限，不会无界增长）。</p>
     */
    private void evictIfNeeded(String dictType, long now) {
        if (cache.size() < maxSize || cache.containsKey(dictType)) {
            return;
        }
        cache.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
        if (cache.size() < maxSize) {
            return;
        }
        String victim = null;
        long earliest = Long.MAX_VALUE;
        for (Map.Entry<String, Entry> entry : cache.entrySet()) {
            long expireAt = entry.getValue().expireAt();
            if (expireAt < earliest) {
                earliest = expireAt;
                victim = entry.getKey();
            }
        }
        if (victim != null) {
            cache.remove(victim);
        }
    }

    private Entry load(String dictType, long now) {
        List<DictItem> items = provider.getItems(dictType);
        List<DictItem> safeItems = items == null ? List.of() : List.copyOf(items);
        Map<String, String> labels = new LinkedHashMap<>();
        for (DictItem item : safeItems) {
            if (item.getValue() != null) {
                labels.put(item.getValue(), item.getLabel());
            }
        }
        return new Entry(safeItems, Collections.unmodifiableMap(labels), expireAt(now));
    }

    /** 到期时间戳；加法溢出时钳到 {@link Long#MAX_VALUE}（不静默回绕成过去时间） */
    private long expireAt(long now) {
        return ttlMillis > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + ttlMillis;
    }

    /** 是否过期：正好到期（now == expireAt）即视为过期 */
    private boolean isExpired(Entry entry, long now) {
        return now >= entry.expireAt();
    }
}
