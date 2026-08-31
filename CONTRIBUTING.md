# 贡献指南

欢迎参与 ypbin-starter 的贡献！本文件说明如何提交 Issue、编写代码与发起 Pull Request。

## 1. 提交 Issue

- **Bug 报告**：请使用 [Bug 模板](.github/ISSUE_TEMPLATE/bug_report.yml)，说明复现步骤、期望行为与实际行为，附上相关日志与版本号。
- **功能建议**：请使用 [Feature 模板](.github/ISSUE_TEMPLATE/feature_request.yml)，说明使用场景与期望能力。
- 提交前先搜索是否已有相同 Issue，避免重复。

## 2. 环境准备

- JDK 21（`maven.compiler.release=21`）
- Maven 3.9+（本地用 IDEA 内置 Maven 亦可）
- 构建命令：`mvn clean install`（含 spotless 格式校验、全量单测与覆盖率门禁）

## 3. 开发规范

开发前必读仓库根目录 `AGENTS.md` 与 `docs/MODULES.md`，核心红线：

- 类级 Javadoc 带 `@author wenbin` + `@since`，禁 `@date`；顶部 Apache License 头
- 业务实体与 DTO 一律 `@Getter @Setter`，禁 `@Data`（配置绑定类除外）
- 禁内联全限定类名（FQCN），统一顶部 import
- 禁魔法值：状态/类型判断必须用常量或枚举
- 集合查无数据返回空集合，禁 null；`@Transactional` 必须显式 `rollbackFor`
- 禁静默吞异常；`log.error` 必须传完整堆栈
- 禁品牌词（blade/continew 等参考项目）

## 4. 提交规范

- 提交信息遵循 Conventional Commits：`type(scope): 描述`，如 `feat(ai): 新增多模型切换`、`fix(cache): 修复 TTL 失效`
- 提交前运行 `mvn spotless:apply` 保证格式通过
- 不要添加 `Co-Authored-By` 尾注

## 5. 发起 Pull Request

1. 从 `master` 拉取最新代码，新建功能分支（如 `feat/xxx`、`fix/xxx`）
2. 按 [PR 模板](.github/pull_request_template.md) 填写描述
3. 确保 CI 通过（构建 + 测试 + 覆盖率门禁 + CodeQL）
4. 新功能必须有对应单元测试；改动公开 API 需同步更新 CHANGELOG 与文档

## 6. 版本与发布

- 版本管理见 [RELEASING.md](RELEASING.md)：`revision` 一处改全模块生效
- 破坏性变更升 MAJOR，功能新增升 MINOR，修复升 PATCH；发布节奏遵循 RELEASING.md 第五节约定
- 发布由维护者执行（打 tag 触发流水线），外部贡献者无需操作
