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
package cn.ypbin.starter.license.extension;

import cn.ypbin.starter.license.core.LicenseContent;

/**
 * 联机校验扩展点。
 *
 * <p>starter 只定义联机回验的契约，不内建鉴权端。业务侧（如 admin 内建鉴权端）实现本接口，
 * 向远程校验服务上报当前授权编号与机器指纹，校验授权是否被吊销、是否超出并发/额度等在线约束。</p>
 *
 * <p>校验不通过时抛出运行时异常以阻断；正常返回代表放行。未提供实现时联机校验为空操作，仅走离线校验，
 * 不做任何静默兜底伪装成「已联机」。实现应对网络异常有明确的容忍策略（由业务侧决定放行或拒绝），
 * 而非在此吞掉异常。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
@FunctionalInterface
public interface RemoteVerifyProvider {

    /**
     * 对当前授权做一次联机回验。
     *
     * @param content     当前授权内容
     * @param fingerprint 当前机器指纹
     */
    void verify(LicenseContent content, String fingerprint);
}
