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
package cn.ypbin.starter.tracking.core;

import java.util.Map;
import java.util.Set;

/**
 * 埋点事件码常量。
 *
 * <p><strong>本文件由 {@code tools/export-tracking-events.mjs} 生成，请勿手工修改。</strong>
 * 事实源是 {@code docs/tracking-events.json}：修改目录后必须重新执行生成器，
 * 否则 CI 的「校验埋点事件目录未漂移」步骤会失败。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
public final class TrackingEventCodes {

    /** api.request.end：接口调用结束：含耗时与业务结果。 */
    public static final String API_REQUEST_END = "api.request.end";

    /** auth.user.login：登录成功。 */
    public static final String AUTH_USER_LOGIN = "auth.user.login";

    /** auth.user.logout：用户登出。 */
    public static final String AUTH_USER_LOGOUT = "auth.user.logout";

    /** system.user.export：用户数据导出（业务事件示例）。 */
    public static final String SYSTEM_USER_EXPORT = "system.user.export";

    /** ui.click.action：白名单点击：元素需显式声明 data-track 属性才会采集。 */
    public static final String UI_CLICK_ACTION = "ui.click.action";

    /** ui.page.leave：页面离开：携带本次停留时长。 */
    public static final String UI_PAGE_LEAVE = "ui.page.leave";

    /** ui.page.view：页面浏览：路由进入后上报一次。 */
    public static final String UI_PAGE_VIEW = "ui.page.view";

    /** web.error.js：前端 JS 运行时异常。 */
    public static final String WEB_ERROR_JS = "web.error.js";

    /** web.error.resource：前端资源加载失败。 */
    public static final String WEB_ERROR_RESOURCE = "web.error.resource";

    /** web.vital.report：Web Vitals 指标上报（LCP / INP / CLS / TTFB）。 */
    public static final String WEB_VITAL_REPORT = "web.vital.report";

    /** 全部已登记事件码（不可变）。未登记的事件码在采集入口即被拒绝，不会静默入库。 */
    public static final Set<String> ALL = Set.of(
        API_REQUEST_END,
        AUTH_USER_LOGIN,
        AUTH_USER_LOGOUT,
        SYSTEM_USER_EXPORT,
        UI_CLICK_ACTION,
        UI_PAGE_LEAVE,
        UI_PAGE_VIEW,
        WEB_ERROR_JS,
        WEB_ERROR_RESOURCE,
        WEB_VITAL_REPORT
    );

    /** 事件码到说明的映射（不可变），用于日志与排查。 */
    public static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
            Map.entry(API_REQUEST_END, "接口调用结束：含耗时与业务结果"),
            Map.entry(AUTH_USER_LOGIN, "登录成功"),
            Map.entry(AUTH_USER_LOGOUT, "用户登出"),
            Map.entry(SYSTEM_USER_EXPORT, "用户数据导出（业务事件示例）"),
            Map.entry(UI_CLICK_ACTION, "白名单点击：元素需显式声明 data-track 属性才会采集"),
            Map.entry(UI_PAGE_LEAVE, "页面离开：携带本次停留时长"),
            Map.entry(UI_PAGE_VIEW, "页面浏览：路由进入后上报一次"),
            Map.entry(WEB_ERROR_JS, "前端 JS 运行时异常"),
            Map.entry(WEB_ERROR_RESOURCE, "前端资源加载失败"),
            Map.entry(WEB_VITAL_REPORT, "Web Vitals 指标上报（LCP / INP / CLS / TTFB）")
    );

    private TrackingEventCodes() {
    }
}
