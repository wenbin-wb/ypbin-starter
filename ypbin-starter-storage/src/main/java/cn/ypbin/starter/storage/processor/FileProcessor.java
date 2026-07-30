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
import org.springframework.core.Ordered;

/**
 * 文件上传前处理器。
 *
 * <p>统一的可插拔责任链接口，覆盖校验、文件名生成、路径生成等上传前处理需求。
 * 相比拆成 Validator / NameGenerator / PathGenerator 等多个语义接口，单一接口 +
 * {@link #support} 判定 + {@link #getOrder} 排序更简洁，且业务方只需实现一个接口即可扩展。</p>
 *
 * <p>处理器按 order 升序依次执行，每个处理器可读取并修改 {@link UploadContext}
 * （如写回生成的文件名、路径），校验类处理器通过抛出异常中断上传。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface FileProcessor extends Ordered {

    /**
     * 是否处理当前上下文。
     *
     * @param context 上传上下文
     * @return true 表示参与处理
     */
    default boolean support(UploadContext context) {
        return true;
    }

    /**
     * 执行处理。
     *
     * @param context 上传上下文
     */
    void process(UploadContext context);

    @Override
    default int getOrder() {
        return 0;
    }
}
