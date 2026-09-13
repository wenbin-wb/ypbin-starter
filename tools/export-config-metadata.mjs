#!/usr/bin/env node
/**
 * Starter 配置元数据导出器。
 *
 * 把各模块构建产物中的 `META-INF/spring-configuration-metadata.json`（由
 * spring-boot-configuration-processor 生成）聚合为站点配置参考所需的单一 JSON，
 * 从而让「配置参考」由构建产物驱动、不再依赖人工维护的属性清单（人工维护是此前漏项与漂移的根因）。
 *
 * 用法：
 *   node tools/export-config-metadata.mjs            # 生成 tools/generated/starter-config-metadata.json
 *   node tools/export-config-metadata.mjs --check    # 只校验已提交文件与当前产物是否一致（CI 漂移门禁）
 *
 * 前置：需先执行 `mvn -DskipTests install`（或 package），使各模块 target/classes 下存在元数据。
 *
 * 说明：Spring 元数据不含源码行号，这里以 sourceType 定位源文件并按属性名尽力回填行号；
 * 元数据无法表达的「启用条件/生产注意」以及宿主标准（Spring/第三方）配置项，
 * 维护在 tools/config-metadata-extras.json，由本脚本合并。
 */
import { readFile, writeFile, readdir, mkdir, stat } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { resolve, join, basename } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const toolsDir = resolve(import.meta.dirname)
const outFile = resolve(toolsDir, 'generated', 'starter-config-metadata.json')
const extrasFile = resolve(toolsDir, 'config-metadata-extras.json')
const checkMode = process.argv.includes('--check')

const DEFAULT_ENABLEMENT = '绑定后由对应模块读取；模块还需满足类路径和自动装配条件。'

/** 模块目录名（ypbin-starter-* 且含 pom.xml） */
async function listModules() {
  const entries = await readdir(root, { withFileTypes: true })
  const modules = []
  for (const entry of entries) {
    if (!entry.isDirectory() || !entry.name.startsWith('ypbin-starter-')) continue
    if (!existsSync(join(root, entry.name, 'pom.xml'))) continue
    modules.push(entry.name)
  }
  return modules.sort()
}

/** 读取某模块的 configuration-metadata.json；不存在（如 BOM/聚合模块）返回 null */
async function readMetadata(module) {
  const file = join(root, module, 'target', 'classes', 'META-INF', 'spring-configuration-metadata.json')
  if (!existsSync(file)) return null
  try {
    return JSON.parse(await readFile(file, 'utf8'))
  } catch {
    return null
  }
}

/** class FQN → 源文件相对路径（best-effort，仅用于展示来源） */
async function classToSourcePath(sourceType) {
  if (!sourceType) return null
  const rel = sourceType.replaceAll('.', '/')
  for (const module of await listModules()) {
    const candidate = join(module, 'src', 'main', 'java', `${rel}.java`)
    if (existsSync(join(root, candidate))) return candidate
  }
  return null
}

/** 在源文件中按属性名回填行号（best-effort，失败不影响导出） */
const lineCache = new Map()
async function readSource(relPath) {
  if (!relPath) return null
  let content = lineCache.get(relPath)
  if (content === undefined) {
    try {
      content = await readFile(join(root, relPath), 'utf8')
    } catch {
      content = null
    }
    lineCache.set(relPath, content)
  }
  return content
}

async function findLine(relPath, propertyName) {
  const content = await readSource(relPath)
  if (!content) return null
  // 属性名 kebab-case → 字段名 camelCase，例如 fail-on-missing-tenant → failOnMissingTenant
  const camel = toCamel(propertyName)
  const lines = content.split('\n')
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i]
    if (line.includes(` ${camel};`) || line.includes(` ${camel} =`) || line.includes(` ${camel}()`)) {
      return i + 1
    }
  }
  return null
}

/**
 * 清洗 Javadoc 描述：Spring 元数据直接取自字段 Javadoc 原文，含 `{@code}`/`{@link}`/`<p>` 等标记。
 *
 * <p>配置参考面向使用者，应展示可直接阅读的文本：这里只保留**首个段落**（摘要句），
 * 并把内联 Javadoc 标记还原为纯文本，避免文档里出现 `&#64;code` 之类的转义噪音。</p>
 *
 * @param description 原始描述（可能为 undefined）
 * @returns 清洗后的描述（可能为空串）
 */
