# 验证记录

本文档记录当前有效静态验证、最近可信实机验证和仍需验证的范围。它不保存历史版本产物细节；这些应查阅 Git 历史、CHANGELOG 或对应 tag 的 GitHub Release。

## 当前静态基线

当前分支：`devin/a14-runtime-hardening`

```bash
python tools/verify.py full
python tools/check-invariants.py
python -m unittest discover -s tools/tests -p "test_*.py"
```

静态入口：

- `check-invariants.py`：
  - 每条规则都有对应的 `tools/tests/*.py` 覆盖。
  - 退出码非 0 时禁止提交。
  - 不得通过放宽规则或向 `ALLOWED` 添加 `mods/` 文件来通过。
- `verify.py`：
  - 编排 `check-invariants`、Gradle `compile` / `test` / `lintDebug`。
  - `full` 用于完整验证；`fast` / `--changed` 用于本地增量验证。

## 本轮 A14-6F 静态验证结果

- `check-invariants.py`：157 个文件，0 个违规。
- `compileDebugKotlin`、`compileDebugJavaWithJavac`、`testDebugUnitTest`、`lintDebug` 均通过。
- `python -m unittest discover -s tools/tests -p "test_*.py"`：Python 工具测试通过。
- 未执行 `assemble*`、`package`、`bundle`、`install`、`sign`、`publish`、`officialRelease`。

## 本轮 A14-6G 静态验证结果

| 项目 | 状态 |
|---|---|
| Feature lazy construction | VERIFIED_STATIC |
| Install OOM cleanup | VERIFIED_STATIC |
| Registry does not retain FeatureDefinition objects | VERIFIED_STATIC |
| ReflectionCache fatal boundary | VERIFIED_STATIC |
| API102 stable hook ID | READY_NOT_WIRED |
| Device validation | DEFERRED_EXTERNAL |

- `FeatureInstallRegistry` 不保留已创建的 `FeatureDefinition`；如果 `spec.create()` 或 `install()` 抛出 `OutOfMemoryError`，状态会置为 `FAILED_TRANSIENT` 并重新抛出 OOM。
- 运行时偏好变化由进程本地 `PrefMap` 和专用 Controller/Observer 处理，不再维护不可达的第二套 Feature 偏好分发状态机。
- `ReflectionCache.resolveDependencyMethod()` 中 `depClass.getDeclaredMethod(...)` 的 OOM 被单独捕获并重新抛出，不写 `dependencyMethodResolved`、不写 negative cache。
- 文档使用新措辞说明：关闭功能时不创建 `FeatureDefinition`、业务 installer 对象或 Hook 对象，仅保留固定 `LazyFeatureSpec` 元数据和轻量 lambda。
- `check-invariants.py` 新增对应静态规则，未引入第三方 AST 库。

## 最近一次可信实机验证

版本：`r14.13.8` / versionCode `186`

- 提交：`dcbbebc8bbb84710b998ee588171fb9d809d963d`
- 环境：Android 14 / HyperOS 1、LSPosed 2.1.1（7790）
- 日志：84,411 行，P0 = 0、P1 = 0
- 模块在 SystemUI 与 Launcher 正常加载；`system_server` 完成启动广播，未发现模块加载失败。
- 两次快速重启后系统均完成启动，未发现 SystemUI、Launcher 或 `system_server` 崩溃、Hook 异常、Receiver 重复注册或快速重启相关异常。

注意：该验证对应 `r14.13.8` 源码与构建产物。当前 `devin/a14-runtime-hardening` 的改动已通过静态门，但仍需一次新的实机验证。

## 当前待实机验证项

- 新 `FeatureInstallResult` enum 与 `FeatureInstallState` 在 `system_server` / SystemUI / Launcher 的加载顺序和异常路径。
- `FeatureSpec` 到 `FeatureDefinition` 的延迟创建在不同进程的加载行为。
- `XposedApiCapabilities` 在 API 101 与 API 102 宿主上的初始化结果。
- `Api102HookBridge` 的 `setId` 隔离（目前未接线到生产路径，因此无需设备验证 setId）。
- `WeakOwnerReceiver` 在真实 GC 与框架广播竞争下的清理行为。
- `ReflectionCache.onSafeLifecycle` 在不同 ROM 上的触发时机。
- 热路径 `ResourceHooks` 在主题切换、SystemUI 与 Launcher 重建时的延迟与内存。
- 快速重启 Receiver 和 10 秒重启守卫。

## A14-7C 构建身份前置

- 模块加载标记包含 `BuildConfig.BUILD_REVISION`，格式为
  `CustoMIUIzer <versionName> (<versionCode>) [<short SHA>] loaded in <process>`。
- `BUILD_REVISION` 在 Gradle 配置期从当前 `HEAD` 解析为 8 位短 SHA；Git 元数据不可用时明确写入
  `unknown`，不得仅凭版本号推断精确源码。
- 该项只证明新构建具备可审计身份。当前 A14-7A APK 仍是旧日志格式；在新 APK 完成实机行为验证前，
  `deviceVerified` 继续为 `false`，A14-7C 功能状态继续为 `NOT_EXERCISED` 或 `BEHAVIOR_PENDING`。

## 发布资产边界

- 不提交 APK、keystore、密码、构建缓存或本地日志。
- 发布 SHA-256 和签名证书以对应 tag 的 GitHub Release 为准。
- 当前 `module.prop`：
  - `minApiVersion=101`
  - `targetApiVersion=102`
  - `staticScope=false`
