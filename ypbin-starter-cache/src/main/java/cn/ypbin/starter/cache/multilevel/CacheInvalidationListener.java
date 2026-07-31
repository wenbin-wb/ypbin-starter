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
package cn.ypbin.starter.cache.multilevel;

import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

/**
 * 本地缓存失效广播订阅者。
 *
 * <p>收到集群广播的失效消息后摘除本实例 L1。忽略本实例自己发出的消息（消息体前缀为实例标识），
 * 避免自广播回环。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class CacheInvalidationListener implements MessageListener {

    private final MultiLevelCacheService cacheService;
    private final String instanceId;

    public CacheInvalidationListener(MultiLevelCacheService cacheService, String instanceId) {
        this.cacheService = cacheService;
        this.instanceId = instanceId;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        int sep = body.indexOf('|');
        if (sep < 0) {
            return;
        }
        String fromInstance = body.substring(0, sep);
        String key = body.substring(sep + 1);
        // 忽略自身发出的消息（本实例发布时已就地失效）
        if (instanceId.equals(fromInstance)) {
            return;
        }
        cacheService.invalidateLocalOnly(key);
    }
}
