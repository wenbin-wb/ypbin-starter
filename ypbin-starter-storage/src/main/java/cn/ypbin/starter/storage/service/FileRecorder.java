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
package cn.ypbin.starter.storage.service;

import cn.ypbin.starter.storage.model.FileInfo;

/**
 * 文件记录器扩展点。
 *
 * <p>上传成功后旁路记录文件元数据，删除时按 URL 反查。默认 no-op 实现，
 * 业务方按需实现以对接数据库 / Redis。分片会话、MD5 秒传等进阶映射不塞进本接口，
 * 保持职责单一、可选实现负担最小。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface FileRecorder {

    /**
     * 记录文件信息。
     *
     * @param fileInfo 文件信息
     */
    default void record(FileInfo fileInfo) {
        // 默认不持久化
    }

    /**
     * 按访问 URL 反查文件信息。
     *
     * @param url 访问 URL
     * @return 文件信息，不存在返回 {@code null}
     */
    default FileInfo getByUrl(String url) {
        return null;
    }

    /**
     * 删除文件记录。
     *
     * @param url 访问 URL
     */
    default void deleteByUrl(String url) {
        // 默认不持久化
    }
}
