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
package cn.ypbin.starter.log.spi;

import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.model.LogRecord;
import org.jspecify.annotations.Nullable;

/**
 * 操作日志「远端上报」窄端口：把一条采集完成的 {@link LogRecord} 交给宿主自己的传输实现。
 *
 * <p><strong>为什么需要这个端口</strong>：微服务形态下通常只有 system 服务有 {@code sys_log} 数据源，
 * auth/ai 等下游服务没有本地落库能力。若它们不提供 {@code LogDao}，starter 会装配只打印日志的
 * {@code DefaultLogDao}——操作日志<strong>全部丢失且不报错</strong>，是最难排查的一类静默失效。
 * 本端口把「怎么发出去」与「日志模块如何装配」解耦：宿主只需写一个适配 Bean（通常一行方法引用），
 * 不必再自己读 starter 的自动配置装配顺序、也不必自己写
 * {@code @AutoConfiguration(beforeName = "...starter 内部类")}——那段曾经指向 starter 内部类名的知识
 * 一旦写错就会静默退回默认实现。</p>
 *
 * <p><strong>为什么不放在 {@code ypbin-starter-cloud-core}</strong>：本端口被 {@code ypbin-starter-log}
 * 的默认 {@code RemoteLogDao} 与自动配置直接使用。若把端口放进 L3 的 cloud-core，
 * L1 的 log 模块就得反向依赖 L3，既违反本仓分层铁律（{@code LayeringRulesTest} 会在字节码级别判违规），
 * 也会让「只想用操作日志」的宿主被迫拖入 Spring Cloud。端口本身不含任何传输技术栈
 * （无 Feign、无 HTTP 类型），放在 log 模块即可被两类宿主共用。</p>
 *
 * <p><strong>契约</strong>：</p>
 * <ul>
 *     <li>返回值用 starter 自己的 {@link R}（{@code ypbin-starter-core} 提供），<strong>不引入任何业务仓的
 *     结果类型</strong>；业务仓的 Feign 客户端若也返回 {@code R<Void>}，可直接写成
 *     {@code return systemClient::ingestLog;} 完成适配；</li>
 *     <li><strong>传输层异常必须上抛</strong>（连接/读取超时、熔断、解码失败）：由 {@code RemoteLogDao}
 *     记完整堆栈后原样上抛，不允许在本接口实现里静默吞掉；</li>
 *     <li>业务失败通过 {@code R.isSuccess() == false}（或返回 {@code null}）表达，同样会被识别为失败；
 *     实现<strong>不应</strong>把失败包装成 {@code R.ok()}。</li>
 * </ul>
 *
 * <p>调用线程：本接口由 {@code LogEventListener} 在异步线程调用（{@code @Async}），
 * 允许阻塞一次远程往返；请为该远程调用显式配置连接与读取超时（远程调用防失控是仓库红线）。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
@FunctionalInterface
public interface RemoteLogIngestClient {

    /**
     * 上报一条操作日志。
     *
     * <p>允许返回 {@code null}（部分 Feign 实现在空响应体时返回 null），调用方会把它按失败处理；
     * 实现方不必为了「非空」而伪造一个成功的 {@code R}。</p>
     *
     * @param record 采集完成的日志记录
     * @return 远端统一响应；传输异常应直接抛出，业务失败用失败 {@link R} 表达
     */
    @Nullable R<Void> ingestLog(LogRecord record);
}
