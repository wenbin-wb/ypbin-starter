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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.identity.IdentityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link PlatformAccessAspect} 单元测试。
 *
 * @author wenbin
 * @since 2026-09-01
 */
class PlatformAccessAspectTest {

    @AfterEach
    void tearDown() {
        IdentityContext.clear();
    }

    @Test
    void shouldRejectWhenNotLoggedInAndCheckerStrict() {
        PlatformAccessAspect strict = new PlatformAccessAspect(new StrictChecker());
        assertThatThrownBy(() -> strict.guardClass(marker()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅平台用户可访问");
    }

    @Test
    void shouldRejectWhenCheckerRejects() {
        IdentityContext.setLoginUser(user(99L));
        PlatformAccessAspect aspect = new PlatformAccessAspect(new RejectChecker());
        assertThatThrownBy(() -> aspect.guardClass(marker()))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldPassWhenUserPresentAndCheckerAccepts() {
        IdentityContext.setLoginUser(user(1L));
        PlatformAccessAspect aspect = new PlatformAccessAspect(new AcceptChecker());
        aspect.guardClass(marker());
        aspect.guardMethod(marker());
    }

    @Test
    void shouldPassWhenNotLoggedInAndCheckerDefault() {
        PlatformAccessAspect aspect = new PlatformAccessAspect(new PlatformUserChecker() {
        });
        aspect.guardClass(marker());
    }

    @Test
    void defaultCheckerAllowsAll() {
        assertThat(new PlatformUserChecker() {
        }.isPlatformUser(42L)).isTrue();
    }

    private LoginUser user(long id) {
        LoginUser user = new LoginUser();
        user.setId(id);
        return user;
    }

    private PlatformAccess marker() {
        return new PlatformAccess() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return PlatformAccess.class;
            }
        };
    }

    private static class StrictChecker implements PlatformUserChecker {

        @Override
        public boolean isPlatformUser(Long userId) {
            return userId != null;
        }
    }

    private static class RejectChecker implements PlatformUserChecker {

        @Override
        public boolean isPlatformUser(Long userId) {
            return false;
        }
    }

    private static class AcceptChecker implements PlatformUserChecker {

        @Override
        public boolean isPlatformUser(Long userId) {
            return userId != null && userId == 1L;
        }
    }
}
