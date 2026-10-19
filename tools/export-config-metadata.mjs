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
 *
 * 活文档引用（如 docs/MODULES.md）不写死行号：extras 中记录唯一「锚点」文本（`anchor`），
 * 生成时读取目标文件并回填当前行号到 `line`（输出字段与消费方契约不变）。锚点缺失或不唯一时
 * 写 `line: null` 并告警，绝不猜测行号；`--check` 下锚点无法唯一解析会直接失败，
 * 避免“文档一编辑、引用就静默指向错误内容”。
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
/** 确实删除了模块/配置项时才允许显式声明，否则拒绝对已提交基线做「静默缩小」 */
const allowShrink = process.argv.includes('--allow-shrink')

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
 * 把 `anchor` 解析为目标文件中的当前行号。
 *
 * 活文档会被持续编辑，写死行号必然漂移且不会报错；锚点文本（唯一出现）随内容走，编辑后仍能定位。
 * 找不到、出现多次或文件缺失一律返回 `line: null`——调用方负责告警，绝不猜测行号。
 *
 * @returns {{ line: number|null, reason: 'resolved'|'file-missing'|'not-found'|'ambiguous', hits: number[] }}
 */
async function resolveAnchor(relPath, anchor) {
  const content = await readSource(relPath)
  if (content === null) return { line: null, reason: 'file-missing', hits: [] }
  const hits = []
  const lines = content.split('\n')
  for (let i = 0; i < lines.length; i += 1) {
    if (lines[i].includes(anchor)) hits.push(i + 1)
  }
  if (hits.length === 1) return { line: hits[0], reason: 'resolved', hits }
  return { line: null, reason: hits.length === 0 ? 'not-found' : 'ambiguous', hits }
}

/**
 * 把 extras 中带 `anchor` 的 source 原地解析为行号：写入 `line`，并删除仅用于定位的 `anchor`，
 * 使输出仍严格保持 `{ kind, path, line }` 契约（消费方无需改动）。
 *
 * @returns {Promise<Array<{module: string, key: string, path: string, anchor: string, reason: string, hits: number[]}>>}
 *          未能唯一解析的锚点清单（`line` 已写为 null）
 */
async function resolveAnchoredSources(extras) {
  const issues = []
  const seen = new Set()
  for (const [module, entries] of Object.entries(extras.standardHostProperties ?? {})) {
    for (const entry of entries) {
      // entries 同时维护 sources / source 两份镜像，二者需一致解析
      for (const field of ['sources', 'source']) {
        const sources = entry[field]
        if (!Array.isArray(sources)) continue
        for (const source of sources) {
          if (!source.anchor) continue
          const anchor = source.anchor
          const { line, reason, hits } = await resolveAnchor(source.path, anchor)
          source.line = line
          delete source.anchor
          if (line === null) {
            const id = `${module}\u0000${entry.key}\u0000${source.path}\u0000${anchor}`
            if (seen.has(id)) continue
            seen.add(id)
            issues.push({ module, key: entry.key, path: source.path, anchor, reason, hits })
          }
        }
      }
    }
  }
  return issues
}

/** 打印未能唯一解析的锚点（生成与 --check 两条路径共用） */
function reportAnchorIssues(issues) {
  if (!issues.length) return
  console.warn(`\u26a0 ${issues.length} 处文档锚点未能唯一解析，line 记为 null（不编造行号）：`)
  for (const issue of issues) {
    const detail =
      issue.reason === 'ambiguous'
        ? `锚点在 ${issue.path} 出现 ${issue.hits.length} 次（行 ${issue.hits.join(', ')}），锚点必须唯一`
        : issue.reason === 'file-missing'
          ? `文件不存在：${issue.path}`
          : `锚点在 ${issue.path} 中未找到`
    console.warn(`  - ${issue.module} / ${issue.key} → ${detail}：${JSON.stringify(issue.anchor)}`)
  }
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
 * 收集源文件中的 `static final String NAME = "值"` 常量，键为「声明类简单名.常量名」。
 *
 * 供 {@link scanConditionalSwitches} 解析 `@ConditionalOnProperty(prefix = XxxProperties.PREFIX)`
 * 这类前缀常量：前缀统一为常量后注解里不再出现字面量，若仍只认字面量，配置参考会静默丢掉这些开关。
 * 只解析纯字符串字面量初始化；拼接等表达式一律不猜测（解析不到即按原逻辑跳过，绝不编造）。
 */
async function buildConstantIndex(modules) {
  const index = new Map()
  for (const module of modules) {
    const srcRoot = join(root, module, 'src', 'main', 'java')
    if (!existsSync(srcRoot)) continue
    const files = []
    async function walk(dir) {
      for (const entry of await readdir(dir, { withFileTypes: true })) {
        const full = join(dir, entry.name)
        if (entry.isDirectory()) await walk(full)
        else if (entry.name.endsWith('.java')) files.push(full)
      }
    }
    await walk(srcRoot)
    for (const file of files) {
      const content = await readFile(file, 'utf8')
      if (!content.includes('static final String')) continue
      const decls = [...content.matchAll(/\b(?:class|interface|enum|record)\s+([A-Za-z_$][\w$]*)/g)]
      for (const match of content.matchAll(/\bstatic\s+final\s+String\s+([A-Za-z_$][\w$]*)\s*=\s*"([^"]*)"/g)) {
        const owner = decls.filter((decl) => decl.index < match.index).pop()
        if (!owner) continue
        index.set(`${owner[1]}.${match[1]}`, match[2])
      }
    }
  }
  return index
}

