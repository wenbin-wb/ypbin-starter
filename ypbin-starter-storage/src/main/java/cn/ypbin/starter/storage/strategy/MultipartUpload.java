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
import java.io.InputStream;
import java.util.List;

/**
 * 分片上传可选能力。
 *
 * <p>由支持分片的存储后端返回（见 {@link StorageStrategy#multipart()}）。
 * 保持最小方法集：初始化、上传分片、完成、终止。断点续传 / 秒传等进阶特性
 * 交由业务方在 {@code FileRecorder} 之上自行编排，不在此强绑定。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface MultipartUpload {

    /**
     * 初始化分片上传会话。
     *
     * @param bucket 桶
     * @param path   路径
     * @return 上传会话 ID
     */
    String init(String bucket, String path);

    /**
     * 上传单个分片。
     *
     * @param bucket     桶
     * @param path       路径
     * @param uploadId   会话 ID
     * @param partNumber 分片序号（从 1 开始）
     * @param in         分片内容
     * @param size       分片大小
     * @return 分片标识（ETag）
     */
    String uploadPart(String bucket, String path, String uploadId, int partNumber, InputStream in, long size);

    /**
     * 完成分片上传。
     *
     * @param bucket   桶
     * @param path     路径
     * @param uploadId 会话 ID
     * @param partTags 各分片标识（按序号顺序）
     * @return 文件信息
     */
    FileInfo complete(String bucket, String path, String uploadId, List<String> partTags);

    /**
     * 终止分片上传，清理已上传分片。
     *
     * @param bucket   桶
     * @param path     路径
     * @param uploadId 会话 ID
     */
    void abort(String bucket, String path, String uploadId);
}
