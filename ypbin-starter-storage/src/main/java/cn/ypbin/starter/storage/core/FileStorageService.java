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
package cn.ypbin.starter.storage.core;

import cn.ypbin.starter.storage.engine.StorageRouter;
import cn.ypbin.starter.storage.model.FileInfo;
import cn.ypbin.starter.storage.model.UploadContext;
import cn.ypbin.starter.storage.processor.FileProcessor;
import cn.ypbin.starter.storage.service.FileRecorder;
import cn.ypbin.starter.storage.strategy.StorageStrategy;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;

/**
 * 文件存储业务门面。
 *
 * <p>面向业务的统一入口：链式构造上传请求，编排处理器链 → 路由策略 → 落地 → 记录。
 * 90% 的使用场景只需接触本类。处理器与监听器全部通过 {@link UploadContext} 显式传递，
 * 不使用 ThreadLocal。</p>
 *
 * <pre>{@code
 * fileStorageService.upload(inputStream, "a.png")
 *     .platform("oss-main")
 *     .bucket("images")
 *     .onProgress((t, total, pct) -> log.info("{}%", pct))
 *     .execute();
 * }</pre>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class FileStorageService {

    private final StorageRouter router;
    private final List<FileProcessor> processors;
    private final FileRecorder fileRecorder;

    public FileStorageService(StorageRouter router,
                              List<FileProcessor> processors,
                              FileRecorder fileRecorder) {
        this.router = router;
        this.processors = processors;
        this.fileRecorder = fileRecorder;
    }

    /**
     * 开启一次上传。
     *
     * @param inputStream  内容流
     * @param originalName 原始文件名
     * @return 上传构建器
     */
    public UploadBuilder upload(InputStream inputStream, String originalName) {
        UploadContext context = new UploadContext();
        context.setInputStream(inputStream);
        context.setOriginalName(originalName);
        return new UploadBuilder(context);
    }

    /**
     * 下载文件。
     *
     * @param platform 平台标识
     * @param bucket   桶
     * @param path     路径
     * @return 内容流
     */
    public InputStream download(String platform, String bucket, String path) {
        return router.route(platform).download(bucket, path);
    }

    /**
     * 删除物理文件（不含元数据记录）。
     *
     * <p>仅删除存储后端的文件本身。若使用了 {@code FileRecorder} 记录元数据，请在业务侧
     * 另行调用 {@code fileRecorder.deleteByUrl(...)} 清理记录——因删除按 platform/bucket/path
     * 定位，而记录以 URL 为键，二者语义不同，框架不代为反查删除以免误删。</p>
     *
     * @param platform 平台标识
     * @param bucket   桶
     * @param path     路径
     */
    public void delete(String platform, String bucket, String path) {
        StorageStrategy strategy = router.route(platform);
        strategy.delete(bucket, path);
    }

    /**
     * 生成访问 URL。
     *
     * @param platform 平台标识
     * @param bucket   桶
     * @param path     路径
     * @param expire   有效期
     * @return 访问 URL
     */
    public String url(String platform, String bucket, String path, Duration expire) {
        return router.route(platform).url(bucket, path, expire);
    }

    private FileInfo doUpload(UploadContext context) {
        // 1. 处理器链：校验、命名、路径生成（按 order 升序）
        for (FileProcessor processor : processors) {
            if (processor.support(context)) {
                processor.process(context);
            }
        }
        // 2. 路由策略并落地
        StorageStrategy strategy = router.route(context.getPlatform());
        if (context.getBucket() == null) {
            context.setBucket(strategy.defaultBucket());
        }
        FileInfo fileInfo = strategy.upload(context);
        // 3. 旁路记录
        fileRecorder.record(fileInfo);
        return fileInfo;
    }

    /**
     * 上传链式构建器。
     */
    public class UploadBuilder {

        private final UploadContext context;

        UploadBuilder(UploadContext context) {
            this.context = context;
        }

        public UploadBuilder platform(String platform) {
            context.setPlatform(platform);
            return this;
        }

        public UploadBuilder bucket(String bucket) {
            context.setBucket(bucket);
            return this;
        }

        public UploadBuilder path(String pathPrefix) {
            context.setPathPrefix(pathPrefix);
            return this;
        }

        public UploadBuilder fileName(String fileName) {
            context.setFileName(fileName);
            return this;
        }

        public UploadBuilder contentType(String contentType) {
            context.setContentType(contentType);
            return this;
        }

        public UploadBuilder size(long size) {
            context.setSize(size);
            return this;
        }

        public UploadBuilder onProgress(cn.ypbin.starter.storage.model.UploadProgressListener listener) {
            context.setProgressListener(listener);
            return this;
        }

        /**
         * 执行上传。
         *
         * @return 文件信息
         */
        public FileInfo execute() {
            return doUpload(context);
        }
    }
}
