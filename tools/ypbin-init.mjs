#!/usr/bin/env node
/**
 * ypbin 项目脚手架生成器。
 *
 * 从一个可直接编译运行的项目骨架开始，而不是让接入方自行挑选 36 个模块。
 * 内置四档预设，覆盖常见项目形态：
 *
 *   api-only      纯 REST API（Web + 统一响应 + 接口文档），无数据库
 *   monolith      单体应用（Web + 数据访问 + 缓存 + 安全 + 日志 + 工具）
 *   microservice  微服务（注册配置 + Feign + 网关客户端；含独立 service-api 契约模块）
 *   worker        后台任务进程（非 Web：数据访问 + 异步 + 定时任务 + 日志）
 *
 * 用法：
 *   node tools/ypbin-init.mjs <preset> <artifactId> [选项]
 *
 * 选项：
 *   --group=cn.example         Maven groupId（默认 cn.ypbin.demo）
 *   --package=cn.example.demo  Java 包名（默认 <groupId>.<artifactId 归一化>）
 *   --name="示例服务"           应用展示名（默认取 artifactId）
 *   --port=8080               HTTP 端口（默认 8080；worker 预设忽略）
 *   --out=./my-app            输出目录（默认 ./<artifactId>）
 *   --force                   目标目录非空时也继续（会覆盖同名文件）
 *
 * 示例：
 *   node tools/ypbin-init.mjs monolith demo-admin --group=cn.ypbin.demo --out=/tmp/demo-admin
 *
 * 生成后进入目录执行 `mvn -o test` 即可验证骨架可用。
 */
import { readdir, readFile, writeFile, mkdir, stat } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { resolve, join, dirname, relative } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const templatesDir = resolve(import.meta.dirname, 'templates')

const PRESETS = {
  'api-only': {
    description: '纯 REST API（Web + 统一响应 + 接口文档），无数据库',
    port: '8080',
  },
  monolith: {
    description: '单体应用（Web + 数据访问 + 缓存 + 安全 + 日志 + 工具）',
    port: '8080',
  },
  microservice: {
    description: '微服务（注册配置 + Feign + 服务契约模块）',
    port: '8080',
  },
  worker: {
    description: '后台任务进程（非 Web：数据访问 + 异步 + 定时任务）',
    port: '',
  },
}

function parseArgs(argv) {
  const positional = []
  const options = {}
  for (const arg of argv) {
    const match = /^--([^=]+)(?:=(.*))?$/.exec(arg)
    if (match) {
      options[match[1]] = match[2] ?? 'true'
    } else {
      positional.push(arg)
    }
  }
  return { positional, options }
}

