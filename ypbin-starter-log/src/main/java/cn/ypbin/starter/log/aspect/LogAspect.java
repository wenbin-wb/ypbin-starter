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
package cn.ypbin.starter.log.aspect;

import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.log.enums.Include;
import cn.ypbin.starter.log.event.LogEvent;
import cn.ypbin.starter.log.model.LogRecord;
import cn.ypbin.starter.log.support.LogCollector;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * 操作日志切面。
 *
 * <p>环绕拦截 {@link Log} 标注的方法：合并类级与方法级注解、按 {@link Include} 计算最终
 * 采集集合、记录耗时与异常，最终交由 {@link LogDao} 持久化。日志采集失败不影响业务流程。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@Aspect
public class LogAspect {

    private static final Logger log = LoggerFactory.getLogger(LogAspect.class);

    private final LogCollector collector;
    private final ApplicationEventPublisher eventPublisher;
    private final Set<Include> globalIncludes;

    public LogAspect(LogCollector collector, ApplicationEventPublisher eventPublisher, Set<Include> globalIncludes) {
        this.collector = collector;
        this.eventPublisher = eventPublisher;
        this.globalIncludes = globalIncludes;
    }

    @Around("@annotation(cn.ypbin.starter.log.annotation.Log) "
        + "|| @within(cn.ypbin.starter.log.annotation.Log)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        // 用 Spring 的注解查找工具，跨接口/实现类、代理类/目标类，避免 JDK 动态代理下注解丢失
        Log methodLog = AnnotatedElementUtils.findMergedAnnotation(method, Log.class);
        Log classLog = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), Log.class);

        // 忽略判定：方法或类任一声明 ignore 即跳过
        if ((methodLog != null && methodLog.ignore()) || (classLog != null && classLog.ignore())) {
            return point.proceed();
        }

        Instant start = Instant.now();
        LogRecord record = new LogRecord();
        record.setTimestamp(start);
        Object result = null;
        try {
            result = point.proceed();
            record.setSuccess(true);
            return result;
        } catch (Throwable t) {
            record.setSuccess(false);
            record.setErrorMsg(t.getMessage());
            throw t;
        } finally {
            try {
                record.setTimeTakenMillis(Duration.between(start, Instant.now()).toMillis());
                fillMeta(record, methodLog, classLog);
                collector.collect(record, resolveIncludes(methodLog), point.getArgs(), result, null);
                // 仅发布事件，持久化由异步监听器完成，不占用业务请求线程
                eventPublisher.publishEvent(new LogEvent(record));
            } catch (Exception e) {
                // 日志采集异常绝不能影响业务
                log.warn("[ypbin-starter] operation log collect failed: {}", e.getMessage());
            }
        }
    }

    private void fillMeta(LogRecord record, Log methodLog, Log classLog) {
        if (methodLog != null && !methodLog.value().isBlank()) {
            record.setDescription(methodLog.value());
        }
        String module = "";
        if (methodLog != null && !methodLog.module().isBlank()) {
            module = methodLog.module();
        } else if (classLog != null && !classLog.module().isBlank()) {
            module = classLog.module();
        }
        record.setModule(module);
    }

    private Set<Include> resolveIncludes(Log methodLog) {
        Set<Include> result = EnumSet.noneOf(Include.class);
        result.addAll(globalIncludes);
        if (methodLog != null) {
            result.addAll(Set.of(methodLog.includes()));
            Set.of(methodLog.excludes()).forEach(result::remove);
        }
        return result;
    }
}
