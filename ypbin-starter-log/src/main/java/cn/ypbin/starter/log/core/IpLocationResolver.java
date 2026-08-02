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
package cn.ypbin.starter.log.core;

/**
 * IP 归属地解析扩展点。
 *
 * <p>把客户端 IP 解析为地理位置（如「广东省深圳市」），供操作日志的 location 字段填充。归属地解析需要
 * IP 库（如 ip2region 离线库或第三方 API），体量/依赖较重且非通用刚需，故 starter 不内置具体实现，
 * 只定义扩展点。业务方需要时提供实现即可（接 ip2region、调 IP 归属地服务等）；未提供时使用默认实现
 * 返回 {@code null}，location 字段留空。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@FunctionalInterface
public interface IpLocationResolver {

    /**
     * 解析 IP 归属地。
     *
     * @param ip 客户端 IP
     * @return 归属地描述；无法解析或未接入时返回 {@code null}
     */
    String resolve(String ip);
}
