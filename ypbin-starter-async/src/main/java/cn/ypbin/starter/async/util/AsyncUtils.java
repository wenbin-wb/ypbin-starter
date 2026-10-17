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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
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
 * <p><strong>超时纪律</strong>：批量入口提供「无超时」与「带 {@link Duration} 超时」两组重载。无超时版本
 * （{@link #joinAll(List)}、{@link #runAll(Collection)}、{@link #supplyAll(Collection)}、
 * {@link #mapAll(Collection, Function)}）在任一任务永久挂起时会<strong>永久阻塞调用线程</strong>，只适合
 * 确定会结束的本地任务；涉及远程调用、IO、第三方依赖等不可控任务时务必用带超时重载——超时会取消未完成的
 * future，但请注意 {@code CompletableFuture.cancel(true)} <strong>不会中断在途任务</strong>
 * （见 {@link #joinAll(List, Duration)} 的说明）。</p>
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
            BiFunction<? super A, ? super B, ? extends R> fn) {
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
     * <p><strong>无超时</strong>：只要有一个任务永久挂起，调用线程就永久阻塞（Web 线程池耗尽的典型根因）。
     * 远程调用、IO、第三方依赖等不可控任务请改用 {@link #supplyAll(Collection, Duration)}。</p>
     *
     * @param suppliers 任务集合
     * @param <T>       返回类型
     * @return 结果列表
     */
    public static <T> List<T> supplyAll(Collection<? extends Supplier<T>> suppliers) {
        return joinAll(submitAll(suppliers));
    }

    /**
     * 并发执行多个有返回值任务（带超时），返回结果列表（顺序与入参一致）。
     *
     * <p>超时、失败与取消语义完全见 {@link #joinAll(List, Duration)}——本方法只是「提交 + 带超时收集」的
     * 便捷组合。</p>
     *
     * @param suppliers 任务集合
     * @param timeout   整体超时时间
     * @param <T>       返回类型
     * @return 结果列表
     */
    public static <T> List<T> supplyAll(Collection<? extends Supplier<T>> suppliers, Duration timeout) {
        return joinAll(submitAll(suppliers), timeout);
    }

    /**
     * 并发处理集合中的每个元素，返回映射结果列表（顺序与入参一致）。
     *
     * <p><strong>无超时</strong>：同 {@link #supplyAll(Collection)}——不可控任务请改用
     * {@link #mapAll(Collection, Function, Duration)}。</p>
     *
     * @param items  元素集合
     * @param mapper 映射函数
     * @param <T>    元素类型
     * @param <R>    结果类型
     * @return 结果列表
     */
    public static <T, R> List<R> mapAll(Collection<T> items, Function<? super T, ? extends R> mapper) {
        return joinAll(submitAllMapped(items, mapper));
    }

    /**
     * 并发处理集合中的每个元素（带超时），返回映射结果列表（顺序与入参一致）。
     *
     * <p>超时、失败与取消语义完全见 {@link #joinAll(List, Duration)}。</p>
     *
     * @param items   元素集合
     * @param mapper  映射函数
     * @param timeout 整体超时时间
     * @param <T>     元素类型
     * @param <R>     结果类型
     * @return 结果列表
     */
    public static <T, R> List<R> mapAll(Collection<T> items, Function<? super T, ? extends R> mapper,
            Duration timeout) {
        return joinAll(submitAllMapped(items, mapper), timeout);
    }

    /**
     * 并发执行多个无返回值任务，全部完成后返回。
     *
     * <p><strong>无超时</strong>：只要有一个任务永久挂起，调用线程就永久阻塞。远程调用、IO、第三方依赖等
     * 不可控任务请改用 {@link #runAll(Collection, Duration)}。</p>
     *
     * @param tasks 任务集合
     */
    public static void runAll(Collection<? extends Runnable> tasks) {
        allOf(submitAllRunnable(tasks)).join();
    }

    /**
     * 并发执行多个无返回值任务（带超时），全部完成后返回。
     *
     * <p>超时、失败与取消语义完全见 {@link #joinAll(List, Duration)}：超时抛
     * {@link IllegalStateException} 并取消未完成的 future（取消<strong>不会中断</strong>在途任务）。</p>
     *
     * @param tasks   任务集合
     * @param timeout 整体超时时间
     */
    public static void runAll(Collection<? extends Runnable> tasks, Duration timeout) {
        awaitAll(submitAllRunnable(tasks), timeout);
    }

    private static <T> List<CompletableFuture<T>> submitAll(Collection<? extends Supplier<T>> suppliers) {
        return suppliers.stream().map(AsyncUtils::supply).toList();
    }

    private static <T, R> List<CompletableFuture<R>> submitAllMapped(Collection<T> items,
            Function<? super T, ? extends R> mapper) {
        return items.stream()
            .<CompletableFuture<R>>map(item -> supply(() -> mapper.apply(item)))
            .toList();
    }

    private static List<CompletableFuture<Void>> submitAllRunnable(Collection<? extends Runnable> tasks) {
        return tasks.stream().map(AsyncUtils::run).toList();
    }

    // ------------------------------------------------------------------ 等待

    /**
     * 等待所有 future 完成。
     *
     * <p><strong>语义提醒</strong>：{@code CompletableFuture.allOf} 的契约是「所有 future 都完成后」才完成，
     * 任一个失败不会使其提前失败——因此 {@code join()} 抛出时集合内所有 future 均已完成（已在 JDK 21
     * 实测确认，见 {@code AsyncUtilsTest#joinAllShouldWaitForAllFuturesBeforeThrowing}）。
     * 若需「首个失败即返回」，请勿依赖本方法，应改用带超时/竞速的编排方式（批量等待请用
     * {@link #joinAll(List, Duration)}）。
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
     * <p>本方法通过 {@code allOf} 等待全部 future 完成后返回结果；任一 future 失败时，等其余 future
     * 全部结束后抛出与 {@code CompletableFuture.join()} 一致的 {@link CompletionException}，不额外取消
     * 任何 future——抛出时不存在「在途」future，无可取消对象（详见 {@link #allOf} 的语义提醒）。
     *
     * <p><strong>无超时</strong>：只要有一个 future 永不完成，调用线程就永久阻塞。远程调用、IO、第三方
     * 依赖等不可控任务请改用 {@link #joinAll(List, Duration)}；本方法只适合「确定会结束」的本地任务。</p>
     *
     * @param futures future 列表
     * @param <T>     结果类型
     * @return 结果列表
     */
    public static <T> List<T> joinAll(List<? extends CompletableFuture<T>> futures) {
        allOf(futures).join();
        return collectResults(futures);
    }

    /**
     * 阻塞收集所有 future 的结果（顺序保持），整体等待不超过 {@code timeout}。
     *
     * <p><strong>超时语义</strong>：超时抛出 {@link IllegalStateException}（cause 为
     * {@link TimeoutException}，与 {@link #join(CompletableFuture, Duration)} 风格一致），并在抛出前取消
     * 所有<strong>尚未完成</strong>的 future；已完成的 future 不受影响。本方法<strong>不返回部分结果</strong>，
     * 超时即整体失败，避免调用方拿到「看起来完整、实则缺项」的列表。</p>
     *
     * <p><strong>取消的真实效果（勿误读）</strong>：{@link CompletableFuture#cancel(boolean)} 的 JDK 契约
     * 明确说明 {@code mayInterruptIfRunning} 在 CompletableFuture 中<strong>无效</strong>（中断不用于控制
     * 其处理），即取消<strong>只把 future 置为已取消状态</strong>，<strong>不会中断</strong>正在执行任务的
     * 线程、也不会让任务停下来。因此超时返回后，在途任务可能仍在后台继续运行并继续占用执行器线程；
     * 要真正止损，需任务自身可被打断或带有底层超时（如 HTTP/RPC 超时），本工具无法代劳。</p>
     *
     * <p><strong>非 fail-fast</strong>：与 {@link #joinAll(List)} 一样基于 {@code allOf}，任一 future 失败
     * 不会让本方法提前返回——若某个任务已失败、其余任务仍在途，本方法会一直等它们结束；等到的是
     * {@link CompletionException}（全部结束）还是超时异常取决于谁先到，失败先发生而超时后发生时，
     * 抛出的是超时异常而非失败异常。需要「首个失败即返回」请自行用 {@code anyOf}/竞速编排。</p>
     *
     * @param futures future 列表
     * @param timeout 整体超时时间（非正数表示不等待：未完成即视为超时）
     * @param <T>     结果类型
     * @return 结果列表
     */
    public static <T> List<T> joinAll(List<? extends CompletableFuture<T>> futures, Duration timeout) {
        awaitAll(futures, timeout);
        return collectResults(futures);
    }

    /** 按入参顺序取结果；调用前必须已确认全部 future 结束 */
    private static <T> List<T> collectResults(List<? extends CompletableFuture<T>> futures) {
        return futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
    }

    /**
     * 等待全部 future 结束；超时则取消未完成者并抛出超时异常。
     *
     * @param futures future 集合
     * @param timeout 整体超时时间
     */
    private static void awaitAll(Collection<? extends CompletableFuture<?>> futures, Duration timeout) {
        Duration limit = Objects.requireNonNull(timeout, "timeout 不能为空");
        CompletableFuture<Void> all = allOf(futures);
        try {
            all.get(limit.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待异步批量结果被中断", e);
        } catch (ExecutionException e) {
            // 剥离出真正的业务异常，用 CompletionException 包装，与 joinAll(List) / join(future, timeout) 一致
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            throw new CompletionException(cause);
        } catch (TimeoutException e) {
            cancelIncomplete(futures);
            throw new IllegalStateException("等待异步批量结果超时（超时 " + limit + "），已取消未完成的 future", e);
        }
    }

    /** 取消尚未完成的 future；已完成者不受影响（对已完成 future 调 cancel 是空操作） */
    private static void cancelIncomplete(Collection<? extends CompletableFuture<?>> futures) {
        for (CompletableFuture<?> future : futures) {
            if (!future.isDone()) {
                // true 仅表达「请求取消」的统一语义：CompletableFuture 不据此中断在途线程（见 joinAll 超时重载）
                future.cancel(true);
            }
        }
    }

    /**
     * 带超时地获取 future 结果，超时抛出异常。
     *
     * <p>超时抛 {@link IllegalStateException}（cause 为 {@link TimeoutException}）；<strong>不取消</strong>传入的
     * future（单个 future 的取消由调用方决定）。批量等待与「超时取消未完成者」请用
     * {@link #joinAll(List, Duration)}。</p>
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
        scheduler().schedule(task, Instant.now().plus(delay));
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
     * <p><strong>无超时</strong>：内部走 {@link #joinAll(List)}，只要有一个 future 永不完成就永久阻塞；
     * 不可控任务请直接调用 {@link #joinAll(List, Duration)}。</p>
     *
     * @param futures future 集合
     * @param <T>     结果类型
     * @return 结果列表
     */
    public static <T> List<T> collect(Collection<? extends CompletableFuture<T>> futures) {
        return joinAll(new ArrayList<>(futures));
    }
}
