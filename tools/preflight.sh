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

step "1/6 全量构建（编译 + spotless + 单测 + 架构约束测试）"
mvn -B clean install || fail "全量构建失败"

step "2/6 空值语义静态检查（NullAway；参与模块由 pom 里的 nullaway.packages 决定）"
MODULES="$(for pom in ./*/pom.xml; do
    dir="$(dirname "$pom")"
    [ -d "$dir/src/main/java" ] || continue
    if grep -q '<nullaway.packages>' "$pom"; then printf '%s\n' "${dir#./}"; fi
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

step "3/6 依赖版本收敛（同一构件多版本即失败）"
mvn -B -Pdep-convergence validate --fail-at-end || fail "依赖版本收敛检查未通过（把版本钉到 dependencyManagement）"

step "4/6 集成测试（外部实例优先 / Testcontainers 回退）"
if docker info >/dev/null 2>&1; then
  mvn -B -Pit verify -DskipTests=false || fail "集成测试失败"
elif [ "${SKIP_IT:-0}" = "1" ]; then
  echo "⚠ SKIP_IT=1：显式跳过集成测试（发布前请确认这不是自欺）"
else
  # 不静默跳过：Docker 不可用时所有 IT 会被 @EnabledIf 条件跳过，门禁就变成空转
  fail "本机 Docker 不可用，集成测试会被条件跳过；请修复 Docker，或显式 SKIP_IT=1 确认放弃该门禁"
fi

step "5/6 配置元数据未漂移"
node tools/export-config-metadata.mjs --check || fail "配置元数据漂移，请运行 node tools/export-config-metadata.mjs 后提交"

step "6/6 埋点事件目录未漂移"
node tools/export-tracking-events.mjs --check || fail "埋点事件目录漂移，请运行 node tools/export-tracking-events.mjs 后提交"

echo
echo "✓ 发布前置门禁全部通过（全量构建 / NullAway / 依赖收敛 / 集成测试 / 配置元数据 / 埋点事件目录）。下一步："
echo "  确认根 pom 的 <revision> 已是正式版本号（去掉 -SNAPSHOT），"
echo "  然后执行 mvn clean deploy -Prelease"
