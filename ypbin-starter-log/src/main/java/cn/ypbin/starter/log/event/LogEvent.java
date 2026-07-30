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

import cn.ypbin.starter.log.model.LogRecord;
import java.io.Serial;
import java.io.Serializable;

/**
 * 操作日志事件。
 *
 * <p>切面采集完成后发布本事件，由异步监听器消费并持久化，从而将写日志从主干业务的
 * 响应链路中剥离，避免持久化耗时（如数据库抖动）拖慢核心接口。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class LogEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient LogRecord logRecord;

    public LogEvent(LogRecord logRecord) {
        this.logRecord = logRecord;
    }

    public LogRecord getLogRecord() {
        return logRecord;
    }
}
