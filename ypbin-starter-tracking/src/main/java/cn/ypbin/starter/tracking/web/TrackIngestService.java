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

import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import cn.ypbin.starter.tracking.core.TrackRejectionReason;
import cn.ypbin.starter.tracking.core.TrackRequestContext;
import cn.ypbin.starter.tracking.core.TrackingEventCatalog;
import cn.ypbin.starter.tracking.core.TrackingEventCatalog.EventSchema;
import cn.ypbin.starter.tracking.core.TrackingEventCatalog.PropertySchema;
import cn.ypbin.starter.tracking.support.TrackCounters;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * 采集端点的校验与入队逻辑（Controller 保持极薄，逻辑全部在这里）。
 *
 * <p><strong>请求线程上只做便宜的事</strong>：白名单裁剪、长度截断、按字节估算体积。这些都是
 * 线性、无 IO、无锁的操作；重量级工作（UA 解析、IP 归属地、落库）一律留给消费者线程或宿主的落点实现
 * ——采集端点与业务接口<strong>共用同一个 Servlet 线程池</strong>，在请求线程上做重活等于让匿名流量挤占业务容量。</p>
 *
 * <p><strong>字符串超长会被截断</strong>（按目录声明长度），这是明确契约而非静默降级；
 * 而<strong>属性名不在白名单、类型不符、体积超限一律拒绝并给出原因</strong>。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
public class TrackIngestService {

    /** 非字符串属性在体积估算中的固定字节数（键另计） */
    private static final int NON_STRING_VALUE_BYTES = 8;

    private static final int MAX_APP_ID_LENGTH = 64;

    /** User-Agent 原串上限（与落库列宽一致） */
    private static final int MAX_USER_AGENT_LENGTH = 512;

    /** 链路 ID 上限（与落库列宽一致） */
    private static final int MAX_TRACE_ID_LENGTH = 64;

    /** IPv6 总段数 */
    private static final int IPV6_GROUP_COUNT = 8;

    /** IPv6 保留前缀段数（/64） */
    private static final int IPV6_PREFIX_GROUPS = 4;

    /** 单个 IPv6 段的最大字符数 */
    private static final int MAX_HEXTET_LENGTH = 4;

    private final TrackRecorder recorder;

    private final TrackCounters counters;

    private final TrackingEventCatalog catalog;

    private final int maxEventsPerRequest;

    private final int maxPayloadBytes;

    private final @Nullable String defaultAppId;

    private final boolean anonymizeClientIp;

    /**
     * 创建采集服务（客户端 IP 按默认策略脱敏）。
     *
     * @param recorder            采集门面
     * @param counters            计数器
     * @param catalog             事件目录
     * @param maxEventsPerRequest 单请求事件数上限
     * @param maxPayloadBytes     单事件属性体积上限（估算字节）
     * @param defaultAppId        缺省应用标识；为空表示不写该维度
     */
    public TrackIngestService(TrackRecorder recorder, TrackCounters counters, TrackingEventCatalog catalog,
                              int maxEventsPerRequest, int maxPayloadBytes, @Nullable String defaultAppId) {
        this(recorder, counters, catalog, maxEventsPerRequest, maxPayloadBytes, defaultAppId, true);
    }

    /**
     * 创建采集服务。
     *
     * @param recorder            采集门面
     * @param counters            计数器
     * @param catalog             事件目录
     * @param maxEventsPerRequest 单请求事件数上限
     * @param maxPayloadBytes     单事件属性体积上限（估算字节）
     * @param defaultAppId        缺省应用标识；为空表示不写该维度
     * @param anonymizeClientIp   是否对客户端 IP 脱敏（IPv4 保留 /24、IPv6 保留 /64）
     */
    public TrackIngestService(TrackRecorder recorder, TrackCounters counters, TrackingEventCatalog catalog,
                              int maxEventsPerRequest, int maxPayloadBytes, @Nullable String defaultAppId,
                              boolean anonymizeClientIp) {
        this.recorder = recorder;
        this.counters = counters;
        this.catalog = catalog;
        this.maxEventsPerRequest = maxEventsPerRequest;
        this.maxPayloadBytes = maxPayloadBytes;
        this.defaultAppId = defaultAppId;
        this.anonymizeClientIp = anonymizeClientIp;
    }

