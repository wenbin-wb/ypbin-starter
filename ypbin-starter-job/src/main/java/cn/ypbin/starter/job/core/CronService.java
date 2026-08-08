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
package cn.ypbin.starter.job.core;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Cron 表达式服务。
 *
 * @author wenbin
 * @since 2026-08-07
 */
public interface CronService {

    /**
     * 校验 Spring Cron 表达式。
     *
     * @param expression 表达式
     * @throws IllegalArgumentException 表达式不合法
     */
    void validate(String expression);

    /**
     * 计算后续执行时间。
     *
     * @param expression 表达式
     * @param start 起算时间
     * @param count 数量，范围 1-100
     * @return 后续执行时间
     */
    List<ZonedDateTime> nextExecutionTimes(String expression, ZonedDateTime start, int count);
}
