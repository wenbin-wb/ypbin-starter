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
package cn.ypbin.starter.async.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.scheduling.TaskScheduler;

/**
 * 异步任务静态工具。
 *
 * <p>面向业务模块的一站式异步入口：无需注入执行器即可提交任务、编排组合、批量并发、定时调度。
 * 底层复用 {@code ypbin-starter-async} 装配的统一线程池（已挂载上下文透传装饰器，租户/用户/MDC
 * 会自动传播到异步线程）。所有 {@code run/supply} 方法默认走该线程池，也提供显式指定 {@link Executor}
 * 的重载。</p>
 *
 * <p>占位说明：本类方法较多，按「提交 / 编排 / 批量 / 调度 / 等待」分区，详见各方法注释。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public final class AsyncUtils {

    private AsyncUtils() {
    }

    // ------------------------------------------------------------------ 执行器访问

    /**
     * 获取统一线程池执行器。
     *
     * @return 执行器
     */
    public static Executor executor() {
        return AsyncHolder.getExecutor();
    }

    /**
     * 获取任务调度器。
     *
     * @return 调度器
     */
    public static TaskScheduler scheduler() {
        return AsyncHolder.getScheduler();
    }

    /**
     * 异步组件是否就绪。
     *
     * @return 是否就绪
     */
    public static boolean isReady() {
        return AsyncHolder.isReady();
    }

    // ------------------------------------------------------------------ 提交：无返回

    /**
     * 提交一个无返回值的异步任务（默认线程池）。
     *
     * @param task 任务
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> run(Runnable task) {
        return CompletableFuture.runAsync(task, executor());
    }

    /**
     * 提交一个无返回值的异步任务（指定执行器）。
     *
     * @param task     任务
     * @param executor 执行器
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> run(Runnable task, Executor executor) {
        return CompletableFuture.runAsync(task, executor);
    }

    // ------------------------------------------------------------------ 提交：有返回

    /**
     * 提交一个有返回值的异步任务（默认线程池）。
     *
     * @param supplier 任务
     * @param <T>      返回类型
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor());
    }

    /**
     * 提交一个有返回值的异步任务（指定执行器）。
     *
     * @param supplier 任务
     * @param executor 执行器
     * @param <T>      返回类型
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> supply(Supplier<T> supplier, Executor executor) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    // ------------------------------------------------------------------ 编排：串行 / 组合 / 异常

    /**
     * 在异步结果上串行追加一个转换步骤（仍在异步线程）。
     *
     * @param future 上游 future
     * @param fn     转换函数
     * @param <T>    上游类型
     * @param <R>    结果类型
     * @return 新 future
     */
    public static <T, R> CompletableFuture<R> then(CompletableFuture<T> future, Function<? super T, ? extends R> fn) {
        return future.thenApplyAsync(fn, executor());
    }

    /**
     * 组合两个异步结果。
     *
     * @param first  第一个 future
     * @param second 第二个 future
     * @param fn     合并函数
     * @param <A>    第一个结果类型
     * @param <B>    第二个结果类型
     * @param <R>    合并结果类型
     * @return 合并后的 future
     */
    public static <A, B, R> CompletableFuture<R> combine(CompletableFuture<A> first, CompletableFuture<B> second,
            java.util.function.BiFunction<? super A, ? super B, ? extends R> fn) {
        return first.thenCombineAsync(second, fn, executor());
    }

    /**
     * 为异步任务提供异常兜底值。
     *
     * @param future   future
     * @param fallback 异常时的兜底函数
     * @param <T>      结果类型
     * @return 带兜底的 future
     */
    public static <T> CompletableFuture<T> withFallback(CompletableFuture<T> future,
            Function<Throwable, ? extends T> fallback) {
        return future.exceptionally(fallback);
    }

    // ------------------------------------------------------------------ 批量并发

    /**
     * 并发执行多个有返回值任务，返回结果列表（顺序与入参一致）。
     *
     * @param suppliers 任务集合
     * @param <T>       返回类型
     * @return 结果列表
     */
    public static <T> List<T> supplyAll(Collection<? extends Supplier<T>> suppliers) {
        List<CompletableFuture<T>> futures = suppliers.stream()
            .map(AsyncUtils::supply)
            .toList();
        return joinAll(futures);
    }

    /**
     * 并发处理集合中的每个元素，返回映射结果列表（顺序与入参一致）。
     *
     * @param items  元素集合
     * @param mapper 映射函数
     * @param <T>    元素类型
     * @param <R>    结果类型
     * @return 结果列表
     */
    public static <T, R> List<R> mapAll(Collection<T> items, Function<? super T, ? extends R> mapper) {
        List<CompletableFuture<R>> futures = items.stream()
            .<CompletableFuture<R>>map(item -> supply(() -> mapper.apply(item)))
            .toList();
        return joinAll(futures);
    }

    /**
     * 并发执行多个无返回值任务，全部完成后返回。
     *
     * @param tasks 任务集合
     */
    public static void runAll(Collection<? extends Runnable> tasks) {
        List<CompletableFuture<Void>> futures = tasks.stream()
            .map(AsyncUtils::run)
            .toList();
        allOf(futures).join();
    }

    // ------------------------------------------------------------------ 等待

    /**
     * 等待所有 future 完成。
     *
     * @param futures future 集合
     * @return 聚合 future
     */
    public static CompletableFuture<Void> allOf(Collection<? extends CompletableFuture<?>> futures) {
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * 任意一个 future 完成即返回。
     *
     * @param futures future 集合
     * @return 聚合 future
     */
    public static CompletableFuture<Object> anyOf(Collection<? extends CompletableFuture<?>> futures) {
        return CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * 阻塞收集所有 future 的结果（顺序保持）。
     *
     * @param futures future 列表
     * @param <T>     结果类型
     * @return 结果列表
     */
    public static <T> List<T> joinAll(List<? extends CompletableFuture<T>> futures) {
        allOf(futures).join();
        return futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
    }

    /**
     * 带超时地获取 future 结果，超时抛出异常。
     *
     * @param future  future
     * @param timeout 超时时间
     * @param <T>     结果类型
     * @return 结果
     */
    public static <T> T join(CompletableFuture<T> future, Duration timeout) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待异步结果被中断", e);
        } catch (ExecutionException e) {
            // 剥离出真正的业务异常，用 CompletionException 包装，与 CompletableFuture.join() 语义一致，
            // 避免堆栈嵌套过深（IllegalStateException -> ExecutionException -> 真实异常）掩盖病因
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            throw new CompletionException(cause);
        } catch (TimeoutException e) {
            throw new IllegalStateException("等待异步结果超时", e);
        }
    }

    // ------------------------------------------------------------------ 调度

    /**
     * 延迟执行一次。
     *
     * @param task  任务
     * @param delay 延迟时长
     */
    public static void schedule(Runnable task, Duration delay) {
        scheduler().schedule(task, java.time.Instant.now().plus(delay));
    }

    /**
     * 固定速率周期执行（不等待上次完成）。
     *
     * @param task   任务
     * @param period 周期
     */
    public static void scheduleAtFixedRate(Runnable task, Duration period) {
        scheduler().scheduleAtFixedRate(task, period);
    }

    /**
     * 固定延迟周期执行（上次完成后再等待固定间隔）。
     *
     * @param task  任务
     * @param delay 间隔
     */
    public static void scheduleWithFixedDelay(Runnable task, Duration delay) {
        scheduler().scheduleWithFixedDelay(task, delay);
    }

    // ------------------------------------------------------------------ 兼容工具

    /**
     * 把已有 future 集合按顺序转为结果列表（浅拷贝，防御空集合）。
     *
     * @param futures future 集合
     * @param <T>     结果类型
     * @return 结果列表
     */
    public static <T> List<T> collect(Collection<? extends CompletableFuture<T>> futures) {
        return joinAll(new ArrayList<>(futures));
    }
}
