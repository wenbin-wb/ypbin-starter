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
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 带上限的输入流。
 *
 * <p>包装原始流，累计已读字节，一旦超过 {@code maxBytes} 立即抛出 {@link StorageException}。
 * 用于未知大小（Content-Length 缺失）的流式上传：即使事先拿不到大小，也能在写入过程中及时中断，
 * 防止无限落盘打满磁盘（DoS）。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class BoundedInputStream extends FilterInputStream {

    private final long maxBytes;
    private long count;

    public BoundedInputStream(InputStream in, long maxBytes) {
        super(in);
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            increment(1);
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) {
            increment(n);
        }
        return n;
    }

    private void increment(int n) {
        count += n;
        if (count > maxBytes) {
            throw new StorageException("上传内容超过大小上限 " + maxBytes + " 字节");
        }
    }
}
