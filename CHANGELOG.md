# Changelog

简体中文 | [English](CHANGELOG_EN.md)

本文件记录公开版本的用户可见变化、兼容边界、验证结论和回退价值。内部迁移批次、
Agent 工作记录、临时 APK 和未经同条件测量的性能数字不作为 Release changelog。

## 公开 Release

| 版本 | 日期 | 定位 |
| --- | --- | --- |
| `r14.13.5` | 2026-07-28 | 当前稳定版；修复 r14.13.4 首页搜索导航回归 |
| `r14.13.4` | 2026-07-28 | 已撤回；首页搜索导航回归，由 `r14.13.5` 取代 |
| `r14.12.0` | 2026-07-26 | 上一稳定版；旧签名，升级到新版本前必须备份并重装 |
| `r14.8.0` | 2026-07-25 | Kotlin 基础设施回退点 |
| `r14.7.4` | 2026-07-25 | r14.7.x Kotlin/Coroutine 迁移合并版 |
| `r14.5.0` | 2026-07-24 | 独立包名、签名和发布路径基线 |

Release 标题统一为纯版本号。已移除版本的资产名、大小与 SHA-256 见
[历史 Release 归档](docs/RELEASE_ARCHIVE.md)；对应源码仍可通过 Git tag 获取。

## 开发中（未发布）

### 状态稳定化：界面语言切换

- 统一语言设置状态所有者到 `AppLocaleController`。
- 用户切换语言时弹出确认框；取消不保存、不退出、不改变 Preference。
- 确认后同步 `commit()` 保存选择，标记 `pref_key_miuizer_locale_reconcile_pending`。
- 保存成功后调用 `finishAffinity()` 并结束设置应用自身进程；不重启 SystemUI/Launcher/设备。
- 下次启动 `MainApplication` 时执行一次 Locale 对账，与当前 AppCompat 应用 locale 比较，仅在不一致时应用。
- 移除 `MainActivity.attachBaseContext` 手动 `createConfigurationContext`； Activities 统一由 AppCompat 处理。
- 移除 `AppHelper.getLocaleContext()` 与 `AppHelper.applyLocaleChange()`，不再维护双重 Locale 控制。
- `ListPreferenceEx` 增加 entries/values 不匹配与 value 不在 entryValues 中的防御性回退。
- 新增 `RestartRequirement` 生效等级枚举。
- 新增回归测试：`AppLocaleNormalizationTest`、`AppLocaleEntryTest`、`AppLocaleReconcileTest`、`RestartRequirementTest`。
- 未完成实机 20 轮语言切换验收前不创建新 Release。

## [r14.13.5] - 2026-07-28

### 版本定位

`r14.13.4` 的紧急热修版本。修复首页搜索功能在 `Various` 结果、子分类跳转和返回首页
过程中出现的导航回归，恢复 `0/1/2` 三态搜索状态机，统一 `sub` 空/空白字符串语义，并
修正 `openModCat()` 的返回语义。其余内容与 `r14.13.4` 保持一致。

本版本继续仅支持 HyperOS 1 / Android 14 和 `arm64-v8a`，保持 libxposed
API 101/102 单 APK 兼容边界。使用与 `r14.13.4` 相同的新正式签名证书，可直接覆盖安装
`r14.13.4`。

### 修复

- 修复搜索结果属于 `Various` 页面或带子分类的 System/Launcher/Controls 项目时，
  点击后跳转随即返回首页、目标 Preference 未高亮的问题。
- 恢复明确的搜索导航状态机：
  - `0 = 普通首页`；
  - `1 = 正在显示搜索结果`；
  - `2 = 已从搜索结果进入目标页面，返回首页后清理搜索 UI`。
- 将 `ModData.sub` 改为可空 `String?`，搜索索引不再把无子分类项存成空字符串。
- `MainFragment.openModCat()` 对 System、Launcher、Controls、Various 四类均返回
  导航成功/失败语义，避免把事务结果与分类类型混用。
- `SubFragment` 增加 `sub` 空白保护，避免把空字符串误判为有效子分类并触发
  `PreferenceCategoryEx` 强制类型转换。
- 新增 `SearchRouteResolver` 与 `SearchStateMachine` 纯逻辑单元测试。

### 构建与兼容

