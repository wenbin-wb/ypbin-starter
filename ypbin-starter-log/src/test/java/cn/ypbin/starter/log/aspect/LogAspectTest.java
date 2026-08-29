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
package cn.ypbin.starter.log.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.log.core.LogUserProvider;
import cn.ypbin.starter.log.enums.Include;
import cn.ypbin.starter.log.event.LogEvent;
import cn.ypbin.starter.log.model.LogRecord;
import cn.ypbin.starter.log.support.LogCollector;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.event.EventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link LogAspect} 真实 AOP 代理织入测试。
 *
 * <p>注册一个同步事件监听器捕获 {@link LogEvent}，断言切面正确采集元信息、
 * 记录成功/失败、并遵守 {@code ignore}。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
class LogAspectTest {

    static final List<LogRecord> CAPTURED = new ArrayList<>();

    private AnnotationConfigApplicationContext ctx;
    private DemoService service;

    @BeforeEach
    void setUp() {
        CAPTURED.clear();
        ctx = new AnnotationConfigApplicationContext(Config.class);
        service = ctx.getBean(DemoService.class);
    }

    @AfterEach
    void tearDown() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void loggedMethod_publishesRecordWithMeta() {
        service.create();
        assertThat(CAPTURED).hasSize(1);
        LogRecord record = CAPTURED.getFirst();
        assertThat(record.getDescription()).isEqualTo("创建");
        assertThat(record.getModule()).isEqualTo("演示");
        assertThat(record.isSuccess()).isTrue();
        assertThat(record.getTimeTakenMillis()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void failedMethod_recordsError_andRethrows() {
        assertThatThrownBy(() -> service.fail()).isInstanceOf(IllegalStateException.class);
        assertThat(CAPTURED).hasSize(1);
        assertThat(CAPTURED.getFirst().isSuccess()).isFalse();
        assertThat(CAPTURED.getFirst().getErrorMsg()).contains("boom");
    }

    @Test
    void ignoredMethod_publishesNothing() {
        service.ignored();
        assertThat(CAPTURED).isEmpty();
    }

    @EnableAspectJAutoProxy
    static class Config {
        @Bean
        LogCollector logCollector() {
            return new LogCollector(new NoUser(), new ObjectMapper());
        }

        @Bean
        LogAspect logAspect(LogCollector collector,
            org.springframework.context.ApplicationEventPublisher publisher) {
            return new LogAspect(collector, publisher, Include.defaultIncludes());
        }

        @Bean
        DemoService demoService() {
            return new DemoService();
        }

        @Bean
        CapturingListener capturingListener() {
            return new CapturingListener();
        }
    }

    /** 用 @EventListener 捕获：对事件类型无上界约束（LogEvent 是普通 POJO，非 ApplicationEvent 子类）。 */
    static class CapturingListener {
        @EventListener
        public void onLogEvent(LogEvent event) {
            CAPTURED.add(event.getLogRecord());
        }
    }

    static class NoUser implements LogUserProvider {
        @Override
        public Optional<Long> getCurrentUserId() {
            return Optional.empty();
        }
    }

    @Log(module = "演示")
    static class DemoService {
        @Log("创建")
        public void create() {
        }

        @Log("失败")
        public void fail() {
            throw new IllegalStateException("boom");
        }

        @Log(value = "忽略", ignore = true)
        public void ignored() {
        }
    }
}
