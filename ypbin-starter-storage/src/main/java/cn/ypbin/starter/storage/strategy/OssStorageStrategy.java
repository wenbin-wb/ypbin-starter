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

import cn.ypbin.starter.storage.autoconfigure.StorageProperties.OssConfig;
import cn.ypbin.starter.storage.exception.StorageException;
import cn.ypbin.starter.storage.model.FileInfo;
import cn.ypbin.starter.storage.model.UploadContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * S3 兼容对象存储策略。
 *
 * <p>基于 AWS SDK v2 的 {@link S3Client}，覆盖所有兼容 S3 协议的对象存储服务
 * （阿里云 OSS、腾讯云 COS、MinIO、七牛等），通过 endpoint / region / pathStyle 区分，
 * 无需为每家云单独实现。私有桶通过预签名生成临时访问 URL。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class OssStorageStrategy implements StorageStrategy {

    private final OssConfig config;
    private final S3Client s3Client;
    private final S3Presigner presigner;

    public OssStorageStrategy(OssConfig config) {
        this.config = config;
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey()));
        S3Configuration s3Config = S3Configuration.builder()
            .pathStyleAccessEnabled(config.isPathStyleAccess())
            .build();
        URI endpoint = URI.create(config.getEndpoint());
        this.s3Client = S3Client.builder()
            .endpointOverride(endpoint)
            .region(Region.of(config.getRegion()))
            .credentialsProvider(credentials)
            .serviceConfiguration(s3Config)
            .build();
        this.presigner = S3Presigner.builder()
            .endpointOverride(endpoint)
            .region(Region.of(config.getRegion()))
            .credentialsProvider(credentials)
            .serviceConfiguration(s3Config)
            .build();
    }

    @Override
    public String platform() {
        return config.getPlatform();
    }

    @Override
    public String defaultBucket() {
        return config.getBucket();
    }

    @Override
    public FileInfo upload(UploadContext context) {
        String bucket = (context.getBucket() != null) ? context.getBucket() : defaultBucket();
        String key = context.fullPath();
        PutObjectRequest.Builder req = PutObjectRequest.builder().bucket(bucket).key(key);
        if (context.getContentType() != null) {
            req.contentType(context.getContentType());
        }
        long size = context.getSize();
        if (size > 0) {
            // 已知大小：直接流式上传，零额外内存/磁盘开销
            try (InputStream in = context.getInputStream()) {
                s3Client.putObject(req.build(), RequestBody.fromInputStream(in, size));
                reportComplete(context, size);
                return buildFileInfo(context, bucket, key, size);
            } catch (IOException e) {
                throw new StorageException("对象存储上传失败：" + key, e);
            }
        }
        // 未知大小：落地临时文件获取真实大小后上传，避免 readAllBytes 打爆堆内存
        return uploadViaTempFile(context, bucket, key, req);
    }

    /**
     * 大小未知时的兜底上传：先将流写入临时文件，得到真实大小后用 {@code fromFile} 上传，
     * 仅消耗少量磁盘 IO，规避将整个大文件读入 JVM 堆导致的 OOM。
     */
    private FileInfo uploadViaTempFile(UploadContext context, String bucket, String key,
                                       PutObjectRequest.Builder req) {
        Path temp = null;
        try (InputStream in = context.getInputStream()) {
            temp = Files.createTempFile("ypbin-upload-", ".tmp");
            long size = Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            s3Client.putObject(req.build(), RequestBody.fromFile(temp));
            reportComplete(context, size);
            return buildFileInfo(context, bucket, key, size);
        } catch (IOException e) {
            throw new StorageException("对象存储上传失败：" + key, e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // 临时文件清理失败不影响上传结果
                }
            }
        }
    }

    @Override
    public InputStream download(String bucket, String path) {
        return s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(path).build());
    }

    @Override
    public void delete(String bucket, String path) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(path).build());
    }

    @Override
    public boolean exists(String bucket, String path) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(path).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            // HEAD 无 body，SDK 常无法还原为 NoSuchKeyException，而是抛 404 的 S3Exception；
            // 部分兼容服务无 ListBucket 权限时对不存在对象返回 403。仅 404 视为不存在，其余抛出。
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public String url(String bucket, String path, Duration expire) {
        // 配置了自定义域名则拼直链
        if (config.getDomain() != null && !config.getDomain().isBlank()) {
            String prefix = config.getDomain();
            String base = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
            // 对路径做 URL 编码，避免中文/空格/加号等导致前端访问 400/404
            return base + "/" + StoragePaths.encodePath(path);
        }
        // 否则生成预签名 URL
        Duration ttl = (expire != null) ? expire : Duration.ofHours(1);
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(path).build())
            .build();
        return presigner.presignGetObject(presignRequest).url().toString();
    }

    private void reportComplete(UploadContext context, long size) {
        if (context.getProgressListener() != null && size > 0) {
            context.getProgressListener().onProgress(size, size, 100);
        }
    }

    private FileInfo buildFileInfo(UploadContext context, String bucket, String key, long size) {
        FileInfo info = new FileInfo();
        info.setPlatform(platform());
        info.setBucket(bucket);
        info.setPath(key);
        info.setOriginalName(context.getOriginalName());
        info.setFileName(context.getFileName());
        info.setContentType(context.getContentType());
        info.setSize(size);
        // 仅配置了自定义域名（稳定直链）时才落库 URL；私有桶只能生成临时签名 URL，
        // 落库会 1 小时失效且反查不可靠，故留空，访问时由 url(...) 按需实时生成。
        if (config.getDomain() != null && !config.getDomain().isBlank()) {
            info.setUrl(url(bucket, key, null));
        }
        info.setCreateTime(LocalDateTime.now());
        int dot = key.lastIndexOf('.');
        if (dot >= 0) {
            info.setExtension(key.substring(dot + 1));
        }
        return info;
    }
}
