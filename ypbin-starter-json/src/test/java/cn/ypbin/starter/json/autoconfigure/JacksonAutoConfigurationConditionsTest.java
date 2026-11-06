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
package cn.ypbin.starter.json.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.json.dict.DictCache;
import cn.ypbin.starter.json.dict.DictItem;
import cn.ypbin.starter.json.dict.DictProvider;
import cn.ypbin.starter.json.ref.RefTextManager;
import cn.ypbin.starter.json.ref.RefTextProvider;
import cn.ypbin.starter.json.ref.RefTextResolver;
import cn.ypbin.starter.json.ref.RefTextResponseAdvice;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link JacksonAutoConfiguration} 的条件装配用例（{@code ApplicationContextRunner} + {@code FilteredClassLoader}）。
 *
 * <p>本模块有 5 个条件 Bean：{@code DictCache}/{@code RefTextManager} 依赖宿主提供的 Provider，
 * {@code RefTextResponseAdvice} 依赖可选的 spring-webmvc。这些条件此前<strong>零门禁</strong>——
 * 条件写错（如把 {@code @ConditionalOnBean} 当成必然成立）只会在宿主启动时表现为「Bean 静默不装配」。
 * 这里两态各测一遍：无依赖时不装配且不失败，有依赖时装配的是预期实现。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
class JacksonAutoConfigurationConditionsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));

    @Test
    @DisplayName("无 Provider：字典/引用翻译 Bean 不装配，但主配置照常生效")
    void withoutProviders_translationBeansBackOff() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("ypbinJacksonCustomizer");
            assertThat(context).doesNotHaveBean(DictCache.class);
            assertThat(context).doesNotHaveBean(RefTextManager.class);
            assertThat(context).doesNotHaveBean(RefTextResolver.class);
        });
    }

    @Test
    @DisplayName("有 DictProvider：装配 DictCache 且可翻译")
    void withDictProvider_createsDictCache() {
        runner.withBean(DictProvider.class, () -> dictType -> List.of(new DictItem("1", "启用")))
            .run(context -> {
                assertThat(context).hasNotFailed();
                DictCache dictCache = context.getBean(DictCache.class);
                assertThat(dictCache.translate("status", "1")).isEqualTo("启用");
            });
    }

    @Test
    @DisplayName("有 RefTextProvider（servlet 环境）：装配管理器、解析器与响应增强")
    void withRefTextProviderInServletEnvironment_createsWholeChain() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withBean(RefTextProvider.class, StubRefTextProvider::new)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(RefTextManager.class);
                assertThat(context).hasSingleBean(RefTextResolver.class);
                assertThat(context)
                    .as("servlet 环境下必须自动装配预加载增强，否则列表翻译仍有 N+1")
                    .hasSingleBean(RefTextResponseAdvice.class);
            });
    }

    /**
     * 没有 RefTextProvider 时增强仍会装配（弱依赖取不到解析器），但必须是彻底的空操作——
     * 一旦这里退化成 NPE，所有接口的响应都会 500。
     */
    @Test
    @DisplayName("servlet 环境无 RefTextProvider：增强装配为空操作，不影响响应")
    void servletWithoutProvider_adviceIsNoOp() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(RefTextResolver.class);
                RefTextResponseAdvice advice = context.getBean(RefTextResponseAdvice.class);
                Object body = new Object();
                assertThat(advice.beforeBodyWrite(body, null, null, null, null, null))
                    .as("无解析器时必须原样返回响应体")
                    .isSameAs(body);
            });
    }

    /**
     * 宿主只引入本模块、<strong>没有</strong> spring-webmvc 时，嵌套的 Web 配置必须整块跳过且不影响启动。
     * 这条正是「可选依赖类型出现在 {@code @Bean} 签名里」的地基级坑：类级条件必须在内省方法之前判定。
     */
    @Test
    @DisplayName("无 spring-webmvc：Web 增强配置整体跳过，应用照常启动")
    void withoutSpringWebMvc_backsOffWithoutFailure() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withClassLoader(new FilteredClassLoader(ResponseBodyAdvice.class))
            .withBean(RefTextProvider.class, StubRefTextProvider::new)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(RefTextResponseAdvice.class);
                assertThat(context).hasSingleBean(RefTextManager.class);
            });
    }

    @Test
    @DisplayName("无 Jackson 3：自动配置整体退让，不因缺类启动失败")
    void withoutJackson_backsOffWithoutFailure() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withClassLoader(new FilteredClassLoader(ObjectMapper.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(JsonMapperBuilderCustomizer.class);
            });
    }

    @Test
    @DisplayName("ypbin.json.enabled=false：定制器不装配")
    void disabledByProperty_customizerBacksOff() {
        runner.withPropertyValues("ypbin.json.enabled=false")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean("ypbinJacksonCustomizer");
            });
    }

    /** 最小引用翻译数据源：把 id 原样当成名称，足以验证批量契约被调用。 */
    static class StubRefTextProvider implements RefTextProvider {

        @Override
        public String type() {
            return "user";
        }

        @Override
        public Map<Object, String> getNames(Collection<Object> ids) {
            return ids.stream().collect(Collectors.toMap(id -> id, Object::toString));
        }
    }
}
