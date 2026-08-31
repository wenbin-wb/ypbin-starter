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
package cn.ypbin.starter.sensitivewords.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.starter.sensitivewords.autoconfigure.SensitiveWordProperties;
import cn.ypbin.starter.sensitivewords.core.SensitiveWordService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

/**
 * 敏感词过滤切面测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class SensitiveWordFilterAspectTest {

    static class Demo {
        @cn.ypbin.starter.sensitivewords.annotation.SensitiveWordFilter
        private String content;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    @Test
    void shouldFilterArgsAndProceed() throws Throwable {
        SensitiveWordService service = mock(SensitiveWordService.class);
        SensitiveWordProperties props = new SensitiveWordProperties();
        SensitiveWordFilterAspect aspect = new SensitiveWordFilterAspect(service, props);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        Demo demo = new Demo();
        demo.setContent("需要过滤的词");
        Object[] args = {demo};
        when(pjp.getArgs()).thenReturn(args);
        when(pjp.proceed(args)).thenReturn("result");

        Object result = aspect.filterArgs(pjp);

        assertThat(result).isEqualTo("result");
        verify(service).filter("需要过滤的词", '*');
    }

    @Test
    void shouldHandleNullArgs() throws Throwable {
        SensitiveWordService service = mock(SensitiveWordService.class);
        SensitiveWordProperties props = new SensitiveWordProperties();
        SensitiveWordFilterAspect aspect = new SensitiveWordFilterAspect(service, props);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(null);
        when(pjp.proceed(org.mockito.ArgumentMatchers.any())).thenReturn("ok");

        assertThat(aspect.filterArgs(pjp)).isEqualTo("ok");
    }
}
