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
package cn.ypbin.starter.messaging.sms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SmsService#sendBatch(String, List, Map)} 默认实现用例（逐号按模板渲染）。
 *
 * @author wenbin
 * @since 2026-11-06
 */
class SmsServiceDefaultMethodTest {

    /** 记录每次模板发送的简化实现：手机号含 bad 时失败。 */
    private static final class RecordingSmsService implements SmsService {

        private final List<String> calls = new ArrayList<>();

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public boolean send(String phone, String message) {
            return true;
        }

        @Override
        public boolean send(String phone, Map<String, String> variables) {
            return true;
        }

        @Override
        public boolean sendByTemplate(String phone, String templateId, Map<String, String> variables) {
            calls.add(phone + "|" + templateId + "|" + variables.get("code"));
            return !phone.contains("bad");
        }

        @Override
        public boolean sendByConfig(String configId, String phone, String templateId,
                                    Map<String, String> variables) {
            return true;
        }

        @Override
        public boolean sendBatch(List<String> phones, Map<String, String> variables) {
            return true;
        }
    }

    @Test
    @DisplayName("默认群发按模板逐号渲染变量，全部成功才返回 true")
    void rendersTemplatePerPhone() {
        RecordingSmsService service = new RecordingSmsService();

        boolean ok = service.sendBatch("tpl-1", List.of("13800000001", "13800000002"), Map.of("code", "1234"));

        assertThat(ok).isTrue();
        assertThat(service.calls)
            .containsExactly("13800000001|tpl-1|1234", "13800000002|tpl-1|1234");
    }

    @Test
    @DisplayName("任一号码失败即整体返回 false")
    void anyFailureReturnsFalse() {
        RecordingSmsService service = new RecordingSmsService();

        assertThat(service.sendBatch("tpl-1", List.of("13800000001", "bad-number"), Map.of("code", "1")))
            .isFalse();
    }

    @Test
    @DisplayName("空号码列表不发送且不谎报成功")
    void emptyPhonesIsFailure() {
        RecordingSmsService service = new RecordingSmsService();

        assertThat(service.sendBatch("tpl-1", List.of(), Map.of("code", "1"))).isFalse();
        assertThat(service.calls).isEmpty();
    }
}
