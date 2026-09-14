# ypbin-starter-benchmarks

性能基线（JMH 微基准）。**不发布**：这个模块存在的意义是让「复杂度/吞吐退化」在改动时能被度量出来，
而不是把数字塞进 CI 卡门禁。

## 运行

```bash
# 1) 打包（自动生成 META-INF/BenchmarkList 并 shade 成可执行 jar）
mvn -pl ypbin-starter-benchmarks -am package -DskipTests

# 2) 全部基准（默认 3 轮预热 + 5 轮测量，耗时较长）
java -jar ypbin-starter-benchmarks/target/benchmarks.jar

# 3) 快速冒烟（只看量级，不做严谨比较）
java -jar target/benchmarks.jar -f 1 -wi 2 -i 3 -r 1s -w 1s

# 4) 只跑某一类
java -jar target/benchmarks.jar TreeUtils
java -jar target/benchmarks.jar 'RequestIdUtilsBenchmark.*'
```

## 覆盖范围

| 基准 | 度量对象 | 关注点 |
|---|---|---|
| `TreeUtilsBenchmark` | `TreeUtils.build(flatList)` | 树组装是否为 O(n)：节点规模 1k → 10k，耗时增长应接近线性 |
| `RequestIdUtilsBenchmark` | `RequestIdUtils.sanitize` / `generate` | 每请求热路径：合法值、含控制字符（提前拒绝）、超长、生成四档成本 |
| `RedisSerializerBenchmark` | `RedisJsonSerializerFactory.create(null)` 的写/读 | 缓存未命中写路径（含不可变集合规范化）与读路径（多态还原）开销 |

## 使用约定

- **不做 CI 时间门禁**：挂钟断言在共享 runner 上必然抖动，只会带来假失败。基准用于人工/定期度量与对比。
- **改动涉及热路径时先测再改**：例如缓存序列化、链路 ID 校验、树组装、向量库落盘；
  记录改动前后同一命令的输出再判断收益。
- **复杂度回归靠单测兜底**：可精确断言的复杂度/正确性（如并发合并、防抖合并、原子替换、集合空值）
  由单元测试保证（见 `PersistCoordinatorTest`、`ImmutableCollectionNormalizerTest`），
  基准只负责量化。
- 基准只依赖 L1 基础能力模块，避免把上层能力拖进度量环境（`ypbin-starter-architecture-tests`
  的分层规则已把本包列入「可横跨各层」的开发工具）。