- 继续使用已验证的 JDK 17、Gradle 9.6.1、AGP 9.2.1 和 Kotlin 2.3.21。
- Release 保持 R8、资源压缩、zipalign 和 APK Signature Scheme v2。
- 签名证书 SHA-256：
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`。

### 验证

- 单元测试：68 tests, 0 failures, 0 skipped。
- Lint / `lintRelease` / `lintVitalRelease`：通过，107 deprecation warnings，0 errors。
- Debug / Release、R8 和资源压缩：通过（`BUILD SUCCESSFUL in 2m 8s`）。
- APK：`CustoMIUIzer-A14-r14.13.5.apk`。
- APK 大小：3,032,173 bytes。
- APK SHA-256：`89AE5046564F69D491DC44F7B853443113FEC7100FE997ABA9984181C4983EA5`。
- 签名：APK Signature Scheme v2，证书 SHA-256
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`。
- versionCode/versionName：`183 / r14.13.5`。
- `minSdk/targetSdk`：`34 / 34`。
- Xposed metadata：`minApiVersion=101`、`targetApiVersion=102`、`staticScope=false`。

### 重要：r14.13.4 已撤回

- `r14.13.4` 存在首页搜索导航回归，已被 `r14.13.5` 取代。
- 已删除 `r14.13.4` 的 GitHub Release 与 tag；历史资产信息见
  [RELEASE_ARCHIVE.md](docs/RELEASE_ARCHIVE.md)。
- 如已安装 `r14.13.4`，可直接覆盖安装 `r14.13.5`，无需卸载。

## [r14.13.4] - 2026-07-28

### 版本定位

> 已撤回；被 `r14.13.5` 取代。

在 r14.12.0 稳定基线之上，完成设置应用、Locale、主题、生命周期和高频 Hook
路径的集中治理，并正式收口 r14.13 开发线中的架构审计、Kotlin 迁移回归和性能修复。

本版本继续仅支持 HyperOS 1 / Android 14 和 `arm64-v8a`，保持 libxposed
API 101/102 单 APK 兼容边界。

### 设置与界面

- 应用内语言入口集中到 About 页面，支持跟随系统及项目现有多语言。
- 修复语言和日间/夜间模式切换后的 Activity、系统栏与设置页面重建行为。
- About 页面分别显示维护者、上游来源和当前版本。
- 修复搜索结果进入功能后的返回状态及 Fragment 重建状态恢复。
- Launcher、SystemUI 和 Security Center 重启改为后台 Root 命令，并补充无 Root、
  目标未运行和执行失败反馈。
- 整理 Preference 标题、summary、弹窗、间距、圆角和多语言资源。

### 稳定性与性能

- 修复 SystemUI 状态栏温度、电流等文本图标长期强引用旧 View 的问题；主题、密度、
  横竖屏或状态栏重新创建后，失效 View 可以被回收。
- 优化资源替换 Hook 的未命中路径，减少资源读取中的整数装箱、JNI 方法名读取和无效
  资源名称解析，并为 Sparse 容器增加安全发布。
- 修复 Java → Kotlin 迁移后 CPU thermal zone 扫描丢失首次命中退出语义的问题，
  避免周期任务重复打开无关 sysfs 文件。
- 移除 `first|second` 配置解析中的重复 Regex 编译，并增加 PrefPair 回归测试。
- 缓存 application ClassLoader fallback，避免 ROM 合法类缺失时重复执行反射探测。
- 修复 RemotePreferences 早期空快照被永久视为已加载的问题。
- 仅在 preference listener 注册成功后设置注册状态。
- 防止 DexKitBridge 重复创建。

### 构建与兼容

- 继续使用已验证的 JDK 17、Gradle 9.6.1、AGP 9.2.1 和 Kotlin 2.3.21。
- 本版本不包含 AGP 9.3.1 或其他工具链升级。
- 使用 libxposed API/service 102 构建，`minApiVersion=101`、
  `targetApiVersion=102`、`staticScope=false`。
- 公共加载与 Hook 路径保持 API 101 可用；未启用 Hot Reload、hook ID 或原子
  replacement。
- Release 保持 R8、资源压缩、zipalign 和 APK Signature Scheme v2。

### 重要：签名密钥变更

