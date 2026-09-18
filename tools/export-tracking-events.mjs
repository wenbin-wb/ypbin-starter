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
 *
 * 退出码契约（**与 admin-ui 的 `scripts/sync-tracking-events.mjs` 完全一致**，两仓同一语义）：
 *   0 = 成功 / `--check` 下产物与事实源一致；
 *   1 = **真漂移**（仅 `--check`：产物与本次生成结果不同）；
 *   2 = **数据/检出/配置问题**，比较基准不可信或产物不可生成——**不能**把这类问题说成「已漂移」：
 *       事实源或宿主目录缺失/读不到/不是合法 JSON、`validate()` 校验不通过（含**空 `events`**、
 *       **同层重复事件码**、`maxLength` 非正整数等）、`--host`/`--merged-out` 参数错误、
 *       以及生成物写入失败。**除真漂移外，本脚本不以任何其它方式退出 1**——未捕获异常会让 Node
 *       固定退 1，故异常一律收口（见 `failDataProblem` 与两处 process 级兜底）。
 *
 * 生成期自检（**不让「非法数据」以退出码 0 产出非法 Java**）：
 *   `--check` 只比对文本，若生成物本身语法非法，它照样退 0（把问题推到 `mvn compile`）。故本脚本
 *   自己保证生成物合法，且**不依赖 javac**（低配机代价过高）：
 *     ① `description` 一律经 `javaLiteral()` 转义后嵌入 Java 字符串字面量：反斜杠/引号/换行/回车/制表
 *        等逐个转义，其余控制字符走三位八进制 `\ooo`——于是生成物里**不含数据派生的 `\u`**，
 *        天然避开 JLS 3.3 的 unicode 转义预处理（该处理连注释里都生效，`\uZZZZ` 会让 javac 直接报
 *        `illegal unicode escape`，`\u0041` 又会被静默改写成 `A`）；
 *     ② Javadoc 里 `*` + `/` 中性化为 `*&#47;`（否则注释提前终止，其后正文被当成代码）、控制字符折成空格
 *        （保证单行）、反斜杠同样成对转义；
 *     ③ `assertJavaSourceSafe()` 扫描最终文本：出现**奇数个**连续反斜杠后跟 `u` 即判非法（退 2），
 *        并用 `decodeJavaLiteral()` 逐条做「编码→解码＝原文」往返断言——编码器写错会被当场抓住，
 *        而不是等 `mvn compile` 或运行时取值被改写。
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

/** 退出码：真漂移专用（`--check` 下产物与本次生成结果不同） */
const EXIT_DRIFT = 1

/** 退出码：数据/检出/配置问题（不是漂移）——与 admin-ui 生成器同一契约 */
const EXIT_DATA_PROBLEM = 2

/**
 * 以退出码 2 失败：**数据/检出/配置问题**，明确区别于「真漂移」。
 *
 * 为什么必须收口：Node 对未捕获异常固定退 1，那正是「已漂移」的专用码——把「JSON 读不到」「校验不通过」
 * 报成「事件码已漂移」会把排查方向带偏（本仓与 admin-ui 都真踩过）。
 *
 * @param reason 失败原因（一句话）
 * @param hints  补充提示（可选，逐行输出）
 */
function failDataProblem(reason, hints = []) {
  console.error(`✖ 配置/数据问题（不是事件码漂移）：${reason}`)
  for (const hint of hints) {
    console.error(`  ${hint}`)
  }
  process.exit(EXIT_DATA_PROBLEM)
}

// 兜底：任何未预期异常/未处理拒绝都不得落回 Node 默认的退出码 1（那是「真漂移」的专用码）
process.on('uncaughtException', (error) => {
  failDataProblem(`未预期的失败：${error.message}`)
})
process.on('unhandledRejection', (error) => {
  failDataProblem(`未预期的失败：${error?.message ?? error}`)
})

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

