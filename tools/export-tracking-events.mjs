#!/usr/bin/env node
/**
 * 埋点事件目录导出器（两层目录：base + 宿主 project）。
 *
 * **base** 是本文件所在仓库的 `docs/tracking-events.json`，生成两份产物：
 *   1) Java 常量：ypbin-starter-tracking/src/main/java/cn/ypbin/starter/tracking/core/TrackingEventCodes.java
 *   2) 运行时资源：ypbin-starter-tracking/src/main/resources/META-INF/ypbin/tracking-events.json
 *      （供运行期的事件码/属性白名单校验读取，避免把目录硬编码进 Java）
 *
 * 这两份产物**只由 base 生成**（向后兼容：现有常量名不删不改；starter 不替宿主生成其私有事件的常量）。
 *
 * **project** 是宿主项目仓内的 `META-INF/ypbin/tracking-events.json`。传 `--host` 时本脚本会把它叠加到
 * base 上并输出**联合结果**，用于宿主侧与前端生成器的取数：
 *   - 冲突规则（与运行时 `TrackingCatalogMerger` 同口径）：同一事件码**以 project 为准**；
 *   - **必须打印差异**：覆盖是本方案里唯一有意偏离「禁静默降级」的地方，故任何覆盖都会逐字段打印
 *     （description 变化、properties 新增/删除、属性 type/maxLength 变化）；两边完全相同的码不报噪音；
 *   - 联合结果里的顶层 `overriddenCodes` 字段给出「被覆盖且确有字段变化」的事件码（可审计的标注）。
 *
 * 用法：
 *   node tools/export-tracking-events.mjs                     # 从 base 生成两份产物
 *   node tools/export-tracking-events.mjs --check             # 只校验已提交产物与 base 是否一致（CI 漂移门禁）
 *   node tools/export-tracking-events.mjs --host <宿主目录或文件>              # 打印合并摘要与覆盖差异
 *   node tools/export-tracking-events.mjs --host <..> --merged-out <文件|->    # 输出联合结果（`-` 即 stdout）
 *   node tools/export-tracking-events.mjs --check --host <..> --merged-out <文件>  # 校验联合结果未漂移
 *
 * `--host` 给目录时按约定取该目录下的 `META-INF/ypbin/tracking-events.json`；给文件时直接用该文件。
 *
 * 设计约束：
 *   - 输出必须是**确定性**的（事件码排序、属性排序固定），否则漂移门禁会误报；
 *   - 生成的 Java 必须能被 spotless 的 check 通过（license 头 + import 字母序 + 无行尾空白 + 结尾换行）；
 *   - 事实源校验失败（重复码、非法码、类型非法、string 缺 maxLength）直接 exit 1，不做静默兜底；
 *   - 差异/摘要一律走 **stderr**，于是 `--merged-out -` 的 stdout 保持机器可读（可被管道直接消费）。
 */
import { existsSync, statSync } from 'node:fs'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, join, resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const sourceFile = join(root, 'docs', 'tracking-events.json')
const javaFile = join(
  root,
  'ypbin-starter-tracking',
  'src',
  'main',
  'java',
  'cn',
  'ypbin',
  'starter',
  'tracking',
  'core',
  'TrackingEventCodes.java',
)
const resourceFile = join(
  root,
  'ypbin-starter-tracking',
  'src',
  'main',
  'resources',
  'META-INF',
  'ypbin',
  'tracking-events.json',
)

const checkMode = process.argv.includes('--check')

/** 宿主项目目录（project 层）的约定相对路径，与运行时 `TrackingEventCatalog.RESOURCE_PATH` 同值 */
const HOST_CATALOG_RELATIVE = join('META-INF', 'ypbin', 'tracking-events.json')

/**
 * 读取 `--name value` 形式的参数。
 *
 * @param name 参数名（含 `--`）
 * @returns 取值；未提供时返回 null
 */
