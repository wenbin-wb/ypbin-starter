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
package cn.ypbin.starter.core.blocklist;

/**
 * 参与黑名单匹配的请求信息。
 *
 * @param clientIp  客户端 IP（已按可信代理规则解析）
 * @param userAgent User-Agent（可能为空字符串）
 * @param path      应用内路径
 * @author wenbin
 * @since 2026-11-04
 */
public record BlocklistRequest(String clientIp, String userAgent, String path) {
}
