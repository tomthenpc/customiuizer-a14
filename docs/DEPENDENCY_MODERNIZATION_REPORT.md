# 依赖与构建系统现代化报告

## 范围与基线

- 基线提交：`5226144497eacd29337ec33548c3d9083371afb4`
- 分支：`codex/r14.12-modern-api102-kotlin`
- 审计日期：2026-07-26
- Java 工具链：Microsoft JDK 17.0.19，保持不变
- Android 配置：`compileSdk=37`、`buildToolsVersion=37.0.0`、`minSdk=34`、`targetSdk=34`，保持不变
- libxposed 元数据：`minApiVersion=101`、`targetApiVersion=102`、`staticScope=false`，保持不变
- 版本号与签名配置均未修改

审计使用了：

```powershell
.\gradlew.bat --no-daemon buildEnvironment
.\gradlew.bat --no-daemon app:dependencies
.\gradlew.bat --no-daemon dependencyUpdates
.\gradlew.bat --no-daemon :app:dependencyInsight --dependency org.jetbrains.kotlin:kotlin-stdlib --configuration releaseRuntimeClasspath
.\gradlew.bat --no-daemon :app:dependencyInsight --dependency org.jetbrains.kotlinx:kotlinx-coroutines-core --configuration releaseRuntimeClasspath
```

## 最终变更

| 组件 | 原版本 | 最终版本 | 结论 |
| --- | --- | --- | --- |
| Gradle Wrapper | 9.5.1 | 9.6.1 | 更新到当前稳定补丁版；加入官方 binary ZIP SHA-256 校验 |
| Kotlin stdlib BOM | 2.2.21 | 2.3.21 | 更新到 AGP 内置 Kotlin 2.2 编译器可读取的最新 2.3 补丁版 |
| kotlinx.coroutines Android/Test | 1.6.4 | 1.11.0 | 更新到官方稳定版；应用 Release 类路径统一解析为 1.11.0 |

Gradle 9.6.1 的 `distributionSha256Sum` 为：

```text
9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14
```

## 保持不变的直接依赖

| 组件 | 版本 | 保持原因 |
| --- | --- | --- |
| Android Gradle Plugin | 9.2.1 | 官方 9.2 稳定线最新补丁；9.4 仅有 alpha |
| AGP 内置 Kotlin Gradle Plugin | 2.2.10 | 由 AGP 9.2.1 提供，不单独覆盖；避免破坏 built-in Kotlin 组合 |
| ben-manes versions plugin | 0.54.0 | 当前稳定版本 |
| libxposed API | 102.0.0 | 官方最新稳定版本；继续 `compileOnly` |
| libxposed service | 102.0.0 | 官方最新稳定版本 |
| AppCompat | 1.7.1 | 官方稳定通道最新；1.8.0 仍为 alpha |
| Preference | 1.2.1 | 官方稳定通道最新，且该库处于维护模式 |
| Palette | 1.0.0 | 官方稳定通道最新；1.1.0 仍为 alpha |
| DexKit | 2.2.0 | 官方最新 Release |
| Apache Commons Lang | 3.20.0 | 官方最新正式 Release；3.21.0 尚未发布 |
| JUnit 4 | 4.13.2 | JUnit 4 最终稳定版本；不借本阶段迁移测试框架 |

项目未直接声明 Material Components、Fragment、RecyclerView、ConstraintLayout、Activity、Lifecycle 或 Core KTX。当前出现的这些组件来自 AndroidX 传递依赖。本阶段没有为了追求版本数字而显式覆盖传递版本，因为这会扩大 UI 与生命周期回归范围，且没有发现对应阻断问题。

## Kotlin 版本边界

AGP 9.2.1 默认使用 built-in Kotlin，实际构建环境中的 Kotlin Gradle Plugin/编译器为 2.2.10。版本目录中的 `kotlin` 仅控制应用运行时的 Kotlin BOM，不会升级编译器。

曾隔离测试 Kotlin BOM 2.4.10。`compileDebugKotlin` 明确失败：

```text
Module was compiled with an incompatible version of Kotlin.
The binary version of its metadata is 2.4.0, expected version is 2.2.0.
```

因此没有使用 `-Xskip-metadata-version-check`、全局 suppress 或替换 AGP 内置编译器来绕过。最终选择 2.3.21；其 metadata 仍在当前编译器可读取的 2.3 边界内，并已通过完整构建。

## 版本锁定与新增依赖

- 所有直接依赖继续集中在 `gradle/libs.versions.toml`，没有动态版本。
- Gradle Wrapper 下载增加官方 SHA-256 固定值。
- 未新增运行时依赖。
- 未引入 dependencyGuard。当前 version catalog 已覆盖直接版本，额外生成全配置传递依赖锁会带来较大的 Android/AGP 配置维护面，而本阶段没有发现依赖漂移问题。
- 未采用 alpha、beta、RC、nightly、snapshot 或未发布 commit。

## 验证

Gradle、Kotlin BOM 与 Coroutines 每组均独立验证。最终组合执行：

```powershell
.\gradlew.bat --no-daemon clean test lint lintRelease lintVitalRelease assembleDebug assembleRelease
```

结果：

- 单元测试：通过
- Debug：通过
- Release：通过
- Kotlin/Java 编译：通过
- duplicate class 检查：通过
- Manifest 处理：通过
- Lint、lintRelease：通过
- lintVitalRelease：任务按 AGP 当前配置正常跳过
- R8：通过
- 资源压缩：通过
- Release 签名校验任务：通过
- 配置缓存：可生成并复用

首次验证 Gradle 9.6.1 时，外层命令超时后遗留的 Gradle 子进程与后续 `clean` 并发，随后一次 `lintReportRelease` 出现内部参数错误。等待遗留进程结束后，隔离 `lintRelease` 和原始完整命令均成功复现通过，因此没有通过关闭 Lint 或配置缓存来规避。

阶段 F 前后的 Release APK 均为 `3,020,257` 字节。R8 后体积没有变化，不能把本次依赖更新表述为 APK 体积优化。

## 官方依据

- Gradle 9.6.1 Release Notes: <https://docs.gradle.org/9.6.1/release-notes.html>
- Gradle checksums: <https://gradle.org/release-checksums/>
- AGP 9.2 Release Notes: <https://developer.android.com/build/releases/agp-9-2-0-release-notes>
- AGP built-in Kotlin migration: <https://developer.android.com/build/migrate-to-built-in-kotlin>
- Kotlin release process: <https://kotlinlang.org/docs/releases.html>
- Kotlin/AGP/R8 compatibility: <https://developer.android.com/build/kotlin-support>
- kotlinx.coroutines releases: <https://github.com/Kotlin/kotlinx.coroutines/releases>
- AndroidX stable channel: <https://developer.android.com/jetpack/androidx/versions/stable-channel>
- DexKit releases: <https://github.com/LuckyPray/DexKit/releases>
- libxposed API releases: <https://github.com/libxposed/api/releases>
- libxposed service releases: <https://github.com/libxposed/service/releases>
- Commons Lang releases: <https://commons.apache.org/proper/commons-lang/changes.html>
