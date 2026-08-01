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
package cn.ypbin.starter.security.password.policy;

/**
 * 密码策略来源扩展点。
 *
 * <p>starter 默认提供配置文件版实现。业务系统若把密码策略做成后台可配置（如存配置中心表），
 * 实现本接口返回动态策略即可覆盖，从而支持运行时调整而无需重启。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@FunctionalInterface
public interface PasswordPolicyProvider {

    /**
     * 获取当前生效的密码策略。
     *
     * @return 密码策略
     */
    PasswordPolicy getPolicy();
}
