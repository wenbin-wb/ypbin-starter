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
package cn.ypbin.starter.gateway.support;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.gateway.filter.InternalPathBlockGlobalFilter;
import cn.ypbin.starter.gateway.filter.InternalPathBlockProperties;
import cn.ypbin.starter.gateway.handler.GatewayExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 网关 404 信封「不可区分」回归测试（GitHub issue #96 的独立验收缺陷）。
 *
 * <p>缺陷形态：{@code InternalPathBlockGlobalFilter} 曾用 {@link GlobalErrorCode#NOT_FOUND} 的
 * 默认文案（「请求的资源不存在」），而 {@link GatewayExceptionHandler} 对未知路由硬编码
 * 「接口不存在」⇒ 两个 404 的响应体文案不同，调用方据此即可判断某个内部端点是否真实存在
 * （内部 API 面泄露）。本用例把「除动态 {@code timestamp} 外逐字段一致」钉住。</p>
 *
 * <p>本用例在<b>序列化结果</b>层面比较（各自走真实生产路径：异常处理器走
 * {@code handle}，过滤器走 {@code filter}），而不是只比较两个常量——后者测不出字段增删、
 * 序列化配置差异或某一侧自行拼装响应体。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
class GatewayNotFoundEnvelopeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final GatewayExceptionHandler HANDLER = new GatewayExceptionHandler(OBJECT_MAPPER);

    /** 验证「时间戳随请求推进」所需的间隔；远超 JVM 时钟精度，避免抖动导致误判。 */
    private static final long TIMESTAMP_GAP_MILLIS = 300L;

    @Test
    void sharedFactoryShouldCarryCanonicalNotFoundEnvelope() {
        R<Void> envelope = GatewayResponseSupport.notFound();

        assertThat(envelope.getCode()).isEqualTo(GlobalErrorCode.NOT_FOUND.getCode());
        assertThat(envelope.getMessage()).isEqualTo(GatewayResponseSupport.NOT_FOUND_MESSAGE);
        assertThat(envelope.getData()).isNull();
        assertThat(envelope.isSuccess()).isFalse();
    }

    /**
     * 未知路由（HTTP 200）与内部路径阻断（HTTP 404）的响应体必须逐字段一致。
     *
     * <p>唯一允许的差异是 HTTP 状态码（未知路由沿用既有 HTTP 200 约定；内部阻断按 issue #96 要求为 404，
     * 本用例显式断言）与动态字段 {@code timestamp}（两个来源各自 {@code LocalDateTime.now()}，无法也不应统一）。</p>
     */
    @Test
    void unknownRouteAndBlockedInternalPathShouldDifferOnlyInTimestampAndHttpStatus() {
        MockServerWebExchange unknownRoute = MockServerWebExchange.from(
            MockServerHttpRequest.get("/no-such-route"));
        HANDLER.handle(unknownRoute, new ResponseStatusException(HttpStatus.NOT_FOUND)).block();

        MockServerWebExchange blockedInternalPath = MockServerWebExchange.from(
            MockServerHttpRequest.get("/internal/secret"));
        InternalPathBlockGlobalFilter filter = new InternalPathBlockGlobalFilter(
            new InternalPathBlockProperties(), OBJECT_MAPPER);
        filter.filter(blockedInternalPath, exchange -> Mono.empty()).block();

        ObjectNode handlerJson = readBody(unknownRoute);
        ObjectNode filterJson = readBody(blockedInternalPath);

        // 前提：两个响应体都确实带动态 timestamp（否则「剔除后再比较」是空比较，测不出问题）
        assertThat(handlerJson.has("timestamp")).as("异常处理器 404 信封应带 timestamp").isTrue();
        assertThat(filterJson.has("timestamp")).as("阻断过滤器 404 信封应带 timestamp").isTrue();

        assertThat(withoutTimestamp(filterJson))
            .as("内部路径阻断与未知路由的 404 响应体必须逐字段一致，否则调用方可据此区分内部端点存在性")
            .isEqualTo(withoutTimestamp(handlerJson));

        // HTTP 状态码是有意保留的差异（issue 要求），一并钉住防止被"顺手统一"
        assertThat(unknownRoute.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(blockedInternalPath.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static ObjectNode withoutTimestamp(ObjectNode source) {
        ObjectNode copy = source.deepCopy();
        assertThat(copy.remove("timestamp")).as("剔除的必须是真实存在的 timestamp 字段").isNotNull();
        return copy;
    }

    /**
     * 回归：阻断响应的 {@code timestamp} 必须是「本次请求时刻」，不得在构造期预生成。
     *
     * <p>若预生成响应体，同一路径的连续两次拒绝会返回<b>同一个</b>时间戳（启动时刻），而未知路由的 404
     * 每次都是当前时刻——调用方据此即可区分「内部路径被阻断」与「路由不存在」，比文案不同更隐蔽。</p>
     */
    @Test
    void blockedResponseTimestampShouldNotBeFrozenAtConstruction() throws InterruptedException {
        InternalPathBlockGlobalFilter filter = new InternalPathBlockGlobalFilter(
            new InternalPathBlockProperties(), OBJECT_MAPPER);

        ObjectNode first = blockedEnvelope(filter);
        Thread.sleep(TIMESTAMP_GAP_MILLIS);
        ObjectNode second = blockedEnvelope(filter);

        assertThat(second.get("timestamp"))
            .as("阻断响应的时间戳必须随请求推进，否则调用方可据此与未知路由 404 区分")
            .isNotEqualTo(first.get("timestamp"));
    }

    private static ObjectNode blockedEnvelope(InternalPathBlockGlobalFilter filter) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/internal/secret"));
        filter.filter(exchange, ignored -> Mono.empty()).block();
        return readBody(exchange);
    }

    private static ObjectNode readBody(MockServerWebExchange exchange) {
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).as("404 信封必须写出响应体").isNotNull();
        return (ObjectNode) OBJECT_MAPPER.readTree(body);
    }
}
