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
package cn.ypbin.starter.tracking.web;

import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.tools.limiter.RateLimit;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 埋点采集端点（匿名可写，是本模块唯一的对外开放入口）。
 *
 * <p>只做路由分发，校验与入队全部在 {@link TrackIngestService}——Controller 保持极薄是本仓库的硬约束。</p>
 *
 * <p><strong>安全前提</strong>：本端点默认关闭（{@code ypbin.tracking.ingest-enabled=true} 才注册），
 * 且必须由宿主把它加入网关的免登录白名单；未加白名单时未登录事件会被网关拒绝，属预期行为。</p>
 *
 * <p>限流按「方法 + 客户端 IP」生效。注意：部署在网关之后时，需开启
 * {@code ypbin.tools.rate-limit.trust-forwarded=true}，否则所有请求的对端地址都是网关本身，
 * 按 IP 限流会退化成全局单桶。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
@RestController
@RequestMapping("${ypbin.tracking.path:/tracking/ingest}")
public class TrackIngestController {

    private final TrackIngestService ingestService;

    /**
     * 创建采集端点。
     *
     * @param ingestService 校验与入队服务
     */
    public TrackIngestController(TrackIngestService ingestService) {
        this.ingestService = ingestService;
    }

    /**
     * 接收批量埋点上报。
     *
     * @param request 请求体
     * @return 统一响应体，{@code data} 为逐项计数
     */
    @PostMapping
    @RateLimit(key = "ypbin-tracking-ingest", window = 1, count = 20, byIp = true,
        message = "埋点上报过于频繁，请稍后再试")
    public R<TrackIngestResp> ingest(@RequestBody TrackIngestReq request) {
        return R.ok(ingestService.ingest(request));
    }
}
