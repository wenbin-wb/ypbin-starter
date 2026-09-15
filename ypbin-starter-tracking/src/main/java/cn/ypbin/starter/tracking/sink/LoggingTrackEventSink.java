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
package cn.ypbin.starter.tracking.sink;

import cn.ypbin.starter.core.util.LogSanitizer;
import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackEventSink;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认埋点持久化实现：打印到应用日志。
 *
 * <p>零配置即可看到事件，适合开发调试。<strong>它不是落库实现</strong>——宿主应覆盖
 * {@link TrackEventSink} Bean 落库；未覆盖时装配期会打印一次 WARN 提示（避免「看起来配好了、
 * 其实没有落库」的静默降级）。</p>
 *
 * <p><strong>刻意不打印 payload</strong>：属性表可能含用户可控内容，打印会同时带来日志噪音与
 * 日志注入面；日志只输出事件码与去重键，且经 {@link LogSanitizer} 处理。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
public class LoggingTrackEventSink implements TrackEventSink {

    private static final Logger log = LoggerFactory.getLogger(LoggingTrackEventSink.class);

    @Override
    public void write(List<TrackEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        log.info("[ypbin-starter] tracking sink is not configured, {} event(s) written to log only.", events.size());
        for (TrackEvent event : events) {
            log.debug("[ypbin-starter] tracking event: code={}, eventId={}",
                LogSanitizer.sanitize(event.eventCode()), LogSanitizer.sanitize(event.eventId()));
        }
    }
}
