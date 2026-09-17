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
package cn.ypbin.starter.datapermission.autoconfigure;

import cn.ypbin.starter.datapermission.core.DataScopeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 数据权限「已开启但无处理器」的启动期告警。
 *
 * <p>{@link DataPermissionAutoConfiguration} 带类级 {@code @ConditionalOnBean(DataScopeHandler.class)}：
 * 宿主打开了 {@code ypbin.data-permission.enabled=true} 却没提供 {@link DataScopeHandler} 时，整个配置类
 * （含拦截器与切面）被静默跳过，查询会返回全部数据且启动期毫无提示。本配置只带「开关已开」这一个条件，
 * 恰好在前者被条件跳过时生效，负责把这种「能力未生效」说出来（禁静默不生效）。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ypbin.data-permission", name = "enabled", havingValue = "true")
public class DataPermissionMissingHandlerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DataPermissionMissingHandlerAutoConfiguration.class);

    DataPermissionMissingHandlerAutoConfiguration(ObjectProvider<DataScopeHandler> handlerProvider) {
        if (handlerProvider.getIfAvailable() == null) {
            log.warn("[ypbin-starter] 已开启 ypbin.data-permission.enabled=true，但容器中没有 DataScopeHandler，"
                + "数据权限拦截不会生效（数据范围过滤与 @DataPermission 均不生效，查询将返回全部数据）；"
                + "请提供 DataScopeHandler Bean。");
        }
    }
}
