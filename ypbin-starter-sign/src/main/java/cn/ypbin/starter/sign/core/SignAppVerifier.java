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
package cn.ypbin.starter.sign.core;

import org.jspecify.annotations.Nullable;

/**
 * 开放应用校验扩展点（租户 / 密钥哈希 / 配额）。
 *
 * <p>starter 负责签名校验的<b>通用骨架</b>（四件套、时间戳、nonce 防重放、签名比对），
 * 但有三件事<b>语义属业务</b>、starter 不预设，交由本接口实现：</p>
 *
 * <ol>
 *     <li><b>密钥哈希比对</b>：库内只存哈希时（如 iot 的 OpenApiKey 体系），
 *     哈希算法与 pepper 由业务自定 ⇒ starter 不假设算法；</li>
 *     <li><b>作用域校验</b>：权限码体系由业务定义 ⇒ starter 只把 {@link SignApp#getScopes()}
 *     传出来，是否放行由业务白名单判定；</li>
 *     <li><b>配额/限流计数</b>：计数存储（Redis/内存）与维度键由业务决定。</li>
 * </ol>
 *
 * <p><b>默认行为</b>：未提供实现时，{@link #DEFAULT} 对三件事一律<b>放行</b>——
 * 与既有版本行为完全一致（既有接入方只配 {@code ypbin.sign.apps} 即可，不受新增维度影响）。
 * 需要更严语义的业务显式提供实现即可，starter 用 {@code @ConditionalOnMissingBean} 让业务实现优先。</p>
 *
 * <p><b>fail-closed 纪律</b>：实现的返回语义必须是"不通过即拒绝"。为避免"实现抛异常被吞掉后静默放行"，
 * {@link SignChecker} 对实现抛出的异常<b>按拒绝处理并记日志</b>（不 fail-open）。</p>
 *
 * @author wenbin
 * @since 2026-10-05
 */
public interface SignAppVerifier {

    /**
     * 校验密钥是否匹配（仅当 {@link SignApp#getSecretKey()} 为空、且有哈希时被调用）。
     *
     * @param app        应用信息
     * @param rawSecret  调用方送来的明文密钥（可为 {@code null}）
     * @return 匹配返回 {@code true}
     */
    default boolean matchesSecret(SignApp app, @Nullable String rawSecret) {
        return false;
    }

    /**
     * 校验作用域是否全部被允许（仅当 {@link SignApp#getScopes()} 非空时被调用）。
     *
     * @param app    应用信息
     * @param scopes 该应用声明的作用域（非空）
     * @return 全部合法返回 {@code true}
     */
    default boolean scopesAllowed(SignApp app, java.util.List<String> scopes) {
        return true;
    }

    /**
     * 配额/限流判定（仅当 {@link SignApp#hasQuota()} 为真时被调用）。
     *
     * <p><b>调用时机</b>：{@link SignChecker} 在<b>签名校验通过之后</b>才调用本方法 ——
     * 否则未认证请求可消耗配额，等于免费的拒绝服务面。</p>
     *
     * @param app 应用信息
     * @return 判定结果（{@code null} 视为不限，保持默认放行语义）
     */
    @Nullable
    default QuotaDecision checkQuota(SignApp app) {
        return null;
    }

    /**
     * 默认实现：三件事一律放行（= 既有版本行为）。
     */
    SignAppVerifier DEFAULT = new SignAppVerifier() {
    };

    /**
     * 配额判定结果。
     *
     * @param allowed 是否放行
     * @param message 拒绝原因（放行时为空，用于 429 提示人话）
     * @author wenbin
     * @since 2026-10-05
     */
    record QuotaDecision(boolean allowed, String message) {

        /**
         * 放行。
         *
         * @return 放行结果
         */
        public static QuotaDecision ok() {
            return new QuotaDecision(true, "");
        }

        /**
         * 拒绝。
         *
         * @param message 拒绝原因
         * @return 拒绝结果
         */
        public static QuotaDecision reject(String message) {
            return new QuotaDecision(false, message);
        }
    }
}
