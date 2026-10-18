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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * {@code @AutoConfigureBefore} 有效性对照夹具（仅测试用，不在 {@code AutoConfiguration.imports} 中）。
 *
 * <p><strong>为何需要它</strong>：生产实现 {@link RemoteLogAutoConfiguration} 的类名以 {@code R} 开头，
 * 字典序排在 {@link LogAutoConfiguration}（{@code L}）<em>之后</em>——「默认实现退让」这一现象因此
 * 只能由顺序注解解释，本不需要额外对照。但「顺序注解写错方向/写错类名」是本仓反复出现的装配陷阱，
 * 故仍保留一个只差顺序注解的孪生配置做反向对照：本类（{@code Zzz} 前缀，字典序最靠后）不带
 * {@code @AutoConfigureBefore}，跑出来必然是 {@code DefaultLogDao} 抢先注册——若哪天它也装配出了
 * 远端实现，说明对照组失效，正向用例的结论不再可信。</p>
 *
 * <p>注意：Spring Boot 的自动配置排序按<b>全限定类名</b>比较，故本夹具是同包下的顶层类，
 * 不是测试类的嵌套类（嵌套二进制名会带上外层类名，排序位置随之改变）。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
@AutoConfiguration
@ConditionalOnBean(RemoteLogIngestClient.class)
class ZzzUnorderedRemoteLogFixture {

    @Bean
    @ConditionalOnMissingBean(LogDao.class)
    LogDao zzzUnorderedRemoteLogDao(RemoteLogIngestClient ingestClient) {
        return new RemoteLogDao(ingestClient);
    }
}
