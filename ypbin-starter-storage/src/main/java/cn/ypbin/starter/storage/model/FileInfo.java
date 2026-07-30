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
package cn.ypbin.starter.storage.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件信息。
 *
 * <p>上传完成后返回的文件元数据，也是 {@code FileRecorder} 持久化的载体。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class FileInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 存储平台标识（对应配置的 platform key） */
    private String platform;

    /** 存储桶 */
    private String bucket;

    /** 存储路径（相对 bucket，含文件名） */
    private String path;

    /** 原始文件名 */
    private String originalName;

    /** 存储文件名（生成后的） */
    private String fileName;

    /** 文件访问 URL */
    private String url;

    /** 文件大小（字节） */
    private long size;

    /** 内容类型（MIME） */
    private String contentType;

    /** 扩展名（不含点） */
    private String extension;

    /** 内容摘要（如 MD5/ETag），可空 */
    private String hash;

    /** 缩略图 URL，可空 */
    private String thumbnailUrl;

    /** 创建时间 */
    private LocalDateTime createTime;

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
