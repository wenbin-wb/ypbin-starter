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

import cn.ypbin.starter.storage.exception.StorageException;
import cn.ypbin.starter.storage.model.UploadContext;

/**
 * 文件大小校验处理器。
 *
 * <p>当配置了最大字节数且能得知文件大小时，超限抛出 {@link StorageException} 中断上传。
 * 优先级高于生成类处理器，确保校验先行。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class FileSizeValidator implements FileProcessor {

    private final long maxFileSize;

    public FileSizeValidator(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    @Override
    public boolean support(UploadContext context) {
        // 只要配置了上限就参与：大小已知时直接校验，未知时用限流流兜底
        return maxFileSize > 0;
    }

    @Override
    public void process(UploadContext context) {
        if (context.getSize() > 0) {
            // 已知大小：直接校验，避免无谓包装
            if (context.getSize() > maxFileSize) {
                throw new StorageException(
                    "文件大小 " + context.getSize() + " 字节超过上限 " + maxFileSize + " 字节");
            }
            return;
        }
        // 未知大小（缺 Content-Length 的流式上传）：包装为限流流，写入超上限即中断，防无限落盘
        if (context.getInputStream() != null) {
            context.setInputStream(new BoundedInputStream(context.getInputStream(), maxFileSize));
        }
    }

    @Override
    public int getOrder() {
        // 校验先于生成执行
        return 10;
    }
}
