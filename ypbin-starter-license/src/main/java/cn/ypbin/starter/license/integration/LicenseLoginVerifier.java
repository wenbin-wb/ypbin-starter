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
package cn.ypbin.starter.license.integration;

import cn.ypbin.starter.license.core.LicenseContent;
import cn.ypbin.starter.license.core.LicenseManager;
import cn.ypbin.starter.license.core.MachineFingerprint;
import cn.ypbin.starter.license.extension.RemoteVerifyProvider;
import cn.ypbin.starter.security.core.LoginVerifyProvider;
import java.util.List;

/**
 * 登录回验的授权实现。
 *
 * <p>对接 security 的 {@link LoginVerifyProvider} 扩展点，在每次登录成功后回验当前授权是否可用：
 * 先做离线可用性校验，再逐个回调联机校验扩展点做实时回验（感知远程吊销）。授权不可用（过期/被吊销）
 * 时抛出授权异常阻断登录，实现「每次远程登录均回验当前授权」。</p>
 *
 * <p>未接入联机校验扩展点时退化为纯离线回验，与未配置联机时的行为一致。仅当 classpath 存在 security
 * 模块时才装配（详见自动配置的条件装配），未引入 security 时不影响其余授权能力。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
public class LicenseLoginVerifier implements LoginVerifyProvider {

    private final LicenseManager manager;
    private final List<RemoteVerifyProvider> remoteVerifyProviders;

    public LicenseLoginVerifier(LicenseManager manager, List<RemoteVerifyProvider> remoteVerifyProviders) {
        this.manager = manager;
        this.remoteVerifyProviders = remoteVerifyProviders;
    }

    @Override
    public void verify(Object loginId, String loginType) {
        // 离线可用性：授权不可用时抛出授权异常，阻断本次登录并让调用方感知拒绝原因
        manager.assertUsable();
        LicenseContent content = manager.getContent();
        if (content == null || remoteVerifyProviders.isEmpty()) {
            return;
        }
        String fingerprint = MachineFingerprint.current();
        for (RemoteVerifyProvider provider : remoteVerifyProviders) {
            provider.verify(content, fingerprint);
        }
    }
}
