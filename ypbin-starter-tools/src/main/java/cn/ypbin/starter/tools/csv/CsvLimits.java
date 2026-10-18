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
package cn.ypbin.starter.tools.csv;

/**
 * CSV 解析的资源上限（最大字节数 / 最大记录数 / 最大列数）。
 *
 * <p><b>为什么需要显式上限</b>：解析入口直接面对用户上传的文件，没有上限就意味着「一行 1GB 的引号字段」
 * 或「一千万行」可以把堆内存打满——那时应用表现为整体不可用，而不是「这次导入被拒绝」。
 * 上限让失败发生在解析早期、归因明确。</p>
 *
 * <p><b>超限语义（禁静默截断）</b>：三个上限中任意一个被突破，{@link CsvReader} 一律抛
 * {@link CsvLimitExceededException}，<b>绝不</b>截断后返回部分数据——静默截断会让用户以为
 * 「导入成功了」，而实际数据缺失。上限是<b>机制</b>，业务配额（如「单次最多 5000 条设备」）
 * 仍由业务层自行定义并用更小的值覆盖。</p>
 *
 * <p><b>默认值口径</b>：默认值对齐常见的 Excel/CSV 导入场景（2 MiB 文件、1 万条记录、256 列），
 * 只用于「宿主没配」时的兜底；业务需要更严格或更宽松时显式传入自己的实例。</p>
 *
 * @param maxBytes   最大字节数（UTF-8 编码后；含 BOM，必须是正数）
 * @param maxRows    最大记录数（含表头记录，必须是正数）
 * @param maxColumns 单条记录最大列数（必须是正数）
 * @author wenbin
 * @since 2026-10-18
 */
public record CsvLimits(int maxBytes, int maxRows, int maxColumns) {

    /** 默认最大字节数：2 MiB。 */
    public static final int DEFAULT_MAX_BYTES = 2 * 1024 * 1024;

    /** 默认最大记录数（含表头）。 */
    public static final int DEFAULT_MAX_ROWS = 10_000;

    /** 默认最大列数。 */
    public static final int DEFAULT_MAX_COLUMNS = 256;

    /**
     * 校验上限取值。
     *
     * @throws IllegalArgumentException 任一上限不是正数
     */
    public CsvLimits {
        requirePositive(maxBytes, "maxBytes");
        requirePositive(maxRows, "maxRows");
        requirePositive(maxColumns, "maxColumns");
    }

    /**
     * 默认上限（2 MiB / 1 万记录 / 256 列）。
     *
     * @return 默认上限
     */
    public static CsvLimits defaults() {
        return new CsvLimits(DEFAULT_MAX_BYTES, DEFAULT_MAX_ROWS, DEFAULT_MAX_COLUMNS);
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException("CSV 解析上限必须是正数：" + name + "=" + value);
        }
    }
}
