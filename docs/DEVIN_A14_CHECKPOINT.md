# DEVIN A14 CHECKPOINT

> 此文件记录当前开发线的已验证状态和下一步，不替代源码、Git、构建产物或实机证据。
> 每次继续工作前，必须重新核对本地分支、远端分支、工作区和 Release。

## 当前状态

- 仓库：`tomthenpc/customiuizer-a14`
- 正式发布版本：`r14.13.5` / versionCode `183`
- 正式发布日期：2026-07-28
- 正式 tag：`r14.13.5` → `4225d80e95ed9965ab68a09b575aff4046666a5d`
- 当前正式源码基线：`main` / `r14.13.5`
- 源码仓库 Release：[r14.13.5](https://github.com/tomthenpc/customiuizer-a14/releases/tag/r14.13.5)
- LSPosed 模块仓库 tag：`183-r14.13.5`
- LSPosed 模块仓库 Release：[183-r14.13.5](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases/tag/183-r14.13.5)
- `r14.13.4` 已撤回；其 GitHub Release 与 tag 已删除，历史资产信息见
  [RELEASE_ARCHIVE.md](RELEASE_ARCHIVE.md)。
- 当前 `main` 承载 `r14.13.5` 正式发布提交。

## 当前活跃开发分支

- 分支：`devin/r14.13-kotlin-refactor`
- 分支基点：`064ba854`（与 `main` 相同），当前 ahead 4 / behind 0
- 正在进行：**运行期健壮性与热路径加固**（尚未发版，版本号仍为 `r14.13.5` / `183`）

该分支此前完成的 `A14 设置状态稳定化：语言切换确认、退出后生效与重启策略统一`
已经合并进 `main` / `r14.13.5` 正式基线。之后该分支重新启用，承载下述加固工作。

### 本轮加固（2026-07-28）

来源：对全部 109 个 Kotlin 文件做的证据驱动审计，不是文档推导。
每一项都是编译通过、lint 通过、单元测试通过，但会在设备上出问题的代码。

1. **异常逃逸 → 系统进程崩溃**
   模块在 hook 里注册出去的回调（`handleMessage` / `onReceive` / `onChange` / `run` /
   `post{}` / `runOnUiThread{}` / 监听器 lambda）不在 `MethodHook` 的 try/catch 里。
   新增 `ModuleHelper.guarded`（`inline`，零分配）并包住全部 30 处反射回调。
   `handlePreferenceChanged` 改为逐个观察者隔离 —— 此前一个观察者抛异常，
   既杀进程也让后续观察者静默收不到变更。
   `DeviceInfoMonitor` 的 sysfs 解析改为全函数（不再 `NumberFormatException`），
   并把 `scheduleNextTick()` 移进 `finally`，ticker 不会再永久停摆。

2. **注册泄漏 → 内存与耗电**
   原来的清理逻辑把上一个 receiver 存在被 hook 实例的字段上，而 `hookAllConstructors`
   每次都是新实例，**这段清理从未生效**。`MiuiPhoneStatusBarPolicy` 的泄漏 receiver 监听
   `TIME_TICK`，即每分钟 N 次无用唤醒。
   新增 `registerModuleReceiver` / `registerOwnedReceiver` / `replaceModuleRegistration`；
   偏好观察者改为弱引用持有，强引用挂在 owner 上随 owner 消亡。

3. **热路径参数编排**
   `getArgsArray` 每次分配两次，`proceed(args)` 让框架重新 marshal 全部参数。
   117 个只读参数的 `intercept` 改用 `Chain.getArg(i)` / `Chain.getArgs()` + `Chain.proceed()`；
   新增 `BeforeHookCallback.getArg(i)` 让 17 个 `before()` 走零拷贝分支。
   真正改写参数的 41 处保持原样。`Chain.getArg` 在 API 101 已存在，运行基线不变。

4. **静态门禁**
   `tools/check-invariants.py`：6 条规则，每条对应上面一个真实缺陷。
   全量扫描 96 个文件，0 违规。规则说明见 `docs/RUNTIME_INVARIANTS.md`。
   `AGENTS.md` 重写为可执行、可校验的形式。

### 第二轮加固（2026-07-28，同分支）

5. **`system_server` 里的未保护 lambda（最高危）**
   第一轮的门禁规则只匹配 `override fun run()` 这类具名回调，漏掉了
   `postDelayed(Runnable { ... })` 的 lambda 形状。`Controls.kt` 电源键与音量键长按的两个
   Runnable 跑在 `MiuiPhoneWindowManager` 的 handler 上，也就是 **`system_server` 进程内**，
   里面有 `newWakeLock`、`sendBroadcast`、反射按键注入和 `getStringAsInt` 的 `toInt()`。
   在这里抛异常不是应用崩溃，是设备重启。共加固 `mods/` 下 23 处 deferred 回调。

6. **协程 scope 未处理失败**
   `SupervisorJob()` 只防连坐，不吞异常；`launch` 里未捕获的异常仍会走到线程默认处理器，
   在 SystemUI / Launcher 中即进程死亡。三个 scope 全部加上
   `ModuleHelper.coroutineFailureHandler`（挂 scope 而非包 `launch`）。

7. **反射缓存命中仍在分配**
   `findField` 每次调用都 `new MemberCacheKey.Field(...)` 才去查表——命中也要分配。
   模块 hook 体内共 616 处字段访问，跑在绘制与滚动频率上。无参 `callMethod` 更差：
   `findMethodExact` 失败后 `findMethodBestMatch` 再建一个 key，继承方法上每次调用两次分配。
   两者改为 `Class -> name -> member` 两级嵌套，命中零分配；带参数类型的查找保留结构化 key。
   `ReflectionCacheAllocationTest` 用 HotSpot 线程分配计数器断言 <1 byte/call，并有对照测试
   防止计数器失效导致断言空过。

8. **门禁扩到 8 条规则**
   新增 `guard-deferred-callbacks`、`coroutine-scopes-handle-failure`，并做了负向验证
   （移掉一处 guard 会触发）。同时修正了两条第一轮误报（注释里的 `Handler()`、
   `"\s+".toRegex()` 这类真正的多字符模式）。

### 本轮验证

- `python tools/check-invariants.py` → 96 files, 8 条规则, no violations
- `./gradlew clean test lintDebug lintRelease lintVitalRelease assembleDebug assembleRelease` → 退出码 0
- 单元测试 106 个, 0 失败; lint / lintRelease 均 0 errors
- 全部 Kotlin 转换都是编译期可验证的（被写入 / 被 `*args` 展开 / 被当 `Array<Any?>` 传出的
  参数数组，改成 List 或单值访问后一定编译失败）

### 待实机验证

- SystemUI 多次主题 / 密度 / 折叠态切换后，receiver 与偏好观察者数量不再增长
- 状态栏电池温度与电流读数在缺少 `POWER_SUPPLY_TEMP` 的机型上降级而不是崩溃
- 锁屏手电筒长按、PIP 截图隐藏、侧边栏、freeform 相关广播动作行为不变
- 时钟秒针在 `TIME_SET` 后仍然重新初始化（多时钟控制器共存场景）

## 发布产物与签名

| 项目 | 值 |
| --- | --- |
| APK | `CustoMIUIzer-A14-r14.13.5.apk` |
| 大小 | 3,032,173 bytes |
| APK SHA-256 | `89AE5046564F69D491DC44F7B853443113FEC7100FE997ABA9984181C4983EA5` |
| 签名证书 SHA-256 | `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70` |
| 签名 | APK Signature Scheme v2，1 个签名者 |
| applicationId | `tv.withaibuild.customiuizer.r14` |
| ABI | `arm64-v8a` |
| SDK | min/target `34 / 34` |
| libxposed | min API `101` / target API `102` / `staticScope=false` |

`r14.12.0` 及更早公开版本的旧签名私钥已经遗失。它们不能直接覆盖安装
`r14.13.5`；升级时必须备份设置、卸载旧版、安装 `r14.13.5`、重新启用作用域、恢复设置并完整重启。

## 已验证

- 正式构建：JDK 17、Gradle 9.6.1、AGP 9.2.1、Kotlin 2.3.21。
- `clean test lintDebug lintRelease lintVitalRelease assembleDebug assembleDevelop assembleRelease` 退出码 0。
- 新增 `AppLocaleNormalizationTest`、`AppLocaleEntryTest`、`AppLocaleReconcileTest`、`RestartRequirementTest`；原有测试继续通过，总测试数 68。
- Lint / `lintRelease` / `lintVitalRelease` 为 0 errors（依赖弃用 warnings 不变）。
- Release R8、资源压缩、zipalign、APK v2 签名、APK 元数据与 Xposed metadata 均已检查。
- `AppLocaleController` 作为唯一状态源：
  - `setUserLocale` 同步 `commit()` 保存选择并设置 `LOCALE_RECONCILE_PENDING`；
  - 确认后 `AboutFragment` 调用 `exitApplicationAfterLocaleSave()` 结束设置应用；
  - `MainApplication` 启动时 `reconcileAndApply()` 只与当前 AppCompat 应用 locale 比较一次，不一致才应用；
  - `ListPreference` 自动持久化关闭，确认弹窗是唯一切换路径；
  - 移除 `MainActivity.attachBaseContext` 手动 `createConfigurationContext`、`AppHelper.getLocaleContext()`、`AppHelper.applyLocaleChange()` 双重控制。
- 新增 `RestartRequirement` 生效等级枚举。

完整命令、产物边界和日志结论见[验证记录](VERIFICATION.md)。构建或 API 101 日志不构成
API 102 的实机证明。

## 性能/内存/省电专项优化（当前会话）

当前工作基于 `devin/r14.13-kotlin-refactor` 分支，在 `r14.13.5` 正式基线之后继续推进。已完成优化范围：

- `DeviceInfoMonitor`：状态栏电量/温度监控集中化，屏关暂停，sysfs 退避读取，弱引用释放。
- `ScreenStateController` + `StepCounterController` / `WeatherDataController` / `SystemClockHooks`：秒针/步数/天气按屏幕状态懒注册 `TIME_TICK` 和 ContentProvider 查询。
- `AudioVisualizer`：31 个 `ValueAnimator` 替换为单个 `Choreographer` 帧调度；FFT band/bin 预计算；Palette 只提交最新结果；不可见/息屏/无音乐时停止采样与动画。
- `LockScreenAlbumArtController`：锁屏专辑图缩放/灰度/模糊处理离线程；单协程取消，只保留最新请求；先降采样再模糊；AOD/息屏暂停。
- 设置应用 `AppDataAdapter` / `BitmapCachedLoader`：预计算搜索/图标 key，替换 `CopyOnWriteArrayList` 为 `ArrayList`，图标 in-flight 去重，图标缓存预算减半，`SubFragment.saveSharedPrefs` 批量 `apply`，`MainApplication.onTrimMemory` 与包变化清理缓存。

已完成构建矩阵：

- `$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\gradlew.bat --no-daemon test lint assembleDebug assembleDevelop assembleRelease`
- 退出码 `0`，单元测试通过，Lint 0 errors，R8/资源压缩在 `develop`/`release` 通过。

仍需实机验证：

- 状态栏监控、步数/天气/秒针在 AOD/息屏/亮屏切换下的刷新与功耗。
- `AudioVisualizer` 在播放、暂停、切歌、息屏和面板状态变化时的动画、内存与 CPU。
- 锁屏专辑图在不同分辨率封面与 `scale/blur/grayscale` 组合下的正确性与延迟。
- 设置应用列表滑动、搜索、图标加载和安装/卸载应用后的缓存失效。

## 仍需实机验证

- API 102 独立框架环境：冷启动、RemotePreferences、`system_server`、SystemUI、Launcher。
- 设置应用：日间/夜间主题、系统栏图标、语言切换/跟随系统、搜索返回和 Fragment 重建。
- Root 重启：有/无 Root、目标未运行、多 PID、失败输出和退出页面后的 UI 安全。
- SystemUI/Launcher：状态栏文本图标在主题、密度、折叠和重启后的显示与更新；资源替换、BT/WiFi 列表及 `包名|活动` 解析。

## 维护规则

- 非 API 迁移任务不得改变 API 101/102、Hot Reload 关闭或 Legacy Xposed API 禁止边界。
- 只有取得新的代码、构建或实机证据后，才更新版本结论和验证状态。
- 不提交 keystore、密码、APK、私人日志、缓存或机器专属数据。
- 当前公开 Release 已完成；未获用户单独要求时，不创建新 tag、Release、PR 或修改 `main`。
