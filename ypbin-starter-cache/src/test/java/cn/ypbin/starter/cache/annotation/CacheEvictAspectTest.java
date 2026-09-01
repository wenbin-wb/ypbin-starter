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
package cn.ypbin.starter.cache.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * {@link CacheEvictAspect} 单元测试。
 *
 * @author wenbin
 * @since 2026-09-01
 */
class CacheEvictAspectTest {

    static class DemoService {

        @CacheEvict(keys = {"'sys:user:id:' + #userId", "'sys:user:username:' + #username"})
        public void updateUser(Long userId, String username) {
            // 写操作
        }

        @CacheEvict(keys = {"'sys:config:key:' + #req.configKey"})
        public void updateConfig(Object req) {
            // 写操作
        }
    }

    static class ConfigReq {

        private final String configKey;

        ConfigReq(String configKey) {
            this.configKey = configKey;
        }

        public String getConfigKey() {
            return configKey;
        }
    }

    @Test
    void shouldResolveSpelKeysFromArguments() throws Exception {
        DemoService target = new DemoService();
        Method method = DemoService.class.getMethod("updateUser", Long.class, String.class);
        JoinPoint joinPoint = joinPoint(target, method, new Object[] {42L, "alice"});

        CacheEvictAspect aspect = new CacheEvictAspect();
        List<String> keys = aspect.resolveKeys(joinPoint, method.getAnnotation(CacheEvict.class));

        // 单引号字符串前缀 + #参数引用，冒号在字符串内合法，解析为实际缓存键
        assertThat(keys).containsExactly("sys:user:id:42", "sys:user:username:alice");
    }

    @Test
    void shouldResolveNestedFieldInSpel() throws Exception {
        DemoService target = new DemoService();
        Method method = DemoService.class.getMethod("updateConfig", Object.class);
        JoinPoint joinPoint = joinPoint(target, method, new Object[] {new ConfigReq("LOGIN_CAPTCHA_ENABLED")});

        CacheEvictAspect aspect = new CacheEvictAspect();
        List<String> keys = aspect.resolveKeys(joinPoint, method.getAnnotation(CacheEvict.class));

        assertThat(keys).containsExactly("sys:config:key:LOGIN_CAPTCHA_ENABLED");
    }

    @Test
    void annotationShouldCarrySpelKeys() throws Exception {
        Method method = DemoService.class.getMethod("updateUser", Long.class, String.class);
        CacheEvict annotation = method.getAnnotation(CacheEvict.class);
        assertThat(annotation.keys())
            .containsExactly("'sys:user:id:' + #userId", "'sys:user:username:' + #username");
    }

    private JoinPoint joinPoint(Object target, Method method, Object[] args) throws Exception {
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        Mockito.when(signature.getMethod()).thenReturn(method);
        JoinPoint joinPoint = Mockito.mock(JoinPoint.class);
        Mockito.when(joinPoint.getSignature()).thenReturn(signature);
        Mockito.when(joinPoint.getTarget()).thenReturn(target);
        Mockito.when(joinPoint.getArgs()).thenReturn(args);
        return joinPoint;
    }
}
