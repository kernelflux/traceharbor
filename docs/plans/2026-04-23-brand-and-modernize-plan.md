# TraceHarbor 品牌清理 + 工程现代化总计划

> 起草日期：2026-04-23
> 范围：清理 `com.tencent.*` 残留、引入 `libs.versions.toml`、Groovy → KTS、Java → Kotlin、提供品牌一键切换脚本
> 仓库根：`/Users/okt12/coding/android/ws_opt/traceharbor`

---

## 1. 目标

| # | 目标 | 收益 |
|---|---|---|
| G1 | 彻底去 Tencent/MM 品牌字样（包名 + 类名 + 注释） | 品牌独立、避免商标/混淆 |
| G2 | 全部模块 `build.gradle` (Groovy) → `build.gradle.kts` (KTS) | 类型安全、IDE 跳转、与 toml 配合 |
| G3 | 引入 `gradle/libs.versions.toml` 统一管理版本 | 单点升级、KTS 友好、可被 Renovate/Dependabot 识别 |
| G4 | 全部 `.java` → `.kt` | 单一语言栈（**注：高风险，分批多轮推进**） |
| G5 | 提供 `tools/rename-brand.sh` 一键切换品牌前缀 | 后续从 `com.kernelflux.*` 换 `cn.xxxx.*` 等情况零摩擦 |

## 2. 现状盘点

| 维度 | 数值 |
|---|---|
| `.java` 源文件 | **452** |
| `.kt` 源文件 | 55（主要在 `traceharbor-gradle-plugin`） |
| `build.gradle` (Groovy) | 27 |
| `build.gradle.kts` | 0 |
| `com/tencent/**` 残留物理目录 | 14 处（3 大块：arscutil、android-lib/mrs/plugin、test 残留） |
| `com.tencent.*` 字样命中文件 | ~40 |
| 含 `MM*` 类名残留 | `MMTaskJsonResult`、`SQLiteLintOwnDatabase` 等 |

## 3. 风险评估（必读）

### 3.1 Java → Kotlin 全量翻译的真实风险

`traceharbor` 不是普通 Android App，是字节码 + Native + APM 工程，包含：

- **ASM Visitor**（`MethodVisitor`/`ClassVisitor`/`AsmClassVisitorFactory`）：强类型、栈帧敏感，Kotlin 翻译后默认参数/null 推断/`this` 语义不一致会改变插桩行为。
- **JNI 绑定**：JNI 函数命名 `Java_<package>_<class>_<method>`，class 改 Kotlin 后必须保持 `external fun` + `@JvmStatic` + `companion object` 命名严格映射，否则 native 链接断。
- **HPROF 解析（`HprofBufferShrinker` 等）**：手工 `ByteBuffer` 操作 + 性能敏感，Kotlin 装箱/拆箱会拖慢 10-30%。
- **ARSC 解析（`ArscReader`/`ArscWriter`）**：手工字节序处理。
- **IDynamicConfig 接口**：被插桩字节码硬编码引用，**类全限名变更等于 ABI 破坏**，必须配契约保留措施。

### 3.2 Groovy → KTS 的隐性风险

- 当前 27 个 build.gradle 大量依赖 `gradle.ext.XXX` / `rootProject.ext.XXX` 动态属性。KTS 必须 explicit type 化。
- `singleVariant("release")`、`extraPublications`、`androidNamespaces` map 等都要重写。
- maven-publish.gradle / private-properties.gradle / check.gradle 这种"基础设施层"先迁更安全。

### 3.3 类名去 MM 化的隐性风险

- `IDynamicConfig` 全限名 `com.tencent.mrs.plugin.IDynamicConfig` 可能被插桩字节码硬编码引用。重命名前要扫所有 ASM Visitor/`Type.getInternalName(...)`/`"Lcom/tencent/mrs/...;"` 字符串引用。
- 公共 SDK API 改名属于 ABI 破坏，需要在 release notes 中明示 migration guide。

## 4. 分阶段实施计划（按依赖顺序，从低风险到高风险）

### 阶段 0：本计划文档（当前文件）+ 跟踪文档骨架
- ✅ 输出：`docs/plans/2026-04-23-brand-and-modernize-plan.md`
- ✅ 后续追加：`docs/plans/2026-04-23-java-to-kotlin-tracking.md`（在阶段 7 创建）

### 阶段 1：品牌切换脚本 `tools/rename-brand.sh`
**先做这个**，因为后面阶段 2 要用它当工具。

**功能契约**：
```
tools/rename-brand.sh \
    --from com.kernelflux \
    --to   cn.example \
    [--scope source|all]   \
    [--dry-run|--apply]    \
    [--no-jni]             \
    [--no-namespace]
```