function normalizeArtifactId(value) {
  return value
    .replace(/[^A-Za-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toLowerCase()
}

function packageToPath(packageName) {
  return packageName.replaceAll('.', '/')
}

function classNameOf(artifactId) {
  return artifactId
    .split(/[-_]/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join('')
}

/** 读取 starter 当前版本：优先取根 pom 的 revision，保证生成的项目与本地仓库一致 */
async function starterVersion() {
  const pom = await readFile(join(root, 'pom.xml'), 'utf8')
  const match = /<revision>([^<]+)<\/revision>/.exec(pom)
  if (!match) {
    throw new Error('未能从根 pom 解析 <revision>，无法确定 starter 版本')
  }
  return match[1]
}

/** 读取 starter 基线使用的 Spring Boot 版本，保证生成项目与 starter 平台版本一致 */
async function springBootVersion() {
  const pom = await readFile(join(root, 'ypbin-starter-dependencies', 'pom.xml'), 'utf8')
  const match = /<spring-boot\.version>([^<]+)<\/spring-boot\.version>/.exec(pom)
  if (!match) {
    throw new Error('未能从 ypbin-starter-dependencies 解析 spring-boot.version')
  }
  return match[1]
}

/** 递归收集模板文件 */
async function collectFiles(dir) {
  const result = []
  async function walk(current) {
    for (const entry of await readdir(current, { withFileTypes: true })) {
      const full = join(current, entry.name)
      if (entry.isDirectory()) {
        await walk(full)
      } else {
        result.push(full)
      }
    }
  }
  await walk(dir)
  return result
}

function applyPlaceholders(content, values) {
  let output = content
  for (const [key, value] of Object.entries(values)) {
    output = output.replaceAll(`__${key}__`, value)
  }
  return output
}

/**
 * 校验模板相对路径在替换后仍然合法。
 *
 * 目录名里出现 `.`（包名被当成单级目录）或残留 `__`（占位符拼错/漏配）都会生成
 * 「能编译但目录结构错乱」的项目——javac 不要求目录与包名一致，所以这种缺陷
 * 不会在 `mvn test` 中暴露，必须在生成阶段直接失败。
 */
function assertPathIsSane(templateRelPath, targetRelPath) {
  const segments = targetRelPath.split(/[\\/]/)
  for (const dir of segments.slice(0, -1)) {
    if (dir.includes('.')) {
      throw new Error(
        `模板路径含点号目录「${dir}」（${templateRelPath}）：包名目录占位请用 __PACKAGE_PATH__，不要用 __PACKAGE__`)
    }
  }
  if (targetRelPath.includes('__')) {
    throw new Error(`模板路径存在未替换占位符（${templateRelPath} → ${targetRelPath}）：请检查占位符拼写与取值`)
  }
}

async function main() {
  const { positional, options } = parseArgs(process.argv.slice(2))

  if (positional.length === 0 || positional[0] === 'help' || options.help) {
    console.log('ypbin 项目脚手架生成器\n')
    console.log('用法：node tools/ypbin-init.mjs <preset> <artifactId> [选项]\n')
    console.log('预设：')
    for (const [name, preset] of Object.entries(PRESETS)) {
      console.log(`  ${name.padEnd(14)} ${preset.description}`)
    }
    console.log('\n选项：--group= --package= --name= --port= --out= --force')
    return
  }

  const presetName = positional[0]
  const preset = PRESETS[presetName]
  if (!preset) {
    console.error(`✖ 未知预设：${presetName}\n  可用预设：${Object.keys(PRESETS).join('、')}`)
    process.exit(1)
  }

  const rawArtifactId = positional[1]
  if (!rawArtifactId) {
    console.error('✖ 缺少 artifactId，例如：node tools/ypbin-init.mjs monolith demo-admin')
    process.exit(1)
  }
  const artifactId = normalizeArtifactId(rawArtifactId)

  const groupId = options.group ?? 'cn.ypbin.demo'
  const packageName = options.package ?? `${groupId}.${artifactId.replaceAll('-', '')}`
  const appName = options.name ?? artifactId
  const port = options.port ?? preset.port
  const outDir = resolve(options.out ?? `./${artifactId}`)
  const version = await starterVersion()
  const bootVersion = await springBootVersion()

  const values = {
    GROUP_ID: groupId,
    ARTIFACT_ID: artifactId,
    PACKAGE: packageName,
    PACKAGE_PATH: packageToPath(packageName),
    APP_NAME: appName,
    APP_CLASS: `${classNameOf(artifactId)}Application`,
    PORT: port,
    STARTER_VERSION: version,
    SPRING_BOOT_VERSION: bootVersion,
  }

  const templateDir = join(templatesDir, presetName)
  if (!existsSync(templateDir)) {
    console.error(`✖ 模板目录不存在：${templateDir}`)
    process.exit(1)
  }

  if (existsSync(outDir) && !options.force) {
    const entries = await readdir(outDir)
    if (entries.length > 0) {
      console.error(`✖ 目标目录非空：${outDir}\n  如需覆盖请加 --force，或用 --out= 指定新目录。`)
      process.exit(1)
    }
  }

  const files = await collectFiles(templateDir)
  let written = 0
  for (const file of files) {
    const relPath = relative(templateDir, file)
    // 模板中以 `__PACKAGE_PATH__` 占位的 Java 源码目录按真实包名落盘
    const targetRel = applyPlaceholders(relPath, values)
    assertPathIsSane(relPath, targetRel)
    const target = join(outDir, targetRel)
    await mkdir(dirname(target), { recursive: true })
    const content = await readFile(file, 'utf8')
    await writeFile(target, applyPlaceholders(content, values))
    written += 1
  }

  console.log(`✓ 已生成 ${presetName} 预设项目：${outDir}`)
  console.log(`  groupId=${groupId}  artifactId=${artifactId}  package=${packageName}`)
  console.log(`  starter=${version}  文件数=${written}`)
  console.log('\n下一步：')
  console.log(`  cd ${relative(process.cwd(), outDir) || '.'}`)
  console.log('  mvn -o test          # 单元测试（离线可用）')
  console.log('  mvn spring-boot:run  # 启动')
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
