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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@link AccessLogAspect} 真实 Spring Boot 启动集成测试：完整启动应用（自动配置全加载），经 MockMvc
 * 发真实 HTTP 请求验证切面拦截 {@code @RestController} 并打印访问日志。复现 admin 生产环境行为。
 *
 * @author wenbin
 * @since 2026-08-06
 */
@SpringBootTest(classes = AccessLogAspectRealIntegrationTest.TestApplication.class,
    properties = "ypbin.log.access.enabled=true")
@AutoConfigureMockMvc
class AccessLogAspectRealIntegrationTest {

    @RestController
    static class DemoController {
        @GetMapping("/demo")
        public String demo(@RequestParam(value = "current", defaultValue = "1") int current) {
            return "ok";
        }
    }

    @org.springframework.stereotype.Controller
    static class PlainController {
        @GetMapping("/plain")
        @org.springframework.web.bind.annotation.ResponseBody
        public String plain() {
            return "plain-ok";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({DemoController.class, PlainController.class})
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        Logger accessLogger = (Logger) LoggerFactory.getLogger("ypbin.access");
        appender = new ListAppender<>();
        appender.start();
        accessLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        Logger accessLogger = (Logger) LoggerFactory.getLogger("ypbin.access");
        accessLogger.detachAppender(appender);
    }

    @Test
    void aspectLogsRealHttpRequest() throws Exception {
        // 切面 bean 已装配（enabled=true）
        assertThat(context.containsBean("accessLogAspect")).as("切面 bean 应注册").isTrue();
        // controller 被 AOP 代理是切面生效的前提
        assertThat(AopUtils.isAopProxy(context.getBean(DemoController.class)))
            .as("controller 应被 AOP 代理").isTrue();

        mockMvc.perform(get("/demo").param("current", "2"))
            .andExpect(status().isOk());

        String captured = appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.joining("\n"));
        assertThat(captured).contains("===Handler===  " + DemoController.class.getSimpleName() + ".demo");
        assertThat(captured).contains("===> GET: /demo Parameters: {\"current\":2}");
        assertThat(captured).contains("===Result===  \"ok\"");
    }

    @Test
    void aspectLogsPlainControllerWithResponseBodyMethod() throws Exception {
        // 普通 @Controller 也会被 AOP 代理——切入点 @annotation(ResponseBody) 分支生效的前提
        assertThat(AopUtils.isAopProxy(context.getBean(PlainController.class)))
            .as("方法级 @ResponseBody 所在的 controller 应被 AOP 代理").isTrue();

        mockMvc.perform(get("/plain")).andExpect(status().isOk());

        String captured = appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.joining("\n"));
        assertThat(captured).contains("===Handler===  " + PlainController.class.getSimpleName() + ".plain");
        assertThat(captured).contains("===> GET: /plain Parameters: {}");
        assertThat(captured).contains("===Result===  \"plain-ok\"");
    }
}