    /**
     * 处理一次批量上报（不携带请求上下文）。
     *
     * @param request 请求体；为 {@code null} 时按空批次处理
     * @return 逐项计数结果
     */
    public TrackIngestResp ingest(@Nullable TrackIngestReq request) {
        return ingest(request, TrackRequestContext.EMPTY);
    }

    /**
     * 处理一次批量上报（携带采集时刻的请求上下文）。
     *
     * <p>IP 与 UA 只能从 HTTP 请求上取，而落库发生在消费者线程上——那时请求早已结束。
     * 因此必须在请求线程上捕获、随事件带下去。</p>
     *
     * @param request 请求体；为 {@code null} 时按空批次处理
     * @param context 采集时刻捕获的 IP / User-Agent / 链路 ID
     * @return 逐项计数结果
     */
    public TrackIngestResp ingest(@Nullable TrackIngestReq request, TrackRequestContext context) {
        List<TrackIngestEvent> events = resolveEvents(request);
        int received = events.size();
        // 事件级拒绝（事件未被接收）与属性级问题（事件仍被接收，只是丢了个别属性）分开统计：
        // 混在一起会产出「received=1 / accepted=1 / rejected=2」这种自相矛盾的结果
        Map<TrackRejectionReason, Integer> rejections = new EnumMap<>(TrackRejectionReason.class);
        Map<TrackRejectionReason, Integer> attributeIssues = new EnumMap<>(TrackRejectionReason.class);

        if (received > maxEventsPerRequest) {
            addReason(rejections, attributeIssues, TrackRejectionReason.OVER_REQUEST_LIMIT,
                received - maxEventsPerRequest);
        }
        int processLimit = Math.min(received, maxEventsPerRequest);
        String appId = resolveAppId(request);
        List<TrackEvent> accepted = new ArrayList<>(processLimit);
        for (int index = 0; index < processLimit; index++) {
            convert(events.get(index), appId, context, rejections, attributeIssues).ifPresent(accepted::add);
        }

        int queued = recorder.record(accepted);
        int rejected = sum(rejections);
        if (rejected > 0) {
            rejectCounters(rejections);
        }
        if (!attributeIssues.isEmpty()) {
            attributeIssueCounters(attributeIssues);
        }
        return new TrackIngestResp(received, queued, rejected, accepted.size() - queued,
            toCodeMap(rejections), toCodeMap(attributeIssues));
    }

    private Optional<TrackEvent> convert(TrackIngestEvent raw, @Nullable String appId,
                                         TrackRequestContext context,
                                         Map<TrackRejectionReason, Integer> rejections,
                                         Map<TrackRejectionReason, Integer> attributeIssues) {
        String eventId = raw.eventId();
        String eventCode = raw.eventCode();
        if (eventId == null || eventId.isBlank() || eventCode == null || eventCode.isBlank()) {
            addReason(rejections, attributeIssues, TrackRejectionReason.MISSING_REQUIRED_FIELD, 1);
            return Optional.empty();
        }
        EventSchema schema = catalog.schema(eventCode);
        if (schema == null) {
            addReason(rejections, attributeIssues, TrackRejectionReason.UNREGISTERED, 1);
            return Optional.empty();
        }
        String rawEventTime = raw.eventTime();
        if (rawEventTime == null || rawEventTime.isBlank()) {
            addReason(rejections, attributeIssues, TrackRejectionReason.MISSING_REQUIRED_FIELD, 1);
            return Optional.empty();
        }
        Instant eventTime = parseEventTime(rawEventTime);
        if (eventTime == null) {
            addReason(rejections, attributeIssues, TrackRejectionReason.INVALID_EVENT_TIME, 1);
            return Optional.empty();
        }
        PayloadResult payload = prunePayload(raw.payload(), schema);
        if (payload.notAllowed() > 0) {
            addReason(rejections, attributeIssues, TrackRejectionReason.PAYLOAD_KEY_NOT_ALLOWED,
                payload.notAllowed());
        }
        if (payload.typeMismatch() > 0) {
            addReason(rejections, attributeIssues, TrackRejectionReason.PAYLOAD_TYPE_MISMATCH,
                payload.typeMismatch());
        }
        if (estimatePayloadBytes(payload.payload()) > maxPayloadBytes) {
            addReason(rejections, attributeIssues, TrackRejectionReason.PAYLOAD_TOO_LARGE, 1);
            return Optional.empty();
        }
        TrackRequestContext eventContext = anonymizeClientIp ? context.withClientIp(maskIp(context.clientIp()))
            : context;
        return Optional.of(new TrackEvent(eventId, eventCode, eventTime, appId,
            raw.sessionId(), raw.anonId(), raw.pageUrl(), raw.referrer(),
            raw.durationMs(), raw.success(), payload.payload(), eventContext));
    }

