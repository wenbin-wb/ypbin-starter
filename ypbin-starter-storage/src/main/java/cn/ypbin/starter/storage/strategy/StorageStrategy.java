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
package cn.ypbin.starter.storage.strategy;

import cn.ypbin.starter.storage.model.FileInfo;
import cn.ypbin.starter.storage.model.UploadContext;
import java.io.InputStream;
import java.time.Duration;

/**
 * 存储后端能力契约。
 *
 * <p>单个存储后端（本地磁盘、S3 兼容对象存储等）的读写能力。每个实例对应一个
 * {@link #platform() 平台标识}，由 {@code StorageStrategyRouter} 按标识路由。</p>
 *
 * <p>基础 IO 为必需能力；分片上传作为可选能力，通过 {@link #multipart()} 暴露，
 * 不支持的后端返回 {@code null} 即可，不强塞进主链路。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface StorageStrategy {

    /**
     * 平台标识（对应配置的 platform key，全局唯一）。
     *
     * @return 平台标识
     */
    String platform();

    /**
     * 默认桶。
     *
     * @return 默认桶名
     */
    String defaultBucket();

    /**
     * 上传文件。
     *
     * @param context 上传上下文（含目标桶、路径、内容流、进度监听器等）
     * @return 文件信息
     */
    FileInfo upload(UploadContext context);

    /**
     * 下载文件。
     *
     * @param bucket 桶
     * @param path   路径
     * @return 内容输入流
     */
    InputStream download(String bucket, String path);

    /**
     * 删除文件。
     *
     * @param bucket 桶
     * @param path   路径
     */
    void delete(String bucket, String path);

    /**
     * 判断文件是否存在。
     *
     * @param bucket 桶
     * @param path   路径
     * @return 是否存在
     */
    boolean exists(String bucket, String path);

    /**
     * 生成访问 URL。
     *
     * <p>公有读后端返回直链，私有后端返回带签名的临时 URL。</p>
     *
     * @param bucket 桶
     * @param path   路径
     * @param expire 有效期（对直链无意义时可忽略）
     * @return 访问 URL
     */
    String url(String bucket, String path, Duration expire);

    /**
     * 分片上传能力，不支持时返回 {@code null}。
     *
     * @return 分片上传能力接口，或 {@code null}
     */
    default MultipartUpload multipart() {
        return null;
    }
}