// 参数错误属「配置问题」（退 2），不是「产物已漂移」（退 1）：`optionValue` 会抛，故在此收口
let hostArgument
let mergedOutArgument
try {
  hostArgument = optionValue('--host')
  mergedOutArgument = optionValue('--merged-out')
  if (mergedOutArgument !== null && hostArgument === null) {
    throw new Error('--merged-out 必须与 --host 一起使用：没有宿主目录就没有"联合结果"可言')
  }
} catch (error) {
  failDataProblem(`命令行参数错误：${error.message}`)
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

/**
 * 校验事实源，任何不合法都直接抛错（不静默跳过）；调用方 `validateOrFail` 把异常翻成**退出码 2**。
 *
 * 口径与运行时的对应关系（base 与宿主目录**各自**校验，与运行时 `TrackingCatalogLoader` 一致）：
 *   - `events` 必须**非空**：运行时对空目录直接启动失败（`tracking event catalog has no events`）；
 *   - **同层重复事件码**是数据错误（运行时 `duplicated tracking event code` 启动即失败），
 *     与「base 被 project 跨层覆盖」完全不是一回事——后者是合法覆盖，由 `mergeCatalogs` 打印 WARN；
 *   - `string` 属性必须声明**正整数** `maxLength`（运行时绑定 `Integer`，且 `TrackIngestService#truncate`
 *     以 `<=0` 表示「不限制」）；
 *   - `description`（事件与属性）必须是 `String`：非字符串会让 Java 侧（`String()` 强转）与运行时资源
 *     （Jackson 绑定 String）取值分叉，属数据错误。
 *
 * @param catalog 已解析的目录对象
 */
function validate(catalog) {
  if (catalog.schemaVersion !== 1) {
    throw new Error(`不支持的 schemaVersion: ${catalog.schemaVersion}（当前仅支持 1）`)
  }
  if (!Array.isArray(catalog.events) || catalog.events.length === 0) {
    throw new Error('events 必须是非空数组')
  }
  const seen = new Set()
  for (const event of catalog.events) {
    if (event === null || typeof event !== 'object') {
      throw new Error(`events 的每个元素必须是对象，当前: ${JSON.stringify(event)}`)
    }
    if (typeof event.code !== 'string' || !CODE_PATTERN.test(event.code)) {
      throw new Error(`非法事件码: ${JSON.stringify(event.code)}（须形如 ui.page.view）`)
    }
    if (seen.has(event.code)) {
      throw new Error(`重复事件码: ${event.code}`)
    }
    seen.add(event.code)
    // 必须是**字符串**而非"真值即可"：非字符串会被 `javaLiteral()` 的 String() 强制转换（Java 侧得到
    // "[object Object]"），而运行时资源里仍是原值、Jackson 绑定 String 时直接失败——两侧取值就此分叉
    if (typeof event.description !== 'string' || event.description.length === 0) {
      throw new Error(`事件 ${event.code} 的 description 必须是非空字符串，当前: ${JSON.stringify(event.description)}`)
    }
    if (!SOURCES.has(event.source)) {
      throw new Error(`事件 ${event.code} 的 source 非法: ${event.source}（允许 ${[...SOURCES].join('/')}）`)
    }
    if (!/^\d+\.\d+\.\d+$/.test(event.since ?? '')) {
      throw new Error(`事件 ${event.code} 的 since 必须是 x.y.z 形式，当前: ${event.since}`)
    }
    if (event.properties !== undefined && !Array.isArray(event.properties)) {
      throw new Error(`事件 ${event.code} 的 properties 必须是数组`)
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
      if (typeof property.description !== 'string' || property.description.length === 0) {
        throw new Error(
          `事件 ${event.code} 属性 ${property.name} 的 description 必须是非空字符串，当前: ${JSON.stringify(property.description)}`,
        )
      }
    }
  }
}

/** 事件码 → Java 常量名：ui.page.view → UI_PAGE_VIEW */
function constantName(code) {
  return code.replaceAll('.', '_').replaceAll('-', '_').toUpperCase()
}

/** 单个反斜杠；`String.raw` 无法表达它（会连结束反引号一起转义），故用普通字面量并逐行豁免该规则 */
// oxlint-disable-next-line unicorn/prefer-string-raw -- 见上：String.raw 无法表达单个反斜杠
const BACKSLASH = '\\'

/**
 * Java 字符串字面量里需要具名转义的字符（其余控制字符走三位八进制，见 `javaLiteral`）。
 *
 * 值本身即「反斜杠 + 字符」两个字符。
 */
const JAVA_NAMED_ESCAPES = new Map([
  [BACKSLASH, String.raw`\\`],
  ['"', String.raw`\"`],
  ['\n', String.raw`\n`],
  ['\r', String.raw`\r`],
  ['\t', String.raw`\t`],
  ['\f', String.raw`\f`],
  ['\b', String.raw`\b`],
])

/**
 * 把任意文本编码成 **Java 字符串字面量**（含两端双引号）。
 *
 * 两个要点：
 *   - 反斜杠**成对转义**后，任何数据里出现的 `\u` 其前导反斜杠个数都是偶数，按 JLS 3.3 属**不合格**
 *     的 unicode 转义，于是不会被预处理——既不会把 `\u0041` 静默改写成 `A`，也不会让 `\uZZZZ`
 *     触发 `illegal unicode escape`；
 *   - 其余控制字符用**三位八进制**（`\ooo`）而不是 `\uXXXX`：既避开上面那条预处理语义，也让生成物里
 *     完全不出现数据派生的 `\u`（`assertJavaSourceSafe` 据此做单条不变量检查）。
 *
 * @param text 原始文本
 * @returns Java 字符串字面量（如 `"页面浏览"`）
 */
function javaLiteral(text) {
  const body = [...String(text)]
    .map((character) => {
      const named = JAVA_NAMED_ESCAPES.get(character)
      if (named !== undefined) {
        return named
      }
      const codePoint = character.codePointAt(0)
      if (codePoint < 32 || codePoint === 127) {
        return `${BACKSLASH}${codePoint.toString(8).padStart(3, '0')}`
      }
      return character
    })
    .join('')
  return `"${body}"`
}

/**
 * `javaLiteral()` 的逆运算：把 Java 字符串字面量解回文本（**仅供自检**）。
 *
 * 只在「生成物不含不合格 `\u`」这一前提下才是忠实的——而该前提由 `assertJavaSourceSafe` 先验证，
 * 故两函数配合即可在**不调用 javac** 的情况下断言「编码→解码＝原文」。
 *
 * @param literal Java 字符串字面量（含两端双引号）
 * @returns 解码后的文本
 */
function decodeJavaLiteral(literal) {
  const body = literal.slice(1, -1)
  let decoded = ''
  for (let index = 0; index < body.length; index++) {
    const character = body[index]
    if (character !== BACKSLASH) {
      decoded += character
      continue
    }
    const escaped = body[index + 1]
    switch (escaped) {
      case BACKSLASH: {
        decoded += BACKSLASH
        index += 1
        break
      }
      case '"': {
        decoded += '"'
        index += 1
        break
      }
      case 'n': {
        decoded += '\n'
        index += 1
        break
      }
      case 'r': {
        decoded += '\r'
        index += 1
        break
      }
      case 't': {
        decoded += '\t'
        index += 1
        break
      }
      case 'f': {
        decoded += '\f'
        index += 1
        break
      }
      case 'b': {
        decoded += '\b'
        index += 1
        break
      }
      default: {
        const octal = /^[0-7]{1,3}/.exec(body.slice(index + 1))
        if (octal === null) {
          throw new Error(`无法解码的转义序列: ${JSON.stringify(body.slice(index, index + 4))}`)
        }
        decoded += String.fromCodePoint(Number.parseInt(octal[0], 8))
        index += octal[0].length
      }
    }
  }
  return decoded
}

/**
 * 把事实源里的文本转成**单行 Javadoc 正文**。
 *
 * `*` + `/` 必须中性化：块注释提前终止后，其后的正文会被 javac 当成代码（实测报
 * `illegal character: '\u3002'`）。反斜杠同样成对转义（注释里也会做 unicode 转义预处理）；
 * 换行/控制字符折成空格，保证注释始终单行。
 *
 * @param text 原始文本
 * @returns 可直接放进 `/** ... *\/` 的文本
 */
function javadocText(text) {
  return [...String(text)]
    .map((character) => {
      if (character === BACKSLASH) {
        return BACKSLASH + BACKSLASH
      }
      const codePoint = character.codePointAt(0)
      return codePoint < 32 || codePoint === 127 ? ' ' : character
    })
    .join('')
    .replaceAll('*/', '*&#47;')
}

/**
 * 生成物自检（**生成器自己发现非法，不靠 javac**）。
 *
 * ① 全文中不得出现「奇数个连续反斜杠 + u」：那是**不合格**的 unicode 转义，会被 JLS 3.3 预处理成
 *    别的字符（或直接报 `illegal unicode escape`）——合法数据经 `javaLiteral()` 编码后不可能出现；
 * ② 每条 Javadoc 正文不得含 `*` + `/`（注释提前终止）；
 * ③ 逐条断言 `decodeJavaLiteral(javaLiteral(description)) === description`（编码器写错会被当场抓住，
 *    而不是等 `mvn compile` 失败或运行时取值被悄悄改写）。
 *
 * @param catalog 事实源目录（已校验）
 * @param source  已渲染的 Java 源码文本
 */
function assertJavaSourceSafe(catalog, source) {
  // 逐条检查**所有**匹配，而不是只看第一处：即使前面都是安全（偶数）的，后面仍可能有危险序列
  for (const hazard of source.matchAll(/(\\+)u/g)) {
    if (hazard[1].length % 2 === 1) {
      failDataProblem(
        `生成的 Java 含不合格的 unicode 转义序列（${JSON.stringify(hazard[0])}）`,
        ['Java 会在词法分析前预处理 unicode 转义，连注释里也生效；请修正事件说明里的反斜杠用法。'],
      )
    }
  }
  for (const event of catalog.events) {
    const description = event.description
    // Javadoc 必须单行，且 `*` + `/` 只能作为注释终止符出现在末尾（正文里出现会让注释提前结束）
    const javadoc = `/** ${event.code}：${javadocText(description)}。 */`
    if (javadoc.indexOf('*/') !== javadoc.length - 2) {
      failDataProblem(
        `事件 ${event.code} 的 description 会提前终止 Javadoc 注释`,
        [`渲染结果: ${javadoc}`],
      )
    }
    let roundTrip
    try {
      roundTrip = decodeJavaLiteral(javaLiteral(description))
    } catch (error) {
      failDataProblem(`事件 ${event.code} 的 description 无法编码为 Java 字符串字面量：${error.message}`)
    }
    if (roundTrip !== description) {
      failDataProblem(
        `事件 ${event.code} 的 description 编码往返不一致（生成物取值会被改写）`,
        [`原文: ${JSON.stringify(description)}`, `还原: ${JSON.stringify(roundTrip)}`],
      )
    }
  }
}

/** 生成 Java 常量类；输出必须与 spotless 的格式要求一致（import 按字母序、行尾无空白、结尾换行） */
function renderJava(catalog) {
  const events = [...catalog.events].sort((a, b) => a.code.localeCompare(b.code))
  const constants = events
    .map(
      (event) =>
        `    /** ${event.code}：${javadocText(event.description)}。 */\n`
        + `    public static final String ${constantName(event.code)} = ${javaLiteral(event.code)};`,
    )
    .join('\n\n')
  const allValues = events.map((event) => constantName(event.code)).join(',\n        ')
  const descriptionEntries = events
    .map((event) => `            Map.entry(${constantName(event.code)}, ${javaLiteral(event.description)})`)
    .join(',\n')

  const source = `${LICENSE_HEADER}package cn.ypbin.starter.tracking.core;

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
  // 生成期自检：注释终止符 / unicode 转义陷阱 / 编码往返（详见 assertJavaSourceSafe）
  assertJavaSourceSafe(catalog, source)
  return source
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
 * **前置条件**：两份目录各自都**没有同层重复码**（由 `validate` 保证，违反即退 2）。
 * 因此本函数报告的每一次「覆盖」都必然是**跨层**覆盖——这正是本方案唯一有意保留的
 * 「后者胜出」语义；同层重复是数据错误，不在这里被静默吞掉。
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

/**
 * 读取并解析一份事件目录 JSON。
 *
 * 「读不到」与「不是合法 JSON」都是**数据/检出问题**（退 2）：它们让比较基准不可信，
 * 与「产物内容已漂移」是两件事，不能混为一谈。
 *
 * @param file  目录文件
 * @param layer 层名（base / 宿主），仅用于报错措辞
 * @returns 解析后的目录对象
 */
async function readCatalogJson(file, layer) {
  let raw
  try {
    raw = await readFile(file, 'utf8')
  } catch (error) {
    failDataProblem(`${layer}事件目录读不到：${file}`, [error.message])
  }
  try {
    return JSON.parse(raw)
  } catch (error) {
    failDataProblem(`${layer}事件目录不是合法 JSON：${file}`, [error.message])
  }
}

/**
 * 校验事实源；不通过即退 2。
 *
 * `validate()` 刻意保持「纯函数 + 抛错」，由本函数把异常翻译成退出码 2——此前它直接冒泡成未捕获异常，
 * Node 固定退 1，等于把「目录数据非法」报成「事件码已漂移」。
 *
 * @param catalog 已解析的目录对象
 * @param layer   层名（base / 宿主），仅用于报错措辞
 */
function validateOrFail(catalog, layer) {
  try {
    validate(catalog)
  } catch (error) {
    failDataProblem(`${layer}事件目录校验不通过：${error.message}`, [
      '数据错误与「产物已漂移」性质不同：修正目录后重跑本脚本即可，不要据此判断事件码漂移。',
    ])
  }
}

const catalog = await readCatalogJson(sourceFile, 'base ')
validateOrFail(catalog, 'base ')

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

// 宿主 project 目录（可选）：读取 → 校验 → 合并 → 打印差异。任一步失败即退 2（数据/检出问题），不静默跳过
let merge = null
let mergedContent = null
if (hostArgument !== null) {
  let hostFile
  try {
    hostFile = resolveHostCatalog(hostArgument)
  } catch (error) {
    failDataProblem(`--host 参数无法解析出宿主事件目录：${error.message}`)
  }
  const projectCatalog = await readCatalogJson(hostFile, '宿主 ')
  validateOrFail(projectCatalog, '宿主 ')
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
    // 读产物失败（路径被占成目录、权限等）是环境问题而非漂移，收口到 2
    let current
    try {
      current = await readIfExists(target.file)
    } catch (error) {
      failDataProblem(`读不到已提交的产物 ${target.file.slice(root.length + 1)}：${error.message}`)
    }
    if (current !== target.content) {
      drifted.push(`${target.label}（${target.file.slice(root.length + 1)}）`)
    }
  }
  // 联合结果的漂移只在明确给了输出文件时校验（`-` 表示写到 stdout，没有可比对的落盘产物）
  if (mergedOutArgument !== null && mergedOutArgument !== '-') {
    const mergedFile = resolve(mergedOutArgument)
    let currentMerged
    try {
      currentMerged = await readIfExists(mergedFile)
    } catch (error) {
      failDataProblem(`读不到已提交的联合事件目录 ${mergedOutArgument}：${error.message}`)
    }
    if (currentMerged !== mergedContent) {
      drifted.push(`联合事件目录（${mergedOutArgument}）`)
    }
  }
  if (drifted.length > 0) {
    console.error('✗ 埋点事件目录已漂移，以下产物与 docs/tracking-events.json 不一致：')
    for (const item of drifted) {
      console.error(`  - ${item}`)
    }
    console.error('  修复：node tools/export-tracking-events.mjs')
    // 只有走到这里才是**真漂移**：事实源已读取、已校验，产物也读到了，单纯内容不一致
    process.exit(EXIT_DRIFT)
  }
  log(`✓ 埋点事件目录一致（base ${catalog.events.length} 个事件${merge ? `，联合 ${merge.events.length} 个` : ''}）`)
} else {
  for (const target of targets) {
    try {
      await mkdir(dirname(target.file), { recursive: true })
      await writeFile(target.file, target.content, 'utf8')
    } catch (error) {
      failDataProblem(`写入产物失败 ${target.file.slice(root.length + 1)}：${error.message}`)
    }
    log(`✓ 已生成 ${target.label}：${target.file.slice(root.length + 1)}`)
  }
  log(`  base 事件数：${catalog.events.length}`)
  if (mergedOutArgument !== null) {
    if (stdoutIsMachineReadable) {
      process.stdout.write(mergedContent)
    } else {
      const mergedFile = resolve(mergedOutArgument)
      try {
        await mkdir(dirname(mergedFile), { recursive: true })
        await writeFile(mergedFile, mergedContent, 'utf8')
      } catch (error) {
        failDataProblem(`写入联合事件目录失败 ${mergedOutArgument}：${error.message}`)
      }
      log(`✓ 已生成 联合事件目录：${mergedOutArgument}（${merge.events.length} 个事件）`)
    }
  }
}
