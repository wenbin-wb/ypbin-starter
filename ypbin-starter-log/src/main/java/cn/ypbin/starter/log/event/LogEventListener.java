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
package cn.ypbin.starter.log.event;

import cn.ypbin.starter.log.dao.LogDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

/**
 * 操作日志事件监听器。
 *
 * <p>异步消费 {@link LogEvent} 并交由 {@link LogDao} 持久化。{@code @Async} 确保写日志
 * 在独立线程执行，不占用业务请求线程，即使持久化耗时或抛错也不影响主干业务。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class LogEventListener {

    private static final Logger log = LoggerFactory.getLogger(LogEventListener.class);

    private final LogDao logDao;

    public LogEventListener(LogDao logDao) {
        this.logDao = logDao;
    }

    @Async
    @EventListener
    public void onLogEvent(LogEvent event) {
        try {
            logDao.add(event.getLogRecord());
        } catch (Exception e) {
            log.warn("[ypbin-starter] operation log persist failed: {}", e.getMessage());
        }
    }
}
