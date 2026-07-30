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

/**
 * 上传进度监听器。
 *
 * <p>可选。传入 {@link UploadContext} 后，策略在写入过程中按字节回调进度百分比。
 * 未提供时不产生任何开销。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@FunctionalInterface
public interface UploadProgressListener {

    /**
     * 进度回调。
     *
     * @param transferred 已传输字节数
     * @param total       总字节数（未知为 -1）
     * @param percent     进度百分比（0-100，total 未知时为 -1）
     */
    void onProgress(long transferred, long total, int percent);
}
