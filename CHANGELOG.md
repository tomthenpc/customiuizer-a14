# Changelog

简体中文 | [English](CHANGELOG_EN.md)

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