/**
 * 解析注解里的前缀引用（如 `GatewayProperties.Auth.PREFIX`）。
 *
 * 只认「类简单名.常量名」形式；查不到返回 null，由调用方跳过（不猜字面量）。
 */
function resolvePrefixReference(reference, constantIndex) {
  if (!reference) return null
  const parts = reference.split('.')
  if (parts.length < 2) return null
  return constantIndex.get(parts.slice(-2).join('.')) ?? null
}

/**
 * 扫描 `@ConditionalOnProperty` 专用开关。
 *
 * Spring 元数据只收录 `@ConfigurationProperties` 字段，形如 `ypbin.resilience.enabled`
 * 这类「仅由自动装配开关消费、没有绑定字段」的配置项不会出现在元数据里，需从源码补齐，
 * 否则配置参考会漏掉这类开关（此前人工快照正是靠手工补，容易漂移）。
 *
 * 前缀既支持字面量，也支持指向 `static final String` 常量的引用（见 {@link buildConstantIndex}）。
 */
async function scanConditionalSwitches(module, modules, extras, knownKeys, constantIndex) {
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
        ?? resolvePrefixReference(/prefix\s*=\s*([A-Za-z_$][\w$.]*)/.exec(body)?.[1], constantIndex)
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
  // 先把活文档引用的 anchor 解析为当前行号（原地写回 line），再做后续聚合
  const anchorIssues = await resolveAnchoredSources(extras)
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
  const constantIndex = await buildConstantIndex(modules)
  for (const module of modules) {
    const switches = await scanConditionalSwitches(module, modules, extras, knownKeys, constantIndex)
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

  // 缩小守卫（2026-10-19 加）：本脚本只读各模块 `target/classes`，若先跑过 `clean`（或只构建了
  // 部分模块），模块集合/条目会**静默缩小**，而此后 `--check` 永远通过 —— 属「假绿」。
  // 实测事故：某次生成误删 9000 行（ypbin.ai.* 等整段消失）。
  // 判据：与已提交基线比较，模块集合或属性条目**减少**即失败，除非显式传 `--allow-shrink`。
  if (!checkMode && existsSync(outFile)) {
    const baseline = JSON.parse(await readFile(outFile, 'utf8'))
    const baselineModules = new Set(Object.keys(baseline.modules ?? {}))
    const currentModules = new Set(Object.keys(result.modules))
    const lostModules = [...baselineModules].filter((m) => !currentModules.has(m))
    const baselineKeys = new Set(
      Object.values(baseline.modules ?? {}).flatMap((m) => (m.configurationProperties ?? []).map((e) => e.key)),
    )
    const currentKeys = new Set(
      Object.values(result.modules).flatMap((m) => m.configurationProperties.map((e) => e.key)),
    )
    const lostKeys = [...baselineKeys].filter((k) => !currentKeys.has(k))
    if (lostModules.length > 0 || lostKeys.length > 0) {
      const detail = [
        lostModules.length > 0 ? `丢失模块 ${lostModules.length} 个：${lostModules.slice(0, 5).join(', ')}` : null,
        lostKeys.length > 0 ? `丢失配置项 ${lostKeys.length} 个：${lostKeys.slice(0, 8).join(', ')}` : null,
      ].filter(Boolean).join('；')
      if (!allowShrink) {
        console.error(
          `\u2716 生成结果比已提交基线**变小**（${detail}）。\n` +
            '  这通常意味着本轮构建不完整（如先跑了 `clean`、或只构建了部分模块）——\n' +
            '  生成器只读各模块 target/classes，缺产物就会静默丢条目。\n' +
            '  处理：先 `mvn -DskipTests package` 全量构建再生成；确属删除能力/模块时显式加 `--allow-shrink`。',
        )
        process.exit(1)
      }
      console.warn(`\u26a0 已显式声明 --allow-shrink，接受缩小：${detail}`)
    }
  }


  reportAnchorIssues(anchorIssues)

  if (checkMode) {
    if (anchorIssues.length > 0) {
      console.error('\u2716 存在无法唯一解析的文档锚点：请修正 tools/config-metadata-extras.json 中对应 source 的 anchor（须在目标文件中唯一出现）。')
      process.exit(1)
    }
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
  if (anchorIssues.length > 0) {
    console.warn(`\u26a0 上述 ${anchorIssues.length} 处锚点已写为 line: null；\`node tools/export-config-metadata.mjs --check\` 会因此失败，请先修好锚点。`)
  }
  if (missing > 0) {
    console.log(`  （${missing} 个模块未产出元数据：BOM/聚合类模块属正常）`)
  }
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
