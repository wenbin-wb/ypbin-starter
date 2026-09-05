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
package cn.ypbin.starter.xxljob.autoconfigure;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * XXL-JOB 执行器自动配置。
 *
 * <p>仅当 {@code ypbin.xxl-job.enabled=true} 时装配 {@link XxlJobSpringExecutor}：
 * 注册到 xxl-job-admin 调度中心，业务方法标注 {@code @XxlJob} 即成为可调度任务。</p>
 *
 * <p>配置缺失（admin 地址/执行器名）直接抛异常暴露——执行器连不上调度中心等于任务静默不跑，
 * 比启动失败更难排查，故禁止静默降级。</p>
 *
 * @author wenbin
 * @since 2026-09-05
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ypbin.xxl-job", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(XxlJobProperties.class)
public class XxlJobAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(XxlJobAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public XxlJobSpringExecutor xxlJobExecutor(XxlJobProperties properties) {
        if (!StringUtils.hasText(properties.getAdminAddresses())) {
            throw new IllegalStateException("ypbin.xxl-job.admin-addresses 未配置，无法注册 XXL-JOB 执行器");
        }
        if (!StringUtils.hasText(properties.getAppname())) {
            throw new IllegalStateException("ypbin.xxl-job.appname 未配置，无法注册 XXL-JOB 执行器");
        }
        log.info("[ypbin-starter] xxl-job executor init: appname={}, admin={}, port={}.",
            properties.getAppname(), properties.getAdminAddresses(), properties.getPort());

        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(properties.getAdminAddresses());
        executor.setAccessToken(properties.getAccessToken());
        executor.setAppname(properties.getAppname());
        if (StringUtils.hasText(properties.getAddress())) {
            executor.setAddress(properties.getAddress());
        }
        if (StringUtils.hasText(properties.getIp())) {
            executor.setIp(properties.getIp());
        }
        executor.setPort(properties.getPort());
        executor.setLogPath(properties.getLogPath());
        executor.setLogRetentionDays(properties.getLogRetentionDays());
        return executor;
    }
}