function cleanDescription(description) {
  if (!description) return ''
  let text = String(description)
  // 仅取首段：截断到空行或 <p> 之前
  text = text.split(/\n\s*\n|<p>/)[0]
  // 内联 Javadoc 标记 → 纯文本
  text = text.replace(/\{@(?:code|link|linkplain|literal)\s+([^}]*)\}/g, '$1')
  // HTML 标签与转义实体
  text = text.replace(/<[^>]+>/g, ' ')
  text = text.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&')
  // 折叠空白
  return text.replace(/\s+/g, ' ').trim()
}

/** kebab-case → camelCase */
function toCamel(name) {
  return name
    .split('-')
    .map((part, index) => (index === 0 ? part : part.charAt(0).toUpperCase() + part.slice(1)))
    .join('')
}

/** camelCase → kebab-case */
function toKebab(name) {
  return name.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase()
}

/**
 * FQCN → 源文件相对路径（在已列出的模块中查找）。
 * 嵌套类（`Outer$Inner`）落在外部类的 .java 中，故按 `$` 截断后再定位文件。
 */
async function resolveSourcePath(fqcn, modules) {
  if (!fqcn) return null
  const outer = fqcn.split('$')[0]
  const rel = outer.replaceAll('.', '/')
  for (const module of modules) {
    const candidate = join(module, 'src', 'main', 'java', `${rel}.java`)
    if (existsSync(join(root, candidate))) return candidate
  }
  return null
}

/**
 * 取出类型体：按大括号配平截取 `class/record/interface X` 的方法体内容。
 * 返回 null 表示未找到或该类型是枚举（枚举不需要展开字段）。
 */
function extractTypeBody(content, simpleName) {
  if (!content || !simpleName) return null
  const enumRe = new RegExp(`\\benum\\s+${simpleName}\\b`)
  if (enumRe.test(content)) return null
  const declRe = new RegExp(`\\b(?:class|record|interface)\\s+${simpleName}\\b`)
  const match = declRe.exec(content)
  if (!match) return null
  const openIndex = content.indexOf('{', match.index)
  if (openIndex < 0) return null
  let depth = 0
  for (let i = openIndex; i < content.length; i += 1) {
    const ch = content[i]
    if (ch === '{') depth += 1
    else if (ch === '}') {
      depth -= 1
      if (depth === 0) return content.slice(openIndex, i + 1)
    }
  }
  return content.slice(openIndex)
}

/**
 * 解析 POJO 源码字段（名称/类型/默认值/单行 Javadoc 描述）。
 *
 * 仅用于 Spring 元数据不展开的场景——最典型是 `List<Pojo>` 这类对象列表：元数据只给出
 * `List<X>` 一行，宿主却需要逐字段说明（如登录客户端的 client-id/secret 等）。
 */
function parsePojoFields(content) {
  if (!content) return []
  const lines = content.split('\n')
  const fields = []
  let javadoc = null
  for (const raw of lines) {
    const line = raw.trim()
    const doc = line.match(/^\/\*\*\s*(.*?)\s*\*\/$/)
    if (doc) {
      javadoc = doc[1]
      continue
    }
    if (line.startsWith('/**')) {
      javadoc = ''
      continue
    }
    if (javadoc !== null && line.startsWith('*')) {
      const text = line.replace(/^\*\s?/, '').trim()
      if (text && !text.startsWith('@')) javadoc = javadoc ? `${javadoc} ${text}` : text
      continue
    }
    if (line.startsWith('*/')) continue
    const field = line.match(/^(?:private|protected|public)\s+(?:static\s+|final\s+|transient\s+)*([A-Za-z_$][\w$.<>,\s\[\]?]*?)\s+([a-z_$][\w$]*)\s*(?:=\s*(.+?))?;$/)
    if (field) {
      const [, type, name, initializer] = field
      if (name === 'serialVersionUID') {
        javadoc = null
        continue
      }
      if (!/static/.test(line)) {
        fields.push({
          name,
          type: type.trim(),
          defaultValue: initializer ? initializer.trim() : null,
          description: javadoc ?? '',
        })
      }
      javadoc = null
      continue
    }
    if (line && !line.startsWith('*') && !line.startsWith('//') && !line.startsWith('@')) {
      if (!line.startsWith('/**')) javadoc = null
    }
  }
  return fields
}