function optionValue(name) {
  const index = process.argv.indexOf(name)
  if (index < 0) {
    return null
  }
  const value = process.argv[index + 1]
  if (value === undefined || value.startsWith('--')) {
    throw new Error(`参数 ${name} 缺少取值`)
  }
  return value
}

/**
 * 解析 `--host` 取值：目录按约定取内部路径、文件直接用；不存在即报错（不静默当"没有宿主目录"）。
 *
 * @param argument `--host` 的取值
 * @returns 宿主事件目录文件路径
 */
function resolveHostCatalog(argument) {
  const candidate = resolve(argument)
  if (!existsSync(candidate)) {
    throw new Error(`宿主项目事件目录不存在: ${candidate}`)
  }
  return statSync(candidate).isDirectory() ? join(candidate, HOST_CATALOG_RELATIVE) : candidate
}

const hostArgument = optionValue('--host')
const mergedOutArgument = optionValue('--merged-out')

if (mergedOutArgument !== null && hostArgument === null) {
  throw new Error('--merged-out 必须与 --host 一起使用：没有宿主目录就没有"联合结果"可言')
}

/** 事件码格式：{domain}.{object}.{action}，全小写、段内下划线 */
const CODE_PATTERN = /^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*){2}$/
/** 允许的属性类型 */
const PROPERTY_TYPES = new Set(['string', 'integer', 'number', 'boolean'])
/** 允许的事件来源 */
const SOURCES = new Set(['web', 'backend', 'iot'])

const LICENSE_HEADER = `/*
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
`

/** 校验事实源，任何不合法都直接抛错（不静默跳过） */
function validate(catalog) {
  if (catalog.schemaVersion !== 1) {
    throw new Error(`不支持的 schemaVersion: ${catalog.schemaVersion}（当前仅支持 1）`)
  }
  if (!Array.isArray(catalog.events) || catalog.events.length === 0) {
    throw new Error('events 必须是非空数组')
  }
  const seen = new Set()
  for (const event of catalog.events) {
    if (typeof event.code !== 'string' || !CODE_PATTERN.test(event.code)) {
      throw new Error(`非法事件码: ${JSON.stringify(event.code)}（须形如 ui.page.view）`)
    }
    if (seen.has(event.code)) {
      throw new Error(`重复事件码: ${event.code}`)
    }
    seen.add(event.code)
    if (!event.description) {
      throw new Error(`事件 ${event.code} 缺少 description`)
    }
    if (!SOURCES.has(event.source)) {
      throw new Error(`事件 ${event.code} 的 source 非法: ${event.source}（允许 ${[...SOURCES].join('/')}）`)
    }
    if (!/^\d+\.\d+\.\d+$/.test(event.since ?? '')) {
      throw new Error(`事件 ${event.code} 的 since 必须是 x.y.z 形式，当前: ${event.since}`)
    }
    const propertyNames = new Set()
    for (const property of event.properties ?? []) {
      if (!/^[a-z][a-zA-Z0-9]*$/.test(property.name ?? '')) {
        throw new Error(`事件 ${event.code} 的属性名非法: ${property.name}（须为小驼峰）`)
      }
      if (propertyNames.has(property.name)) {
        throw new Error(`事件 ${event.code} 的属性名重复: ${property.name}`)
      }
      propertyNames.add(property.name)
      if (!PROPERTY_TYPES.has(property.type)) {
        throw new Error(
          `事件 ${event.code} 属性 ${property.name} 的类型非法: ${property.type}（允许 ${[...PROPERTY_TYPES].join('/')}）`,
        )
      }
      if (property.type === 'string' && !(Number.isInteger(property.maxLength) && property.maxLength > 0)) {
        throw new Error(`事件 ${event.code} 属性 ${property.name} 为 string，必须声明正整数 maxLength`)
      }
      if (!property.description) {
        throw new Error(`事件 ${event.code} 属性 ${property.name} 缺少 description`)
      }
    }
  }
}

