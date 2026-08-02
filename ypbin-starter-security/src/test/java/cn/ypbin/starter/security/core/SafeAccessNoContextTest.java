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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 验证「安全取」方法在无 Sa-Token 上下文的线程（异步、定时任务）中不抛异常、安全返回空。
 *
 * <p>回归：{@code getUserIdSafely} 曾裸调 {@code StpUtil.getLoginIdDefaultNull()}，在无 SaTokenContext 的
 * 线程抛 SaTokenContextException，导致 AuditorProvider 在异步落库时审计字段填充崩溃。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
class SafeAccessNoContextTest {

    /**
     * 在一个纯净的新线程里跑（无任何 Servlet/Sa-Token 上下文），模拟 @Async / 定时任务线程。
     */
    private <T> T runInBareThread(java.util.function.Supplier<T> action) throws InterruptedException {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                result.set(action.get());
            } catch (Throwable t) {
                error.set(t);
            }
        });
        thread.start();
        thread.join();
        if (error.get() != null) {
            throw new AssertionError("安全取方法在无上下文线程抛异常", error.get());
        }
        return result.get();
    }

    @Test
    void getUserIdSafelyReturnsEmptyWithoutContext() throws InterruptedException {
        Optional<Long> userId = runInBareThread(LoginHelper::getUserIdSafely);
        assertThat(userId).isEmpty();
    }

    @Test
    void isLoginReturnsFalseWithoutContext() throws InterruptedException {
        Boolean login = runInBareThread(LoginHelper::isLogin);
        assertThat(login).isFalse();
    }

    @Test
    void userContextSafeGettersReturnEmptyWithoutContext() throws InterruptedException {
        assertThatCode(() -> {
            assertThat(runInBareThread(UserContext::getUserIdSafely)).isEmpty();
            assertThat(runInBareThread(UserContext::getLoginUser)).isEmpty();
            assertThat(runInBareThread(UserContext::getUsername)).isEmpty();
            assertThat(runInBareThread(UserContext::getTenantId)).isEmpty();
            assertThat(runInBareThread(UserContext::isLogin)).isFalse();
        }).doesNotThrowAnyException();
    }
}
