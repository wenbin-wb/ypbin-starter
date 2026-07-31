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
package cn.ypbin.starter.messaging.push;

/**
 * 统一实时推送门面。
 *
 * <p>屏蔽底层通道差异（SSE / WebSocket），业务方一行代码即可向指定用户或全体在线用户推送事件。
 * 典型场景：全局未读消息提醒（推指定用户）、扫码登录状态变更（推指定用户）、大屏数据刷新（广播）。</p>
 *
 * <p>默认实现会把事件同时投递到已启用的通道；单机版基于内存连接表，多实例扇出需另配（见文档）。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public interface PushService {

    /**
     * 向指定用户推送（该用户的所有在线连接都会收到）。
     *
     * @param userId 用户标识
     * @param event  事件名
     * @param data   载荷
     */
    void sendToUser(String userId, String event, Object data);

    /**
     * 向指定用户推送事件对象。
     *
     * @param userId 用户标识
     * @param event  事件
     */
    void sendToUser(String userId, PushEvent event);

    /**
     * 向全体在线用户广播。
     *
     * @param event 事件名
     * @param data  载荷
     */
    void broadcast(String event, Object data);

    /**
     * 向全体在线用户广播事件对象。
     *
     * @param event 事件
     */
    void broadcast(PushEvent event);

    /**
     * 指定用户当前是否有在线连接。
     *
     * @param userId 用户标识
     * @return 是否在线
     */
    boolean isOnline(String userId);

    /**
     * 当前在线用户数（去重）。
     *
     * @return 在线用户数
     */
    int onlineCount();
}
