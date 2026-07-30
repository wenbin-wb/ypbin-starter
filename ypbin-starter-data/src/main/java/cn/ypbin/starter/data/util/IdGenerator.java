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
package cn.ypbin.starter.data.util;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

/**
 * 分布式 ID 生成工具。
 *
 * <p>基于 MyBatis-Plus 内置的雪花算法（{@link IdWorker}），提供在实体持久化之外主动获取
 * 分布式唯一 ID 的能力（如提前生成主键用于关联、幂等键、订单号等）。workerId / dataCenterId
 * 由 MyBatis-Plus 依据机器信息自动推断，多机部署可通过 MyBatis-Plus 的 IdentifierGenerator 定制。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class IdGenerator {

    private IdGenerator() {
    }

    /**
     * 生成 long 型雪花 ID。
     *
     * @return 唯一 ID
     */
    public static long nextId() {
        return IdWorker.getId();
    }

    /**
     * 生成字符串型雪花 ID。
     *
     * @return 唯一 ID 字符串
     */
    public static String nextIdStr() {
        return IdWorker.getIdStr();
    }

    /**
     * 生成不含分隔符的 UUID（32 位小写）。
     *
     * @return UUID 字符串
     */
    public static String simpleUuid() {
        return IdWorker.get32UUID();
    }
}
