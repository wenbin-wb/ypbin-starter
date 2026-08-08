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
import java.util.ArrayList;
import java.util.List;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.StringUtils;

/**
 * 基于 Spring {@link CronExpression} 的 Cron 服务。
 *
 * @author wenbin
 * @since 2026-08-07
 */
public class SpringCronService implements CronService {

    private static final int MAX_PREVIEW_COUNT = 100;

    @Override
    public void validate(String expression) {
        parse(expression);
    }

    @Override
    public List<ZonedDateTime> nextExecutionTimes(String expression, ZonedDateTime start, int count) {
        if (start == null) {
            throw new IllegalArgumentException("Cron 起算时间不能为空");
        }
        if (count < 1 || count > MAX_PREVIEW_COUNT) {
            throw new IllegalArgumentException("Cron 预览数量必须在 1 到 " + MAX_PREVIEW_COUNT + " 之间");
        }
        CronExpression cronExpression = parse(expression);
        List<ZonedDateTime> result = new ArrayList<>(count);
        ZonedDateTime cursor = start;
        for (int i = 0; i < count; i++) {
            cursor = cronExpression.next(cursor);
            if (cursor == null) {
                break;
            }
            result.add(cursor);
        }
        return List.copyOf(result);
    }

    private CronExpression parse(String expression) {
        if (!StringUtils.hasText(expression)) {
            throw new IllegalArgumentException("Cron 表达式不能为空");
        }
        try {
            return CronExpression.parse(expression.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cron 表达式不合法：" + e.getMessage(), e);
        }
    }
}
