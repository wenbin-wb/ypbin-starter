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

import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.tracking.core.TrackRejectionReason;
import cn.ypbin.starter.tracking.support.TrackCounters;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * 采集端点的请求体体积闸门。
 *
 * <p>为什么单独做这一层：采集端点与业务接口共用同一个 Servlet 线程池，限制单请求体积是避免
 * 匿名流量挤占业务容量的第一道手段（第二道是单请求事件数上限，第三道是队列丢弃）。</p>
 *
 * <p><strong>为什么必须读流而不是只看 {@code Content-Length}</strong>：分块传输
 * （{@code Transfer-Encoding: chunked}）时长度未知（值为 -1），只看声明值等于直接放行
 * ——匿名方可借此绕过体积限制。因此本过滤器<strong>实际读取请求体并强制上限</strong>：
 * 最多读 {@code maxRequestBytes + 1} 字节，超出即拒绝。</p>
 *
 * <p>读入的字节以缓存包装的形式继续传给后续链路，不影响控制器解析请求体；缓存空间由
 * {@code maxRequestBytes} 界定（默认 256KB），不会无界增长。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
public class TrackIngestSizeFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TrackIngestSizeFilter.class);

    private final ObjectMapper objectMapper;

    private final TrackCounters counters;

    private final int maxRequestBytes;

    /**
     * 创建体积闸门。
     *
     * @param objectMapper    用于输出统一响应体
     * @param counters        计数器
     * @param maxRequestBytes 单请求体上限（字节）
     */
    public TrackIngestSizeFilter(ObjectMapper objectMapper, TrackCounters counters, int maxRequestBytes) {
        this.objectMapper = objectMapper;
        this.counters = counters;
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > maxRequestBytes) {
            reject(response, declaredLength);
            return;
        }
        byte[] body;
        try {
            body = readBounded(request.getInputStream());
        } catch (RequestTooLargeException ex) {
            reject(response, ex.getReadBytes());
            return;
        }
        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    /** 最多读取 {@code maxRequestBytes} 字节；多读一个字节用于判定超限。 */
    private byte[] readBounded(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[maxRequestBytes + 1];
        int read = 0;
        while (read < buffer.length) {
            int current = inputStream.read(buffer, read, buffer.length - read);
            if (current < 0) {
                break;
            }
            read += current;
        }
        if (read > maxRequestBytes) {
            throw new RequestTooLargeException(read);
        }
        byte[] body = new byte[read];
        System.arraycopy(buffer, 0, body, 0, read);
        return body;
    }

    private void reject(HttpServletResponse response, long detectedBytes) throws IOException {
        counters.rejected(TrackRejectionReason.REQUEST_TOO_LARGE, 1);
        // 只记录数值，不记录请求内容：请求体可能含用户可控数据（避免日志注入与隐私扩散）
        log.warn("[ypbin-starter] tracking ingest rejected: request body exceeds limit {} (detected {} bytes).",
            maxRequestBytes, detectedBytes);
        response.setStatus(GlobalErrorCode.SUCCESS.getCode());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getOutputStream(), R.fail(GlobalErrorCode.PAYLOAD_TOO_LARGE));
    }

    /** 体积超限信号；携带已读字节数，便于日志说明「检测到多少」。 */
    private static final class RequestTooLargeException extends IOException {

        @Serial
        private static final long serialVersionUID = 1L;

        private final transient long readBytes;

        private RequestTooLargeException(long readBytes) {
            super("tracking ingest request body too large");
            this.readBytes = readBytes;
        }

        private long getReadBytes() {
            return readBytes;
        }
    }

    /** 把已缓存的请求体回放给后续链路。 */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new ByteArrayServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    /** 基于字节数组的 {@link ServletInputStream}。 */
    private static final class ByteArrayServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        private ByteArrayServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            return delegate.read(bytes, offset, length);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // 本过滤器不做异步读取：请求体已在过滤阶段读完并缓存
            throw new UnsupportedOperationException("async read is not supported by tracking size filter");
        }
    }
}
