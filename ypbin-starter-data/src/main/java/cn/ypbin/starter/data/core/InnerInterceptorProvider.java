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
package cn.ypbin.starter.data.core;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.springframework.core.Ordered;

/**
 * MyBatis-Plus 内部拦截器贡献者扩展点。
 *
 * <p>MyBatis-Plus 的内部拦截器有严格的顺序要求（如多租户、数据权限必须先于分页）。
 * data 模块收集容器内所有本接口实现，按 {@link #getOrder() order} 升序装入唯一的
 * {@code MybatisPlusInterceptor}。扩展模块（多租户、数据权限等）通过实现本接口按正确顺序
 * 贡献自己的拦截器，无需接管整个拦截器 Bean，从而多个扩展可共存且顺序可控。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface InnerInterceptorProvider extends Ordered {

    /** 分页拦截器建议 order，扩展应使用更小值以排在其前 */
    int ORDER_PAGINATION = 1000;

    /** 多租户拦截器建议 order */
    int ORDER_TENANT = 100;

    /** 数据权限拦截器建议 order */
    int ORDER_DATA_PERMISSION = 200;

    /**
     * 提供的内部拦截器。
     *
     * @return 内部拦截器实例
     */
    InnerInterceptor getInnerInterceptor();
}
