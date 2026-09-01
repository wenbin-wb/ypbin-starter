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

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.security.identity.IdentityContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * 平台用户访问守卫切面。
 *
 * <p>拦截标注 {@link PlatformAccess} 的类/方法，校验当前登录用户（来自
 * {@link IdentityContext}）是否为平台用户；非平台用户抛出
 * {@link BusinessException}（403）。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@Aspect
@Component
public class PlatformAccessAspect {

    private final PlatformUserChecker checker;

    public PlatformAccessAspect(PlatformUserChecker checker) {
        this.checker = checker;
    }

    @Before("@within(platformAccess)")
    public void guardClass(PlatformAccess platformAccess) {
        checkPlatformAccess();
    }

    @Before("@annotation(platformAccess)")
    public void guardMethod(PlatformAccess platformAccess) {
        checkPlatformAccess();
    }

    private void checkPlatformAccess() {
        Long userId = IdentityContext.getUserId().orElse(null);
        if (!checker.isPlatformUser(userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "仅平台用户可访问");
        }
    }
}
