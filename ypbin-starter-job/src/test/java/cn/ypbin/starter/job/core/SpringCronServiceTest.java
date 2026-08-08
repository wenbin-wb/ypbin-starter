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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link SpringCronService} 测试。
 *
 * @author wenbin
 * @since 2026-08-07
 */
class SpringCronServiceTest {

    private final CronService cronService = new SpringCronService();

    @Test
    void acceptsSpringSixFieldAndAdvancedExpressions() {
        List<String> expressions = List.of(
            "0 0 9 * * MON-FRI",
            "0 0 0 L * ?",
            "0 0 0 15W * ?",
            "0 0 0 ? * MON#2",
            "@hourly");

        expressions.forEach(cronService::validate);
    }

    @Test
    void rejectsBlankAndInvalidExpressions() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> cronService.validate(" "))
            .withMessage("Cron 表达式不能为空");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> cronService.validate("0 0 25 * * ?"))
            .withMessageContaining("Cron 表达式不合法");
    }

    @Test
    void calculatesNextExecutionTimesWithOriginalZone() {
        ZonedDateTime start = ZonedDateTime.of(2026, 8, 7, 8, 30, 0, 0, ZoneId.of("Asia/Shanghai"));

        List<ZonedDateTime> result = cronService.nextExecutionTimes("0 0 9 * * MON-FRI", start, 3);

        assertThat(result).containsExactly(
            ZonedDateTime.of(2026, 8, 7, 9, 0, 0, 0, ZoneId.of("Asia/Shanghai")),
            ZonedDateTime.of(2026, 8, 10, 9, 0, 0, 0, ZoneId.of("Asia/Shanghai")),
            ZonedDateTime.of(2026, 8, 11, 9, 0, 0, 0, ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void validatesPreviewArguments() {
        ZonedDateTime start = ZonedDateTime.now();

        assertThatIllegalArgumentException()
            .isThrownBy(() -> cronService.nextExecutionTimes("@hourly", null, 5))
            .withMessage("Cron 起算时间不能为空");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> cronService.nextExecutionTimes("@hourly", start, 0))
            .withMessageContaining("1 到 100");
        assertThat(cronService.nextExecutionTimes("@hourly", start, 1)).hasSize(1);
    }
}