- `r14.12.0` 及更早公开版本使用的旧签名私钥已经遗失，无法继续用于后续构建。
- `r14.13.4` 使用新的正式签名证书，因此不能直接覆盖安装旧公开版本。
- 升级前必须先在旧版本中备份模块设置，然后卸载旧版本、安装 `r14.13.4`、
  重新启用 LSPosed/Vector 作用域、恢复设置并完整重启设备。
- 新签名证书 SHA-256：
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`

### 验证

- 单元测试：45 tests, 0 failures, 0 skipped。
- Lint / `lintRelease` / `lintVitalRelease`：通过，107 deprecation warnings，0 errors。
- Debug / Develop / Release、R8 和资源压缩：通过（`BUILD SUCCESSFUL in 3m 32s`）。
- APK：`CustoMIUIzer-A14-r14.13.4.apk`。
- APK 大小：3,032,173 bytes。
- APK SHA-256：`E8A2BD362C0540972441B8D1DE0BCACE8FE85FEF71F31406F3B4DA1A4027D26C`。
- 签名：APK Signature Scheme v2，证书 SHA-256
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`。
- applicationId：`tv.withaibuild.customiuizer.r14`。
- versionCode/versionName：`182 / r14.13.4`。
- `minSdk/targetSdk`：`34 / 34`。
- Xposed metadata：`minApiVersion=101`、`targetApiVersion=102`、
  `staticScope=false`。

### 已知限制

- 仅支持 HyperOS 1 / Android 14 和 `arm64-v8a`。
- API 102 仍需在对应框架环境进行独立实机覆盖。
- 厂商系统应用更新可能改变 Hook 目标。
- 性能和功耗收益取决于 ROM、启用功能和使用方式，不声明未经同设备对照测量的固定比例。

## [r14.13.3] - 2026-07-27

### 版本定位

> 非公开候选版本；相关改动已由 `r14.13.4` 正式版收口发布。

针对 UI/Locale/About 页面、主题重建、LSPosed 日志审计和 DexKitBridge 初始化的维护性修复与文档同步候选。

### 修复

- 清理设置首页重复语言入口，集中到 About 页面并启用 `valueAsSummary`。
- About 页面拆分为 maintainer、based_on、version 三行信息。
- `MainActivity` `configChanges` 移除 `uiMode`，让系统正常重建以刷新日间/夜间主题。
- `XposedHelpers.createBridge` 增加非空守护，避免 DexKitBridge 重复创建。
- 补充 `prefs_about.xml` 缺失的 `xmlns:miuizer` 命名空间，修复 Release 资源合并。

### 验证

- 构建：单元测试、Lint、`lintRelease`、`lintVitalRelease`、Debug/Release 全部通过。
- APK：`CustoMIUIzer-A14-r14.13.3.apk`，3,039,311 bytes，SHA-256 `FCF048906551ED6BFEC903B1FFCD44796A2A5B777D0327CC5EC16FE520381927`，APK Signature Scheme v2 签名。
- 签名证书 SHA-256：`C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`。
- LSPosed 日志审计 r14.13.3 重启日志：未发现可归因于模块的崩溃、ANR、Hook 失败或 RemotePreferences 异常；tombstones 中未出现模块包名。
- `apksigner verify -v` 与 `aapt2 dump badging` 确认 applicationId、versionCode/versionName、`minSdk`/`targetSdk`、`module.prop` 元数据正确。
- 实机 UI/Locale/Hook 回归与 API 102 环境独立验证尚未完成。

### 签名

- 从 `r14.13.0-rc1` 开始更换 APK 签名证书；`r14.13.3` 继续使用该新证书。
- 原 `r14.12.0` 及更早版本使用的签名私钥已经遗失，无法继续用于后续构建。
- 新签名版本无法直接覆盖安装旧签名版本。
- 升级前需要备份模块设置，卸载旧版本后再安装新版本。
- 新证书 SHA-256：`C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`。

### 已知限制

- 实机设置应用、日间/夜间主题、语言切换、Root 重启反馈仍需验证。
- API 102 独立框架环境尚未验证。

## [r14.13.0-rc1] - 非公开候选

> 该候选版本未单独公开发布；相关工作已由 `r14.13.4` 正式版收口。

### 重构

- 完成 r14.13 第一阶段 Kotlin 与设置层代码整理。
- 完成 Java/Kotlin 边界与核心热路径审计。

