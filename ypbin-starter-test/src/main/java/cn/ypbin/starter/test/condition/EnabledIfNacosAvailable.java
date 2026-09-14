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
package cn.ypbin.starter.test.condition;

import cn.ypbin.starter.test.container.ContainerSupport;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * 仅在 Nacos 可用于集成测试时执行（外部地址或 Docker 容器任一可用）。
 *
 * <p>用条件跳过而非直接失败：无中间件的开发机上跑 {@code mvn -Pit verify} 不应产生假失败，
 * 而 CI（有 Docker）会真实拉起 Nacos 容器执行。跳过的原因会写入测试报告，避免「看起来通过其实没跑」。</p>
 *
 * <p>外部地址支持两种写法：{@code -Dypbin.it.nacos-addr=host:8848}（既有用法）或环境变量
 * {@code YPBIN_TEST_NACOS_ADDR}。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(EnabledIfNacosAvailable.NacosCondition.class)
public @interface EnabledIfNacosAvailable {

    /**
     * Nacos 可用性判断条件。
     */
    class NacosCondition implements ExecutionCondition {

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (ContainerSupport.nacosAvailable()) {
                return ConditionEvaluationResult.enabled("Nacos 可用（外部地址或 Docker 容器）");
            }
            return ConditionEvaluationResult.disabled(
                "Nacos 不可用：未设置 " + ContainerSupport.PROP_NACOS_ADDR + " / "
                    + ContainerSupport.ENV_NACOS_ADDR + " 且本机 Docker 不可用，跳过集成测试");
        }
    }
}
