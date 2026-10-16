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
package cn.ypbin.starter.messaging.sse;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * {@link SseStreamBridge} 自动配置。
 *
 * <p><strong>放在 {@code sse} 包而非 {@code messaging.autoconfigure} 的原因</strong>：自动配置注册依赖
 * <strong>类级</strong> {@code @ConditionalOnClass(Flux.class)}，而 {@code reactor-core} 在 messaging 里是
 * <em>optional</em> 依赖。{@code @ConditionalOnClass} 在类被<strong>内省</strong>（加载元数据）阶段就生效，
 * 因此「配置类自身的方法签名/注解是否触发 {@code Flux} 解析」决定成败——把它与桥接实现放在同一包、彼此邻近，
 * 便于后续维护者一眼看出「这两个类的类路径前置条件必须保持一致」。</p>
 *
 * <p><strong>为何独立于 {@link cn.ypbin.starter.messaging.autoconfigure.SseAutoConfiguration}</strong>：
 * 流式桥接只依赖 {@code Flux} + {@code SseEmitter}，与「订阅端点 / 票据 / 跨实例扇出」那套长连接能力无关。
 * 若挂到 {@code SseAutoConfiguration}（其带 {@code @ConditionalOnProperty(ypbin.sse.enabled=true)}），
 * 只想用流式对话、不开 SSE 订阅端点的宿主就<strong>拿不到这个 Bean 且没有任何提示</strong>——正是本仓
 * 反复强调的「静默不生效」形态。故本配置独立注册，只按类存在性与 Web 形态判定。</p>
 *
 * <p><strong>为何两个 {@code @ConditionalOnClass}</strong>：{@code reactor-core} 与 {@code spring-webmvc}
 * 在 messaging 里都是 <em>optional</em> 依赖。任一缺席时本类必须整体不生效，否则会在纯 servlet（无 Reactor）
 * 宿主上装配出一个「调用即 {@code NoClassDefFoundError}」的 Bean。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
@AutoConfiguration
@ConditionalOnClass({Flux.class, SseEmitter.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SseStreamBridgeAutoConfiguration {

    /**
     * 流式桥接 Bean（无状态、线程安全，可单例复用）。宿主可定义同类型 Bean 覆盖，故带
     * {@code @ConditionalOnMissingBean}。
     *
     * @return 桥接实例
     */
    @Bean
    @ConditionalOnMissingBean
    public SseStreamBridge sseStreamBridge() {
        return new SseStreamBridge();
    }
}
