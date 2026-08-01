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
package cn.ypbin.starter.job.autoconfigure;

import cn.ypbin.starter.job.core.JobManager;
import java.lang.reflect.Method;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * 集群防重锁工厂。
 *
 * <p>桥接 tools 模块的 {@code LockService}（若在容器中存在）：反射调用其 {@code tryLock/unlock}，
 * 避免 job 模块对 tools 的硬依赖。未引入 tools 时返回单机无锁实现（永远抢锁成功）——单节点部署安全，
 * 多节点务必引入 tools 以获得真正的分布式防重。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
final class JobLockFactory {

    private static final Logger log = LoggerFactory.getLogger(JobLockFactory.class);

    private static final String LOCK_SERVICE_CLASS = "cn.ypbin.starter.tools.lock.LockService";

    private JobLockFactory() {
    }

    static JobManager.JobLock create(ApplicationContext ctx) {
        Object lockService = resolveLockService(ctx);
        if (lockService == null) {
            log.warn("[ypbin-starter] 未检测到分布式锁（tools 的 LockService），定时任务集群防重退化为单机无锁；"
                + "多节点部署请引入 ypbin-starter-tools。");
            return new NoopJobLock();
        }
        return new DelegatingJobLock(lockService);
    }

    private static Object resolveLockService(ApplicationContext ctx) {
        try {
            Class<?> type = Class.forName(LOCK_SERVICE_CLASS);
            String[] names = ctx.getBeanNamesForType(type);
            return names.length > 0 ? ctx.getBean(type) : null;
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /** 单机无锁：永远抢锁成功。 */
    static final class NoopJobLock implements JobManager.JobLock {
        @Override
        public boolean tryLock(String key, String owner, Duration ttl) {
            return true;
        }

        @Override
        public boolean unlock(String key, String owner) {
            return true;
        }
    }

    /** 反射委托给 tools 的 LockService。 */
    static final class DelegatingJobLock implements JobManager.JobLock {
        private final Object lockService;
        private final Method tryLock;
        private final Method unlock;

        DelegatingJobLock(Object lockService) {
            this.lockService = lockService;
            try {
                this.tryLock = lockService.getClass().getMethod("tryLock", String.class, String.class, Duration.class);
                this.unlock = lockService.getClass().getMethod("unlock", String.class, String.class);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("LockService 方法签名不兼容", e);
            }
        }

        @Override
        public boolean tryLock(String key, String owner, Duration ttl) {
            try {
                return (boolean) tryLock.invoke(lockService, key, owner, ttl);
            } catch (ReflectiveOperationException e) {
                log.warn("[ypbin-starter] 分布式锁 tryLock 调用失败，本次按未抢到处理: {}", e.getMessage());
                return false;
            }
        }

        @Override
        public boolean unlock(String key, String owner) {
            try {
                return (boolean) unlock.invoke(lockService, key, owner);
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }
    }
}
