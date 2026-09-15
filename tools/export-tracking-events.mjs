#!/usr/bin/env node
/**
 * 埋点事件目录导出器。
 *
 * 以 `docs/tracking-events.json` 为唯一事实源，生成两份产物：
 *   1) Java 常量：ypbin-starter-tracking/src/main/java/cn/ypbin/starter/tracking/core/TrackingEventCodes.java
 *   2) 运行时资源：ypbin-starter-tracking/src/main/resources/META-INF/ypbin/tracking-events.json
 *      （供运行期的事件码/属性白名单校验读取，避免把目录硬编码进 Java）
 *
 * 用法：
 *   node tools/export-tracking-events.mjs            # 生成两份产物
 *   node tools/export-tracking-events.mjs --check    # 只校验已提交产物与事实源是否一致（CI 漂移门禁）
 *
 * 设计约束：
 *   - 输出必须是**确定性**的（事件码排序、属性排序固定），否则漂移门禁会误报；
 *   - 生成的 Java 必须能被 spotless 的 check 通过（license 头 + import 字母序 + 无行尾空白 + 结尾换行）；
 *   - 事实源校验失败（重复码、非法码、类型非法、string 缺 maxLength）直接 exit 1，不做静默兜底。
 */
import { readFile, writeFile, mkdir } from 'node:fs/promises'
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
  if (drifted.length > 0) {
    console.error('✗ 埋点事件目录已漂移，以下产物与 docs/tracking-events.json 不一致：')
    for (const item of drifted) {
      console.error(`  - ${item}`)
    }
    console.error('  修复：node tools/export-tracking-events.mjs')
    process.exit(1)
  }
  console.log(`✓ 埋点事件目录一致（${catalog.events.length} 个事件）`)
} else {
  for (const target of targets) {
    await mkdir(dirname(target.file), { recursive: true })
    await writeFile(target.file, target.content, 'utf8')
    console.log(`✓ 已生成 ${target.label}：${target.file.slice(root.length + 1)}`)
  }
  console.log(`  事件数：${catalog.events.length}`)
}
