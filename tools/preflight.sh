#!/usr/bin/env bash
#
# 发布前置门禁：在 `mvn clean deploy -Prelease` 之前跑一遍。
#
# 为什么需要它：`-Prelease` 会激活 release profile，从而使承载「非发布模块」的 dev-only profile
# 失效——架构约束测试与性能基线模块因此**不进入发布反应堆**（这是为了让未签名产物不混进
# Central 上传包）。副作用是发布构建本身不再执行铁律门禁，所以必须在发布前单独跑一次。
#
# 用法：
#   bash tools/preflight.sh
#
# 全部通过后才执行：mvn clean deploy -Prelease
#
set -euo pipefail
cd "$(dirname "$0")/.."

step() { echo; echo "==> $*"; }
fail() { echo "✖ $*"; exit 1; }

step "1/4 全量构建（编译 + spotless + 单测 + 架构约束测试）"
mvn -B clean install || fail "全量构建失败"

step "2/4 空值语义静态检查（NullAway；参与模块由 pom 里的 nullaway.packages 决定）"
MODULES="$(for pom in ./*/pom.xml; do
    dir="$(dirname "$pom")"
    [ -d "$dir/src/main/java" ] || continue
    grep -q '<nullaway.packages>' "$pom" && printf '%s\n' "${dir#./}"
  done | paste -sd, -)"
if [ -z "$MODULES" ]; then
  echo "没有模块声明 nullaway.packages，跳过"
else
  echo "参与模块：$MODULES"
  # 必须 clean：Error Prone 只在 javac 真正执行时生效，若前置步骤已编译过同类，Maven 会
  # 输出「Nothing to compile」直接跳过，门禁静默变成空转；--fail-at-end 则保证不会因快速失败
  # 让后续模块「看起来通过」。
  if ! OUT="$(mvn -B -Pnullaway -pl "$MODULES" clean compile --fail-at-end 2>&1)"; then
    printf '%s\n' "$OUT" | grep -E "\[ERROR\]" | head -30
    fail "空值语义检查未通过"
  fi
  if printf '%s\n' "$OUT" | grep -q "Nothing to compile"; then
    fail "未发生实际编译，NullAway 等于空转（请检查是否遗漏 clean）"
  fi
fi

step "3/4 集成测试（外部实例优先 / Testcontainers 回退；无 Docker 时优雅跳过）"
if docker info >/dev/null 2>&1; then
  mvn -B -Pit verify -DskipTests=false || fail "集成测试失败"
else
  echo "⚠ 本机 Docker 不可用，集成测试将按条件跳过（CI 上会真实执行）"
  mvn -B -Pit verify -DskipTests=false || fail "集成测试失败"
fi

step "4/4 配置元数据未漂移"
node tools/export-config-metadata.mjs --check || fail "配置元数据漂移，请运行 node tools/export-config-metadata.mjs 后提交"

echo
echo "✓ 发布前置门禁全部通过。下一步：确认根 pom 的 <revision> 已是正式版本号（去掉 -SNAPSHOT），"
echo "  然后执行 mvn clean deploy -Prelease"
