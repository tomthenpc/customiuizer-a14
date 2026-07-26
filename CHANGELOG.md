# Changelog

本文件记录公开版本的用户可见变化、兼容边界、验证结论和回退价值。内部迁移批次、
Agent 工作记录、临时 APK 和未经同条件测量的性能数字不作为 Release changelog。

## 当前公开版本

| 版本 | 日期 | 定位 |
| --- | --- | --- |
| `r14.12.0` | 2026-07-26 | 当前稳定版；API 101/102、Kotlin、生命周期与构建治理 |
| `r14.8.0` | 2026-07-25 | Kotlin 基础设施回退点 |
| `r14.7.4` | 2026-07-25 | r14.7.x Kotlin/Coroutine 迁移合并版 |
| `r14.5.0` | 2026-07-24 | 独立包名、签名和发布路径基线 |

Release 标题统一为纯版本号。已移除版本的资产名、大小与 SHA-256 见
[历史 Release 归档](docs/RELEASE_ARCHIVE.md)；对应源码仍可通过 Git tag 获取。

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
