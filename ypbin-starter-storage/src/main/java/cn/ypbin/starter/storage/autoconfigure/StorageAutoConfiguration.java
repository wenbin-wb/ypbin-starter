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
package cn.ypbin.starter.storage.autoconfigure;

import cn.ypbin.starter.storage.autoconfigure.StorageProperties.LocalConfig;
import cn.ypbin.starter.storage.autoconfigure.StorageProperties.OssConfig;
import cn.ypbin.starter.storage.core.FileStorageService;
import cn.ypbin.starter.storage.engine.StorageRouter;
import cn.ypbin.starter.storage.engine.StorageStrategyRegistrar;
import cn.ypbin.starter.storage.processor.DefaultFileNameProcessor;
import cn.ypbin.starter.storage.processor.FileProcessor;
import cn.ypbin.starter.storage.processor.FileSizeValidator;
import cn.ypbin.starter.storage.service.FileRecorder;
import cn.ypbin.starter.storage.strategy.LocalStorageStrategy;
import cn.ypbin.starter.storage.strategy.OssStorageStrategy;
import cn.ypbin.starter.storage.strategy.StorageStrategy;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

/**
 * 存储模块自动配置。
 *
 * <p>按配置的多源列表装配存储策略：本地源始终可用；对象存储源仅当 AWS SDK 存在时装配
 * （通过嵌套配置 {@code @ConditionalOnClass} 隔离，未引入 SDK 时不影响本地存储）。
 * 处理器链 = 内置（大小校验 + 默认命名）+ 容器中所有 {@link FileProcessor}，按 order 排序。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ypbin.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StorageAutoConfiguration.class);

    /**
     * 本地存储策略贡献者。
     */
    @Bean
    public StorageStrategyRegistrar localStorageRegistrar(StorageProperties properties) {
        return () -> {
            List<StorageStrategy> list = new ArrayList<>();
            for (LocalConfig config : properties.getLocal()) {
                if (config.isEnabled()) {
                    list.add(new LocalStorageStrategy(config));
                    log.debug("[ypbin-starter] local storage registered, platform={}.", config.getPlatform());
                }
            }
            return list;
        };
    }

    /**
     * 默认文件记录器：no-op。
     */
    @Bean
    @ConditionalOnMissingBean
    public FileRecorder fileRecorder() {
        return new FileRecorder() {
        };
    }

    /**
     * 存储路由器：汇总所有策略集合（本地 + 可选 OSS）。
     */
    @Bean
    @ConditionalOnMissingBean
    public StorageRouter storageRouter(List<StorageStrategyRegistrar> registrars,
                                       StorageProperties properties) {
        List<StorageStrategy> all = new ArrayList<>();
        registrars.forEach(r -> all.addAll(r.strategies()));
        String defaultPlatform = properties.getDefaultPlatform();
        if ((defaultPlatform == null || defaultPlatform.isBlank()) && !all.isEmpty()) {
            defaultPlatform = all.get(0).platform();
        }
        log.info("[ypbin-starter] storage router initialized, platforms={}, default={}.",
            all.stream().map(StorageStrategy::platform).toList(), defaultPlatform);
        return new StorageRouter(all, defaultPlatform);
    }

    /**
     * 文件存储门面。
     */
    @Bean
    @ConditionalOnMissingBean
    public FileStorageService fileStorageService(StorageRouter router,
                                                 StorageProperties properties,
                                                 List<FileProcessor> customProcessors,
                                                 FileRecorder fileRecorder) {
        List<FileProcessor> processors = new ArrayList<>();
        processors.add(new FileSizeValidator(properties.getMaxFileSize()));
        processors.add(new DefaultFileNameProcessor());
        processors.addAll(customProcessors);
        AnnotationAwareOrderComparator.sort(processors);
        return new FileStorageService(router, processors, fileRecorder);
    }

    /**
     * 对象存储装配（仅当 AWS SDK 存在时生效）。
     */
    @AutoConfiguration
    @ConditionalOnClass(software.amazon.awssdk.services.s3.S3Client.class)
    static class OssStorageConfiguration {

        @Bean
        public StorageStrategyRegistrar ossStorageRegistrar(StorageProperties properties) {
            return () -> {
                List<StorageStrategy> list = new ArrayList<>();
                for (OssConfig config : properties.getOss()) {
                    if (config.isEnabled()) {
                        list.add(new OssStorageStrategy(config));
                    }
                }
                return list;
            };
        }
    }
}
