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

import cn.ypbin.starter.storage.autoconfigure.StorageProperties.LocalConfig;
import cn.ypbin.starter.storage.exception.StorageException;
import cn.ypbin.starter.storage.model.FileInfo;
import cn.ypbin.starter.storage.model.UploadContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 本地磁盘存储策略。
 *
 * <p>基于 NIO {@link Files} 读写。桶映射为根目录下的子目录，URL 由配置的域名前缀拼接。
 * 零配置即可用，适合开发环境与单机部署。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class LocalStorageStrategy implements StorageStrategy {

    private final LocalConfig config;

    public LocalStorageStrategy(LocalConfig config) {
        this.config = config;
    }

    @Override
    public String platform() {
        return config.getPlatform();
    }

    @Override
    public String defaultBucket() {
        // 本地存储以空桶表示直接放在根目录下
        return "";
    }

    @Override
    public FileInfo upload(UploadContext context) {
        String bucket = (context.getBucket() != null) ? context.getBucket() : defaultBucket();
        String relativePath = context.fullPath();
        Path target = resolve(bucket, relativePath);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = context.getInputStream()) {
                long size = Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                reportComplete(context, size);
                return buildFileInfo(context, bucket, relativePath, size);
            }
        } catch (IOException e) {
            throw new StorageException("本地文件写入失败：" + target, e);
        }
    }

    @Override
    public InputStream download(String bucket, String path) {
        Path target = resolve(bucket, path);
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new StorageException("本地文件读取失败：" + target, e);
        }
    }

    @Override
    public void delete(String bucket, String path) {
        try {
            Files.deleteIfExists(resolve(bucket, path));
        } catch (IOException e) {
            throw new StorageException("本地文件删除失败：" + path, e);
        }
    }

    @Override
    public boolean exists(String bucket, String path) {
        return Files.exists(resolve(bucket, path));
    }

    @Override
    public String url(String bucket, String path, Duration expire) {
        String prefix = config.getDomain();
        StringBuilder sb = new StringBuilder();
        if (prefix != null && !prefix.isBlank()) {
            sb.append(prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix);
        }
        if (bucket != null && !bucket.isBlank()) {
            sb.append('/').append(bucket);
        }
        // 对路径做 URL 编码，避免中文/空格/加号等导致前端访问出错
        sb.append('/').append(StoragePaths.encodePath(path));
        return sb.toString();
    }

    private Path resolve(String bucket, String path) {
        Path base = Paths.get(config.getBasePath());
        Path resolved = (bucket == null || bucket.isBlank())
            ? base.resolve(path)
            : base.resolve(bucket).resolve(path);
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path normalizedTarget = resolved.toAbsolutePath().normalize();
        // 防目录穿越：目标必须位于根目录之内
        if (!normalizedTarget.startsWith(normalizedBase)) {
            throw new StorageException("非法存储路径（越界）：" + path);
        }
        return normalizedTarget;
    }

    private void reportComplete(UploadContext context, long size) {
        if (context.getProgressListener() != null) {
            context.getProgressListener().onProgress(size, size, 100);
        }
    }

    private FileInfo buildFileInfo(UploadContext context, String bucket, String relativePath, long size) {
        FileInfo info = new FileInfo();
        info.setPlatform(platform());
        info.setBucket(bucket);
        info.setPath(relativePath);
        info.setOriginalName(context.getOriginalName());
        info.setFileName(context.getFileName());
        info.setContentType(context.getContentType());
        info.setSize(size);
        info.setUrl(url(bucket, relativePath, null));
        info.setCreateTime(LocalDateTime.now());
        int dot = relativePath.lastIndexOf('.');
        if (dot >= 0) {
            info.setExtension(relativePath.substring(dot + 1));
        }
        return info;
    }
}