### 修复

- 修复 Bitmap 缓存线程池线程数边界计算。
- 恢复振动辅助函数的可空 Context 容错语义。
- 网络速度格式化固定使用 `Locale.ROOT`。

### 性能

- 缓存状态栏手势路径使用的 DisplayManager 与 displayId，减少高频反射和重复查询。

### 验证

- 单元测试、Lint、Debug 和 Release 构建通过。
- 尚需用户完成长期实机回归和 LSPosed/Vector 日志审计。

## [r14.12.0] - 2026-07-26

### 版本定位

完成核心 Kotlin 迁移、生命周期与热路径治理，并以同一 APK 支持 libxposed API 101
和 API 102。Android 支持范围保持 HyperOS 1 / Android 14。

### 主要变化

- 使用 API 102 编译，`minApiVersion=101`、`targetApiVersion=102`、
  `staticScope=false`。
- 公共 Hook 路径只依赖 API 101 已有接口；未启用 Hot Reload、hook ID 和原子
  replacement。
- 核心 Hook、设置 UI 和工具代码完成保守 Kotlin 迁移，保留 `MainModule.java`、
  libxposed 兼容层及必要 JVM 反射边界。
- 修复应用选择页加载状态、分享/打开方式去重、隐私应用和应用锁重复数据。
- 收紧截图 DexKit 目标匹配，避免 Hook 到签名不符的方法。

### 生命周期与性能

- `AudioVisualizer` 的 Observer、Coroutine、动画和原生 `Visualizer` 随 owner 释放。
- `BatteryIndicator` detach 后注销 Receiver/Observer 和绘制回调。
- 音量模糊、截图栏隐藏和锁屏专辑封面监听在 SystemUI 重建后不重复注册。
- 双排信号、定时振动和 Launcher 图标缩放热路径减少临时对象、格式化和资源读取。
- 反射、DexKit 和资源探测保留在初始化路径；未引入轮询、永久后台任务或大型抽象层。
- 功能关闭时尽量不注册对应 Hook 和长期监听。

### 构建与依赖

- Groovy 构建脚本迁移到 Kotlin DSL，直接依赖集中到 version catalog。
- Gradle Wrapper 9.6.1、Android Gradle Plugin 9.2.1、Kotlin BOM 2.3.21。
- kotlinx.coroutines 1.11.0、libxposed API/service 102.0.0。
- Release 启用 R8、资源压缩、zipalign 和 APK Signature Scheme v2。

### 验证

- 单元测试、Debug、Release、Lint、`lintRelease`、`lintVitalRelease` 通过。
- API 101 依赖回编译、API 102 正式构建、Legacy Xposed API 扫描通过。
- APK 入口、scope、`module.prop`、签名和 zip alignment 已检查。
- API 101 实机完成安装、整机重启和完整 `full.log` 审计，未发现模块相关崩溃、ANR、
  入口、Hook 或 API 链接错误。
- APK 摘要、设备环境、日志扫描项和验证边界见[验证记录](docs/VERIFICATION.md)。

### 已知限制

- 仅支持 HyperOS 1 / Android 14 和 `arm64-v8a`。
- API 102 实机仍需在对应框架环境独立验证。
- Hot Reload 未启用。
- 厂商系统应用更新可能改变 Hook 目标。

## [r14.8.0] - 2026-07-25

### 版本定位

建立核心 mods 大规模 Kotlin 化之前的基础设施稳定点，用于区分后续 Hook 迁移问题与
工具层问题。

### 主要变化

- `Helpers`、`AppHelper`、`ModuleHelper`、`HookerClassHelper`、`ResourceHooks`、
  `ShakeManager` 和 `ResourceConstants` 保守迁移到 Kotlin。
- `AppHelperTest`、`PrefMapTest`、`XposedHelpersCacheTest` 迁移到 Kotlin。
- 保持 Java/Kotlin 静态互操作、反射入口、Hook priority 和异常传播语义。
- 修复 `MainFragment`、`SpinnerEx`、`SortableListView` 和 Intent flags 的 Lint 问题。
- 清理旧 APK、临时构建日志和无用产物。

### 验证