/**
 * 展开对象型属性，补充 Spring 元数据不展开的叶子字段：
 *
 * <ul>
 *   <li>对象列表：`ypbin.x.clients`（`List&lt;Pojo&gt;`）→ `ypbin.x.clients[].field`；</li>
 *   <li>直接对象：`ypbin.security.password`（`Pojo`）→ `ypbin.security.password.field`。</li>
 * </ul>
 *
 * 元数据只给出对象/列表一行（如 `List<AppInfo>`），宿主却需要逐字段说明；这里从源码解析字段，
 * 使叶子项同样由构建产物+源码机械生成，而非人工补录（人工补录正是此前漂移的根因）。
 *
 * @returns 展开出的条目数组（不含已存在的 key）
 */
async function expandObjectProperties(module, entries, modules, extras, knownKeys) {
  const expanded = []
  for (const entry of entries) {
    const listMatch = /^(?:java\.util\.)?(?:List|Set|Collection)<(.+)>$/.exec(entry.type ?? '')
    const isList = Boolean(listMatch)
    const type = isList ? listMatch[1].trim() : (entry.type ?? '')
    if (!type || type.startsWith('java.') || type.startsWith('jakarta.')) continue
    if ([...type].some((ch) => '<>?'.includes(ch))) continue // 泛型/通配跳过
    if (type.endsWith('[]')) continue

    const relPath = await resolveSourcePath(type, modules)
    if (!relPath) continue
    const content = await readSource(relPath)
    const simpleName = type.split(/[.$]/).pop()
    const isNested = type.includes('$')
    const body = isNested ? extractTypeBody(content, simpleName) : content
    if (!body) continue
    const fields = parsePojoFields(body)
    if (!fields.length) continue
    const bodyLines = body.split('\n')
    const sourceLines = content.split('\n')
    const bodyOffset = isNested ? sourceLines.findIndex((l) => l.includes(`class ${simpleName}`)) + 1 : 0
    for (const field of fields) {
      const key = isList ? `${entry.key}[].${toKebab(field.name)}` : `${entry.key}.${toKebab(field.name)}`
      if (knownKeys.has(key)) continue
      const localLine = bodyLines.findIndex((l) => l.includes(` ${field.name};`) || l.includes(` ${field.name} =`)) + 1
      const line = localLine > 0 ? bodyOffset + localLine : null
      const sources = [{ kind: 'java-property', path: relPath, line }]
      const notes = extras.safetyNotes[key] ?? []
      const kindText = isList ? `${entry.key} 列表元素的字段` : `${entry.key} 的子字段`
      expanded.push({
        module,
        key,
        type: field.type,
        defaultDefined: field.defaultValue !== null,
        defaultValue: field.defaultValue,
        required: false,
        requiredWhen: null,
        description: field.description || `${entry.description}（${field.name}）`,
        allowedValues: [],
        enablement: extras.enablement[key] ?? `作为${kindText}配置。`,
        safetyNotes: notes,
        sources,
        default: field.defaultValue,
        values: [],
        notes,
        source: sources,
        group: entry.group,
        deprecation: null,
      })
      knownKeys.add(key)
    }
  }
  return expanded
}

/**
 * 扫描 `@ConditionalOnProperty` 专用开关。
 *
 * Spring 元数据只收录 `@ConfigurationProperties` 字段，形如 `ypbin.resilience.enabled`
 * 这类「仅由自动装配开关消费、没有绑定字段」的配置项不会出现在元数据里，需从源码补齐，
 * 否则配置参考会漏掉这类开关（此前人工快照正是靠手工补，容易漂移）。
 */
