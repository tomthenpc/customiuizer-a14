# Changelog

简体中文 | [English](CHANGELOG_EN.md)

## r14.18.1 — 2026-08-07

面向 HyperOS 1 / Android 14（SDK 34）、`arm64-v8a` 与 libxposed API 101/102。

### 核心变化

- 构建工具链升级：JDK 25、Gradle 9.6.1、AGP 9.3.1；Gradle Daemon 固定使用 Java 25 作为 JVM criteria。
- Java source/target 保持 17；Android Java 编译输出仍为 17，Gradle 与 compiler toolchain 使用 25。
- 整理 `.idea` 元数据归属：将 Gradle IDE 模型、部署目标、仓库镜像、迁移状态、inspection profile 等本地/生成文件移出 Git tracking，保持共享 code styles、compiler target hint、编码和 VCS 映射。
- 修复 `SystemLockScreenHooks` 中壁纸 `handleIncomingUser` 解析失败时错误回退到 user 0 的 fail-open 行为；失败后直接返回原方法结果，不再继续 CustoMIUIzer 壁纸后处理。

### 验证边界

- 通过 `python tools/verify.py full`、功能语义校验、源码风险扫描、不变量检查和 `git diff --check`。
- 单元测试与 `lintVitalRelease` 通过；Release APK 完成 R8 压缩、资源收缩和正式签名。
- 本版本不标记为全面 `DEVICE_VERIFIED`；状态栏高度无重启序列等待实机验证。

### 已知 Major

- `SystemNotificationHooks` 通知菜单中 `UserHandle.getUserId` 解析失败时回退 user 0，可能错 user 打开应用详情或 force stop。
- `Various.kt` AppInfo 启动应用时 `UserHandle.getUserId` 解析失败时回退 user 0，可能错 user 启动应用。

---

## r14.18.0 — 2026-08-06

面向 HyperOS 1 / Android 14（SDK 34）、`arm64-v8a` 与 libxposed API 101/102。

### 核心变化

- 锁屏充电信息新增字号调节；默认保持系统字号，重启 SystemUI 后生效。
- 加固锁屏充电信息初始化与热路径；关闭详情时跳过无效调用，减少重复安装、无效读取和异常回退开销。
- 修复启用状态栏电池或温度信息时可能导致的 SystemUI 崩溃，并加固旧 Handler、过期 View、ROM 字段兼容和自定义图标创建路径。
- 修复左侧状态栏自定义文字图标在深色背景下不可见，补齐 tint 注册、初始同步、重建和释放生命周期。
- 新增状态栏高度与 WindowInsets、SystemUI 窗口同步，支持运行时应用及禁用后恢复系统高度；fuxi 无重启切换仍待实机验证。
- 加固状态栏和控制中心手势、View、回调及 ClassLoader 生命周期，减少重复触发、状态冲突和过期对象残留。
- 优化进程路由、Feature 安装去重和关闭功能的初始化路径；普通异常保持隔离，致命错误继续传播。
- 构建产物增加 Git revision 与 provenance 记录，功能语义清单、Python 门禁、单元测试和 lint 纳入统一验证。

### 验证状态

- `python tools/verify.py full`、功能语义校验、源码风险扫描、CI 可移植性检查及 Python 全量测试均通过。
- Python 工具测试共 405 项通过，Android JVM 单元测试与 `lintDebug` 通过。
- 状态栏高度的 `44 → 40 → 12 → 44 → disabled` 无重启实机验证尚未执行，本版本不标记为全面 `DEVICE_VERIFIED`。

## r14.16.1 — 2026-08-01

`versionCode 192`，面向 HyperOS 1 / Android 14（SDK 34）、`arm64-v8a` 与 libxposed API 101/102。

### 核心变化

- 将 SystemUI、Launcher、`system_server` 及普通应用入口拆分为按进程路由的 installer，并用稳定 Feature ID、明确安装状态和惰性 Feature 定义保证同一进程只安装一次；关闭功能跳过无关 Feature 注册和业务对象创建。
- 修复 early preference 快照的并发与空快照语义；安装失败不会残留活动定义，偏好变化不会把已安装 Hook 重置为未安装。
- ReflectionCache 改为按 ClassLoader 隔离的有界状态，ResourceHooks 修复真实 Hook 结果与并发安装语义，并削减命中路径的装箱、数组和名称解析。
- Receiver / Observer 注册统一所有者、替换、active/stale 和释放闭环；释放过期天气、计步、专辑封面、电量指示器和百分比覆盖层，避免长期持有 Context、View 或中间 Bitmap。
- 所有共享 Hook、Java/Kotlin 和日志边界均保持普通异常隔离，同时明确继续抛出 `OutOfMemoryError`，避免把内存耗尽伪装为普通兼容失败。
- 优化网速采样与格式化、充电提示、导航图标重载、电量指示更新及透传 Hook 参数路径，减少 SystemUI 高频回调中的临时对象和重复工作。
- 设置开关在点击后立即显示目标状态，再执行原有持久化、禁用态和重启要求逻辑，改善连续点击时的可见反馈，不改变最终偏好语义。
- 模块加载日志加入版本和短 Git SHA；API 102 稳定 Hook ID 能力仍为隔离的 `READY_NOT_WIRED`，未接入生产路径。

### 验证边界

- 发布提交通过 `python tools/verify.py full`：运行期不变量、Debug Kotlin/Java 编译、单元测试和 `lintDebug`。
- 正式 Release APK 使用 A14 专用证书，产物的版本、SHA-256、签名、zipalign、`debuggable=false` 与 Xposed 元数据在 GitHub Release 中记录。
- 既有 Xiaomi 13 / HyperOS 1 基线未发现模块导致的 P0/P1、重复安装或持续安装异常；本版本新增的运行期与界面变化尚未完成全部功能逐项实机行为验证，不标记为全面 `DEVICE_VERIFIED`。

### 历代核心实现总结

r14 系列建立了独立包名、签名和 HyperOS 1 / Android 14 维护线，完成设置与核心 Hook 的分批 Kotlin 迁移、libxposed API 101/102 单 APK 兼容、`system` 作用域恢复、偏好同步与快速重启修复、Receiver/Observer/View 生命周期治理、反射与资源缓存加固、状态栏和 Launcher 热路径优化，以及网速、锁屏、控制中心和设置界面的持续修复；细节保留在 Git commits 与历史 tags 中，旧 APK 不再保留为 Release 资产。