    /**
     * 客户端 IP 脱敏：IPv4 保留前三段（/24）、IPv6 保留前四段（/64）。
     *
     * <p>识别不出形态时<strong>原样返回</strong>：宁可留一个未脱敏的异常值，也不要把它错改成另一段网段。</p>
     */
    /**
     * 客户端 IP 脱敏：IPv4 保留 /24、IPv6 保留 /64。
     *
     * <p>IPv6 必须先把 {@code ::} 压缩形态展开再按段掩码——只按「段数」判断会同时踩两个坑：
     * {@code fe80::1} 只有 3 段会被整串放过（等于没脱敏），而 {@code 2001:db8::1} 恰好 4 段又会被拼成
     * {@code 2001:db8::1::}（非法地址）。</p>
     *
     * <p>识别不出形态时<strong>原样返回</strong>：宁可留一个异常值，也不要把它错改成另一段网段。</p>
     */
    private @Nullable String maskIp(@Nullable String ip) {
        if (ip == null || ip.isBlank()) {
            return ip;
        }
        return ip.indexOf(':') >= 0 ? maskIpv6(ip) : maskIpv4(ip);
    }

    private String maskIpv4(String ip) {
        int lastDot = ip.lastIndexOf('.');
        return lastDot > 0 ? ip.substring(0, lastDot) + ".0" : ip;
    }

    private String maskIpv6(String ip) {
        Optional<List<String>> parsed = expandIpv6(ip);
        if (parsed.isEmpty()) {
            return ip;
        }
        List<String> groups = parsed.get();
        for (int index = IPV6_PREFIX_GROUPS; index < groups.size(); index++) {
            groups.set(index, "0");
        }
        return String.join(":", groups);
    }

    /**
     * 把 IPv6 地址展开成固定 8 段。
     *
     * @param ip 地址字面量
     * @return 展开后的 8 段；形态不合法（含 IPv4 内嵌、段数不符、非法字符）时为空
     */
    private Optional<List<String>> expandIpv6(String ip) {
        if (ip.indexOf('.') >= 0) {
            // IPv4 内嵌形态（如 ::ffff:10.1.2.3）不在处理范围内，原样返回交由上层决策
            return Optional.empty();
        }
        String[] halves = ip.split("::", -1);
        if (halves.length > 2) {
            return Optional.empty();
        }
        Optional<List<String>> head = toHextets(halves[0]);
        Optional<List<String>> tail = halves.length == 2 ? toHextets(halves[1]) : Optional.of(List.of());
        if (head.isEmpty() || tail.isEmpty()) {
            return Optional.empty();
        }
        List<String> headGroups = head.get();
        List<String> tailGroups = tail.get();
        int present = headGroups.size() + tailGroups.size();
        if (halves.length == 2) {
            if (present > IPV6_GROUP_COUNT) {
                return Optional.empty();
            }
            List<String> groups = new ArrayList<>(headGroups);
            for (int index = present; index < IPV6_GROUP_COUNT; index++) {
                groups.add("0");
            }
            groups.addAll(tailGroups);
            return Optional.of(groups);
        }
        return present == IPV6_GROUP_COUNT ? Optional.of(headGroups) : Optional.empty();
    }

    private Optional<List<String>> toHextets(String part) {
        if (part.isEmpty()) {
            return Optional.of(List.of());
        }
        List<String> groups = new ArrayList<>();
        for (String group : part.split(":", -1)) {
            if (!isHextet(group)) {
                return Optional.empty();
            }
            groups.add(group);
        }
        return Optional.of(groups);
    }

