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

import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.model.LogRecord;
import cn.ypbin.starter.log.spi.RemoteLogIngestClient;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志持久化实现（远端上报）：把采集完成的 {@link LogRecord} 交给宿主的
 * {@link RemoteLogIngestClient} 上报到「有数据源的那个服务」落库。
 *
 * <p><strong>装配时机</strong>：只要宿主提供了 {@code RemoteLogIngestClient} Bean，
 * {@code RemoteLogAutoConfiguration} 就会用本类替换只打印日志的 {@link DefaultLogDao}，
 * 并保证先于 log 模块默认实现求值——宿主<strong>不需要</strong>写任何
 * {@code @AutoConfiguration(beforeName = "...")} 或自己实现 {@code LogDao}。</p>
 *
 * <p><strong>为什么失败必须记完整堆栈并上抛</strong>：远端不可达时若静默 return，
 * 就是「日志没落库且无任何痕迹」的静默降级，属于最难排查的一类故障。这里分两种失败形态：</p>
 * <ul>
 *     <li><strong>传输异常</strong>（连接/读取超时、熔断、响应解码失败）：{@code log.error} 带完整堆栈后
 *     <strong>原样重抛</strong>，保留原始栈；</li>
 *     <li><strong>业务失败</strong>（{@code R.isSuccess() == false} 或响应为 {@code null}）：没有异常对象，
 *     显式构造 {@link IllegalStateException} 以产出可定位的堆栈，记 error 后抛出。</li>
 * </ul>
 * <p>上抛不会波及业务请求：调用方 {@code LogEventListener#onLogEvent} 在异步线程执行并捕获异常
 * （同样带完整堆栈记录）。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public class RemoteLogDao implements LogDao {

    private static final Logger log = LoggerFactory.getLogger(RemoteLogDao.class);

    private final RemoteLogIngestClient ingestClient;

    /**
     * 创建远端上报实现。
     *
     * @param ingestClient 宿主提供的上报端口
     */
    public RemoteLogDao(RemoteLogIngestClient ingestClient) {
        this.ingestClient = Objects.requireNonNull(ingestClient, "RemoteLogIngestClient 不能为空");
    }

    @Override
    public void add(LogRecord logRecord) {
        @Nullable R<Void> result;
        try {
            result = ingestClient.ingestLog(logRecord);
        } catch (RuntimeException ex) {
            // 传输层失败：连接/读取超时、熔断、响应解码异常。记完整堆栈后原样上抛（保留原始栈）
            log.error("[ypbin-starter] 操作日志远端上报失败（传输异常），本条日志未落库: "
                + "module={}, description={}, requestUri={}",
                logRecord.getModule(), logRecord.getDescription(), logRecord.getRequestUri(), ex);
            throw ex;
        }
        if (result == null || !result.isSuccess()) {
            // 业务码失败（内部凭证不匹配、对端落库异常、降级兜底）没有异常对象，显式构造以产出可定位堆栈
            IllegalStateException failure = new IllegalStateException(
                "[ypbin-starter] 操作日志远端上报失败，本条日志未落库: "
                    + "code=" + (result == null ? "null" : result.getCode())
                    + ", message=" + (result == null ? "响应为空" : result.getMessage()));
            log.error("[ypbin-starter] 操作日志远端上报失败（业务码），module={}, description={}, requestUri={}",
                logRecord.getModule(), logRecord.getDescription(), logRecord.getRequestUri(), failure);
            throw failure;
        }
    }
}
