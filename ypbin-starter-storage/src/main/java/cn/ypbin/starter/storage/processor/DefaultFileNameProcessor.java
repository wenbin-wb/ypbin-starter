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
package cn.ypbin.starter.storage.processor;

import cn.ypbin.starter.storage.model.UploadContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 默认文件名与路径生成处理器。
 *
 * <p>路径按 {@code yyyy/MM/dd/} 分层，文件名用 UUID 保留原扩展名，避免重名覆盖。
 * 仅在上下文未显式指定文件名 / 路径时才生成，业务方可注册更高优先级处理器覆盖。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class DefaultFileNameProcessor implements FileProcessor {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd/");

    @Override
    public void process(UploadContext context) {
        if (context.getFileName() == null || context.getFileName().isBlank()) {
            context.setFileName(generateFileName(context.getOriginalName()));
        }
        if (context.getPathPrefix() == null) {
            context.setPathPrefix(LocalDate.now().format(DATE_PATH));
        }
    }

    private String generateFileName(String originalName) {
        String ext = extractExtension(originalName);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return ext.isEmpty() ? uuid : uuid + "." + ext;
    }

    private String extractExtension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return (dot < 0 || dot == name.length() - 1) ? "" : name.substring(dot + 1);
    }

    @Override
    public int getOrder() {
        // 较低优先级，业务方自定义生成器（更小 order）可覆盖
        return 100;
    }
}
