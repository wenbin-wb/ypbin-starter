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
package cn.ypbin.starter.tracking.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 后端业务事件埋点注解。
 *
 * <p>标注在方法上，采集「该方法被调用了几次、耗时多久、成功还是失败」。
 * 事件码<strong>必须已登记在事件目录</strong>（{@code docs/tracking-events.json}），
 * 否则事件会在写入口被拒绝并计数——这样拼错的事件码不会静默进入存储。</p>
 *
 * <p>用法：</p>
 * <pre>{@code
 * @Tracked("auth.user.login")
 * public R<LoginResp> login(LoginReq req) { ... }
 * }</pre>
 *
 * <p><strong>本注解不携带事件属性</strong>：属性白名单由事件目录定义，凭注解无法表达；
 * 需要属性的业务事件请直接调用 {@code TrackRecorder#record}。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tracked {

    /**
     * 事件码（形如 {@code auth.user.login}，必须已登记）。
     *
     * @return 事件码
     */
    String value();
}