    private static boolean isHextet(String group) {
        if (group.isEmpty() || group.length() > MAX_HEXTET_LENGTH) {
            return false;
        }
        for (int index = 0; index < group.length(); index++) {
            char ch = group.charAt(index);
            boolean hex = (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private PayloadResult prunePayload(@Nullable Map<String, Object> rawPayload, EventSchema schema) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            return new PayloadResult(Map.of(), 0, 0);
        }
        Map<String, Object> pruned = new LinkedHashMap<>();
        int notAllowed = 0;
        int typeMismatch = 0;
        for (Map.Entry<String, Object> entry : rawPayload.entrySet()) {
            PropertySchema property = schema.properties().get(entry.getKey());
            if (property == null) {
                notAllowed++;
                continue;
            }
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            Object normalized = normalize(value, property);
            if (normalized == null) {
                typeMismatch++;
                continue;
            }
            pruned.put(entry.getKey(), normalized);
        }
        return new PayloadResult(Map.copyOf(pruned), notAllowed, typeMismatch);
    }

    private @Nullable Object normalize(Object value, PropertySchema property) {
        return switch (property.type()) {
            case "string" -> value instanceof String text ? truncate(text, property.maxLength()) : null;
            case "integer" -> value instanceof Number number ? number.longValue() : null;
            case "number" -> value instanceof Number number ? number.doubleValue() : null;
            case "boolean" -> value instanceof Boolean bool ? bool : null;
            default -> null;
        };
    }

    /**
     * 按上限截断文本。
     *
     * <p>刻意接受 {@code null} 并原样返回：调用点大量来自可空的采集上下文（IP/UA/链路 ID），
     * 让每个调用点各自判空只会把同一件事写很多遍。</p>
     */
    private @Nullable String truncate(@Nullable String text, int maxLength) {
        return text == null || maxLength <= 0 || text.length() <= maxLength
            ? text
            : text.substring(0, maxLength);
    }

    private @Nullable Instant parseEventTime(String eventTime) {
        try {
            return Instant.parse(eventTime);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private long estimatePayloadBytes(Map<String, Object> payload) {
        long total = 0L;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            total += utf8Length(entry.getKey());
            Object value = entry.getValue();
            total += value instanceof String text ? utf8Length(text) : NON_STRING_VALUE_BYTES;
        }
        return total;
    }

    private int utf8Length(@Nullable String text) {
        return text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length;
    }

    private @Nullable String resolveAppId(@Nullable TrackIngestReq request) {
        String fromRequest = request == null ? null : request.appId();
        if (fromRequest != null && !fromRequest.isBlank()) {
            return truncate(fromRequest, MAX_APP_ID_LENGTH);
        }
        if (defaultAppId == null || defaultAppId.isBlank()) {
            return null;
        }
        return truncate(defaultAppId, MAX_APP_ID_LENGTH);
    }

    private static List<TrackIngestEvent> resolveEvents(@Nullable TrackIngestReq request) {
        if (request == null || request.events() == null) {
            return List.of();
        }
        return request.events();
    }

    private void rejectCounters(Map<TrackRejectionReason, Integer> reasons) {
        for (Map.Entry<TrackRejectionReason, Integer> entry : reasons.entrySet()) {
            counters.rejected(entry.getKey(), entry.getValue());
        }
    }

    private void attributeIssueCounters(Map<TrackRejectionReason, Integer> issues) {
        for (Map.Entry<TrackRejectionReason, Integer> entry : issues.entrySet()) {
            counters.attributeIssue(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 按原因自带的级别路由到「事件级拒绝」或「属性级问题」两个桶。
     *
     * <p>路由依据写在枚举上，调用点只需给原因与条数——避免每个调用点各自判断级别而写错。</p>
     */
    private static void addReason(Map<TrackRejectionReason, Integer> rejections,
                                  Map<TrackRejectionReason, Integer> attributeIssues,
                                  TrackRejectionReason reason, int count) {
        Map<TrackRejectionReason, Integer> target =
            reason.getLevel() == TrackRejectionReason.Level.EVENT ? rejections : attributeIssues;
        target.merge(reason, count, Integer::sum);
    }

    private static int sum(Map<TrackRejectionReason, Integer> reasons) {
        int total = 0;
        for (int value : reasons.values()) {
            total += value;
        }
        return total;
    }

    private static Map<String, Integer> toCodeMap(Map<TrackRejectionReason, Integer> reasons) {
        if (reasons.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<TrackRejectionReason, Integer> entry : reasons.entrySet()) {
            result.put(entry.getKey().getCode(), entry.getValue());
        }
        return Map.copyOf(result);
    }

    /**
     * 属性裁剪结果。
     *
     * @param payload      裁剪后的属性（不可变）
     * @param notAllowed   不在白名单的属性数
     * @param typeMismatch 类型不符的属性数
     */
    private record PayloadResult(Map<String, Object> payload, int notAllowed, int typeMismatch) {
    }
}
