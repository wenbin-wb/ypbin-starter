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
package cn.ypbin.starter.tools.lock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 可手动推进的测试时钟。
 *
 * <p>租约锁的到期/续期语义全靠时间驱动。用真实 {@code Thread.sleep} 测不仅慢，还会把
 * 「TTL 边界」变成不稳定用例；注入本时钟后可以精确跨过任意时刻，不真等。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
final class MutableClock extends Clock {

    private long millis;

    private final ZoneId zone;

    MutableClock(long initialMillis) {
        this(initialMillis, ZoneOffset.UTC);
    }

    private MutableClock(long initialMillis, ZoneId zone) {
        this.millis = initialMillis;
        this.zone = zone;
    }

    /**
     * 把时钟往前推。
     *
     * @param deltaMillis 推进的毫秒数
     */
    void advance(long deltaMillis) {
        millis += deltaMillis;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(millis, newZone);
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis);
    }

    @Override
    public long millis() {
        return millis;
    }
}