/** 事件码 → Java 常量名：ui.page.view → UI_PAGE_VIEW */
function constantName(code) {
  return code.replaceAll('.', '_').replaceAll('-', '_').toUpperCase()
}

/** 生成 Java 常量类；输出必须与 spotless 的格式要求一致（import 按字母序、行尾无空白、结尾换行） */
function renderJava(catalog) {
  const events = [...catalog.events].sort((a, b) => a.code.localeCompare(b.code))
  const constants = events
    .map((event) => `    /** ${event.code}：${event.description}。 */\n    public static final String ${constantName(event.code)} = "${event.code}";`)
    .join('\n\n')
  const allValues = events.map((event) => constantName(event.code)).join(',\n        ')
  const descriptionEntries = events
    .map((event) => `            Map.entry(${constantName(event.code)}, "${event.description.replaceAll('"', '\\"')}")`)
    .join(',\n')

  return `${LICENSE_HEADER}package cn.ypbin.starter.tracking.core;

import java.util.Map;
import java.util.Set;

/**
 * 埋点事件码常量。
 *
 * <p><strong>本文件由 {@code tools/export-tracking-events.mjs} 生成，请勿手工修改。</strong>
 * 事实源是 {@code docs/tracking-events.json}：修改目录后必须重新执行生成器，
 * 否则 CI 的「校验埋点事件目录未漂移」步骤会失败。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
public final class TrackingEventCodes {

${constants}

    /** 全部已登记事件码（不可变）。未登记的事件码在采集入口即被拒绝，不会静默入库。 */
    public static final Set<String> ALL = Set.of(
        ${allValues}
    );

    /** 事件码到说明的映射（不可变），用于日志与排查。 */
    public static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
${descriptionEntries}
    );

    private TrackingEventCodes() {
    }
}
`
}

/** 生成运行时资源：保留白名单与长度上限，供采集入口做属性裁剪与校验 */
function renderResource(catalog) {
  const events = [...catalog.events]
    .sort((a, b) => a.code.localeCompare(b.code))
    .map((event) => ({
      code: event.code,
      description: event.description,
      source: event.source,
      since: event.since,
      properties: [...(event.properties ?? [])]
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((property) => {
          const rendered = { name: property.name, type: property.type }
          if (property.maxLength !== undefined) {
            rendered.maxLength = property.maxLength
          }
          rendered.description = property.description
          return rendered
        }),
    }))
  return `${JSON.stringify({ schemaVersion: catalog.schemaVersion, events }, null, 2)}\n`
}

/**
 * 属性长度上限的归一化口径：目录只为 string 声明 maxLength，其余类型缺省即「不限制」（0）。
 * 与运行时 `TrackingCatalogMerger` 完全同口径，避免两侧对「覆盖」的判定不一致。
 *
 * @param property 属性定义
 * @returns 归一化后的长度上限
 */
function normalizedMaxLength(property) {
  return property.maxLength ?? 0
}

/**
 * 逐字段描述 project 相对 base 的差异（顺序确定，便于断言与审计）。
 *
 * @param baseEvent    base 侧事件定义
 * @param projectEvent project 侧事件定义
 * @returns 差异描述列表；两边等价时为空数组
 */
function describeChanges(baseEvent, projectEvent) {
  const changes = []
  if (baseEvent.description !== projectEvent.description) {
    changes.push(`description: "${baseEvent.description}" -> "${projectEvent.description}"`)
  }
  const baseProperties = new Map((baseEvent.properties ?? []).map((property) => [property.name, property]))
  const projectProperties = new Map((projectEvent.properties ?? []).map((property) => [property.name, property]))
  const added = [...projectProperties.keys()].filter((name) => !baseProperties.has(name)).toSorted()
  if (added.length > 0) {
    changes.push(`properties.added: ${added.join(', ')}`)
  }
  const removed = [...baseProperties.keys()].filter((name) => !projectProperties.has(name)).toSorted()
  if (removed.length > 0) {
    changes.push(`properties.removed: ${removed.join(', ')}`)
  }
  const shared = [...projectProperties.keys()].filter((name) => baseProperties.has(name)).toSorted()
  for (const name of shared) {
    const before = baseProperties.get(name)
    const after = projectProperties.get(name)
    const fieldChanges = []
    if (before.type !== after.type) {
      fieldChanges.push(`type ${before.type} -> ${after.type}`)
    }
    if (normalizedMaxLength(before) !== normalizedMaxLength(after)) {
      fieldChanges.push(`maxLength ${normalizedMaxLength(before)} -> ${normalizedMaxLength(after)}`)
    }
    if (fieldChanges.length > 0) {
      changes.push(`properties.${name}: ${fieldChanges.join(', ')}`)
    }
  }
  return changes
}

