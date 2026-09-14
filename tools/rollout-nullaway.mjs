#!/usr/bin/env node
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

/**
 * NullAway 空值语义检查推广助手（幂等，可重复执行）。
 *
 * 把一个模块「纳入 NullAway 检查」所需的样板一次改好：
 *   1. 推导模块包根（cn.ypbin.starter.<X>），在包根生成 `@NullMarked` 的 package-info.java；
 *   2. 模块 pom 声明 `<nullaway.packages>`（多个包根用逗号连接，NullAway 支持列表）；
 *   3. 模块 pom 声明 `org.jspecify:jspecify` 依赖（直接使用语义注解，不靠传递依赖）。
 *
 * 检查配置本体在父 pom `ypbin-starter-dependencies` 的 `nullaway` profile 里，模块侧只需上述三样；
 * CI 与 tools/preflight.sh 会按「含主源码且声明了 nullaway.packages」自动发现参与模块。
 *
 * 用法：
 *   node tools/rollout-nullaway.mjs ypbin-starter-json ypbin-starter-log
 *   node tools/rollout-nullaway.mjs --all-pending      # 推广所有尚未参与的模块（不含 benchmarks）
 */
import { readFile, writeFile, readdir, mkdir } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '..')

/** 不参与推广的模块：JMH 基准模块自带 annotationProcessorPaths，会被 profile 覆盖 */
const EXCLUDED = new Set(['ypbin-starter-benchmarks'])

const LICENSE = `/*
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
 */`

/** 递归收集 .java 文件 */
async function javaFiles(dir) {
  const out = []
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name)
    if (entry.isDirectory()) {
      out.push(...(await javaFiles(full)))
    } else if (entry.name.endsWith('.java')) {
      out.push(full)
    }
  }
  return out
}

/** 推导模块包根：取所有 package 的前 4 段（cn.ypbin.starter.<X>）并去重 */
async function packageRoots(moduleDir) {
  const srcRoot = join(moduleDir, 'src/main/java')
  const files = await javaFiles(srcRoot)
  const roots = new Set()
  for (const file of files) {
    const match = /^package\s+([\w.]+)\s*;/m.exec(await readFile(file, 'utf8'))
    if (!match) {
      continue
    }
    const segments = match[1].split('.')
    if (segments.length < 4 || segments.slice(0, 3).join('.') !== 'cn.ypbin.starter') {
      throw new Error(`${file} 的包名不在 cn.ypbin.starter.<X> 之下：${match[1]}`)
    }
    roots.add(segments.slice(0, 4).join('.'))
  }
  if (roots.size === 0) {
    throw new Error(`${moduleDir} 下没有可解析的包声明`)
  }
  return [...roots].sort()
}

/** 生成包根的 package-info.java（已存在则跳过） */
async function ensurePackageInfo(moduleDir, pkg) {
  const target = join(moduleDir, 'src/main/java', ...pkg.split('.'), 'package-info.java')
  if (existsSync(target)) {
    return false
  }
  await mkdir(dirname(target), { recursive: true })
  const simple = pkg.split('.').at(-1)
  await writeFile(
    target,
    `${LICENSE}

/**
 * ypbin-starter ${simple} 模块。
 *
 * <p>本包及其子包标注 {@link org.jspecify.annotations.NullMarked}：<strong>未标注即为非空</strong>，
 * 可空的返回值、参数与字段必须显式标注 {@code @Nullable}。该约定由
 * {@code mvn -Pnullaway -pl <本模块> compile}（NullAway）在编译期校验，漏标即构建失败。</p>
 *
 * @author wenbin
 * @since 2026-09-14
 */
@NullMarked
package ${pkg};

import org.jspecify.annotations.NullMarked;
`,
    'utf8'
  )
  return true
}

/** 在 pom 里声明 nullaway.packages（已存在则跳过） */
function declarePackages(pom, packages) {
  if (pom.includes('<nullaway.packages>')) {
    return { pom, changed: false }
  }
  const block = `
    <properties>
        <!-- 参与 NullAway 空值语义检查：根包（父 pom 的 nullaway profile 据此限定检查范围） -->
        <nullaway.packages>${packages.join(',')}</nullaway.packages>
    </properties>`
  const anchor = '\n    </dependencies>'
  if (!pom.includes(anchor)) {
    throw new Error('pom 里找不到 </dependencies> 锚点')
  }
  return { pom: pom.replace(anchor, `${anchor}\n${block}`), changed: true }
}

/** 在 pom 里声明 jspecify 依赖（已有则跳过） */
function declareJspecify(pom) {
  if (pom.includes('org.jspecify')) {
    return { pom, changed: false }
  }
  const dep = `    <dependencies>
        <!-- JSpecify 空值语义注解：本模块以 @NullMarked 声明「未标注即非空」 -->
        <dependency>
            <groupId>org.jspecify</groupId>
            <artifactId>jspecify</artifactId>
        </dependency>
`
  if (!pom.includes('    <dependencies>\n')) {
    throw new Error('pom 里找不到 <dependencies> 锚点')
  }
  return { pom: pom.replace('    <dependencies>\n', dep), changed: true }
}

/** 列出所有「有主源码但尚未参与」的模块 */
async function pendingModules() {
  const out = []
  for (const entry of await readdir(repoRoot, { withFileTypes: true })) {
    if (!entry.isDirectory() || EXCLUDED.has(entry.name)) {
      continue
    }
    const pomPath = join(repoRoot, entry.name, 'pom.xml')
    if (!existsSync(pomPath) || !existsSync(join(repoRoot, entry.name, 'src/main/java'))) {
      continue
    }
    if (!(await readFile(pomPath, 'utf8')).includes('<nullaway.packages>')) {
      out.push(entry.name)
    }
  }
  return out.sort()
}

const args = process.argv.slice(2)
const targets = args.includes('--all-pending') ? await pendingModules() : args
if (targets.length === 0) {
  console.error('用法：node tools/rollout-nullaway.mjs <模块目录> [...] 或 --all-pending')
  process.exit(1)
}

for (const name of targets) {
  const moduleDir = join(repoRoot, name)
  if (!existsSync(join(moduleDir, 'pom.xml'))) {
    console.error(`✖ 模块不存在：${name}`)
    process.exit(1)
  }
  const roots = await packageRoots(moduleDir)
  let created = 0
  for (const pkg of roots) {
    if (await ensurePackageInfo(moduleDir, pkg)) {
      created += 1
    }
  }
  const pomPath = join(moduleDir, 'pom.xml')
  let pom = await readFile(pomPath, 'utf8')
  const p1 = declarePackages(pom, roots)
  pom = p1.pom
  const p2 = declareJspecify(pom)
  pom = p2.pom
  if (p1.changed || p2.changed) {
    await writeFile(pomPath, pom)
  }
  console.log(`✓ ${name}：包根 ${roots.join(' , ')}；新增 package-info ${created}；pom 属性 ${p1.changed ? '已加' : '已有'}；jspecify ${p2.changed ? '已加' : '已有'}`)
}
