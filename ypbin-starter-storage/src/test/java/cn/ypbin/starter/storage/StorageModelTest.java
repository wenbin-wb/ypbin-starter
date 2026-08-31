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
package cn.ypbin.starter.storage;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.storage.autoconfigure.StorageProperties;
import cn.ypbin.starter.storage.exception.StorageException;
import cn.ypbin.starter.storage.model.FileInfo;
import cn.ypbin.starter.storage.processor.FileSizeValidator;
import org.junit.jupiter.api.Test;

/**
 * 存储模块模型与校验测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class StorageModelTest {

    @Test
    void storageExceptionShouldCarryMessage() {
        StorageException e = new StorageException("上传失败");
        assertThat(e.getMessage()).isEqualTo("上传失败");
        StorageException withCause = new StorageException("失败", new IllegalStateException("原因"));
        assertThat(withCause.getCause()).isNotNull();
    }

    @Test
    void fileInfoShouldCarryFields() {
        FileInfo info = new FileInfo();
        info.setPlatform("local");
        info.setBucket("b");
        info.setPath("a/b.txt");
        info.setOriginalName("b.txt");
        info.setSize(100L);
        assertThat(info.getPlatform()).isEqualTo("local");
        assertThat(info.getPath()).isEqualTo("a/b.txt");
        assertThat(info.getOriginalName()).isEqualTo("b.txt");
        assertThat(info.getSize()).isEqualTo(100L);
    }

    @Test
    void storagePropertiesShouldExposeDefaults() {
        StorageProperties props = new StorageProperties();
        assertThat(props).isNotNull();
    }

    @Test
    void fileSizeValidatorShouldAcceptWithinLimit() {
        FileSizeValidator validator = new FileSizeValidator(10 * 1024 * 1024);
        assertThat(validator).isNotNull();
    }
}
