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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.storage.exception.StorageException;
import cn.ypbin.starter.storage.model.UploadContext;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/**
 * 存储处理器（大小校验、默认命名）单元测试。
 *
 * @author wenbin
 * @since 2026-07-30
 */
class FileProcessorTest {

    @Test
    void sizeValidator_underLimit_passes() {
        FileSizeValidator validator = new FileSizeValidator(1000);
        UploadContext ctx = new UploadContext();
        ctx.setSize(500);
        assertThat(validator.support(ctx)).isTrue();
        validator.process(ctx); // 不抛异常
    }

    @Test
    void sizeValidator_overLimit_throws() {
        FileSizeValidator validator = new FileSizeValidator(1000);
        UploadContext ctx = new UploadContext();
        ctx.setSize(2000);
        assertThatThrownBy(() -> validator.process(ctx))
            .isInstanceOf(StorageException.class)
            .hasMessageContaining("超过上限");
    }

    @Test
    void sizeValidator_notSupported_whenNoLimit() {
        // 未配置上限：不参与
        assertThat(new FileSizeValidator(-1).support(sizedContext(500))).isFalse();
    }

    @Test
    void sizeValidator_unknownSize_wrapsWithBoundedStream() {
        // 配了上限但大小未知：参与，并把流包装为 BoundedInputStream 兜底防无限落盘
        FileSizeValidator validator = new FileSizeValidator(1000);
        UploadContext ctx = new UploadContext();
        ctx.setSize(-1);
        ctx.setInputStream(new ByteArrayInputStream(new byte[10]));
        assertThat(validator.support(ctx)).isTrue();
        validator.process(ctx);
        assertThat(ctx.getInputStream()).isInstanceOf(BoundedInputStream.class);
    }

    @Test
    void sizeValidator_unknownSize_boundedStreamThrowsWhenExceeded() throws Exception {
        // 未知大小流实际读取超过上限时中断
        FileSizeValidator validator = new FileSizeValidator(4);
        UploadContext ctx = new UploadContext();
        ctx.setSize(-1);
        ctx.setInputStream(new ByteArrayInputStream(new byte[10]));
        validator.process(ctx);
        InputStream wrapped = ctx.getInputStream();
        assertThatThrownBy(() -> wrapped.readAllBytes())
            .isInstanceOf(StorageException.class)
            .hasMessageContaining("上限");
    }

    @Test
    void defaultNameProcessor_generatesNameAndPath() {
        DefaultFileNameProcessor processor = new DefaultFileNameProcessor();
        UploadContext ctx = new UploadContext();
        ctx.setOriginalName("photo.PNG");
        processor.process(ctx);
        assertThat(ctx.getFileName()).endsWith(".PNG").hasSizeGreaterThan(4);
        assertThat(ctx.getPathPrefix()).matches("\\d{4}/\\d{2}/\\d{2}/");
    }

    @Test
    void defaultNameProcessor_doesNotOverrideExplicitValues() {
        DefaultFileNameProcessor processor = new DefaultFileNameProcessor();
        UploadContext ctx = new UploadContext();
        ctx.setFileName("fixed.txt");
        ctx.setPathPrefix("custom/");
        processor.process(ctx);
        assertThat(ctx.getFileName()).isEqualTo("fixed.txt");
        assertThat(ctx.getPathPrefix()).isEqualTo("custom/");
    }

    @Test
    void defaultNameProcessor_noExtension() {
        DefaultFileNameProcessor processor = new DefaultFileNameProcessor();
        UploadContext ctx = new UploadContext();
        ctx.setOriginalName("README");
        processor.process(ctx);
        assertThat(ctx.getFileName()).doesNotContain(".");
    }

    private UploadContext sizedContext(long size) {
        UploadContext ctx = new UploadContext();
        ctx.setSize(size);
        return ctx;
    }
}
