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
package cn.ypbin.starter.security.identity;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 启动期检查「identity 模式 + 宿主持有本地登录端点」的冲突并告警。
 *
 * <p><b>为什么需要它</b>（2026-10-20 真实事故）：某宿主（auth）没显式声明
 * {@code ypbin.security.identity.enabled}，继承了共享配置的 {@code true}。identity 模式下
 * {@code StpUtil} 的 token 取自<strong>网关签发的身份头</strong>，而 {@code /auth/login} 请求本身没有
 * 身份头 ⇒ 建会话失败 ⇒ <strong>正确口令登录也返回 401</strong>（错误口令反而 409，故障形态极迷惑）。
 * 丢失后：CI 不红、启动无告警，直到用户登录才暴露。</p>
 *
 * <p><b>检测方式</b>：容器里的 {@link RequestMappingHandlerMapping} 中存在路径含 {@code /login} 的映射，
 * 即视为「宿主持有本地登录端点」。这是<b>结构性</b>判据（不依赖某个具体类名），因此对自研登录接口同样有效；
 * 只做告警不做拦截——是否关闭 identity 是宿主的部署决策。</p>
 *
 * <p><b>绝不打断启动</b>：检测在任何异常下都只记 WARN（含完整堆栈）。这是旁路诊断，不是安全校验；
 * 让一个「提示」把服务搞挂，正是我们在 BaseEnum 零命中诊断上踩过的坑。</p>
 *
 * @author wenbin
 * @since 2026-10-20
 */
public final class IdentityLoginConflictWarner implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(IdentityLoginConflictWarner.class);

    /** 判定「本地登录端点」的路径片段。 */
    private static final String LOGIN_PATH_HINT = "/login";

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;

    /**
     * 创建告警器。
     *
     * @param handlerMappings 请求映射（可能为空：宿主未启用 Web MVC 时不存在）
     */
    public IdentityLoginConflictWarner(ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
        this.handlerMappings = handlerMappings;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            List<String> loginPatterns = detectLoginPatterns();
            if (loginPatterns.isEmpty()) {
                return;
            }
            log.warn("[ypbin-starter] identity 模式已开启，但检测到本地登录端点 {}。identity 模式下 "
                    + "StpUtil 的 token 取自网关签发的身份头，本地登录无法建立会话（正确口令也会返回 401）。"
                    + "若本服务自己签发会话（如 auth），请显式置 ypbin.security.identity.enabled=false；"
                    + "identity 只给『位于可信网关之后、不自己签发会话』的下游服务。",
                loginPatterns);
        } catch (RuntimeException ex) {
            // 旁路诊断：任何异常都不得打断启动，但必须留完整堆栈（禁静默降级）
            log.warn("[ypbin-starter] identity/本地登录 冲突检查失败（不影响启动，仅本次告警缺失）", ex);
        }
    }

    /**
     * 找出所有路径含 {@code /login} 的映射（供告警与单测使用）。
     *
     * @return 匹配到的路径模式（去重、排序）；无冲突时为空列表
     */
    List<String> detectLoginPatterns() {
        List<String> hits = new ArrayList<>();
        for (RequestMappingHandlerMapping mapping : orderedMappings()) {
            for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
                for (String pattern : info.getPatternValues()) {
                    if (pattern.contains(LOGIN_PATH_HINT) && !hits.contains(pattern)) {
                        hits.add(pattern);
                    }
                }
            }
        }
        hits.sort(null);
        return hits;
    }

    /**
     * 取出容器里的全部 {@link RequestMappingHandlerMapping}（允许为空）。
     *
     * @return 映射实例列表
     */
    private List<RequestMappingHandlerMapping> orderedMappings() {
        List<RequestMappingHandlerMapping> mappings = new ArrayList<>();
        for (@Nullable RequestMappingHandlerMapping mapping : handlerMappings.orderedStream().toList()) {
            if (mapping != null) {
                mappings.add(mapping);
            }
        }
        return mappings;
    }
}
