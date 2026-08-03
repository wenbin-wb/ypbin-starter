# 发布指南

本文档说明如何把 ypbin-starter 发布到 Maven 中央仓库（Sonatype Central Portal），以及后续版本如何迭代。

- groupId：`cn.ypbin`（命名空间 `cn.ypbin` 需验证所有权，见下）
- 版本管理：`${revision}` + flatten-maven-plugin 统一，改一处 `<revision>` 全模块生效
- 发布插件：`central-publishing-maven-plugin`（Central Portal 新流程，仅在 `-Prelease` 激活）

## 一、一次性准备（首次发布前）

**1. 注册 Sonatype Central 账号**
- 打开 https://central.sonatype.com ，用 GitHub 登录或注册。

**2. 验证 groupId 命名空间 `cn.ypbin`**
- `cn.ypbin` 属于 `cn.*` 反向域名，需证明拥有 `ypbin.cn` 域名。
- 在 Central Portal「Add Namespace」填 `cn.ypbin`，它给一个验证 TXT 记录；到 `ypbin.cn` 域名 DNS 加这条 TXT，验证通过即可发布该命名空间下所有 artifact。
- 若暂无 `ypbin.cn` 域名：要么先注册域名，要么临时改用 `io.github.wenbin-wb`（GitHub 仓库验证，无需域名）——改 groupId 需同步改所有 pom 与本文件。

**3. 生成 GPG 密钥（中央仓库强制签名）**
```bash
gpg --gen-key                                   # 按提示填姓名/邮箱，设密码
gpg --list-keys --keyid-format short            # 记下 KEYID
gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>   # 公钥上传（供中央仓库校验签名）
```
> 私钥留本地用于签名；公钥必须上传公钥服务器，否则发布校验失败。

**4. 在 Central Portal 生成发布 Token**
- 账号 →「Generate User Token」，得到一对 `username` / `password`（不是登录密码，是 token）。

**5. 配置本地 `~/.m2/settings.xml`（放 token 与 gpg 密码，不要进 git）**
```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>【Central Token 的 username】</username>
      <password>【Central Token 的 password】</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>gpg</id>
      <properties>
        <!-- 指定 gpg 可执行文件：务必指向生成密钥的那个 gpg，避免 Git 自带旧版 gpg 读不到密钥库 -->
        <gpg.executable>C:\Program Files\GnuPG\bin\gpg.exe</gpg.executable>
        <gpg.keyname>【你的 GPG KEYID】</gpg.keyname>
        <gpg.passphrase>【GPG 密钥密码】</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>gpg</activeProfile>
  </activeProfiles>
</settings>
```
> `<server><id>central</id>` 必须与 pom 里 `central-publishing-maven-plugin` 的 `publishingServerId` 一致。

## 二、发布流程（每次发正式版）

以发布 `1.0.0` 为例：

**1. 确认版本号**：把 `ypbin-starter-dependencies/pom.xml` 里 `<revision>` 改为正式版（去掉 `-SNAPSHOT`）：
```xml
<revision>1.0.0</revision>
```

**2. 更新 CHANGELOG.md**：把「未发布」条目归入 `## [1.0.0] - 日期`。

**3. 全量验证**（正式版一经发布不可撤回，务必全绿）：
```bash
mvn clean install
```

**4. 发布到中央仓库**：
```bash
mvn clean deploy -Prelease
```
- `-Prelease` 激活 source/javadoc/gpg 签名 + central-publishing 上传。
- 插件配置 `autoPublish=true`：校验通过后自动发布，约几分钟到十几分钟同步到中央仓库；设为 `false` 则需登录 Portal 手动点发布。

**5. 打 Git 标签并推送**：
```bash
git commit -am "发布 1.0.0"
git tag v1.0.0
git push && git push --tags
```

**6. 开启下一个开发版本**：把 `<revision>` 改为下一迭代的快照，如 `1.1.0-SNAPSHOT`，提交。

发布后别人即可引用（通过 BOM 或直接坐标）：
```xml
<dependency>
    <groupId>cn.ypbin</groupId>
    <artifactId>ypbin-starter-bom</artifactId>
    <version>1.0.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

## 三、版本迭代规范

遵循[语义化版本](https://semver.org/lang/zh-CN/) `MAJOR.MINOR.PATCH`：

| 变更类型 | 版本位 | 示例 | 场景 |
|---|---|---|---|
| 修复 bug、向后兼容 | PATCH | 1.0.0 → 1.0.1 | 修一个日志字段 bug |
| 新增功能、向后兼容 | MINOR | 1.0.1 → 1.1.0 | 新增一个 starter 模块 |
| 不兼容变更（破坏性） | MAJOR | 1.x → 2.0.0 | 改字段名/接口签名/删能力 |

**破坏性变更纪律**（关系到「专业」）：
- 凡改公开 API/配置项/契约（如曾经的 `size`→`pageSize`、`appId`→`accessKey`），一律升 **MAJOR**，并在 CHANGELOG 写清「变更点 + 迁移方法」。
- 尽量避免破坏性变更；不得不改时，旧能力先标 `@Deprecated` 过渡一个 MINOR 版本，再于下个 MAJOR 移除。
- 正式版本发布后**不可覆盖、不可删除**（中央仓库永久保留），发前必须 `mvn clean install` 全绿。

**每次发版必做**：更新 CHANGELOG.md、打对应 `vX.Y.Z` git tag、发布说明同步到 GitHub Release。

## 四、快照版本（开发期）

开发期版本带 `-SNAPSHOT` 后缀（如 `1.1.0-SNAPSHOT`），特点：
- 可反复覆盖发布，供联调用；不进正式仓库、不永久保留。
- Central Portal 的 snapshot 仓库地址：`https://central.sonatype.com/repository/maven-snapshots/`；使用方需在自己的 pom/settings 显式配置该 snapshot 仓库才能拉取。
- 快照无需 `-Prelease` 全套签名，但发布快照同样要在 settings.xml 配 `central` server 认证。

```bash
# 开发期发快照（可选，供下游联调）
mvn clean deploy    # revision 为 x.y.z-SNAPSHOT 时走 snapshot 仓库
```

> 日常本地开发只需 `mvn install` 装到本地 `.m2`（admin 等同机项目直接引用），无需发快照到远程。仅当需要让**其他机器/成员**拉到未正式发布的版本时，才发快照。