async function scanConditionalSwitches(module, modules, extras, knownKeys) {
  const srcRoot = join(root, module, 'src', 'main', 'java')
  if (!existsSync(srcRoot)) return []
  const files = []
  async function walk(dir) {
    for (const entry of await readdir(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name)
      if (entry.isDirectory()) await walk(full)
      else if (entry.name.endsWith('.java')) files.push(full)
    }
  }
  await walk(srcRoot)

  const found = new Map()
  for (const file of files) {
    const content = await readFile(file, 'utf8')
    if (!content.includes('@ConditionalOnProperty')) continue
    const relPath = file.slice(root.length + 1)
    const lines = content.split('\n')
    const annotationRegex = /@ConditionalOnProperty\(([\s\S]*?)\)/g
    let match
    while ((match = annotationRegex.exec(content)) !== null) {
      const body = match[1]
      const prefix = /prefix\s*=\s*"([^"]*)"/.exec(body)?.[1]
      const nameRaw = /(?:name|value)\s*=\s*(?:"([^"]*)"|\{([^}]*)\})/.exec(body)
      if (!prefix || !nameRaw) continue
      const names = nameRaw[1] ? [nameRaw[1]] : nameRaw[2].split(',').map((s) => s.trim().replace(/^"|"$/g, '')).filter(Boolean)
      const havingValue = /havingValue\s*=\s*"([^"]*)"/.exec(body)?.[1] ?? null
      const matchIfMissing = /matchIfMissing\s*=\s*(true|false)/.exec(body)?.[1] === 'true'
      const line = content.slice(0, match.index).split('\n').length
      const className = basename(file, '.java')
      for (const name of names) {
        const key = `${prefix}.${name}`
        if (knownKeys.has(key) || found.has(key)) continue
        const notes = extras.safetyNotes[key] ?? []
        found.set(key, {
          module,
          key,
          type: havingValue === 'true' || havingValue === 'false' ? 'java.lang.Boolean' : 'java.lang.String',
          defaultDefined: matchIfMissing,
          defaultValue: matchIfMissing ? havingValue : null,
          required: false,
          requiredWhen: null,
          description: extras.description?.[key] ?? `条件开关：${key}=${havingValue ?? '（任意值）'} 时装配 ${className}。`,
          allowedValues: havingValue ? [havingValue] : [],
          enablement: extras.enablement[key] ?? `需显式满足该条件（${className}）。`,
          safetyNotes: notes,
          sources: [{ kind: 'conditional-on-property', path: relPath, line }],
          default: matchIfMissing ? havingValue : null,
          values: havingValue ? [havingValue] : [],
          notes,
          source: [{ kind: 'conditional-on-property', path: relPath, line }],
          group: groupOf(key),
          deprecation: null,
        })
      }
    }
  }
  return [...found.values()]
}

/** Spring 元数据 hints：按属性名取可选值 */
function buildHintsMap(metadata) {
  const map = new Map()
  for (const hint of metadata?.hints ?? []) {
    if (hint?.name && Array.isArray(hint.values)) {
      map.set(hint.name, hint.values.map((v) => v.value))
    }
  }
  return map
}

function groupOf(key) {
  // 取 key 的前两段作为分组依据（如 ypbin.cache.multi-level.enabled → ypbin.cache）
  const parts = key.split('.')
  return parts.length >= 2 ? `${parts[0]}.${parts[1]}` : key
}

/** 单个模块的属性条目 */
async function buildEntries(module, metadata, extras) {
  const hints = buildHintsMap(metadata)
  const entries = []
  for (const property of metadata?.properties ?? []) {
    const key = property.name
    const sourceType = property.sourceType ?? null
    const relPath = await classToSourcePath(sourceType)
    const line = await findLine(relPath, key.split('.').pop())
    const sources = []
    if (relPath) sources.push({ kind: 'java-property', path: relPath, line })
    sources.push(...(property.sourceMethod ? [{ kind: 'java-method', path: relPath, line }] : []))
    const defaultValue = property.defaultValue ?? null
    const allowedValues = hints.get(key) ?? []
    const notes = extras.safetyNotes[key] ?? []
    entries.push({
      module,
      key,
      type: property.type ?? '—',
      defaultDefined: defaultValue !== null && defaultValue !== undefined,
      defaultValue,
      required: property.deprecation ? false : false,
      requiredWhen: null,
      description: cleanDescription(property.description),
      allowedValues,
      enablement: extras.enablement[key] ?? DEFAULT_ENABLEMENT,
      safetyNotes: notes,
      sources,
      default: defaultValue,
      values: allowedValues,
      notes,
      source: sources,
      group: groupOf(key),
      deprecation: property.deprecation ?? null,
    })
  }
  entries.sort((a, b) => a.key.localeCompare(b.key))
  return entries
}

