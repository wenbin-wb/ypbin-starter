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
package cn.ypbin.starter.log.autoconfigure;

import cn.ypbin.starter.log.dao.LogDao;
import cn.ypbin.starter.log.dao.RemoteLogDao;
import cn.ypbin.starter.log.spi.RemoteLogIngestClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 操作日志远端上报自动配置：宿主提供 {@link RemoteLogIngestClient} 时装配远端 {@link LogDao}。
 *
 * <p><strong>宿主侧只需要两件事</strong>：① 引入本模块；② 把自家的远程客户端适配成
 * {@link RemoteLogIngestClient} Bean（通常是一行方法引用）：</p>
 *
 * <pre>{@code
 * @Bean
 * RemoteLogIngestClient remoteLogIngestClient(ISystemClient systemClient) {
 *     return systemClient::ingestLog;
 * }
 * }</pre>
 *
 * <p><strong>为什么宿主不再需要写 {@code beforeName}</strong>：{@link LogAutoConfiguration#logDao()} 的
 * {@code @ConditionalOnMissingBean} 只有在「远端实现先注册」时才会退让。本类因此显式声明
 * {@link AutoConfigureBefore}（{@code @AutoConfigureBefore(LogAutoConfiguration.class)}）——
 * 装配顺序知识留在 starter 内部，宿主写错这种内部类名的风险随之消失
 * （历史形态：{@code @AutoConfiguration(beforeName = "cn.ypbin.starter.log.autoconfigure.LogAutoConfiguration")}
 * 一旦拼错，宿主得到的是静默退回 {@code DefaultLogDao}）。</p>
 *
 * <p><strong>对宿主注册方式的唯一要求</strong>：{@link RemoteLogIngestClient} Bean 必须由<strong>宿主应用或
 * 普通 {@code @Configuration}</strong> 注册（这类 Bean 定义先于自动配置处理完成，{@code @ConditionalOnBean}
 * 才看得见）。若宿主是在<em>另一个自动配置类</em>里注册它，则需在该自动配置上声明
 * {@code @AutoConfigureBefore(RemoteLogAutoConfiguration.class)}（或给本类加对应的
 * {@code @AutoConfigureAfter}），否则条件会先于该 Bean 求值而恒假，静默退回 {@code DefaultLogDao}
 * ——这正是本仓 {@code AutoConfigurationOrderingTest} 把守的装配顺序陷阱（PITFALL 1）。
 * 也就是说：<strong>顺序知识仍在 starter 内部，宿主不需要写任何 {@code beforeName}</strong>，
 * 只需按常规方式注册 Bean。</p>
 *
 * <p><strong>可覆盖语义</strong>：宿主若自己提供 {@link LogDao}，本类的
 * {@code @ConditionalOnMissingBean} 与 {@link LogAutoConfiguration#logDao()} 一样退让；
 * 反过来宿主只想打印、不想上报时，不提供 {@link RemoteLogIngestClient} 即可，本类整块不生效。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
@AutoConfiguration
@AutoConfigureBefore(LogAutoConfiguration.class)
@ConditionalOnBean(RemoteLogIngestClient.class)
@ConditionalOnProperty(prefix = LogProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class RemoteLogAutoConfiguration {

    /**
     * 远端日志持久化实现：把日志交给宿主的 {@link RemoteLogIngestClient} 上报。
     *
     * <p>本 Bean 先于 {@link LogAutoConfiguration#logDao()} 求值，因此后者的
     * {@code @ConditionalOnMissingBean} 会看到本 Bean 并退让——宿主无需任何顺序配置。</p>
     *
     * @param ingestClient 宿主提供的上报端口
     * @return 远端日志持久化实现
     */
    @Bean
    @ConditionalOnMissingBean(LogDao.class)
    public LogDao remoteLogDao(RemoteLogIngestClient ingestClient) {
        return new RemoteLogDao(ingestClient);
    }
}
