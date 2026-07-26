# libxposed API 102 能力审计与采用计划

## 结论

本阶段继续采用“API 102 编译、API 101 最低运行基线”的单 APK 方案，但**不启用任何 API 102 专属运行时能力**。

这不是遗漏实现。逐项核对官方 API 101/102 源码、102 官方 example、service 101/102 源码和实际 Maven AAR JVM 签名后，当前项目没有能以足够低风险从这些能力获益的场景。强行接入反而会扩大 API 101 类验证、Hook 生命周期和模块 ClassLoader 风险。

最终边界保持：

```properties
minApiVersion=101
targetApiVersion=102
staticScope=false
```

不声明 `autoHotReload`，不修改 Android `minSdk` / `targetSdk`，不修改应用版本或签名配置。

## 官方资料与二进制核对

核对时间：2026-07-26。

官方来源：

- [libxposed API 102.0.0](https://github.com/libxposed/api/tree/102.0.0)
- [libxposed API 101.0.1](https://github.com/libxposed/api/tree/101.0.1)
- [API 102 `XposedInterface`](https://github.com/libxposed/api/blob/102.0.0/api/src/main/java/io/github/libxposed/api/XposedInterface.java)
- [API 102 `XposedInterfaceWrapper`](https://github.com/libxposed/api/blob/102.0.0/api/src/main/java/io/github/libxposed/api/XposedInterfaceWrapper.java)
- [API 102 `XposedModuleInterface`](https://github.com/libxposed/api/blob/102.0.0/api/src/main/java/io/github/libxposed/api/XposedModuleInterface.java)
- [API 101 `XposedInterface`](https://github.com/libxposed/api/blob/101.0.1/api/src/main/java/io/github/libxposed/api/XposedInterface.java)
- [API 101 `XposedModuleInterface`](https://github.com/libxposed/api/blob/101.0.1/api/src/main/java/io/github/libxposed/api/XposedModuleInterface.java)
- [官方 example](https://github.com/libxposed/example)
- [libxposed service 102.0.0](https://github.com/libxposed/service/tree/102.0.0)
- [service 102 `XposedService`](https://github.com/libxposed/service/blob/102.0.0/service/src/main/java/io/github/libxposed/service/XposedService.java)
- [service 101 `XposedService`](https://github.com/libxposed/service/blob/101.0.0/service/src/main/java/io/github/libxposed/service/XposedService.java)

除阅读源码外，还直接对本机 Gradle 缓存中的官方 `api-101.0.1.aar` 与 `api-102.0.0.aar` 执行 `javap`。公开 JVM 差异如下：

| 能力 | API 101 | API 102 |
|---|---|---|
| `XposedModule.detach()`（继承自 wrapper） | 不存在 | 新增 |
| `HookBuilder.setId(String)` | 不存在 | 新增 |
| `HookHandle.getId()` | 不存在 | 新增 |
| `HookHandle.replaceHook(Hooker)` | 不存在 | 新增 |
| `onHotReloading(HotReloadingParam)` | 不存在 | 新增 |
| `onHotReloaded(HotReloadedParam)` | 不存在 | 新增 |

API 102 还将 `attachFramework(XposedInterface)` 改为框架内部使用的 `attachFramework(XposedInterface, Runnable)`。项目没有、也不得直接调用这个内部附着接口。

API 102 的行为限制同时明确：以 102 为 target 的现代模块不能调用 Legacy `de.robv.android.xposed` API。当前活动源码没有这种导入或调用；Manifest 中的 `de.robv.android.xposed.category.MODULE_SETTINGS` 只是模块设置入口 category，不是 Legacy API。

## 能力决策

### `XposedModule.detach()`：本阶段不采用

`detach()` 只停止当前 entry 后续接收生命周期回调，不会自动撤销 Hook、注销监听器、停止线程或清理 JNI/静态引用。

本项目使用单一 `MainModule` 处理 system_server、SystemUI、Launcher 及多个系统应用，并依赖同一进程中可能继续出现的 package 回调。入口没有“完成一次初始化后永远不再需要后续回调”的安全点。在早期 package 回调中 detach 可能直接漏掉后续目标；在晚期调用则没有可证明收益。

若仅为非目标进程提前退出，已有 `scope.list`、`isFirstPackage()` 和包/进程判断负责该边界，不需要新增 102 专属调用。

### Hook ID 与 `replaceHook`：本阶段不采用

`setId` / `getId` / `replaceHook` 的核心用途是同一 executable 上的原子 Hook 替换，尤其服务于 Hot Reload。当前冷启动模型只安装一次已启用功能的 Hook，不存在需要在线替换 Hooker 实例的产品行为。

在 API 101 公共 Hook 路径直接调用 `setId` 会产生 API 101 中不存在的方法引用。即使先判断 `getApiVersion()`，仍会让 102 专属调用进入通用兼容层；要完全隔离则需额外的 102-only 类加载边界，但在没有替换需求时只会增加验证和 R8 风险。因此保留现有 API 101 `HookHandle.unhook()`、优先级、before/after 和异常传播语义。

### `onHotReloading` / `onHotReloaded`：本阶段不采用

官方接口要求旧 generation 在允许 reload 前停止模块拥有的 Java/native 线程、注销 native hook 和外部回调、释放 JNI global reference，并清除系统或应用对象持有的模块对象；新 generation 还必须自行替换/移除旧 Hook，框架不会重放普通 package 生命周期。

本项目当前包含：

- 静态偏好和 Hook 状态；
- Remote Preferences listener；
- SystemUI/Launcher/system_server 多进程 Hook；
- Receiver、Observer、Handler、Coroutine 和缓存；
- DexKit JNI 库及动态桥接。

这些资源没有统一的 generation 所有权和可验证的原子卸载协议。现在返回 `true` 会有旧 ClassLoader 残留、重复 Hook、回调进入旧代码或 native 生命周期错误的风险。

此外，把 `HotReloadingParam` / `HotReloadedParam` 写进 API 101 必经入口的覆盖方法签名，会让 API 101 环境需要解析其不存在的类型。为保护单 APK 的 API 101 冷启动路径，本阶段连“始终返回 false”的覆盖方法也不添加。

官方 example 只演示最小型状态传递和旧 Hook handle 清理，并显式声明 `autoHotReload=true`；它不具备本项目的多进程、JNI、长期监听器和静态状态复杂度，不能直接移植。

### service 102 运行目标与热重载：本阶段不采用

service 102 新增 `getRunningTargets()` 和 `hotReloadModule(...)`，且官方说明热重载用于模块 APK 更新后的新 generation，不应用于传播配置变更。当前设置同步已经由 API 101 具备的 Remote Preferences 与 change listener 完成，不需要引入 service 102 的异步 Binder 回调和 UI 状态。

## API 101 回退编译证据

在不修改任何源码的情况下，临时将版本目录切换为：

```toml
libxposed-api = "101.0.1"
libxposed-service = "101.0.0"
```

执行：

```powershell
.\gradlew.bat --no-daemon clean test assembleRelease
```

结果：`BUILD SUCCESSFUL`。随后立即恢复：

```toml
libxposed-api = "102.0.0"
libxposed-service = "102.0.0"
```

并执行：

```powershell
.\gradlew.bat --no-daemon test assembleDebug
```

结果：`BUILD SUCCESSFUL`。版本目录恢复后与提交内容无差异。

这证明当前活动源码可以只靠 API 101 公开表面编译，同时最终产物继续以 API/service 102 构建。它不能代替 API 101 和 API 102 框架实机冷启动验证。

## 后续启用门槛

只有出现明确产品需求，并完成以下独立阶段后，才重新考虑 Hot Reload 或原子 Hook 替换：

1. 为每个 Hook、Receiver、Observer、Handler、Coroutine、缓存和 JNI 资源建立 generation 所有权；
2. 提供可重复、幂等、可超时的停止与注销协议；
3. 将所有 102 专属类型完全隔离在 API 101 不会加载或验证的类中；
4. 对 system_server、SystemUI、Launcher 分别验证旧 generation 无残留；
5. 对 API 101 冷启动、API 102 冷启动和 API 102 reload 建立独立实机矩阵；
6. 经 R8 后再次检查入口签名、102-only 描述符和 Hook 回调识别。

在此之前，冷启动双兼容的稳定收益高于采用 API 102 新能力。