async function main() {
  const modules = await listModules()
  const extras = JSON.parse(await readFile(extrasFile, 'utf8'))
  const result = {
    schemaVersion: 1,
    generatedAt: null, // 生成时填入，避免无变更时因时间戳导致 --check 失败
    sourceRoot: 'ypbin-starter',
    methodology: [
      '扫描各模块 target/classes/META-INF/spring-configuration-metadata.json（spring-boot-configuration-processor 产物）',
      '由 sourceType 定位源文件并按属性名回填行号（best-effort）',
      '展开对象列表属性（List<Pojo> → xxx[].field），补充元数据不展开的叶子字段',
      '扫描 @ConditionalOnProperty 专用开关，补齐无绑定字段的装配开关',
      '合并 config-metadata-extras.json 中的启用条件、生产注意与宿主标准配置',
      '本文件由 tools/export-config-metadata.mjs 生成，禁止手工编辑',
    ],
    summary: {},
    prefixIndex: [],
    modules: {},
  }

  const knownKeys = new Set()
  const perModule = new Map()
  let missing = 0

  // 第一轮：元数据属性 + 其列表展开
  for (const module of modules) {
    const metadata = await readMetadata(module)
    const host = extras.standardHostProperties[module] ?? []
    if (!metadata && host.length === 0) {
      missing += 1
      continue
    }
    const entries = metadata ? await buildEntries(module, metadata, extras) : []
    for (const e of entries) knownKeys.add(e.key)
    const expanded = await expandObjectProperties(module, entries, modules, extras, knownKeys)
    perModule.set(module, { configurationProperties: entries.concat(expanded), standardHostProperties: host })
  }

  // 第二轮：条件开关（排除已由绑定字段覆盖的 key，避免重复）
  for (const module of modules) {
    const switches = await scanConditionalSwitches(module, modules, extras, knownKeys)
    if (!switches.length) continue
    const bucket = perModule.get(module) ?? { configurationProperties: [], standardHostProperties: [] }
    for (const s of switches) {
      knownKeys.add(s.key)
      bucket.configurationProperties.push(s)
    }
    bucket.configurationProperties.sort((a, b) => a.key.localeCompare(b.key))
    perModule.set(module, bucket)
  }

  for (const [module, value] of perModule) result.modules[module] = value

  const customCount = Object.values(result.modules)
    .reduce((sum, m) => sum + m.configurationProperties.length, 0)
  const hostCount = Object.values(result.modules)
    .reduce((sum, m) => sum + m.standardHostProperties.length, 0)
  const prefixes = new Set()
  for (const m of Object.values(result.modules)) {
    for (const e of m.configurationProperties) {
      const parts = e.key.split('.')
      if (parts.length >= 2) prefixes.add(`${parts[0]}.${parts[1]}`)
    }
  }
  result.summary = {
    moduleCount: modules.length,
    modulesWithConfigCount: Object.keys(result.modules).length,
    customPropertyCount: customCount,
    standardHostPropertyCount: hostCount,
    totalEntryCount: customCount + hostCount,
    modulesWithoutMetadata: missing,
  }
  result.prefixIndex = [...prefixes].sort()

  const serialized = `${JSON.stringify(result, null, 2)}\n`

  if (checkMode) {
    if (!existsSync(outFile)) {
      console.error(`\u2716 未找到已提交的元数据文件：${outFile}\n  请运行 node tools/export-config-metadata.mjs 生成后提交。`)
      process.exit(1)
    }
    const committed = await readFile(outFile, 'utf8')
    // 比较时忽略 generatedAt：时间戳变化不代表配置漂移
    // generatedAt 在 --check 路径为 null、在已提交文件中为 ISO 字符串，两者都需归一化
    const strip = (text) => text.replace(/"generatedAt":\s*(?:"[^"]*"|null)/, '"generatedAt": "IGNORED"')
    if (strip(committed) !== strip(serialized)) {
      console.error('\u2716 配置元数据已漂移：已提交文件与当前构建产物不一致。\n  请运行 node tools/export-config-metadata.mjs 重新生成并提交。')
      process.exit(1)
    }
    console.log(`\u2713 配置元数据一致（${customCount} 个 ypbin 配置项 / ${hostCount} 个宿主标准配置项）。`)
    return
  }

  result.generatedAt = new Date().toISOString()
  await mkdir(resolve(toolsDir, 'generated'), { recursive: true })
  await writeFile(outFile, `${JSON.stringify(result, null, 2)}\n`)
  console.log(`\u2713 已生成 ${basename(outFile)}：${customCount} 个 ypbin 配置项 / ${hostCount} 个宿主标准配置项，覆盖 ${Object.keys(result.modules).length} 个模块。`)
  if (missing > 0) {
    console.log(`  （${missing} 个模块未产出元数据：BOM/聚合类模块属正常）`)
  }
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
