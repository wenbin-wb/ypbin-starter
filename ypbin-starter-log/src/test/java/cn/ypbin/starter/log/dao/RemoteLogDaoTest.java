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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.model.LogRecord;
import cn.ypbin.starter.log.spi.RemoteLogIngestClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RemoteLogDao} 的上报与失败语义测试。
 *
 * <p>锁定三条既有语义（从业务仓已生产验证的实现上收，不得退化）：</p>
 * <ul>
 *     <li>成功：原样把同一条 {@link LogRecord} 交给上报端口，不做任何字段改名映射；</li>
 *     <li>业务失败（失败 {@link R} 或 {@code null} 响应）：抛异常，不静默 return；</li>
 *     <li>传输异常：<strong>原样重抛</strong>（保留原始栈类型与消息），不在本层包装成别的异常。</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class RemoteLogDaoTest {

    private static LogRecord record() {
        LogRecord logRecord = new LogRecord();
        logRecord.setModule("订单");
        logRecord.setDescription("创建订单");
        logRecord.setRequestUri("/orders");
        logRecord.setSuccess(true);
        return logRecord;
    }

    @Test
    @DisplayName("成功：把同一条记录交给上报端口（不做字段映射）")
    void success_delegatesSameRecord() {
        LogRecord expected = record();
        LogRecord[] captured = new LogRecord[1];
        RemoteLogDao dao = new RemoteLogDao(log -> {
            captured[0] = log;
            return R.ok();
        });

        dao.add(expected);

        assertThat(captured[0]).isSameAs(expected);
    }

    @Test
    @DisplayName("业务失败 R：抛 IllegalStateException，消息含业务码与提示（不静默丢日志）")
    void businessFailure_throws() {
        RemoteLogDao dao = new RemoteLogDao(log -> R.fail(500, "系统服务暂不可用"));

        assertThatThrownBy(() -> dao.add(record()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("code=500")
            .hasMessageContaining("系统服务暂不可用");
    }

    @Test
    @DisplayName("响应为 null：按失败处理并抛异常（不得当成功）")
    void nullResponse_treatedAsFailure() {
        RemoteLogDao dao = new RemoteLogDao(log -> null);

        assertThatThrownBy(() -> dao.add(record()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("响应为空");
    }

    @Test
    @DisplayName("传输异常：原样重抛（保留异常类型与消息，供上层计失败）")
    void transportFailure_propagatesOriginalException() {
        IllegalStateException transport = new IllegalStateException("connect timed out");
        RemoteLogDao dao = new RemoteLogDao(log -> {
            throw transport;
        });

        assertThatThrownBy(() -> dao.add(record())).isSameAs(transport);
    }

    @Test
    @DisplayName("构造器拒绝 null 上报端口（早失败，避免装配出永远抛 NPE 的 Dao）")
    void constructor_rejectsNullClient() {
        assertThatThrownBy(() -> new RemoteLogDao((RemoteLogIngestClient) null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("RemoteLogIngestClient");
    }
}
