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
package cn.ypbin.starter.log.dao;

import cn.ypbin.starter.log.model.LogRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认日志持久化实现：打印到应用日志。
 *
 * <p>零配置即可看到操作日志，适合开发调试。生产环境建议实现 {@link LogDao} 落库。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class DefaultLogDao implements LogDao {

    private static final Logger log = LoggerFactory.getLogger("ypbin.access-log");

    @Override
    public void add(LogRecord logRecord) {
        log.info("[操作日志] module={}, desc={}, {} {}, status={}, ip={}, userId={}, took={}ms{}",
            logRecord.getModule(),
            logRecord.getDescription(),
            logRecord.getRequestMethod(),
            logRecord.getRequestUri(),
            logRecord.getStatusCode(),
            logRecord.getIp(),
            logRecord.getUserId(),
            logRecord.getTimeTakenMillis(),
            logRecord.isSuccess() ? "" : ", error=" + logRecord.getErrorMsg());
    }
}
