# 安全政策（Security Policy）

感谢你帮助让 `ypbin-starter` 更安全。

## 支持范围

本项目按 **最新发布版本** 提供安全修复（当前为 Maven Central 上的最新 tag）。开发分支（`master`
上的 `-SNAPSHOT`）不承诺安全修复，但在被合并前会经过完整 CI 门禁（构建 + 集成测试 + CodeQL 静态扫描）。

| 版本 | 是否提供安全修复 |
|---|---|
| 最新发布版本 | ✅ |
| 更早的发布版本 | ❌（建议升级） |
| `master` 快照 | 不承诺（仅保证 CI 门禁通过） |

## 如何上报漏洞

**请不要通过公开 Issue 上报安全问题**，以免在修复发布前被利用。请任选一种私密渠道：

1. GitHub 私密漏洞上报：仓库 **Security** 标签页 → *Report a vulnerability*（推荐，可在线协作修复）；
2. 邮件：发送至仓库公开资料中的维护者邮箱，标题以 `[SECURITY] ypbin-starter` 开头。

上报时请尽量包含：

- 受影响的模块与版本（例如 `ypbin-starter-sign` 3.0.0）；
- 复现步骤或最小可复现样例（含依赖版本、JDK 版本）；
- 影响面判断（能读到什么、能改成什么、是否需要已登录/已持有效授权）；
- 若已知，附上缓解建议。

## 我们的处理节奏

- **48 小时内**确认收到并给出初步判断；
- 确认有效后，在修复版本发布前与你同步进展（如需，可约定 90 天协调披露窗口）；
- 修复发布后，在 `CHANGELOG.md` 的「安全」小节致谢（如你愿意具名）。

## 已知的安全设计边界（避免误报）

以下属**有意设计**，欢迎讨论但不视为漏洞：

- **国密算法（SM2/SM3/SM4）**：静态扫描常把 SM2/SM3/SM4 判为「弱算法」，但它们是**中国商用密码标准**，
  不在通用弱算法清单的适用范围内；相关用例保留是为兼容既有存量数据。
- **遗留兼容路径**：`SignGenerator` 的 MD5、`Sm4Utils` 的 ECB 便捷方法为兼容旧数据保留，
  已在 Javadoc 中标注安全警示并给出推荐替代（HMAC-SHA256、SM4/GCM 或 CBC）。
- **网关可信来源令牌**：`ypbin.gateway.auth.trusted-source-token` 用于网关与内部服务之间的身份头签名，
  属部署期约定；`require-trusted-source` 默认开启并要求显式配置，未配置时**快速失败**而非放行。

## 依赖与供应链

- 每周 Dependabot 检查依赖与 GitHub Actions，分组规则见各仓库 `.github/dependabot.yml`；
- CI 执行 CodeQL 静态安全扫描、依赖版本收敛检查（enforcer `dependencyConvergence`）；
- 需要 SBOM 时使用 `mvn -Psbom` 生成 CycloneDX 清单（CI 会归档）。
