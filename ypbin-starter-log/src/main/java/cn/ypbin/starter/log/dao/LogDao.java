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

/**
 * 日志持久化扩展点。
 *
 * <p>采集到的 {@link LogRecord} 交由本接口落地。默认实现仅打印到应用日志，
 * 业务方实现本接口即可对接数据库 / ES / MQ 等，通过 {@code @ConditionalOnMissingBean} 覆盖。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@FunctionalInterface
public interface LogDao {

    /**
     * 记录一条操作日志。
     *
     * @param logRecord 日志记录
     */
    void add(LogRecord logRecord);
}
