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
package cn.ypbin.starter.async.autoconfigure;

import cn.ypbin.starter.async.core.YpbinAsyncConfigurer;
import java.util.concurrent.Executor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @Async} 接管自动配置。
 *
 * <p>仅在 {@code ypbin.async.enabled=true} 且 {@code ypbin.async.enable-annotation=true}（默认）时启用
 * {@link EnableAsync}，把默认执行器指向统一线程池并统一异步异常处理。业务方自定义 {@link AsyncConfigurer}
 * 时不覆盖。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@AutoConfiguration(after = AsyncAutoConfiguration.class)
@ConditionalOnProperty(prefix = AsyncProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableAsync
public class AsyncAnnotationAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = AsyncProperties.PREFIX, name = "enable-annotation", havingValue = "true",
        matchIfMissing = true)
    @ConditionalOnMissingBean(AsyncConfigurer.class)
    public YpbinAsyncConfigurer ypbinAsyncConfigurer(
            @Qualifier("ypbinTaskExecutor") Executor executor,
            AsyncUncaughtExceptionHandler exceptionHandler) {
        return new YpbinAsyncConfigurer(executor, exceptionHandler);
    }
}
