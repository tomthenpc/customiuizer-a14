# DEVIN A14 CHECKPOINT

> 此文件记录当前开发线的已验证状态和下一步，不替代源码、Git、构建产物或实机证据。
> 每次继续工作前，必须重新核对本地分支、远端分支、工作区和 Release。

## 当前状态

- 仓库：`tomthenpc/customiuizer-a14`
- 正式发布版本：`r14.13.6` / versionCode `184`
- 正式发布日期：2026-07-29
- 正式 tag：`r14.13.6` → `be5191b5`
- 当前正式源码基线：`main` / `r14.13.6`
- 源码仓库 Release：[r14.13.6](https://github.com/tomthenpc/customiuizer-a14/releases/tag/r14.13.6)
- LSPosed 模块仓库 tag：`184-r14.13.6`
- LSPosed 模块仓库 Release：[184-r14.13.6](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases/tag/184-r14.13.6)
- `r14.13.5` 的两个 Release 条目已移除（**非撤回**，无已知缺陷；仅保留 r14.13 系列最新一个），
  Git tag 与源码保留，资产信息见 [RELEASE_ARCHIVE.md](RELEASE_ARCHIVE.md)。
- `r14.13.4` 已撤回；其 Release 与 tag 已删除。
- `r14.5.0` 的 Release 条目已移除（非撤回，旧签名）；Git tag 与源码保留。
- `r14.12.0` 的两个 Release 条目已移除（非撤回）；它是最后一个旧签名公开稳定版，
  与 `r14.13.x` 无法直接覆盖安装，保留下载入口意义有限。Git tag 与源码保留。
- 当前 `main` 承载 `r14.13.6` 正式发布提交；开发分支 `devin/r14.13-kotlin-refactor` 与 `main` 同点。

## ⚠️ 本版本的验证状态

**`r14.13.6` 在未完成实机验收的情况下发布**（用户明确要求先发布再测试）。

已通过：`check-invariants` 113 文件 / 8 规则 / 0 违规；122 项单元测试 0 失败；
`lintDebug` / `lintRelease` / `lintVitalRelease` 均 0 errors；`assembleDebug` 与 `assembleRelease` 通过；
APK v2 签名与 zipalign 校验通过；`META-INF/xposed` 元数据完好。

**未做**：任何设备上的运行。下列全部待验：

- 关于页切换界面语言 → 退出 → 重新进入，语言是否真的改变（含切回「跟随系统」）
- 反复切语言 + 切浅色/深色后是否仍会弹「模块未被激活」
- 搜索结果跳转后开关状态是否立即刷新；主界面搜索结果顺序与高亮
- SystemUI 多次主题 / 密度 / 折叠态切换后，receiver 与偏好观察者数量不再增长
- 状态栏电池温度与电流在缺少 `POWER_SUPPLY_TEMP` 的机型上降级而非崩溃
- 电源键 / 音量键长按（手电筒、媒体控制）、导航栏左右键长按未配置动作时仍落到 ROM 默认行为
- 锁屏手电筒长按、PIP 截图隐藏、侧边栏、freeform 广播动作
- 时钟秒针在 `TIME_SET` 后重新初始化（多时钟控制器共存）
- Launcher 应用重命名：设自定义名、清空恢复原名、重启 Launcher 后仍正确
- 拆分过的各功能面（状态栏、控制中心、锁屏、通知、Launcher 手势/图标/文件夹/布局/动画）

拿到实机日志后按 `docs/LOG_TRIAGE.md` 分诊。**先确认日志里有
`CustoMIUIzer r14.13.6 (184) loaded in <进程>`**，否则说明装的不是这个构建。

## 当前活跃开发分支

- 分支：`devin/r14.13-kotlin-refactor`
- 状态：全部工作已合并进 `main` 并发布为 `r14.13.6`，与 `main` 同点。
- 下方各轮记录保留为改动依据；每一轮的结论以其中的验证证据为准。

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

### 第三轮：`setAdditionalInstanceField` 后端重写（2026-07-28，同分支）

9. **实例级状态按 `equals` 存 → 值会丢**
   后端原本是 `WeakHashMap` + 一把全局锁。去看它是为了那把锁，但**键的语义才是真 bug**：
   `WeakHashMap` 按 `equals`/`hashCode` 找键，而"additional **instance** field"的含义是按实例。
   两个不同但 `equals` 的对象会共用字段表；改动参与 `hashCode` 的字段后条目直接找不回来。
   Launcher 重命名正是这个形状：在 `ShortcutInfo` 上存 `mLabelOrig`，改写同一对象的 `mLabel`，
   之后再读回来恢复原名。
   改为按身份比较的弱引用键；读路径无锁零分配（线程内复用探针，用完释放）；
   引用队列在写入时清理；`null` 仍是可存值（哨兵）。
   `AdditionalInstanceFieldTest` 11 个用例，并**对旧实现做了负向验证**：
   `distinctButEqualObjectsDoNotShareFields` 与 `fieldSurvivesMutationOfTheOwnersHash` 在旧实现上失败。

### 第四轮：`mods/System.kt` 按功能域拆分（2026-07-28，同分支）

10. **4898 行 → 593 行 + 7 个功能域**
    此前一直挂着"要等有实机回归能力"。实际卡住的**不是设备，是验证手段**：
    hook 注册顺序是 `MainModule` 调用序列的属性，与被调用者在哪个文件无关，
    所以只需机械证明两件事 —— 成员文本逐字节不变、调用序列不变，两条都能脚本化。
    先量耦合：94 个 public 入口全部且仅被 `MainModule` 调用，19 个私有辅助各自只被同域调用，
    **零 public→public 调用**，16 处共享状态每处只有 1–2 个使用者且都在同一域内。
    `MainModule` 改为直接调用各域对象，顺带删掉早期拆分留下的 10 个转发桩。
    `proguard-rules.pro` 无需改动（keep 规则已按 `mods.**` 通配）。
    工具与方法见 `docs/RUNTIME_INVARIANTS.md` §8。

### 本轮验证

- `python tools/check-invariants.py` → 103 files, 8 条规则, no violations
- `./gradlew clean test lintDebug lintRelease lintVitalRelease assembleDebug assembleRelease` → 退出码 0
- 单元测试 117 个, 0 失败; lint / lintRelease 均 0 errors
- 全部 Kotlin 转换都是编译期可验证的（被写入 / 被 `*args` 展开 / 被当 `Array<Any?>` 传出的
  参数数组，改成 List 或单值访问后一定编译失败）
- 拆分证据：119/119 成员逐字节一致且无遗漏；`System.kt` diff 为纯删除（新增 0 行）；
  `MainModule` 前后各 268 个调用点、序列完全一致；R8 保留方法数前后均 7887，
  唯一 15 处差异是 `access$` 桥接方法参数类型随宿主类变更、一一对应；
  Release APK 3,065,633 字节与拆分前完全相同

### 待实机验证

- SystemUI 多次主题 / 密度 / 折叠态切换后，receiver 与偏好观察者数量不再增长
- 状态栏电池温度与电流读数在缺少 `POWER_SUPPLY_TEMP` 的机型上降级而不是崩溃
- 锁屏手电筒长按、PIP 截图隐藏、侧边栏、freeform 相关广播动作行为不变
- 时钟秒针在 `TIME_SET` 后仍然重新初始化（多时钟控制器共存场景）
- Launcher 应用重命名：设置自定义名称、清空恢复原名、重启 Launcher 后仍正确

## 发布产物与签名

| 项目 | 值 |
| --- | --- |
| APK | `CustoMIUIzer-A14-r14.13.6.apk` |
| 大小 | 3,082,129 bytes |
| APK SHA-256 | `35AEE1FEA1D7B38D967267210B7C272340B56B580ED49BEF4945AA9FC6F2ED96` |
| 签名证书 SHA-256 | `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70` |
| 签名 | APK Signature Scheme v2，1 个签名者 |
| applicationId | `tv.withaibuild.customiuizer.r14` |
| ABI | `arm64-v8a` |
| SDK | min/target `34 / 34` |
| libxposed | min API `101` / target API `102` / `staticScope=false` |

`r14.12.0` 及更早公开版本的旧签名私钥已经遗失。它们不能直接覆盖安装
`r14.13.6`；升级时必须备份设置、卸载旧版、安装 `r14.13.6`、重新启用作用域、恢复设置并完整重启。
`r14.13.5` → `r14.13.6` 为同一签名，可直接覆盖。

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

## 公开发布线收束（2026-07-29）

公开状态已统一到 `r14.13.7`：

- `tomthenpc/customiuizer-a14` 只保留 `main` 分支、`r14.13.7` tag 和 `r14.13.7` Release；
- `Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14` 只保留 `main` 分支、
  `185-r14.13.7` tag 和 `r14.13.7` Release；
- 两个 Release 的 APK 均为 `CustoMIUIzer-A14-r14.13.7.apk`，大小均为 3,084,589 bytes，
  SHA-256 均为
  `11D01A737BED25C3C4D31153DE22CB918A651D0DD043D0374E2C0E41D32492CC`；
- 两边 Release 说明已统一，不再引用已删除的回退 Release 或 tag；
- 历史版本只保留在 Git 提交历史、CHANGELOG 和工程记录中，不再作为公开下载入口。

`r14.13.7` 的静态门禁、测试、lint、构建、产物和签名结论不变；本轮未新增实机证据，
仍不得称为已完成实机验收的稳定版。

## 结构整理（2026-07-29，`refactor/structure-tidy-r14.13.7`）

来源：一次独立工程结构审计。结论是**小范围整理**，不是重构 —— 目录布局、单一 `:app`、
`src/main/java` 混合 source set、剩余 3 个 Java 文件的边界全部判定为保持现状。
本节记录审计中判定为"收益明确、风险可控"的两项，其余一律搁置。

- 工作分支：`refactor/structure-tidy-r14.13.7`（基于 `main` / `d22fc1f2`）
- 状态：**未合并 `main`**，未创建 tag 或 Release
- 本节为当前活跃开发线，取代上文「当前活跃开发分支」中已完成的
  `devin/r14.13-kotlin-refactor` 条目

### 第一步：删除 `GlobalActions` 的 6 个转发桩（`b894af8e`）

`mods/System.kt` 拆分（`fbc16798`）删掉了早期拆分留下的 10 个转发桩，`GlobalActions`
自己的 6 个漏网。实测其中 4 个 —— `launchAppIntent`、`launchActivityIntent`、
`launchShortcutIntent`、`launchIntent` —— **全仓库零调用者**：`GlobalActions.kt` 自身在
`:93`、`:94`、`:105` 就直接调用 `GlobalActionsIntentHelper`。另外 2 个各有一个调用者，
都在 `MainModule`，已改为直连 `GlobalActionSystemServerHooks`。

副作用：`GlobalActions ↔ GlobalActionSystemServerHooks` 的引用环随之断开。
`GlobalActions ↔ GlobalActionsIntentHelper` 保留 —— 其反向边全部是 `ACTION_PREFIX` /
`EVENT_PREFIX`，`const val` 编译期内联，不产生运行期类初始化依赖。

### 第二步：Hook 侧叶子工具函数拆分到 `utils/HookUtils.kt`（`d610e339`）

`utils/Helpers.kt` 是设置应用的工具箱：93 个成员、1215 行。Hook 侧从 23 个文件伸手进去，
但只用其中十几个叶子函数（`dp2px`、`getResId`、振动包装、`fastBlur`、`copyFile`、gamma 数学）。

Kotlin `object` 在首次静态访问时跑 `<clinit>`，所以每一次这样的读取都要在 `system_server`、
SystemUI 和 Launcher 里付整个 object 的初始化成本：一个按 `Runtime.maxMemory()` 定容的
`LruCache` 子类、mod 列表、两个 Comparator、资源 id 表，外加加载 `AppData`、`ModData` 和两个
Comparator lambda 类。`Helpers` 还 import 了 `PrefsProvider` 和 `prefs.PreferenceCategoryEx`，
把设置应用的 UI 类拖进了 Hook 进程的依赖图。

23 个叶子成员逐字移入 `utils/HookUtils.kt`（工具：`tools/split-hook-domain.py`），
`Helpers.kt` 1215 行 → 869 行，`HookUtils.kt` 372 行。两个成员没有搬，而是就地处理：

- `modulePkg` 由 `@JvmField val` 改为 `const val`。const 读取在调用点内联，12 个 Hook 侧
  引用因此编译成字符串字面量，不再引用该类，**一个调用点都没改**。
- `containsStringPair` 本身是 `PrefPair.containsFirst` 的一行转发桩，与第一步删掉的是同一
  模式。4 个调用者改为直连 `PrefPair`，桩删除。

### `HookUtils` 为什么最终放在 `utils/` 而不是 `mods/utils/`

最初放在 `mods/utils/`。`proguard-rules.pro` 的

```
-keepclassmembers class tv.withaibuild.customiuizer.mods.** {
    public static <methods>;
    public <fields>;
}
```

按**包前缀**匹配，于是把 `HookUtils` 的 22 个 public static 全部钉住：R8 seeds 由 **3992
增加到 4014**，同时剥夺了 R8 在 88 个调用点内联 `dp2px`、`constrain` 这类四行函数的自由。

该 keep 规则的存在理由是 `mods.**` 里的 Hook 入口由 `MainModule` 按名调用、不得被裁剪；
`HookUtils` 是叶子工具，只被普通静态调用触达，不需要被 keep。把文件移到 `utils/`（与
`PrefPair` 同处，后者已是"两侧都可用的小叶子类"的既有先例）后，**seeds 精确回到 3992、
零差异**，keep 面未扩大。

### 本轮验证（全部为静态与构建证据）

- 单元测试 171 个，0 失败
- `lintDebug` / `lintRelease` / `lintVitalRelease` 全部通过
- `python tools/check-invariants.py` → 117 files, no violations
- `MainModule` 前后各 **266 个调用点，方法名与顺序完全不变**，只有两处接收方改变
- Release APK **两步前后均为 3,084,589 字节**
- R8 seeds 的唯一变化是删除的 6 个转发桩：**3998 → 3992**；第二步相对第一步 seeds 零差异
- 行级证明：HEAD 的 `Helpers.kt` 与（现 `Helpers.kt` + `HookUtils` 成员区）逐行比对，差异
  只有删除的 `containsStringPair` 桩和 `modulePkg` 的 const 改动，其余全部对上
- 字节码证明：改前 `mods/**` 大面积引用 `utils/Helpers`；改后只剩 2 个类，均为
  `LauncherAnimationHooks$FixAnimHook` 内部类。46 个类改为引用 `HookUtils`
- 结论：**`system_server` 与 SystemUI 已不再加载 `Helpers`**；Launcher 仍有 2 处
  `Helpers.getAnimationScale`（见下文搁置说明）
- 未改变：Hook target、priority、before/after/intercept 语义、`Chain.proceed()` 次数与参数、
  异常传播、进程作用域、偏好键与默认值、API 101/102 边界、R8 keep 规则文本

### ⚠️ 尚未完成实机验证

**本轮没有在任何设备上运行过。** 上述全部是静态门禁、构建产物和字节码证据，
不得据此描述为已实机验证。

待实机验证项：

- 冷启动
- 完整重启
- `system_server`、SystemUI、Launcher 三个进程的模块日志（先确认日志里有
  `CustoMIUIzer r14.13.7 (185) loaded in <进程>`）
- 状态栏图标位置（依赖 `getResId` / `dp2px`）
- 锁屏元素边距（依赖 `dp2px`）
- Launcher 图标缩放（依赖 `dp2px`）
- 自定义全局操作可触发（第一步改动了其 `system_server` 与 SystemUI 安装入口的接收方）

### 本轮明确搁置

1. **`AnimationScale` 拆分** —— 剩余成本只影响 Launcher 的两个调用点，而拆分会触及
   `getAppContentResolver`、`getAnimationScaleKey`、`setAnimationScale`、
   `Helpers.appContentResolver`、`MainApplication` 启动路径和动画缩放设置页面。
   这已经不是可以仅靠构建门禁证明等价的机械重构，收益不足以覆盖新增的设置应用与实机 UI
   回归风险。
2. **`AudioVisualizer` / `BatteryIndicator` 移动** —— 二者是纯 SystemUI Hook 组件，
   放在 `utils/` 属历史遗留，但从 `utils/` 移入 `mods/` 会落进上文那条 keep 规则、
   扩大 keep 面，与本轮第二步踩到的是同一个坑。收益仅为命名整洁。
3. **`mods.System` 重命名** —— 该 object 遮蔽 `java.lang.System`，导致仓库中 15 处必须写
   全限定名。重命名干净但会碰 13 个 Hook 入口并需重建 R8 基线，等有实机回归窗口再做。

### 离线二进制审计（2026-07-29，同分支）

因暂不具备安装测试条件，本轮补做一次纯离线二进制审计：在独立 worktree 中以修改前基线
`d22fc1f2` 构建 Release APK，与当前 HEAD 的 Release APK 逐项对比。**全部为离线静态比对，
不含任何设备运行。**

#### 基线可信度

基线 APK 的 SHA-256 为
`11D01A737BED25C3C4D31153DE22CB918A651D0DD043D0374E2C0E41D32492CC`，
与本文上文「公开发布线收束」记录的 `r14.13.7` 公开 Release APK 哈希**完全一致**，
即基线构建逐字节复现了已发布产物，对比结果因此可信。

#### 产物指纹

| 项目 | 基线 `d22fc1f2` | 当前 HEAD |
| --- | --- | --- |
| APK SHA-256 | `11d01a73…92cc` | `ed806249…1ba2` |
| APK 大小 | 3,084,589 bytes | 3,084,589 bytes |
| `classes.dex` SHA-256 | `97275561…8b27` | `ad396f73…d664` |
| `classes.dex` 大小 | 1,509,316 bytes | 1,509,064 bytes（−252） |
| zip 条目数 | 536 | 536（集合完全一致） |

536 个条目中**只有 4 个内容不同**：`classes.dex`、`assets/dexopt/baseline.prof`、
`assets/dexopt/baseline.profm`（均由 DEX 派生）和 `META-INF/xposed/java_init.list`。
其余 513 个文件逐字节相同。

#### Manifest、资源、ABI 与身份

- `AndroidManifest.xml`：**逐字节相同**（SHA-256 `98da2961…3efb`，14,424 bytes）
- `resources.arsc`：**逐字节相同**（SHA-256 `bab17571…6830`，776,112 bytes）
- `lib/arm64-v8a/libdexkit.so`：**逐字节相同**；ABI 仍为单一 `arm64-v8a`
- `aapt dump badging` 两边一致：`applicationId=tv.withaibuild.customiuizer.r14`、
  `versionCode=185`、`versionName=r14.13.7`、`sdkVersion=34`、`targetSdkVersion=34`
- 签名：仅 v2（v1/v3/v3.1/v3.2/v4 均为 false），1 个签名者，证书 SHA-256 两边同为
  `c0eff2dc…2e70`；`zipalign -c 4` 两边均通过

#### `META-INF/xposed`

- `module.prop`：**逐字节相同**（`minApiVersion=101` / `targetApiVersion=102` / `staticScope=false`）
- `scope.list`：**逐字节相同**（12 个包，顺序不变）
- `java_init.list`：`iq` → `jq`。这是 R8 混淆名位移，不是入口变更 ——
  `mapping.txt` 显示基线 `MainModule -> iq`、HEAD `MainModule -> jq`，
  `proguard-rules.pro` 的 `-adaptresourcefilecontents META-INF/xposed/java_init.list`
  已同步改写该资源。两边入口都正确指向 `MainModule`，且各自 APK 内该名字唯一。

#### R8 四件套

| 文件 | 基线 | HEAD | 差异 |
| --- | --- | --- | --- |
| `configuration.txt` | 525 行 | 525 行 | **完全相同** |
| `seeds.txt` | 3998 行 | 3992 行 | 仅删除的 6 个转发桩，无其他增删 |
| `usage.txt` | 25,386 行 | 25,391 行 | 见下文 `HookUtils` |
| `mapping.txt` | 1997 类 / 101,712 成员 | 1997 类 / 101,699 成员 | 类数不变 |

#### `HookUtils` 的 R8 处置（重点核验项）

1. **package 符合预期**：`tv.withaibuild.customiuizer.utils`，不在 `mods.**` 之下。
2. **未被 keep 规则额外固定**：`HookUtils` 在 `seeds.txt` 中出现 **0 次**。
3. **R8 可正常内联、合并与删除** —— `usage.txt` 记录了它实际被删掉的成员：
   - 无用代码删除：`constrain(int,int,int)`、`lerp(int,int,float)`、`lerpInv`、
     `lerpInvSat`、`saturate`
   - 内联后删除：`INSTANCE` 字段、`performStrongVibration(Context)`

   即 R8 对它拥有完全的优化自由。作为对照，此前放在 `mods/utils/` 时这 22 个 public
   static 全部进入 seeds（3992 → 4014），R8 无法内联。

#### 调用点、方法签名、参数类型与调用顺序

用自建 DEX 解析器（`string_ids` / `method_ids` / `class_data` / `code_item`，
按 `mapping.txt` 反混淆）逐方法提取 `invoke-*` 序列后比对：

- **`MainModule`**：两边同为 7 个方法、**596 次调用**。差异**仅 2 处**（序列第 79 和 492 位），
  方法名（`setupStatusBar` / `setupGlobalActions`）、参数类型（`PackageReadyParam` /
  `SystemServerStartingParam`）、返回类型（`void`）全部相同，只有接收方由 `GlobalActions`
  变为 `GlobalActionSystemServerHooks`。其余 594 次调用的目标、参数与顺序完全一致。
- **全模块 2105 个共有方法**：把本分支认可的两类改写（`Helpers.X` → `HookUtils.X`、
  `containsStringPair` → `PrefPair.containsFirst`）和 R8 水平合并宿主命名差异归一化后，
  仍有差异的只剩 2 个：一个是混淆返回类型名位移（`hs[]` → `is[]`，同一方法），
  另一个是 `Helpers.<clinit>` 由 12 次调用降为 11 次 —— 正是 `modulePkg` 改 `const val`
  的预期结果。
- **DEX 方法清单全量比对**：仅差 8 项 —— 删除 7 个（6 个 `GlobalActions` 转发桩 +
  `Helpers.containsStringPair`），新增 1 个（`HookUtils.<clinit>`，即 `getResId` 的资源 id 缓存）。
  其余 2,619 个方法签名两边完全对应。

#### Hook 语义不变性

- **Hook target 字符串**：DEX 字符串表 11,146 → 11,147 条，逐条比对后，
  **所有发生变化的字符串都是混淆类型描述符或 R8 构建标记，无一条是有语义的字符串**。
  分族集合相等：`com.android.*` 225 条、`com.miui.*` 112 条、`miui.*` 29 条、
  `tv.withaibuild.customiuizer.mods.action./event.*` 54 条。
- **priority**：`MethodHook.<init>()` 两边各 479 次、`MethodHook.<init>(int)` 两边各 7 次；
  `returnConstant` 各 55 次、`DO_NOTHING` 各 2 次。两个 revision 的源码中所有
  `PRIORITY_*` 表达式集合相同（仅一处行号因新增 import 位移 1 行，内容不变）。
- **before / after / intercept**：DEX 中 `before` 66 个、`after` 87 个、`intercept` 340 个，
  **两边完全相同**。
- **`Chain.proceed()`**：两边各 **322** 次调用，参数类型一致；`unhook` 各 2 次。
- **Hook 安装 API 调用数**：`XposedHelpers.findAndHookMethod` 6、`findAndHookConstructor` 2、
  `hookAllMethods` 4、`hookAllConstructors` 2、`doHookMethod` 3、
  `ModuleHelper.findAndHookMethod` 10、`ModuleHelper.hookAllMethods` 12 —— 全部两边相同。
- **偏好键与默认值**：DEX 中 `system_|launcher_|controls_|various_|miuizer_` 前缀字符串
  572 条、`pref_key*` 164 条，两边**集合完全相等**。整个源码 diff 中唯一出现偏好键的行是
  `Helpers.performStrongVibration(...)` → `HookUtils.performStrongVibration(...)` 的接收方改写，
  键名 `controls_volumemedia_vibrate_ignore` 与其默认值未动。
- **进程作用域**：`scope.list` 逐字节相同；`onSystemServerStarting` 与 `onPackageReady`
  的调用序列已由上述 596 次调用比对覆盖，进程分支条件未变。

#### 离线审计结论

- **静态与二进制审计已完成。** 上述全部差异均可逐项归因于本分支的两次改动，
  无任何无法解释的二进制变化。
- **尚未进行实机验证。** 本轮和前两步都没有在任何设备上运行过；二进制等价性不能替代
  运行时验证。
- **本分支不可发布。** 不得据此打 tag、创建 Release 或合并 `main`。
- **后续必须在 Android 14 / HyperOS 1 设备上完成验收**，验收项即上文「⚠️ 尚未完成实机验证」
  一节列出的 7 项。
