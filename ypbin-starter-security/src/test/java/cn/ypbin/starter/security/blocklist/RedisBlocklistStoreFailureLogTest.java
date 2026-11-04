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
package cn.ypbin.starter.security.blocklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.starter.core.blocklist.BlocklistRuleCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 故障日志降频用例。
 *
 * @author wenbin
 * @since 2026-11-04
 */
class RedisBlocklistStoreFailureLogTest {

    @Test
    @DisplayName("Redis 持续失败时 WARN 在窗口内只出现一次")
    void warnsOncePerWindow() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.hasKey(anyString())).thenThrow(new IllegalStateException("redis down"));
        RedisBlocklistStore store = new RedisBlocklistStore(redis, new BlocklistRuleCodec());
        Logger logger = (Logger) LoggerFactory.getLogger(RedisBlocklistStore.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (int i = 0; i < 100; i++) {
                assertThat(store.isBanned("1.2.3.4")).isFalse();
            }
        } finally {
            logger.detachAppender(appender);
        }

        long warnCount = appender.list.stream().filter(event -> event.getLevel() == Level.WARN).count();
        assertThat(warnCount).isEqualTo(1L);
    }
}
