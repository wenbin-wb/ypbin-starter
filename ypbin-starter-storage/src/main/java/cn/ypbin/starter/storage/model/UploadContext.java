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

import java.io.InputStream;

/**
 * 上传上下文。
 *
 * <p>贯穿一次上传流程的可变载体：入参（原始名、内容流、大小等）在处理器链中被读取，
 * 生成的文件名 / 路径 / 目标桶 / 平台被逐步写回。相比用 ThreadLocal 传递隐式状态，
 * 显式上下文对象在异步、并发场景下更安全、更易测试。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class UploadContext {

    /** 目标平台（为空时走默认平台） */
    private String platform;

    /** 目标桶（为空时走平台默认桶） */
    private String bucket;

    /** 原始文件名 */
    private String originalName;

    /** 内容类型 */
    private String contentType;

    /** 文件大小（字节，未知为 -1） */
    private long size = -1L;

    /** 内容输入流 */
    private InputStream inputStream;

    /** 处理器生成的存储文件名 */
    private String fileName;

    /** 处理器生成的存储路径（目录部分，以 / 结尾或为空） */
    private String pathPrefix;

    /** 进度监听器，可空 */
    private UploadProgressListener progressListener;

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

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public void setPathPrefix(String pathPrefix) {
        this.pathPrefix = pathPrefix;
    }

    public UploadProgressListener getProgressListener() {
        return progressListener;
    }

    public void setProgressListener(UploadProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    /**
     * 计算完整存储路径（目录 + 文件名）。
     *
     * @return 完整路径
     */
    public String fullPath() {
        String prefix = (pathPrefix == null) ? "" : pathPrefix;
        return prefix + fileName;
    }
}
