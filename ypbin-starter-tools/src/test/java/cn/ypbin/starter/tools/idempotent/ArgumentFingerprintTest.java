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
package cn.ypbin.starter.tools.idempotent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ArgumentFingerprint} 的值语义与摘要性质测试。
 *
 * <p>两件事分别锁住：①**语义**——内容相同 ⇒ 指纹相同（{@code Arrays.deepHashCode} 做不到的那一条），
 * 用包内可见的 {@link ArgumentFingerprint#describe(Object[])} 断言可读展开结果；②**安全性质**——
 * 对外返回的 {@link ArgumentFingerprint#of(Object[])} 是定长摘要，**不含入参明文**（键会进 Redis 与日志）。</p>
 *
 * @author wenbin
 * @since 2026-09-24
 */
class ArgumentFingerprintTest {

    @Test
    @DisplayName("内容相同的两个 DTO 实例：指纹相同（本次修复的核心）")
    void sameContentDifferentInstances_sameFingerprint() {
        assertThat(ArgumentFingerprint.of(new Object[] {new Req(1L, "open")}))
            .isEqualTo(ArgumentFingerprint.of(new Object[] {new Req(1L, "open")}));
    }

    @Test
    @DisplayName("内容不同的 DTO：指纹不同（不能把不同请求误判为重复）")
    void differentContent_differentFingerprint() {
        assertThat(ArgumentFingerprint.of(new Object[] {new Req(1L, "open")}))
            .isNotEqualTo(ArgumentFingerprint.of(new Object[] {new Req(1L, "close")}));
        assertThat(ArgumentFingerprint.of(new Object[] {new Req(1L, "open")}))
            .isNotEqualTo(ArgumentFingerprint.of(new Object[] {new Req(2L, "open")}));
    }

    @Test
    @DisplayName("对外指纹是定长摘要，且不含入参明文（键会进 Redis 键空间与日志）")
    void digest_isOpaqueAndFixedLength() {
        String fingerprint = ArgumentFingerprint.of(new Object[] {
            new SecretReq("P@ssw0rd-明文口令", "110101199001011234")});

        assertThat(fingerprint)
            .as("SHA-256 前 32 位十六进制")
            .hasSize(32)
            .matches("[0-9a-f]{32}")
            .as("明文绝不能出现在键里")
            .doesNotContain("P@ssw0rd")
            .doesNotContain("110101199001011234");
        assertThat(ArgumentFingerprint.describe(new Object[] {
            new SecretReq("P@ssw0rd-明文口令", "110101199001011234")}))
            .as("展开结果仅供包内调试，必须能读到值（否则上面的「不含明文」就是恒真断言）")
            .contains("P@ssw0rd-明文口令");
    }

    @Test
    @DisplayName("多参数：顺序敏感，null 与空参数稳定")
    void multiArguments_andNull() {
        assertThat(ArgumentFingerprint.describe(new Object[] {1L, "open"}))
            .isNotEqualTo(ArgumentFingerprint.describe(new Object[] {"open", 1L}));
        assertThat(ArgumentFingerprint.describe(new Object[] {null})).isEqualTo("null");
        assertThat(ArgumentFingerprint.describe(new Object[] {null, null})).isEqualTo("null&null");
        assertThat(ArgumentFingerprint.describe(null)).isEmpty();
        assertThat(ArgumentFingerprint.describe(new Object[0])).isEmpty();
    }

    @Test
    @DisplayName("集合：List 保序；Set/Map 排序后稳定（跨实例一致）")
    void collections_andMaps() {
        assertThat(ArgumentFingerprint.of(new Object[] {List.of("a", "b")}))
            .as("List 有序，顺序不同即不同")
            .isNotEqualTo(ArgumentFingerprint.of(new Object[] {List.of("b", "a")}));

        Set<String> first = new LinkedHashSet<>(List.of("b", "a", "c"));
        Set<String> second = new LinkedHashSet<>(List.of("c", "a", "b"));
        assertThat(ArgumentFingerprint.of(new Object[] {first}))
            .as("Set 无序，插入顺序不同但内容相同应得同一指纹")
            .isEqualTo(ArgumentFingerprint.of(new Object[] {second}));

        Map<String, Object> mapOne = new LinkedHashMap<>();
        mapOne.put("b", 2);
        mapOne.put("a", 1);
        Map<String, Object> mapTwo = new LinkedHashMap<>();
        mapTwo.put("a", 1);
        mapTwo.put("b", 2);
        assertThat(ArgumentFingerprint.of(new Object[] {mapOne}))
            .as("Map 条目顺序不同但内容相同应得同一指纹")
            .isEqualTo(ArgumentFingerprint.of(new Object[] {mapTwo}));
    }

    @Test
    @DisplayName("容器语义不同即指纹不同：List vs Set、Optional 包裹 vs 裸值")
    void containerSemantics_areDistinguished() {
        assertThat(ArgumentFingerprint.describe(new Object[] {List.of("a", "b")}))
            .as("List 与 Set 同元素不能得到同一指纹（否则不同请求会被误判为重复）")
            .isNotEqualTo(ArgumentFingerprint.describe(
                new Object[] {new LinkedHashSet<>(List.of("a", "b"))}));
        assertThat(ArgumentFingerprint.describe(new Object[] {Optional.of("x")}))
            .isNotEqualTo(ArgumentFingerprint.describe(new Object[] {"x"}));
    }

    @Test
    @DisplayName("数组与 Optional 递归展开")
    void arraysAndOptional() {
        assertThat(ArgumentFingerprint.of(new Object[] {new String[] {"a", "b"}}))
            .isEqualTo(ArgumentFingerprint.of(new Object[] {new String[] {"a", "b"}}))
            .isNotEqualTo(ArgumentFingerprint.of(new Object[] {new String[] {"b", "a"}}));
        assertThat(ArgumentFingerprint.of(new Object[] {Optional.of("x")}))
            .isEqualTo(ArgumentFingerprint.of(new Object[] {Optional.of("x")}))
            .isNotEqualTo(ArgumentFingerprint.of(new Object[] {Optional.empty()}));
    }

    @Test
    @DisplayName("嵌套对象逐层展开，且类名带包名（跨包同名类不会撞 fingerprint）")
    void nestedObjects() {
        assertThat(ArgumentFingerprint.of(new Object[] {new Outer(new Req(1L, "open"), "x")}))
            .isEqualTo(ArgumentFingerprint.of(new Object[] {new Outer(new Req(1L, "open"), "x")}))
            .isNotEqualTo(ArgumentFingerprint.of(new Object[] {new Outer(new Req(1L, "close"), "x")}));
        assertThat(ArgumentFingerprint.describe(new Object[] {new Req(1L, "open")}))
            .as("用全限定类名，避免不同包的同类名对象撞指纹")
            .contains(Req.class.getName());
    }

    @Test
    @DisplayName("循环引用不炸栈，且留下可见标记")
    void cyclicReference_isVisible() {
        Node first = new Node("a");
        Node second = new Node("b");
        first.next = second;
        second.next = first;

        assertThat(ArgumentFingerprint.describe(new Object[] {first})).contains("!cycle");
        assertThat(ArgumentFingerprint.of(new Object[] {first})).hasSize(32);
    }

    @Test
    @DisplayName("过深对象图截断，且留下可见标记")
    void tooDeep_isTruncated() {
        Node node = new Node("leaf");
        for (int i = 0; i < 8; i++) {
            Node parent = new Node("level-" + i);
            parent.next = node;
            node = parent;
        }

        assertThat(ArgumentFingerprint.describe(new Object[] {node})).contains("!truncated");
    }

    /** 模拟业务 Req DTO：只有 getter/setter，没有 equals/hashCode。 */
    static class Req {

        private final Long deviceId;

        private final String action;

        Req(Long deviceId, String action) {
            this.deviceId = deviceId;
            this.action = action;
        }

        public Long getDeviceId() {
            return deviceId;
        }

        public String getAction() {
            return action;
        }
    }

    /** 含敏感字段的请求：用于证明摘要不落明文。 */
    static class SecretReq {

        private final String password;

        private final String idCard;

        SecretReq(String password, String idCard) {
            this.password = password;
            this.idCard = idCard;
        }

        public String getPassword() {
            return password;
        }

        public String getIdCard() {
            return idCard;
        }
    }

    static class Outer {

        private final Req req;

        private final String tag;

        Outer(Req req, String tag) {
            this.req = req;
            this.tag = tag;
        }
    }

    static class Node {

        private final String name;

        private Node next;

        Node(String name) {
            this.name = name;
        }
    }
}