/**
 * 合并 base 与 project：同一事件码**以 project 为准**，并给出逐字段差异。
 *
 * @param baseCatalog    base 目录（已校验）
 * @param projectCatalog project 目录（已校验）
 * @returns 合并结果（按事件码升序的事件列表 + 新增码 + 覆盖差异）
 */
function mergeCatalogs(baseCatalog, projectCatalog) {
  const merged = new Map(baseCatalog.events.map((event) => [event.code, event]))
  const addedCodes = []
  const overrides = []
  for (const event of projectCatalog.events) {
    const previous = merged.get(event.code)
    if (previous === undefined) {
      addedCodes.push(event.code)
    } else {
      const changes = describeChanges(previous, event)
      if (changes.length > 0) {
        overrides.push({ code: event.code, changes })
      }
    }
    merged.set(event.code, event)
  }
  const codes = [...merged.keys()].toSorted((a, b) => a.localeCompare(b))
  return {
    events: codes.map((code) => merged.get(code)),
    addedCodes,
    overrides,
  }
}

/**
 * 生成联合结果 JSON（事件码与属性名均升序，输出确定）。
 *
 * <p>顶层 `overriddenCodes` 是「该条已被 project 覆盖且字段确有变化」的机器可读标注；刻意放在顶层而不是
 * 事件对象里——事件对象要能被运行时的 `TrackingEventCatalog` 直接解析，不引入它不认识的字段。</p>
 *
 * @param baseCatalog base 目录（提供 schemaVersion）
 * @param merge       合并结果
 * @returns 联合结果 JSON 文本
 */
function renderMerged(baseCatalog, merge) {
  const events = merge.events.map((event) => ({
    code: event.code,
    description: event.description,
    source: event.source,
    since: event.since,
    properties: [...(event.properties ?? [])]
      .toSorted((a, b) => a.name.localeCompare(b.name))
      .map((property) => {
        const rendered = { name: property.name, type: property.type }
        if (property.maxLength !== undefined) {
          rendered.maxLength = property.maxLength
        }
        rendered.description = property.description
        return rendered
      }),
  }))
  const payload = {
    schemaVersion: baseCatalog.schemaVersion,
    description:
      'base（starter 内置）与 project（宿主项目目录）合并后的联合事件目录，由 tools/export-tracking-events.mjs --host 生成；overriddenCodes 列出被 project 覆盖且字段确有变化的事件码。',
    overriddenCodes: merge.overrides.map((override) => override.code).toSorted((a, b) => a.localeCompare(b)),
    events,
  }
  return `${JSON.stringify(payload, null, 2)}\n`
}

/**
 * 打印合并摘要与逐条覆盖差异。
 *
 * 覆盖是本方案里唯一有意偏离本仓「禁静默降级」铁律的地方，故**必须打印**：任何覆盖都要能被人看见。
 * 一律走 stderr，使 `--merged-out -` 的 stdout 保持机器可读。
 *
 * @param baseCatalog    base 目录
 * @param projectCatalog project 目录
 * @param merge          合并结果
 */
