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
 * 仅在 MySQL 可用于集成测试时执行（外部实例或 Docker 容器任一可用）。
 *
 * @author wenbin
 * @since 2026-09-13
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(EnabledIfMySqlAvailable.MySqlCondition.class)
public @interface EnabledIfMySqlAvailable {

    /**
     * MySQL 可用性判断条件。
     */
    class MySqlCondition implements ExecutionCondition {

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (ContainerSupport.mySqlAvailable()) {
                return ConditionEvaluationResult.enabled("MySQL 可用（外部实例或 Docker 容器）");
            }
            return ConditionEvaluationResult.disabled(
                "MySQL 不可用：未设置 " + ContainerSupport.ENV_MYSQL_URL + " 且本机 Docker 不可用，跳过集成测试");
        }
    }
}
