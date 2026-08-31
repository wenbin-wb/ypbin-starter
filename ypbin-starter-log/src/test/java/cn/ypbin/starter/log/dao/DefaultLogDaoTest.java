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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cn.ypbin.starter.log.event.LogEvent;
import cn.ypbin.starter.log.event.LogEventListener;
import cn.ypbin.starter.log.model.LogRecord;
import org.junit.jupiter.api.Test;

/**
 * 日志落库与事件监听测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class DefaultLogDaoTest {

    @Test
    void addShouldAcceptRecordWithoutError() {
        DefaultLogDao dao = new DefaultLogDao();
        LogRecord record = new LogRecord();
        record.setModule("测试");
        record.setDescription("操作");
        record.setRequestMethod("GET");
        record.setRequestUri("/api");
        record.setStatusCode(200);
        record.setIp("127.0.0.1");
        record.setUserId(1L);
        record.setTimeTakenMillis(5L);
        record.setSuccess(true);
        dao.add(record);
        assertThat(record.getModule()).isEqualTo("测试");
    }

    @Test
    void listenerShouldDelegateToDao() {
        LogDao dao = mock(LogDao.class);
        LogEventListener listener = new LogEventListener(dao);
        LogRecord record = new LogRecord();
        record.setSuccess(false);
        record.setErrorMsg("失败原因");
        listener.onLogEvent(new LogEvent(record));
        verify(dao).add(record);
    }

    @Test
    void listenerShouldSwallowDaoFailure() {
        LogDao dao = mock(LogDao.class);
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
            .when(dao).add(org.mockito.ArgumentMatchers.any());
        LogEventListener listener = new LogEventListener(dao);
        listener.onLogEvent(new LogEvent(new LogRecord()));
        // 不抛异常即通过
        assertThat(listener).isNotNull();
    }
}