function printMergeReport(baseCatalog, projectCatalog, merge) {
  for (const override of merge.overrides) {
    console.error(`⚠ 事件码 ${override.code} 被宿主项目目录覆盖（以 project 为准）：`)
    for (const change of override.changes) {
      console.error(`    ${change}`)
    }
  }
  for (const code of [...merge.addedCodes].toSorted()) {
    console.error(`+ 宿主项目目录新增事件码：${code}`)
  }
  console.error(
    `  合并结果：base=${baseCatalog.events.length} project=${projectCatalog.events.length} `
      + `新增=${merge.addedCodes.length} 覆盖=${merge.overrides.length} 合计=${merge.events.length}`,
  )
}

async function readIfExists(file) {
  try {
    return await readFile(file, 'utf8')
  } catch (error) {
    if (error.code === 'ENOENT') {
      return null
    }
    throw error
  }
}

const catalog = JSON.parse(await readFile(sourceFile, 'utf8'))
validate(catalog)

/** stdout 是否被征用为机器可读输出（`--merged-out -`）：是则人类可读日志改走 stderr */
const stdoutIsMachineReadable = mergedOutArgument === '-'

/**
 * 打印人类可读日志（stdout 被征用时走 stderr，避免污染管道消费方）。
 *
 * @param message 日志内容
 */
function log(message) {
  if (stdoutIsMachineReadable) {
    console.error(message)
  } else {
    console.log(message)
  }
}

// 宿主 project 目录（可选）：读取 → 校验 → 合并 → 打印差异。任一步失败即 exit 1，不静默跳过
let merge = null
let mergedContent = null
if (hostArgument !== null) {
  const hostFile = resolveHostCatalog(hostArgument)
  const projectCatalog = JSON.parse(await readFile(hostFile, 'utf8'))
  validate(projectCatalog)
  merge = mergeCatalogs(catalog, projectCatalog)
  mergedContent = renderMerged(catalog, merge)
  printMergeReport(catalog, projectCatalog, merge)
}

const targets = [
  { file: javaFile, content: renderJava(catalog), label: 'Java 常量' },
  { file: resourceFile, content: renderResource(catalog), label: '运行时资源' },
]

if (checkMode) {
  const drifted = []
  for (const target of targets) {
    const current = await readIfExists(target.file)
    if (current !== target.content) {
      drifted.push(`${target.label}（${target.file.slice(root.length + 1)}）`)
    }
  }
  // 联合结果的漂移只在明确给了输出文件时校验（`-` 表示写到 stdout，没有可比对的落盘产物）
  if (mergedOutArgument !== null && mergedOutArgument !== '-') {
    const mergedFile = resolve(mergedOutArgument)
    if ((await readIfExists(mergedFile)) !== mergedContent) {
      drifted.push(`联合事件目录（${mergedOutArgument}）`)
    }
  }
  if (drifted.length > 0) {
    console.error('✗ 埋点事件目录已漂移，以下产物与 docs/tracking-events.json 不一致：')
    for (const item of drifted) {
      console.error(`  - ${item}`)
    }
    console.error('  修复：node tools/export-tracking-events.mjs')
    process.exit(1)
  }
  log(`✓ 埋点事件目录一致（base ${catalog.events.length} 个事件${merge ? `，联合 ${merge.events.length} 个` : ''}）`)
} else {
  for (const target of targets) {
    await mkdir(dirname(target.file), { recursive: true })
    await writeFile(target.file, target.content, 'utf8')
    log(`✓ 已生成 ${target.label}：${target.file.slice(root.length + 1)}`)
  }
  log(`  base 事件数：${catalog.events.length}`)
  if (mergedOutArgument !== null) {
    if (stdoutIsMachineReadable) {
      process.stdout.write(mergedContent)
    } else {
      const mergedFile = resolve(mergedOutArgument)
      await mkdir(dirname(mergedFile), { recursive: true })
      await writeFile(mergedFile, mergedContent, 'utf8')
      log(`✓ 已生成 联合事件目录：${mergedOutArgument}（${merge.events.length} 个事件）`)
    }
  }
}