- versionCode 170 / versionName `r14.8.0`。
- 单元测试、编译、Release、R8、Lint 和签名检查通过。
- 完整重启日志中模块加载成功，未发现模块相关崩溃或 ANR。

### 回退价值

保留为核心 Hook Kotlin 化、API 101/102 改造和后续生命周期治理之前的基础设施对照点。

## [r14.7.4] - 2026-07-25

### 版本定位

合并 r14.7.0–r14.7.3 的 Coroutine、设置子页面、UI 控件和小型工具迁移，作为 r14.7.x
唯一公开稳定版。

### 主要变化

- `BitmapCachedLoader`、天气、步数、音频可视化和电池指示器迁移到有生命周期的
  Kotlin Coroutine。
- Activity/App 选择器、搜索子页面和设置 Fragment 使用 lifecycle scope。
- 列表 Adapter 引入 ViewHolder，偏好控件和小型设置页迁移到 Kotlin。
- 动画缩放改用 `Settings.Global` 公共 API，并保留必要回退。
- 清理废弃构建产物、旧 APK 和临时日志。

### 验证

- versionCode 169 / versionName `r14.7.4`。
- Release 构建和 `lintVitalRelease` 通过。
- 完整重启日志中入口加载成功，未发现模块相关崩溃或 ANR。
- APK SHA-256：
  `1B2026B6FFAEE33C3BE50E4695EE8BF19EAA6740124A199153D89C63251F2329`。

### 回退价值

保留为 r14.7.x Coroutine/UI 迁移合并点，可与 r14.8.0 工具基础设施版分层对照。

## [r14.5.0] - 2026-07-24

### 版本定位

建立当前独立包名、签名与 GitHub 发布路径，是后续 Kotlin 和 API 改造的长期回退基线。

### 主要变化

- 源码包迁移到 `tv.withaibuild.customiuizer`。
- namespace 使用 `tv.withaibuild.customiuizer`，applicationId 使用
  `tv.withaibuild.customiuizer.r14`。
- Manifest、XML、Preference、Shortcut、Tasker 组件和 R8 规则同步更新。
- 为数字格式和大小写比较指定稳定 Locale。
- UI 设置重置由同步 `commit()` 改为 `apply()`。
- Handler 显式绑定主线程 Looper，动态 Receiver 补全 Android 14 导出标志。
- 高频 `Resources.getIdentifier()` 收敛到线程安全的资源 ID 缓存。

### 验证

- versionCode 150 / versionName `r14.5.0`。
- `assembleRelease`、`lintVitalRelease` 和签名检查通过。
- 完整重启后未发现模块相关崩溃、ANR 或异常栈。
- APK SHA-256：
  `DCB9EBC4BBE7AEE721B58F83B5371E1030AD7CAB0C4FE6CC4EAD900C420E8C93`。

### 回退价值

当前包名与签名线的最早公开稳定点。更早版本包名或工程结构不同，不适合作为普通用户
回退版本。

## 非公开工程里程碑

### r14.10.0

- 建立 libxposed API 101/102 单 APK 兼容边界。
- 构建脚本迁移到 Kotlin DSL，并用 version catalog 固定直接依赖。
- 完成 API 101 依赖回编译、API 102 Release、R8、资源压缩和 Legacy API 扫描。
- 该版本未作为当前公开回退 Release 保留，其成果已经合并到 r14.12.0。

## 历史阶段

### r14.0–r14.3

- 建立 HyperOS 1 / Android 14 和现代 libxposed API 101 独立维护线。
- 完成早期资源、反射、状态栏绘制和无效 Hook 优化。

### r14.5–r14.6

- 建立当前独立包名、签名和发布路径。
- 推进生命周期、双排信号、资源查找、R8 和测试治理。

### r14.7–r14.8

- 推进 Coroutine、设置 UI、工具类和基础设施 Kotlin 迁移。
- 清理 hidden API、Lint、死代码和废弃资源。

### r14.9–r14.12

- 完成核心 Hook 的保守 Kotlin 化和 Kotlin/JVM 边界复核。
- 建立 API 101/102 单 APK 兼容、Kotlin DSL 与 version catalog。
- 完成生命周期、重复注册、热路径、设置 UI、依赖和工具链审计。

更细的提交历史可由 Git tag 和 commit 追溯，不再为每个内部批次创建公开 Release。
