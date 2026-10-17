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
package cn.ypbin.starter.tools.lock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试用续期调度器：不启线程、不真等待，由测试显式 {@link #fireAll()} 触发。
 *
 * <p>用它才能把「续期是否注册、注册了几次、间隔多少、退出时是否注销」变成可断言的确定性事实，
 * 而不是靠 sleep 猜时序。真实线程语义另由 {@link ScheduledLockRenewalSchedulerTest} 覆盖。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
final class ManualLockRenewalScheduler implements LockRenewalScheduler {

    private final List<Task> tasks = new ArrayList<>();

    private final List<Duration> intervals = new ArrayList<>();

    private int scheduleCount;

    private int cancelCount;

    @Override
    public Registration schedule(Runnable renewalTask, Duration interval) {
        Task task = new Task(renewalTask);
        tasks.add(task);
        intervals.add(interval);
        scheduleCount++;
        return task;
    }

    /** 触发一次所有「尚未注销」的续期任务（模拟调度到点）。 */
    void fireAll() {
        for (Task task : List.copyOf(tasks)) {
            if (!task.cancelled) {
                task.renewalTask.run();
            }
        }
    }

    /** 注册次数。 */
    int scheduleCount() {
        return scheduleCount;
    }

    /** 注销次数（含幂等重复调用被去重后的实际次数）。 */
    int cancelCount() {
        return cancelCount;
    }

    /** 每次注册时的间隔（按注册顺序）。 */
    List<Duration> intervals() {
        return List.copyOf(intervals);
    }

    /** 一次注册：记录任务与注销状态。 */
    private final class Task implements Registration {

        private final Runnable renewalTask;

        private boolean cancelled;

        private Task(Runnable renewalTask) {
            this.renewalTask = renewalTask;
        }

        @Override
        public void cancel() {
            if (!cancelled) {
                cancelled = true;
                cancelCount++;
            }
        }
    }
}
