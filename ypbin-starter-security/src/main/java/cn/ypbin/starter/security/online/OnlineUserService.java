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
package cn.ypbin.starter.security.online;

import java.util.List;

/**
 * 在线用户服务。
 *
 * <p>基于 Sa-Token 会话枚举在线登录记录，支持查询、按 token/用户强制下线。表与页面由业务系统实现，
 * starter 只提供运行时能力。用户名/昵称/租户/客户端等展示字段来自登录时写入会话的
 * {@link cn.ypbin.starter.security.core.LoginUser}；IP/浏览器/操作系统等可选字段来自登录时通过
 * {@link OnlineUserHelper} 记录的终端扩展信息。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface OnlineUserService {

    /**
     * 列出全部在线用户（一个 token 一条，多端登录多条）。
     *
     * @return 在线用户列表，按登录时间倒序
     */
    List<OnlineUser> list();

    /**
     * 按用户名/昵称关键字过滤在线用户。
     *
     * @param keyword 用户名或昵称关键字，可空（空则返回全部）
     * @return 在线用户列表
     */
    List<OnlineUser> list(String keyword);

    /**
     * 指定用户的全部在线记录。
     *
     * @param userId 用户 ID
     * @return 在线用户列表
     */
    List<OnlineUser> listByUserId(Long userId);

    /**
     * 在线记录总数（token 维度）。
     *
     * @return 在线数量
     */
    long count();

    /**
     * 按 token 强制下线（踢某个具体登录设备）。
     *
     * @param token 令牌值
     */
    void kickoutByToken(String token);

    /**
     * 按用户强制下线其全部登录设备。
     *
     * @param userId 用户 ID
     */
    void kickoutByUserId(Long userId);
}
