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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * {@link DictCache} 的 TTL / 容量上限 / 刷新语义测试。
 *
 * <p>时钟可注入（{@link MutableClock}），所有 TTL 边界用例直接推进毫秒，不使用 sleep，
 * 因此结果确定、不随机器负载抖动。</p>
 *
 * <p>「刷新只作用于当前 JVM」在单进程单测里只能验证到「只清它绑定的那个 cache 实例」
 * （{@link #refreshOnlyAffectsCurrentJvmInstance} / {@link #dictUtilsRefreshOnlyClearsBoundInstance}）；
 * 真正的跨进程传播无法在本模块内断言。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class DictCacheTest {

    /** 可控时钟：起点固定，测试自行推进毫秒 */
    static final class MutableClock extends Clock {

        private final AtomicLong millis = new AtomicLong(1_700_000_000_000L);

        void advance(long deltaMillis) {
            millis.addAndGet(deltaMillis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }

        @Override
        public long millis() {
            return millis.get();
        }
    }

    /** 可改数据源 + 回源次数计数，用于判定是否真的重新装载；可选闸门用于并发用例 */
    static final class CountingProvider implements DictProvider {

        private final AtomicInteger loadCount = new AtomicInteger();
        private final Map<String, List<DictItem>> data = new HashMap<>();

        /** 闸门：开启后首次回源会阻塞，直到测试显式放行 */
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile boolean gated;

        @Override
        public List<DictItem> getItems(String dictType) {
            loadCount.incrementAndGet();
            if (gated) {
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            return data.getOrDefault(dictType, List.of());
        }

        void enableGate() {
            gated = true;
        }

        boolean awaitFirstLoadStarted(long timeout, TimeUnit unit) throws InterruptedException {
            return entered.await(timeout, unit);
        }

        void releaseGate() {
            release.countDown();
        }

        void put(String dictType, String value, String label) {
            data.put(dictType, List.of(new DictItem(value, label)));
        }

        int loadCount() {
            return loadCount.get();
        }
    }

    private final MutableClock clock = new MutableClock();
    private final CountingProvider provider = new CountingProvider();

    @Test
    void notExpiredHitsCacheAndReloadsOnlyOnce() {
        provider.put("gender", "1", "男");
        DictCache cache = new DictCache(provider, 60L, 100, clock);

        assertThat(cache.translate("gender", "1")).isEqualTo("男");
        assertThat(cache.translate("gender", "1")).isEqualTo("男");
        assertThat(cache.getItems("gender")).hasSize(1);
        assertThat(provider.loadCount()).isEqualTo(1);

        // 差一毫秒到期：仍命中缓存，不回源
        clock.advance(59_999L);
        assertThat(cache.translate("gender", "1")).isEqualTo("男");
        assertThat(provider.loadCount()).isEqualTo(1);
    }

    @Test
    void expiredAtExactBoundaryReloadsAndSeesNewData() {
        provider.put("gender", "1", "男");
        DictCache cache = new DictCache(provider, 60L, 100, clock);
        assertThat(cache.translate("gender", "1")).isEqualTo("男");
        assertThat(provider.loadCount()).isEqualTo(1);

        // 字典维护后 provider 侧数据已变化
        provider.put("gender", "1", "男性");
        // 正好到期即视为过期（与引用翻译缓存一致的 >= 语义）
        clock.advance(60_000L);

        assertThat(cache.translate("gender", "1")).isEqualTo("男性");
        assertThat(cache.getItems("gender")).hasSize(1);
        assertThat(provider.loadCount()).isEqualTo(2);
    }

    @Test
    void nonPositiveTtlDisablesCache() {
        provider.put("gender", "1", "男");
        DictCache cache = new DictCache(provider, 0L, 100, clock);

        assertThat(cache.translate("gender", "1")).isEqualTo("男");
        clock.advance(1L);
        assertThat(cache.translate("gender", "1")).isEqualTo("男");
        // ttl <= 0 = 关闭缓存：每次读取都回源（与 ref-text.ttl-seconds=0 的语义一致）
        assertThat(provider.loadCount()).isEqualTo(2);
        assertThat(cache.getItems("gender")).hasSize(1);
        assertThat(provider.loadCount()).isEqualTo(3);
    }

    @Test
    void hugeTtlClampsExpiryInsteadOfOverflowing() {
        provider.put("gender", "1", "男");
        // ttl 毫秒数接近 Long.MAX_VALUE：到期时间戳必须钳位，不能回绕成过去时间而立即过期
        DictCache cache = new DictCache(provider, 9_223_372_036_854_775L, 100, clock);

        assertThat(cache.getItems("gender")).hasSize(1);
        clock.advance(365L * 24 * 60 * 60 * 1000);
        assertThat(cache.getItems("gender")).hasSize(1);
        assertThat(provider.loadCount()).isEqualTo(1);
    }

    @Test
    void oversizedTtlFailsFastInsteadOfSilentTruncation() {
        // 秒数大到转毫秒溢出：显式失败（不静默截断成另一个 TTL）
        assertThatThrownBy(() -> new DictCache(provider, Long.MAX_VALUE, 100, clock))
            .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void maxSizeEvictsSoonestExpiringEntry() {
        provider.put("a", "1", "A");
        provider.put("b", "1", "B");
        DictCache cache = new DictCache(provider, 600L, 2, clock);

        cache.getItems("a");
        clock.advance(1L); // b 比 a 晚到期一毫秒 → 容量压力下先淘汰 a
        cache.getItems("b");
        assertThat(provider.loadCount()).isEqualTo(2);

        cache.getItems("c"); // 达上限：淘汰 a
        assertThat(provider.loadCount()).isEqualTo(3);

        cache.getItems("b"); // b 仍在缓存
        assertThat(provider.loadCount()).isEqualTo(3);
        cache.getItems("a"); // a 已被淘汰 → 回源
        assertThat(provider.loadCount()).isEqualTo(4);
    }

    @Test
    void maxSizePurgesExpiredBeforeEvictingAliveEntry() {
        provider.put("a", "1", "A");
        provider.put("b", "1", "B");
        provider.put("c", "1", "C");
        // TTL = 1 秒，下面推进 1.5 秒确保 a 真的过期（否则走的是淘汰分支，覆盖不到"先清过期"）
        DictCache cache = new DictCache(provider, 1L, 2, clock);

        cache.getItems("a");
        clock.advance(1_500L); // a 已过期
        cache.getItems("b"); // 未过期项，不应被容量淘汰
        assertThat(provider.loadCount()).isEqualTo(2);

        cache.getItems("c"); // 达上限：先清理过期的 a（无需淘汰未过期的 b）
        assertThat(provider.loadCount()).isEqualTo(3);
        cache.getItems("b");
        assertThat(provider.loadCount()).isEqualTo(3); // b 仍在
        cache.getItems("a"); // a 已被清理 → 回源
        assertThat(provider.loadCount()).isEqualTo(4);
    }

    @Test
    void nonPositiveMaxSizeDisablesCaching() {
        provider.put("a", "1", "A");
        provider.put("b", "1", "B");
        provider.put("c", "1", "C");
        DictCache cache = new DictCache(provider, 600L, 0, clock);

        cache.getItems("a");
        cache.getItems("b");
        cache.getItems("c");
        cache.getItems("a");
        cache.getItems("b");
        cache.getItems("c");
        // max-size <= 0 = 关闭缓存：6 次读取全部回源
        assertThat(provider.loadCount()).isEqualTo(6);
    }

    @Test
    void refreshOnlyAffectsCurrentJvmInstance() {
        provider.put("gender", "1", "男");
        DictCache first = new DictCache(provider, 600L, 100, clock);
        DictCache second = new DictCache(provider, 600L, 100, clock);

        first.translate("gender", "1");
        second.translate("gender", "1");
        assertThat(provider.loadCount()).isEqualTo(2);

        // 只在 first 上刷新（模拟「仅当前 JVM 收到刷新」）
        first.refresh();

        first.translate("gender", "1");
        assertThat(provider.loadCount()).isEqualTo(3);
        second.translate("gender", "1");
        // 第二个实例的缓存不受影响，未回源
        assertThat(provider.loadCount()).isEqualTo(3);
    }

    /**
     * 静态门面 {@code DictUtils.refresh()} 只清它绑定的那个 cache 实例——这是「刷新只作用于当前 JVM」
     * 在单测里可验证的部分；真正的跨 JVM 传播无法在单进程内断言（见类 Javadoc 的说明）。
     */
    @Test
    void dictUtilsRefreshOnlyClearsBoundInstance() throws Exception {
        provider.put("gender", "1", "男");
        DictCache bound = new DictCache(provider, 600L, 100, clock);
        DictCache other = new DictCache(provider, 600L, 100, clock);
        DictUtils.bind(bound);
        try {
            DictUtils.translate("gender", "1");
            other.translate("gender", "1");
            assertThat(provider.loadCount()).isEqualTo(2);

            DictUtils.refresh("gender");
            DictUtils.translate("gender", "1");
            assertThat(provider.loadCount()).isEqualTo(3);
            other.translate("gender", "1");
            assertThat(provider.loadCount()).isEqualTo(3);
        } finally {
            DictUtils.bind(null);
        }
    }

    @Test
    void refreshByTypeOnlyClearsThatType() {
        provider.put("a", "1", "A");
        provider.put("b", "1", "B");
        DictCache cache = new DictCache(provider, 600L, 100, clock);

        cache.getItems("a");
        cache.getItems("b");
        assertThat(provider.loadCount()).isEqualTo(2);

        cache.refresh("a");
        cache.getItems("a");
        assertThat(provider.loadCount()).isEqualTo(3);
        cache.getItems("b");
        // b 未被清除
        assertThat(provider.loadCount()).isEqualTo(3);
    }

    @Test
    void blankDictTypeOrNullValueFallsBackWithoutLoading() {
        DictCache cache = new DictCache(provider, 600L, 100, clock);

        assertThat(cache.getItems(null)).isEmpty();
        assertThat(cache.getItems(" ")).isEmpty();
        assertThat(cache.translate(null, "1")).isEqualTo("1");
        assertThat(cache.translate("gender", null)).isNull();
        assertThat(provider.loadCount()).isZero();
    }

    @Test
    void unknownValueFallsBackToOriginal() {
        provider.put("gender", "1", "男");
        DictCache cache = new DictCache(provider, 600L, 100, clock);

        assertThat(cache.translate("gender", "9")).isEqualTo("9");
    }

    /**
     * 同一 dictType 并发装载只回源一次（单飞）。
     *
     * <p>构造是确定性的、不依赖 sleep 巧合：首个线程进入 provider 后被闸门挡住，其余线程必然排队在
     * {@code ConcurrentHashMap#compute} 的桶锁上（{@link Thread.State#BLOCKED}）——我们等到确实出现
     * 排队线程后才放行，此时放行后的线程若没有"已装载则复用"的双重检查就会再次回源（loadCount > 1）。
     * 因此本用例既能证伪双重检查，也不依赖线程调度的时序巧合。</p>
     */
    @Test
    void concurrentLoadsOfSameTypeHitProviderOnlyOnce() throws Exception {
        provider.put("gender", "1", "男");
        provider.enableGate();
        DictCache cache = new DictCache(provider, 600L, 100, clock);
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    cache.translate("gender", "1");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
            worker.start();
            workers.add(worker);
        }
        try {
            start.countDown();
            assertThat(provider.awaitFirstLoadStarted(5, TimeUnit.SECONDS)).isTrue();
            // 等到确有线程排在桶锁上：没有它，本用例证明不了双重检查
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (blockedCount(workers) == 0 && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertThat(blockedCount(workers))
                .as("未能构造出并发排队：放行后无法验证「已装载则复用」的双重检查")
                .isPositive();
        } finally {
            provider.releaseGate();
            for (Thread worker : workers) {
                worker.join(TimeUnit.SECONDS.toMillis(10));
            }
        }
        // 8 个线程只有 1 次回源
        assertThat(provider.loadCount()).isEqualTo(1);
    }

    private static long blockedCount(List<Thread> workers) {
        return workers.stream().filter(worker -> worker.getState() == Thread.State.BLOCKED).count();
    }
}