**自动覆盖**：
- 源文件包声明、import、字符串常量（`"com/kernelflux/..."`、`"Lcom/kernelflux/..."`）
- 物理目录 `git mv`（保留 git 历史）
- JNI 函数名（`Java_com_kernelflux_xxx_*` → `Java_cn_example_xxx_*`）
- Android Manifest `package` / Gradle `namespace`
- `applicationId`、`buildConfigField`、`R.class` 引用
- AGP `androidNamespaces` 映射表
- `.gradle/cache`、`.cxx/`、`build/` 等不动（统一让用户手工清）

**安全保护**：
- 默认 `--dry-run`，必须显式加 `--apply`
- 排除 `LICENSE*`、`NOTICE*`、`COPYRIGHT*`、`THIRD_PARTY_NOTICES*`、`docs/`（许可信息保持原始权属）
- 排除 `*/build/`、`*/.gradle/`、`*/.cxx/`、`*/.git/`
- `--apply` 前必须 `git status` 干净（防止把未提交改动吞没）

### 阶段 2：清理 `com.tencent.*` 包名/目录
用阶段 1 的脚本：

```bash
tools/rename-brand.sh --from com.tencent --to com.kernelflux --apply
```

**预期改动**：
- `com/tencent/mm/arscutil/**` → `com/kernelflux/mm/arscutil/**`（保留 `mm` 子包名以减少类名变更，仅改顶层组织）
- `com/tencent/mrs/plugin/IDynamicConfig` → `com/kernelflux/mrs/plugin/IDynamicConfig`
- 各 test 残留 `com/tencent/backtrace`/`com/tencent/matrix_memory_canary` → `com/kernelflux/...`
- 注释/字符串里的品牌字样

**不改的部分**：
- `LICENSE` / `NOTICE` 中 Tencent 版权声明全部保留（合规要求）
- `traceharbor-resource-canary/.../inspector/MatchInfo.java` 等历史注释里"Copyright (c) Tencent"保留

### 阶段 3：类名去 MM/Tencent 化
属于公共 API 破坏，必须显式管理：

| 旧类名 | 新类名 | 备注 |
|---|---|---|
| `MMTaskJsonResult` | `TaskJsonResult` | apk-canary output |
| `SQLiteLintOwnDatabase` | `LintOwnDatabase` | sqlite-lint persistence |
| 包级 `com.tencent.mm.arscutil` | `com.kernelflux.arscutil` | 二级包同时去 mm（更彻底） |

**操作流程**：
1. `git mv` 所有受影响类文件
2. `find/grep` 替换全工程 import
3. 单独跑各模块 `compileJava` 验证
4. release notes 里登记 migration guide

### 阶段 4：引入 `gradle/libs.versions.toml`
**纯收益、零破坏**。

**建立的 catalog 内容**：
```toml
[versions]
agp = "8.2.2"
kotlin = "1.8.22"
asm = "9.6"
guava = "32.1.3-jre"
gson = "2.10.1"
junit = "4.13.2"
mavenCentralUploader = "1.0.10"
pluginPublish = "1.3.1"
# ...全部当前 hard-coded 版本

[libraries]
agp = { module = "com.android.tools.build:gradle", version.ref = "agp" }
asm = { module = "org.ow2.asm:asm", version.ref = "asm" }
asm-commons = { module = "org.ow2.asm:asm-commons", version.ref = "asm" }
asm-util = { module = "org.ow2.asm:asm-util", version.ref = "asm" }
# ...

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
maven-central-uploader = { id = "com.kernelflux.maven.publish", version.ref = "mavenCentralUploader" }
plugin-publish = { id = "com.gradle.plugin-publish", version.ref = "pluginPublish" }
```

**`settings.gradle` 启用**（KTS 也支持）：
```groovy
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}
```

> 注：当前 settings.gradle 同时被 root build.gradle 和各模块用，`libs` 必须在 dependencyResolutionManagement 里 create 才能被 Groovy/KTS 同时引用。

### 阶段 5：基础设施层迁 KTS
顺序（依赖从下往上）：

1. `gradle/private-properties.gradle` → `gradle/private-properties.gradle.kts`
2. `gradle/maven-publish.gradle` → `gradle/maven-publish.gradle.kts`
3. `gradle/check.gradle` → `gradle/check.gradle.kts`
4. `settings.gradle` → `settings.gradle.kts`
5. `build.gradle` (root) → `build.gradle.kts` (root)

**关键改写点**：
- `gradle.ext.XXX = "yyy"` → `extra["XXX"] = "yyy"` + 显式声明 typed 访问器
- `rootProject.ext.androidNamespaces` map → 用 typed `Map<String, String>` 显式声明
- `apply from:` → `apply(from = ...)` + 注意 KTS 跨脚本访问 `rootProject.extra` 类型问题

