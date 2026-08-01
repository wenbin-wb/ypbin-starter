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
package cn.ypbin.starter.storage.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.storage.exception.StorageException;
import cn.ypbin.starter.storage.model.FileInfo;
import cn.ypbin.starter.storage.model.UploadContext;
import cn.ypbin.starter.storage.strategy.StorageStrategy;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link StorageRouter} 路由与动态刷新测试。
 *
 * @author wenbin
 * @since 2026-08-01
 */
class StorageRouterTest {

    /** 极简假策略，仅用于路由测试 */
    private StorageStrategy strategy(String platform) {
        return new StorageStrategy() {
            @Override
            public String platform() {
                return platform;
            }

            @Override
            public String defaultBucket() {
                return "default";
            }

            @Override
            public FileInfo upload(UploadContext context) {
                return null;
            }

            @Override
            public InputStream download(String bucket, String path) {
                return null;
            }

            @Override
            public void delete(String bucket, String path) {
                // no-op
            }

            @Override
            public boolean exists(String bucket, String path) {
                return false;
            }

            @Override
            public String url(String bucket, String path, Duration expire) {
                return null;
            }
        };
    }

    @Test
    void routeByPlatformAndDefault() {
        StorageRouter router = new StorageRouter(List.of(strategy("local"), strategy("oss")), "local");
        assertThat(router.route("oss").platform()).isEqualTo("oss");
        assertThat(router.route(null).platform()).isEqualTo("local");
    }

    @Test
    void routeUnknownThrows() {
        StorageRouter router = new StorageRouter(List.of(strategy("local")), "local");
        assertThatThrownBy(() -> router.route("missing")).isInstanceOf(StorageException.class);
    }

    @Test
    void rebuildAddsAndRemovesSources() {
        StorageRouter router = new StorageRouter(List.of(strategy("local")), "local");
        assertThat(router.platforms()).containsExactlyInAnyOrder("local");

        // 刷新为 oss + minio，local 应被移除
        router.rebuild(List.of(strategy("oss"), strategy("minio")), "oss");
        assertThat(router.platforms()).containsExactlyInAnyOrder("oss", "minio");
        assertThat(router.route(null).platform()).isEqualTo("oss");
        assertThatThrownBy(() -> router.route("local")).isInstanceOf(StorageException.class);
    }

    @Test
    void rebuildKeepsDefaultWhenStillPresent() {
        StorageRouter router = new StorageRouter(List.of(strategy("local"), strategy("oss")), "oss");
        router.rebuild(List.of(strategy("oss"), strategy("cos")), null);
        // 原默认 oss 仍在，保留
        assertThat(router.route(null).platform()).isEqualTo("oss");
    }
}
