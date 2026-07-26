# libxposed API 101 / API 102 双兼容说明

本文是项目唯一的 libxposed API 101/102 兼容性权威文档。版本状态以仓库中的
`module.prop`、version catalog 和构建脚本为准。

## 兼容目标

r14.10.0 建立单 APK 双兼容边界，r14.12.0 在该边界上完成生命周期和构建治理：

```properties
minApiVersion=101
targetApiVersion=102
staticScope=false
```

Android 平台范围保持 `minSdk=34`、`targetSdk=34`。Android SDK 与 libxposed API 是
两个独立维度。

## 实现边界

- 使用 `io.github.libxposed:api:102.0.0` 编译，最低运行基线仍为 API 101。
- UI 侧 service 使用 `io.github.libxposed:service:102.0.0`。
- 公共 Hook 路径只调用 API 101 已存在的 `XposedModule` 生命周期、`HookBuilder`、
  `Hooker.intercept`、`Chain.proceed` 和 `HookHandle.unhook`。
- API 102 新增的 Hot Reload、hook ID 与原子 replacement 未启用，公共加载路径不引用
  其新增参数类型或方法。
- 不使用反射访问 Xposed API，也不使用 Legacy `de.robv.android.xposed` 运行 API。
- Manifest 的 `de.robv.android.xposed.category.MODULE_SETTINGS` 是模块设置入口
  category，不是 Legacy Xposed API 调用。

API 101 与 API 102 AAR 的公开符号对比表明，项目当前使用的接口签名保持不变。
API 102 新增 `HotReloadingParam`、`HotReloadedParam`、`onHotReloading`、
`onHotReloaded`、`HookBuilder.setId`、`HookHandle.getId` 和
`HookHandle.replaceHook`；这些符号均未进入本项目运行路径。

## 加载和生命周期约束

- API 版本检查只允许位于入口、初始化或其他冷路径，不进入高频 Hook callback。
- API 102 专属类型不得出现在 API 101 必经类的方法签名、字段或静态初始化中。
- `MainModule.java` 保留为稳定入口；`HookerClassHelper` 与 `XposedHelpers` 保留现有
  Hook 参数、异常、priority 和 unhook 语义。
- Receiver、Observer、Listener、CoroutineScope、Handler 和动画必须有明确 owner，
  并在 detach、重建或功能关闭时解除。
- SystemUI、Launcher 和 `system_server` 初始化必须防重复，不能依赖吞异常掩盖重复注册。
- Hot Reload 当前关闭，不实现热卸载、自动 detach 或 Hook 原子替换。

## 构建工具链

- Gradle Wrapper：`9.6.1`
- Android Gradle Plugin：`9.2.1`
- Android 编译平台 / Build Tools：`37` / `37.0.0`
- Java / Kotlin JVM target：`17`
- Android 运行范围：`minSdk=34`、`targetSdk=34`
- Kotlin BOM：`2.3.21`
- kotlinx.coroutines：`1.11.0`
- 构建脚本：Kotlin DSL
- 直接依赖：`gradle/libs.versions.toml`

`service:102.0.0` 的 AAR 元数据要求 `compileSdk >= 37`，因此编译工具链使用 API 37，
但不扩大模块支持的 Android 运行版本。

## 静态与构建验证

- 使用 API 102 依赖完成 `clean`、单元测试、完整 Lint、Debug 和 Release 构建。
- Release 路径包含 R8、资源压缩、zipalign、v2 签名和 `lintVitalRelease`。
- 临时切回 `api:101.0.1` / `service:101.0.0` 后，同一份源码通过
  `clean test assembleRelease`；随后恢复 API 102 配置并重新完整构建。
- Release DEX 未发现 Legacy `de.robv.android.xposed` API 描述符。
- Release DEX 未发现 API 102 专属 Hot Reload、hook ID 或 replacement 符号。
- APK 内 `module.prop`、Xposed 入口、scope、签名和 zip alignment 已独立检查。
- 最终代码基线已完成手机安装、整机重启和基础 LSPosed/Vector 日志检查，未发现阻止发布的
  模块崩溃、ANR 或入口加载错误。

构建、静态检查和单一设备验证不能替代 API 101 与 API 102 两套框架的完整实机矩阵。
发布资产必须是用户实际安装确认的精确 APK，不得用重新构建的不同 SHA-256 替换。

## API 101 管理器提示

模块声明 `targetApiVersion=102`。API 101 框架或管理器可能提示模块“为较新的 Xposed
版本设计”。这只是目标 API 与当前框架 API 的比较结果，不代表模块入口加载失败。

判断顺序应为：

1. 确认框架实际实现的 libxposed API 版本；
2. 检查模块入口和目标进程日志；
3. 检查 `system_server`、SystemUI、Launcher 与 Remote Preferences；
4. 再判断具体 Hook 是否因 ROM 或系统应用版本失配。

## 实机验收清单

### API 101 框架

- 冷启动后模块成功加载，无 `NoSuchMethodError`、`NoClassDefFoundError`、
  `AbstractMethodError`、`IncompatibleClassChangeError` 或 `VerifyError`。
- Remote Preferences 读写正常。
- `system_server`、`com.android.systemui`、`com.miui.home` Hook 正常。
- 设置修改生效；重启目标进程和整机后仍正常。
- 日志中没有模块导致的崩溃、ANR、重复 Hook 或重复初始化。

### API 102 框架

- 冷启动后模块成功加载，API 101 功能行为一致。
- Remote Preferences 读写正常。
- `system_server`、`com.android.systemui`、`com.miui.home` 无模块相关崩溃。
- 没有 Legacy Xposed API 拒绝、重复 Hook、重复初始化或生命周期异常。
- Hot Reload 保持关闭；不测试热重载。

### 两套框架共同覆盖

- Audio Visualizer 开关、播放切换和 View 重建后没有重复 Visualizer、Observer、协程或动画。
- Battery Indicator 偏好变化、主题或 SystemUI 重建后只有一个实例和一组 Receiver/Observer。
- 截图期间状态栏/导航栏隐藏、格式保存和截图结束后的状态恢复正常。
- 锁屏专辑封面和音量模糊观察器在 SystemUI 重建后没有重复回调。
- 普通、分享、“打开方式”、隐私应用和应用锁选择页正常加载且无重复条目。
- 双排移动信号着色、定时振动边界和 Launcher 图标缩放行为正常。
- 完整重启后分别保存模块、`system_server`、SystemUI 和 Launcher 日志，并区分
  ROM/框架异常与模块堆栈。

## 官方资料

- [libxposed API Javadoc](https://libxposed.github.io/api/)
- [libxposed API 官方仓库](https://github.com/libxposed/api)
- [libxposed service 官方仓库](https://github.com/libxposed/service)
- [libxposed 官方 example](https://github.com/libxposed/example)
