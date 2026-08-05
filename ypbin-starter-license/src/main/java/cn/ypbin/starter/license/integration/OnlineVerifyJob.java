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

import cn.ypbin.starter.job.core.JobContext;
import cn.ypbin.starter.job.core.JobHandler;
import cn.ypbin.starter.job.core.YpbinJob;
import cn.ypbin.starter.license.core.LicenseContent;
import cn.ypbin.starter.license.core.LicenseManager;
import cn.ypbin.starter.license.core.MachineFingerprint;
import cn.ypbin.starter.license.extension.RemoteVerifyProvider;
import java.util.List;

/**
 * 运行期定期联机校验任务。
 *
 * <p>对接 job 的调度：业务任务表以执行器标识 {@code licenseOnlineVerify} 关联本执行体，按配置周期触发。
 * 每次执行先重算离线授权状态（实现过期自动锁定，无需重启），再逐个回调联机校验扩展点做实时回验，
 * 可感知远程吊销。</p>
 *
 * <p>仅当 classpath 存在 job 模块时才装配（详见自动配置的条件装配）。联机校验的网络容忍策略由扩展点
 * 实现决定，本任务不吞异常——校验失败将由调度器按其异常处理链路上报。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
@YpbinJob("licenseOnlineVerify")
public class OnlineVerifyJob implements JobHandler {

    private final LicenseManager manager;
    private final List<RemoteVerifyProvider> remoteVerifyProviders;

    public OnlineVerifyJob(LicenseManager manager, List<RemoteVerifyProvider> remoteVerifyProviders) {
        this.manager = manager;
        this.remoteVerifyProviders = remoteVerifyProviders;
    }

    @Override
    public void execute(JobContext context) {
        // 先重算离线状态：跨过到期时间点后自动切换为不可用
        manager.evaluate();
        if (remoteVerifyProviders.isEmpty()) {
            return;
        }
        LicenseContent content = manager.getContent();
        if (content == null) {
            return;
        }
        String fingerprint = MachineFingerprint.current();
        for (RemoteVerifyProvider provider : remoteVerifyProviders) {
            provider.verify(content, fingerprint);
        }
    }
}
