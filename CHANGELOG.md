# Changelog

本文件记录用户可见变化、兼容边界和重要工程基线。中间迁移批次、Agent 工作记录、
临时 APK 数据和低样本性能数字不作为 Release 历史。

## [r14.12.0] - 2026-07-26

### Compatibility

- 维持 HyperOS 1 / Android 14、SDK 34 和 `arm64-v8a` 支持范围。
- 同一 APK 以 libxposed API 101 为最低运行基线、API 102 为编译和目标版本。
- 公共 Hook 路径只依赖 API 101 已存在的接口；API 102 专属 Hot Reload、hook ID 和
  replacement 未启用。
- 清除 Legacy `de.robv.android.xposed` 运行 API；设置入口 category 不属于 Legacy
  API 调用。

### Fixed

- 修复应用选择页加载状态回归，避免恢复后持续显示加载态。
- 分享和“打开方式”包列表改为常数时间去重，保持原有顺序和过滤语义。
- 修复隐私应用和应用锁列表初始数据被重复加入的问题。
- 收紧截图 DexKit 目标匹配，避免把相似但签名不符的方法作为 Hook 目标。

### Lifecycle

- `AudioVisualizer` 的 Observer、Coroutine、动画和原生 `Visualizer` 资源现在随 owner
  结束而取消或释放。
- `BatteryIndicator` 在 View detach 后解除 Receiver/Observer、回调和绘制资源。
- 音量面板模糊 Observer 只注册一次，避免 SystemUI 重建后的重复通知。
- 截图期间状态栏和导航栏 Receiver 绑定到 View attach/detach 生命周期。
- 锁屏专辑封面 Receiver 使用单一注册和弱目标，避免重建后的重复回调和静态 View 持有。
- SystemUI 长期对象的重复 Hook、重复初始化和功能关闭后的残留任务经过集中审计。

### Performance

- 双排移动信号绘制减少临时对象和重复状态计算。
- 定时振动区间判断移除热路径格式化和不必要数学运算。
- Launcher 图标缩放缓存稳定输入，减少重复资源读取。
- 反射、DexKit 和资源查找保留在初始化或冷路径；未引入轮询、永久后台任务或大型抽象层。

### Kotlin and Architecture

- 核心 Hook、设置 UI 和工具代码完成保守 Kotlin 迁移。
- 保留 `MainModule.java`、现代 libxposed 兼容层和局部 JVM 反射实现等必要 Java 边界。
- 修正无收益的 `inline`，检查 nullability、JVM 签名、初始化顺序及 R8 可达性。
- 未为形式统一引入 Flow、Sequence、DSL、新架构层或无所有者协程。

### Build and Dependencies

- Groovy 构建脚本迁移为 Kotlin DSL，直接依赖集中到 version catalog。
- Gradle Wrapper 更新到 9.6.1，并固定发行 ZIP 的 SHA-256。
- Android Gradle Plugin 保持 9.2.1；Kotlin BOM 更新到 2.3.21。
- kotlinx.coroutines 更新到 1.11.0；libxposed API/service 使用 102.0.0。
- Release 继续启用 R8、resource shrink、zipalign 和 APK Signature Scheme v2。

### Verification

- 单元测试、Debug、Release、Lint、`lintRelease`、R8、资源压缩和签名检查通过。
- 使用 API 101 依赖回编译通过，恢复 API 102 依赖后的完整构建通过。
- APK 的 Xposed 入口、scope、`module.prop`、签名和 zip alignment 已检查。
- 最终代码基线已完成手机安装、整机重启和基础 LSPosed/Vector 日志检查，未发现阻止发布的
  模块崩溃、ANR 或入口加载错误。
- Release 资产仍以用户实际安装确认的精确 APK SHA-256 为准；不同构建不得替换已验证资产。

### Known Limits

- 仅支持 HyperOS 1 / Android 14 和 `arm64-v8a`。
- API 101 管理器可能因 `targetApiVersion=102` 显示较新版本提示；该提示不等同于加载失败。
- Hot Reload 未启用，也未验证热卸载或 Hook 原子替换。
- 厂商系统应用更新可能改变 Hook 目标。
- 完整长期 LSPosed/Vector 日志将在发布后单独审计；确认的问题进入 `r14.12.x`。

## [r14.10.0] - 2026-07-26

### Summary

- 建立 libxposed API 101 / API 102 单 APK 双兼容基线。
- 构建脚本迁移到 Kotlin DSL，并用 version catalog 固定直接依赖。
- Android 运行范围保持 SDK 34，没有把 libxposed API 版本与 Android SDK 混淆。

### Compatibility

- `minApiVersion=101`、`targetApiVersion=102`、`staticScope=false`。
- 使用 API/service 102 编译，公共运行路径不引用 API 102 专属类型。
- 保持既有 Hook priority、before/after、参数修改、异常传播和 unhook 语义。

### Verification

- API 101 依赖回编译、API 102 Release、R8、资源压缩和 Legacy API 扫描通过。
- 该版本是 Git 兼容里程碑；若未形成同签名、可下载的正式 Release，不作为公开回退空壳保留。

## [r14.8.0] - 2026-07-25

### Summary

- 建立核心现代化前的 Kotlin 基础设施稳定基线。
- 完成设置 UI、工具类、部分 Hook 和 Coroutine 的保守迁移。
- 替换不必要的 hidden API 使用，并继续清理 lint、死代码和废弃资源。

### Verification

- 通过当时的 Debug/Release、R8、Lint 和签名检查。
- 保留为排除后续核心 Hook 与 API 兼容改动影响的回退点。

## [r14.7.4] - 2026-07-25

### Summary

- 合并 r14.7.x 的 Kotlin/Coroutine 迁移与设置界面整理。
- 保留迁移前后可比较的独立包名和发布路径。

### Why It Is Retained

- 仅在归档审计确认 APK 可下载、签名可覆盖、实机记录充分且相对 r14.8.0 有独立回退价值时，
  才作为第四个公开 Release 保留。
- 若上述条件不成立，则以 `r14.5.0` 作为长期回退候选；不会创建没有可验证资产的版本。

## Historical Consolidation

### r14.0–r14.3

- 建立 HyperOS 1 / Android 14 独立维护版本线。
- 完成早期资源和反射缓存、功能关闭时的无效 Hook 减少，以及状态栏和绘制热路径整理。

### r14.5–r14.6

- 迁移到当前独立包名和签名发布路径。
- 推进生命周期、资源查找、代码拆分、R8 与测试基础，并修复关键兼容问题。

### r14.7–r14.8

- 推进 Kotlin/Coroutine、设置 UI 和工具类迁移。
- 替换部分 hidden API，持续处理 Lint、死代码和构建可重复性。

### r14.9–r14.12

- 完成核心 Hook 的保守 Kotlin 化和 Kotlin/JVM 边界复核。
- 建立 API 101/102 单 APK 兼容以及 Kotlin DSL/version catalog 构建。
- 完成生命周期、重复注册、热路径、逻辑、设置 UI、依赖和工具链的系统审计。
- 通过完整构建门禁和最终代码基线的手机重启、基础日志检查。

更细的中间变化仍可从 Git commit 历史追溯，但不再为每个开发批次保留独立 Release
章节。
