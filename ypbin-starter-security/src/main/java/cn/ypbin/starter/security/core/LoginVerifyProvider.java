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
package cn.ypbin.starter.security.core;

/**
 * 登录回验扩展点。
 *
 * <p>在每次登录成功（{@code StpUtil.login}）后被回调，供业务或其它模块对本次登录做「平台级」二次校验：
 * 校验不通过时抛出运行时异常即可阻断登录并让调用方感知。典型用途是授权（License）回验——每次登录都回验
 * 当前授权是否合法有效，非法则拒绝放行。</p>
 *
 * <p>框架收集容器中所有本接口实现，在登录事件里按顺序逐个回调；任一实现抛异常即中断后续回调。未提供任何
 * 实现时不产生额外校验，保持零配置可登录。实现应保持轻量幂等，避免拖慢登录链路。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
@FunctionalInterface
public interface LoginVerifyProvider {

    /**
     * 登录成功后的回验。
     *
     * <p>校验不通过时抛出运行时异常（异常信息将用于向调用方说明拒绝原因）。正常返回代表放行。</p>
     *
     * @param loginId   登录账号标识
     * @param loginType 账号体系类型（Sa-Token 多账号体系标识）
     */
    void verify(Object loginId, String loginType);
}
