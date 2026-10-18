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

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.log.dao.DefaultLogDao;
import cn.ypbin.starter.log.dao.LogDao;
import cn.ypbin.starter.log.dao.RemoteLogDao;
import cn.ypbin.starter.log.model.LogRecord;
import cn.ypbin.starter.log.spi.RemoteLogIngestClient;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * {@link RemoteLogAutoConfiguration} 的装配与顺序测试——#100 验收标准 2 的 starter 侧证明。
 *
 * <p><strong>要证明的命题</strong>：宿主只注册一个 {@link RemoteLogIngestClient} Bean、<strong>不写任何
 * {@code beforeName}/顺序注解</strong>，容器里的 {@link LogDao} 就必须是远端实现
 * ({@link RemoteLogDao})，而不是只打印日志的 {@link DefaultLogDao}。这等价于
 * 「{@link RemoteLogAutoConfiguration} 真的先于 {@link LogAutoConfiguration#logDao()} 的
 * {@code @ConditionalOnMissingBean} 求值」。</p>
 *
 * <p><strong>为什么还需要反向对照</strong>：正例全绿也可能是类名字典序巧合（本仓包名 {@code cn.ypbin.*}
 * 在 Boot 排序里先于 {@code org.springframework.*}，同类内按全类名字典序）。
 * {@code RemoteLogAutoConfiguration}（{@code R}）恰好排在 {@code LogAutoConfiguration}（{@code L}）
 * <em>之后</em>，字典序反而对本例不利——但为了让「顺序注解是必需的」这条结论可复现，
 * 仍用 {@link ZzzUnorderedRemoteLogFixture}（去掉顺序注解的孪生配置）做反向对照：它必须让
 * {@link DefaultLogDao} 抢先注册，从而证明正例不是侥幸。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class RemoteLogAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(LogAutoConfiguration.class, RemoteLogAutoConfiguration.class));

    @Test
    @DisplayName("★ 存在 RemoteLogIngestClient Bean：LogDao 装配远端实现（非 DefaultLogDao），宿主无需写 beforeName")
    void withIngestClient_logDaoIsRemoteImplementation() {
        RecordingIngestClient ingestClient = new RecordingIngestClient();

        runner.withBean(RemoteLogIngestClient.class, () -> ingestClient).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LogDao.class);
            assertThat(context.getBean(LogDao.class))
                .as("有上报端口时必须装配远端实现；默认实现会让日志看似记录、实则只进应用日志")
                .isInstanceOf(RemoteLogDao.class);
            assertThat(context.getBean(LogDao.class)).isNotInstanceOf(DefaultLogDao.class);

            // 端到端：写入确实穿透到宿主的上报端口（证明 Bean 真的接线，而不是只有类型对）
            LogRecord record = new LogRecord();
            record.setModule("订单");
            context.getBean(LogDao.class).add(record);
            assertThat(ingestClient.records).containsExactly(record);
        });
    }

    @Test
    @DisplayName("没有 RemoteLogIngestClient Bean：整块配置不生效，LogDao 仍是默认实现（旧行为不变）")
    void withoutIngestClient_keepsDefaultLogDao() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LogDao.class)).isInstanceOf(DefaultLogDao.class);
        });
    }

    @Test
    @DisplayName("ypbin.log.enabled=false：远端配置随之关闭（与 log 模块开关保持一致）")
    void disabledBySwitch() {
        runner.withPropertyValues("ypbin.log.enabled=false")
            .withBean(RemoteLogIngestClient.class, RecordingIngestClient::new)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(LogDao.class);
            });
    }

    @Test
    @DisplayName("宿主自定义 LogDao 优先（可覆盖语义未被破坏）")
    void hostLogDaoWins() {
        LogDao hostDao = record -> {
            // 宿主实现
        };
        runner.withBean(RemoteLogIngestClient.class, RecordingIngestClient::new)
            .withBean(LogDao.class, () -> hostDao)
            .run(context -> assertThat(context.getBean(LogDao.class)).isSameAs(hostDao));
    }

    @Test
    @DisplayName("★ 顺序注解有效性（反向对照）：去掉 @AutoConfigureBefore 后默认实现抢先注册")
    void withoutAutoConfigureBefore_defaultLogDaoWins() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                LogAutoConfiguration.class, ZzzUnorderedRemoteLogFixture.class))
            .withBean(RemoteLogIngestClient.class, RecordingIngestClient::new)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(LogDao.class))
                    .as("无顺序注解时默认实现先注册——这正说明生产实现的 @AutoConfigureBefore 是必需的，"
                        + "而非依靠类名字典序侥幸")
                    .isInstanceOf(DefaultLogDao.class);
            });
    }

    /** 记录上报内容的假客户端：不引入任何业务类型，只验证 starter 侧接线。 */
    static class RecordingIngestClient implements RemoteLogIngestClient {

        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public R<Void> ingestLog(LogRecord record) {
            records.add(record);
            return R.ok();
        }
    }
}
