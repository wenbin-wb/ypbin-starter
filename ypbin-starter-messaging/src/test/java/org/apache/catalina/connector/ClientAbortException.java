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
package org.apache.catalina.connector;

import java.io.IOException;

/**
 * Tomcat {@code ClientAbortException} 的<strong>类名替身</strong>（仅测试编译用，不是真实容器实现）。
 *
 * <p>为什么需要它：{@code SseStreamBridge#isClientDisconnectFromUpstream(Throwable)} 与
 * {@code SseStreamBridge#isClientDisconnectOnWrite(Throwable)} 对「客户端中止」容器异常按
 * <strong>类名</strong>匹配——messaging 只依赖 {@code spring-webmvc}（可选），Servlet 容器实现不在编译期
 * classpath，无法直接引用真实类型。本替身只提供同名类以覆盖该判定分支；生产运行时命中的是容器 Jar 里的
 * 真实类，行为一致（实测 tomcat-embed-core 11.0.24：
 * {@code ClientAbortException extends org.apache.coyote.BadRequestException extends IOException}，
 * 且同时存在无参 / {@code String} / {@code Throwable} 构造器）。</p>
 *
 * <p>本文件位于测试源码树，不会进入发布产物。若将来 Tomcat 进入 messaging 的测试 classpath，
 * 应删除本替身以避免同名类歧义。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
public class ClientAbortException extends IOException {

    public ClientAbortException() {
        super();
    }

    public ClientAbortException(String message) {
        super(message);
    }

    public ClientAbortException(String message, Throwable cause) {
        super(message, cause);
    }
}
