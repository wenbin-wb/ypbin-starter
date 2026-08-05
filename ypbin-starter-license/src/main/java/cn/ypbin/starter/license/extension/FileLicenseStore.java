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
package cn.ypbin.starter.license.extension;

import cn.ypbin.starter.license.exception.LicenseErrorCode;
import cn.ypbin.starter.license.exception.LicenseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于本地文件的授权串存取默认实现。
 *
 * <p>从配置指定的授权文件读取 Base64 授权串；在线更新时将新授权串写回同一文件。文件不存在时
 * {@link #load()} 返回 {@code null}（表示未授权），不伪造空授权。读写异常直接暴露为授权异常，
 * 不静默吞没。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
public class FileLicenseStore implements LicenseStore {

    private static final Logger log = LoggerFactory.getLogger(FileLicenseStore.class);

    private final Path location;

    public FileLicenseStore(Path location) {
        this.location = location;
    }

    @Override
    public String load() {
        if (!Files.exists(location)) {
            log.warn("[ypbin-starter] 未找到授权文件：{}", location);
            return null;
        }
        try {
            String content = Files.readString(location, StandardCharsets.UTF_8).trim();
            return content.isEmpty() ? null : content;
        } catch (IOException e) {
            throw new LicenseException(LicenseErrorCode.LICENSE_CORRUPTED, e);
        }
    }

    @Override
    public void save(String authCode) {
        try {
            Path parent = location.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(location, authCode, StandardCharsets.UTF_8);
            log.info("[ypbin-starter] 授权文件已更新：{}", location);
        } catch (IOException e) {
            throw new LicenseException(LicenseErrorCode.LICENSE_CORRUPTED, e);
        }
    }
}
