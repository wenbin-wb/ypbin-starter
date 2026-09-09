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
package cn.ypbin.starter.security.platform;

/**
 * 平台用户判定扩展点。
 *
 * <p>业务方实现本接口，定义"平台用户"的判定规则（如按用户类型/角色/租户归属）。
 * 未提供实现时默认拒绝（{@link #isPlatformUser} 返回 {@code false}，fail-closed）：
 * {@code @PlatformAccess} 标注的资源在无判定实现时一律不可达，避免默认放行造成的越权。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
public interface PlatformUserChecker {

    /**
     * 判定指定用户是否为平台用户。
     *
     * @param userId 用户 ID（未登录/身份缺失时为 {@code null}，应由调用方在调用前拒绝，本方法不负责判空）
     * @return 是平台用户返回 {@code true}
     */
    default boolean isPlatformUser(Long userId) {
        return false;
    }
}
