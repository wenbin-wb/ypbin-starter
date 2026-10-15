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
package cn.ypbin.starter.messaging.sse;

import java.util.Set;

/**
 * SSE 订阅「扇出维度」解析扩展点。
 *
 * <p>内置订阅端点默认只按 {@link SseUserIdResolver} 解析出的 userId 建连（即扇出维度 = 用户），
 * 这正是本类缺席时的行为。业务若需要「按租户/分组/任意 audience 扇出」（例如 IoT 的租户实时流），
 * 提供一个本类型的 Bean 即可：端点会用返回的<strong>全部</strong>目标键注册同一条连接，
 * 于是 {@code sendToAudience("tenant:42", ...)} 能推给该租户的所有在线连接。</p>
 *
 * <p><strong>安全边界</strong>：本扩展点只能返回由<strong>服务端</strong>登录态/请求上下文推导出的目标
 * （tenantId、机构、角色…），<strong>绝不能</strong>把前端传参直接当作 audience——那等于把
 * 「凭 URL 订阅他人推送」的越权从 userId 挪到 audience 上，与内置端点「不信任前端 userId」的
 * 设计相冲突。默认实现（本 Bean 缺席时）只回落 {@code Set.of(userId)}。</p>
 *
 * <p>注册时机：本 Bean 需在 {@link cn.ypbin.starter.messaging.autoconfigure.SseAutoConfiguration}
 * 评估前就绪（业务侧 {@code @Configuration} 即可，其它自动配置则用
 * {@code @AutoConfigureBefore(SseAutoConfiguration.class)}），否则端点会以「默认只按 userId」装配——
 * 与 {@code SseUserIdResolver} 同一约束。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
@FunctionalInterface
public interface SseAudienceResolver {

    /**
     * 解析当前订阅应注册的全部扇出目标键。
     *
     * @param userId 服务端已鉴权解析出的用户标识（非空）
     * @return 订阅目标键集合；至少包含 userId（返回空集合会被端点按「未登录」拒绝，避免建出一条谁都不匹配的连接）
     */
    Set<String> resolve(String userId);
}
