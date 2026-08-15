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
package cn.ypbin.starter.messaging.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * {@link InMemorySseTicketStore} 单元测试。
 *
 * @author wenbin
 * @since 2026-08-03
 */
class InMemorySseTicketStoreTest {

    private final InMemorySseTicketStore store = new InMemorySseTicketStore();

    @Test
    void consume_returnsBoundUser_thenInvalidatesTicket() {
        store.save("t1", "1001", Duration.ofSeconds(30));

        assertThat(store.consume("t1")).contains("1001");
        // 一次性：二次消费失效
        assertThat(store.consume("t1")).isEmpty();
    }

    @Test
    void consume_returnsEmpty_whenExpired() {
        store.save("t2", "1002", Duration.ofMillis(-1));
        assertThat(store.consume("t2")).isEmpty();
    }

    @Test
    void consume_returnsEmpty_forUnknownOrBlankTicket() {
        assertThat(store.consume("nope")).isEmpty();
        assertThat(store.consume("")).isEmpty();
        assertThat(store.consume(null)).isEmpty();
    }

    @Test
    void consume_isAtomic_onlyOneWinnerUnderConcurrency() throws Exception {
        store.save("race", "1003", Duration.ofSeconds(30));

        int threads = 16;
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Callable<Optional<String>>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                tasks.add(() -> store.consume("race"));
            }
            List<Future<Optional<String>>> results = pool.invokeAll(tasks);
            long winners = 0;
            for (Future<Optional<String>> f : results) {
                if (f.get().isPresent()) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
