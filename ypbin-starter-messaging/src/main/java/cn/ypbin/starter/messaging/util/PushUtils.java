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
package cn.ypbin.starter.messaging.util;

import cn.ypbin.starter.core.util.SpringUtils;
import cn.ypbin.starter.messaging.push.PushEvent;
import cn.ypbin.starter.messaging.push.PushService;

/**
 * 实时推送静态工具。
 *
 * <p>面向非 Spring 托管场景（异步任务、事件监听、工具方法等）提供实时推送，内部委托容器中的
 * {@link PushService} 单例。首次调用时经 {@link SpringUtils} 懒获取并缓存该 Bean 引用。</p>
 *
 * <p>Spring 托管组件仍应优先直接注入 {@link PushService}，语义更清晰、更易测试；本工具仅用于拿不到
 * 注入的场景。需引入 {@code ypbin-starter-messaging} 且开启 {@code ypbin.sse.enabled=true}。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public final class PushUtils {

    private static volatile PushService service;

    private PushUtils() {
    }

    /**
     * 懒获取容器中的 {@link PushService} Bean（双重检查，线程安全）。
     *
     * @return 推送服务实例
     */
    private static PushService service() {
        if (service == null) {
            synchronized (PushUtils.class) {
                if (service == null) {
                    service = SpringUtils.getBean(PushService.class);
                }
            }
        }
        return service;
    }

    /**
     * 向指定用户推送。
     *
     * @param userId 用户标识
     * @param event  事件名
     * @param data   载荷
     */
    public static void sendToUser(String userId, String event, Object data) {
        service().sendToUser(userId, event, data);
    }

    /**
     * 向指定用户推送事件对象。
     *
     * @param userId 用户标识
     * @param event  事件
     */
    public static void sendToUser(String userId, PushEvent event) {
        service().sendToUser(userId, event);
    }

    /**
     * 向全体在线用户广播。
     *
     * @param event 事件名
     * @param data  载荷
     */
    public static void broadcast(String event, Object data) {
        service().broadcast(event, data);
    }

    /**
     * 向全体在线用户广播事件对象。
     *
     * @param event 事件
     */
    public static void broadcast(PushEvent event) {
        service().broadcast(event);
    }

    /**
     * 指定用户当前是否在线。
     *
     * @param userId 用户标识
     * @return 是否在线
     */
    public static boolean isOnline(String userId) {
        return service().isOnline(userId);
    }

    /**
     * 当前在线用户数。
     *
     * @return 在线用户数
     */
    public static int onlineCount() {
        return service().onlineCount();
    }
}