### 阶段 6：模块 build.gradle 迁 KTS
**分批顺序**（依赖少 → 依赖多 → 含 cmake）：

**Batch A（纯 java/library，最简单）**：
- `traceharbor-commons`（pure JVM）
- `traceharbor-arscutil`（pure JVM）
- `traceharbor-resource-canary/traceharbor-resource-canary-common`
- `traceharbor-resource-canary/traceharbor-resource-canary-analyzer`
- `traceharbor-resource-canary/traceharbor-resource-canary-analyzer-cli`

**Batch B（android-library 无 cmake）**：
- `traceharbor-android-lib`
- `traceharbor-apk-canary`
- `traceharbor-battery-canary`
- `traceharbor-memory-canary`

**Batch C（android-library 带 cmake / 复杂）**：
- `traceharbor-android-commons`（多 sub-cmake）
- `traceharbor-trace-canary`
- `traceharbor-io-canary`
- `traceharbor-hooks` + `cxx-static`
- `traceharbor-backtrace` + `cxx-static`
- `traceharbor-opengl-leak` + `cxx-static`
- `traceharbor-mallctl`、`traceharbor-memguard`、`traceharbor-fd`、`traceharbor-traffic`
- `traceharbor-resource-canary/traceharbor-resource-canary-android`
- `traceharbor-sqlite-lint/traceharbor-sqlite-lint-android-sdk`（带 flavors）

**Batch D（plugin 与 sample）**：
- `traceharbor-gradle-plugin`（含 `com.gradle.plugin-publish` 配置）
- `samples/sample-android`

**每个 batch 完成必须**：
- `./gradlew :<module>:assembleRelease` 通过
- commit 一次（独立 checkpoint）

### 阶段 7：Java → Kotlin 迁移计划文档
单独输出 `docs/plans/2026-04-23-java-to-kotlin-tracking.md`，把 452 个文件按风险分级到 P0/P1/P2/P3：

| 等级 | 描述 | 处理策略 |
|---|---|---|
| P0 | 纯 POJO/data class、配置类、Builder | IntelliJ J2K 自动转 + 简单手工修 |
| P1 | Util 类、Manager 类、纯 API/算法 | J2K + 手工修 + 单测 |
| P2 | Activity/Fragment/Service、ASM Visitor | J2K 后必须人工逐行 review |
| P3 | JNI 绑定类、HPROF/ARSC 解析、字节码强类型操作 | **不动** 或单独项目专人翻 |

> **P3 默认不翻**，除非用户在该模块 release notes 明示 ABI 破坏 + 提供 native re-binding 方案。

### 阶段 8：Java → Kotlin 按模块执行（多轮）
每轮 1-2 个模块：
1. 列出模块所有 `.java` 文件
2. 用 IntelliJ J2K 翻译（命令行：`kotlinc -script ConvertJavaToKotlin.kts`，或手工 IDE 操作）
3. 修复编译错误（`@JvmStatic` / `@JvmField` / `external fun` / null-safety）
4. `./gradlew :<module>:assembleRelease + :testDebugUnitTest` 全过
5. commit 一次

## 5. Checkpoint / 回滚策略

每个阶段完成后：
- `git commit -m "feat(rename): <阶段描述>"`（独立 commit，便于 revert）
- `git tag rename-stage-N`（重要 milestone 打 tag）
- `./gradlew :traceharbor-gradle-plugin:validatePlugins` + `./gradlew assembleRelease` 全模块编译通过

**任何阶段 build 失败超过 30 分钟修复无果**：
- `git reset --hard <last-checkpoint>`
- 改单个模块 isolated 重试

## 6. 不在本计划范围内

- CI 配置（`.circleci`/`.github/workflows`）的现代化 —— 走独立 PR
- 文档站点改造 —— 走独立 PR
- 单测覆盖率提升 —— 走独立 PR
- Native 代码 (`*.cpp`/`*.h`) 重构 —— 走独立 PR

## 7. 当前 todo 跟踪

```
[in_progress] 阶段 0：写本文档
[pending]     阶段 1：tools/rename-brand.sh
[pending]     阶段 2：tencent → kernelflux 包名/目录
[pending]     阶段 3：类名去 MM 化
[pending]     阶段 4：libs.versions.toml
[pending]     阶段 5：基础设施层迁 KTS
[pending]     阶段 6：模块 build.gradle 迁 KTS
[pending]     阶段 7：Java→Kotlin 计划文档
[pending]     阶段 8：Java→Kotlin 多轮执行
```

后续每阶段完成时同步更新本表。
