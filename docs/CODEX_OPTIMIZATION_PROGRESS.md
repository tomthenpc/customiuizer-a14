# Codex Kotlin / 性能优化进度

## 固定基线

- 优化基线：`0e4c00b8b95f02cc5beaf8288bfe8c28a9423876`
- 当前优化分支：`codex/r14.11-kotlin-performance`
- 当前代码起点：`d39f6e8954ba901ffdea55da7622803d2dc38593`
- libxposed：编译 API `102.0.0`，`minApiVersion=101`，`targetApiVersion=102`
- Hot Reload：关闭
- Android：`minSdk=34`，`targetSdk=34`
- 构建工具：Gradle `9.5.1`、AGP `9.2.1`、compileSdk / Build Tools `37`

## 当前阶段

### 阶段 A：正确性、注册与生命周期

状态：已完成静态审查、代码修复和阶段构建验证；等待实机验证。

本阶段集中审查并处理：

- `AudioVisualizer` 的 View、偏好观察器、协程与原生 `Visualizer` 生命周期；
- SystemUI 其他长期 Receiver / Observer / Listener 的所有权与防重复注册；
- 模块入口、进程判断、重复初始化和功能关闭后的残留任务；
- Handler、Coroutine、Executor、attach/detach 与 SystemUI 重建路径；
- 静态持有 View、Context、Fragment 或 ClassLoader 的明确风险。

每个独立根因保持独立 commit；每个 commit 执行 `test assembleDebug`，涉及 R8、动态入口、
Hooker、反射或 Manifest 时增加 `assembleRelease`。阶段完成后统一执行完整
clean / test / Lint / Debug / Release / R8 / 资源压缩验证并推送当前优化分支。

#### A1：`AudioVisualizer` View 资源闭环

状态：已修复，阶段验证和实机验证待完成。

根因：

- 偏好观察器使用无 owner 注册，View detach 后仍被进程级观察器集合强引用；
- detach 只取消 View 协程，没有保证已启用的原生 `Visualizer` 执行 `release()`；
- `Visualizer(0)` 初始化在 IO 协程中执行，detach 可能发生在初始化完成和字段赋值之间。

处理：

- 使用 View 作为偏好观察器 owner，并在 detach 时成对移除；
- detach 时终止动画、随机颜色任务和 View 协程，并同步释放已经安装的 `Visualizer`；
- 用仅覆盖 link/unlink/detach 冷路径的小锁保护原生对象交接；
- 初始化失败、重复 link 或 detach 竞态中未安装的临时 `Visualizer` 也必定释放；
- 不改变 Hook 目标、显示条件、FFT、颜色、绘制和动画参数。

局部验证：`test assembleDebug` 成功，33 个测试全部通过。

#### A2：`AudioVisualizerHook` 重建与静态引用

状态：已修复，阶段验证和实机验证待完成。

根因：

- `onViewAttachedToWindow` 每次回调都会创建新的容器和 Visualizer View，没有检查同一通知面板已有实例；
- `System.audioViz` 是进程级强引用，旧 View detach 后没有主动清空；
- 创建过程在加入面板前失败时，未 attach 的 View 不会收到 detach 回调，已注册资源无法自动释放；
- 已 dispose 的子 View 随同同一面板重新 attach 时不能继续复用。

处理：

- 使用稳定 tag 在当前通知面板内查找实例，活跃实例直接复用；
- 已 dispose 的旧实例连同旧容器移除后再创建，避免同一面板叠加空容器或失效 View；
- dispose 回调只在静态字段仍指向当前实例时清空引用；
- 创建中途失败时显式 dispose 尚未加入面板的实例；
- 不改变原 Hook 方法、after 顺序、View 层级位置算法或显示条件。

局部验证：`test assembleDebug` 成功，33 个测试全部通过。

#### A3：音量模糊偏好观察器重复注册

状态：已修复，阶段验证和实机验证待完成。

根因：`MiuiVolumeDialogImpl.initDialog` 每执行一次都会向进程级集合加入新的无 owner
偏好观察器；观察器只更新 `blurCollapsed` / `blurExpanded` 两个进程级值，重复实例没有功能收益，
且不会随 Dialog 销毁移除。

处理：

- 在 `BlurVolumeDialogBackgroundHook` 安装时读取初始值并只注册一个进程级观察器；
- 删除仅用于重复注册观察器的 `initDialog` Hook；
- 保持两个模糊参数的实时偏好更新以及音量 Dialog Hook 行为不变。

局部验证：`test assembleDebug` 成功，33 个测试全部通过。

#### A4：截图可见性 Receiver 与 View 生命周期

状态：已修复，阶段验证和实机验证待完成。

根因：

- 状态栏 Fragment 的 `onViewCreated` 和 NavigationBar 的 `onInit` 都会注册捕获 View 的匿名 Receiver；
- View detach 后没有注销，重建或 `onInit` 重入会保留旧 View 并叠加回调；
- NavigationBar 的可见性备份状态位于 Hook 对象，多个 View 会共享状态。

处理：

- 用同一个小型 View 生命周期 Receiver 保持原截图广播和显示/恢复规则；
- 通过 keyed View tag 保证同一 View 只绑定一次，不引入进程级 View 引用；
- detach 时注销，重新 attach 时恢复注册；
- NavigationBar 的可见性状态改为每个 View 实例独立保存。

局部验证：`test assembleDebug` 成功，33 个测试全部通过。

#### A5：锁屏专辑封面 Receiver 重复注册与强引用

状态：已修复，阶段验证和实机验证待完成。

根因：默认锁屏主题下，每个 `MiuiNotificationPanelViewController` 构造实例都会注册一个
进程生命周期匿名 Receiver；Receiver 捕获 Hook callback，进而强持有 controller 和主题背景 View，
SystemUI 重建后会累积旧实例和重复回调。

处理：

- 改为功能级单个进程 Receiver；
- 仅用 `WeakReference` 指向当前 controller，构造新实例时更新目标；
- Receiver 不持有 View、Context 或 Hook callback；
- 保持广播 action、导出限制和 `updateThemeBackgroundVisibility` 调用不变。

局部验证：首次编译发现并修正 callback 可空类型边界；随后 `test assembleDebug` 成功，
33 个测试全部通过。

#### A6：截图格式 DexKit 候选唯一性

状态：已修复，阶段与实机验证待完成。

根因：截图格式 Hook 只用日志字符串查询目标方法，然后直接取第一个结果；ROM 中出现多个
字符串使用点时可能 Hook 错误方法。现有回调明确要求参数数量不少于 7，且第 5 个参数会被替换为
`Bitmap.CompressFormat`，但原查询没有验证这个调用契约。

处理：

- 保留既有字符串和排除包查询，不猜测当前未提供的 ROM 混淆类名或固定方法名；
- 在 DexKit 返回的候选中只接受参数数量不少于 7、且第 5 个参数类型确为
  `Bitmap.CompressFormat` 的方法；
- 只有一个兼容候选时才安装该子 Hook；零个或多个时记录诊断并跳过，避免任取错误方法；
- 方法解析或安装失败不再静默吞掉，仍保留后续通用 `Bitmap.compress` Hook。

局部验证：`test assembleDebug assembleRelease` 成功；33 个测试全部通过，Release R8、
资源压缩和 `lintVitalRelease` 均完成。

#### 阶段 A 收口

独立问题提交：

- `00f52ea`：释放 `AudioVisualizer` 的观察器、协程、动画和原生资源；
- `acdd51e`：防止 `AudioVisualizerHook` 重复实例和失效静态引用；
- `68760f8`：将音量模糊偏好观察器收敛为进程唯一注册；
- `2a95f4a`：将截图状态栏/导航栏 Receiver 绑定到 View attach/detach；
- `1637144`：将锁屏专辑封面 Receiver 收敛为进程唯一注册和弱目标引用；
- `6c813f3`：按真实参数契约要求截图 DexKit 目标唯一。

阶段全量验证执行：

```powershell
.\gradlew.bat --no-daemon clean test lint lintRelease lintVitalRelease assembleDebug assembleRelease
```

- 构建成功；33 个测试全部通过；
- Debug/Release Lint 均为 0 errors、489 warnings；
- Debug、Release、R8 和资源压缩均完成；
- 完整 Release Lint 已覆盖 Vital 分析，因此同一任务图中的
  `lintVitalReportRelease` / `lintVitalRelease` 按既有 Gradle 行为标记为 `SKIPPED`；
- Release APK：`app/build/outputs/apk/release/CustoMIUIzer-A14-r14.10.0.apk`，
  3,021,289 bytes，SHA-256
  `203BD0E9E79B1A092B188A39ABC00F8699261562D9EEB486848F1FD94C2B40E1`；
- APK v2 签名和 zipalign 校验通过，签名证书 SHA-256 仍为
  `3061A3DA1C2FC46B44E215D024B1BFE3A012CB4D70B90B0214FA9FC896CEF60D`；
- APK 内 `module.prop` 仍为 min API 101、target API 102、`staticScope=false`；
- R8 后入口 `ro` 可由 mapping 追溯到 `MainModule`；
- APK DEX 未发现 `de.robv.android.xposed`。

收口边界：

- 未发现 P0；阶段 A 中已确认的 P1 已全部处理；
- PIP 截图 Receiver、锁屏手电筒 Observer 和状态栏秒钟 ticker 的宿主在目标 ROM
  是否会同进程重建，当前缺少设备/ROM 生命周期证据；它们保留为实机观察项，不猜测重写；
- 7 个 Devin 遗留 `mods/*.java.bak` 仍保持未跟踪、未读取内容、未修改和未提交。

### 阶段 B：核心 Hook 与热路径

状态：已完成静态审查、代码优化和阶段构建验证；等待实机验证。

#### B1：双 SIM 信号重绘临时分配

状态：已优化，阶段与实机验证待完成。

证据：

- `StatusBarIconControllerImpl.setMobileIcons` 在双卡状态变化时重复创建同一组四个字段名数组；
- `applyDarknessInternal` / `onDarkChanged` 每次回调都会根据相同的启动期配置拼接两个完整
  drawable 名称，再执行 `HashMap<String, Int>` 查询；
- drawable 风格、明暗模式和 2 × 6 级信号资源集合在 SystemUI 启动后已经固定。

处理：

- 四个同步字段名提升为进程内只读共享数组；
- 启动期仍按原资源名注册 fake resource，但把结果保存为
  `[SIM][信号级别][普通/深色/tint]` 的 `IntArray` 索引表；
- 热回调只计算 0/1/2 模式索引并直接读取资源 ID，不再拼接字符串或查询字符串 HashMap；
- 保持 theme 风格不使用 tint、缺失资源返回 false、原 drawable/tint 设置顺序不变。

局部验证：`test assembleDebug` 成功，33 个测试全部通过。

#### B2：system_server 振动时段判断

状态：已优化，阶段与实机验证待完成。

证据：

- `VibratorService.doVibratorOn` 每次进入自定义振幅路径都会拼接四个偏好 key；
- 同一热回调会修改进程共享的 `SimpleDateFormat` 时区，再创建/格式化/解析当前时间和
  起止时间；该 formatter 也不具备线程安全保证；
- 业务判断只需要比较一天内的分钟数。

处理：

- 直接读取四个固定偏好 key，消除热路径 key 拼接；
- 起点、终点和当前时间统一换算为 0～1439 的整数分钟；
- 保持普通时段、跨午夜时段以及起止分钟排他边界不变；
- 移除共享 formatter 及多组字符串、`Date` 和解析对象，不改变振幅分类和系统调用。

局部验证：`test assembleDebug` 成功，33 个测试全部通过。

#### B3：Launcher 图标缩放与角标资源查找

状态：已优化，阶段与实机验证待完成。

证据：

- `IconScaleHook` 只会在 Launcher 冷启动时按开关安装，但四个图标回调每次都重新读取
  `launcher_iconscale` 并计算同一平方根；
- 每个角标文字变化都会用 `Resources.getIdentifier()` 重查固定的
  `icon_message_max_width`；
- `getIconLocation` 位于拖动/布局相关路径，角标监听器也会随通知数字频繁触发。

处理：

- 在 Hook 安装时只计算一次原有 `sqrt(scale / 100)`，所有回调共享同一不可变值；
- 首次创建角标监听器时通过既有资源缓存取得资源 ID，监听器只执行
  `getDimensionPixelSize()` 以继续适配当前 Resources；
- 每个回调只取一次图标子 View，保持 scale、Rect 扩展、角标位移和动画公式不变；
- 不新增监听器、线程、缓存层或配置格式。

局部验证：`test assembleDebug` 成功，33 个测试全部通过。

#### 阶段 B 收口

独立问题提交：

- `335887e`：移除 SystemUI 双 SIM 信号重绘中的字符串、HashMap 和字段数组临时分配；
- `94cc608`：将 system_server 振动时段判断收敛为整数分钟比较；
- `fc7e4c7`：缓存 Launcher 图标缩放输入和角标资源 ID。

阶段全量验证再次执行：

```powershell
.\gradlew.bat --no-daemon clean test lint lintRelease lintVitalRelease assembleDebug assembleRelease
```

- 构建成功；33 个测试全部通过；
- Debug Lint 为 0 errors、489 warnings，Release Lint 为 0 errors、488 warnings；
- Debug、Release、R8 和资源压缩均完成；
- 完整 Release Lint 已覆盖 Vital 分析，同一任务图中的
  `lintVitalReportRelease` / `lintVitalRelease` 按既有 Gradle 行为标记为 `SKIPPED`；
- DexKit Lint 仍提示 `DexKitBridge.create()`；当前只在两个不同目标包的独立进程中按需创建、
  搜索后立即关闭，不存在同一进程同一 APK 重复创建证据，因此不为消除静态告警扩大生命周期；
- Release APK 为 3,021,289 bytes，SHA-256
  `4EA0966BEC5497263406A31D6CBDE85590C80BC48D063865BD21004DECA0BDC0`；
- APK v2 签名、zipalign、min API 101 / target API 102 / `staticScope=false` 元数据和
  R8 入口 mapping 均保持有效。

收口边界：

- 详细网速的接口枚举决定统计口径，替换为 total traffic 需要目标 ROM 实测；
- 通用 Hook 参数数组适配承载 API 101 的参数修改/异常传播语义，不做全局性能重写；
- 状态栏手势仍有可缓存反射与偏好读取，但涉及多显示器和设置实时生效，缺少 trace 时列为
  低收益或需实机数据项；
- 未发现正常路径高频日志、无效 Binder 轮询或功能关闭后仍安装的新 Hook。

### 阶段 C：逻辑、算法、Kotlin 与设置 UI

状态：进行中。

#### C1：应用选择列表的 nullable 加载状态

状态：已修复，局部验证通过，阶段验证待完成。

根因：

- Java 基线用 `null` 区分“尚未加载”和“已加载但结果为空”；
- `AppHelper` / `Helpers` Kotlin 迁移时把四个列表改为立即创建的空 `ArrayList`；
- `AppSelector.loadApps()` 仍只在列表为 `null` 时执行包管理器查询，因此所有加载分支都被
  编译器判定为恒假，应用选择页可能永久绑定空列表。

处理：

- `installedAppsList`、`launchableAppsList`、`shareAppsList`、`openWithAppsList`
  恢复 nullable 初始状态，保持原 Java 状态机；
- 后台加载完成后仍一次性替换为真实列表，空查询结果继续用非空空列表表示“已加载”；
- `setupList()` 通过局部非空快照绑定 Adapter，消除可变全局字段的空值竞态和强制断言；
- 不改变查询、排序、多用户、黑白名单、隐私应用或选择回传逻辑。

局部验证：

- `.\gradlew.bat --no-daemon test assembleDebug`：成功；
- 33 项单元测试通过；
- Kotlin 编译器原先对四个加载判断给出的“条件恒为 false”警告已消失。

#### C2：分享与“打开方式”应用列表去重

状态：已修复，局部验证通过，阶段验证待完成。

根因：

- 两个包管理器查询结果按包名去重时，每遇到一个候选都会线性扫描已经创建的
  `AppData` 列表；
- “打开方式”还会合并内容 URI 和 HTTPS 两组候选，重复包较多时会形成不必要的
  O(n²) 比较；
- 双开应用也在同一列表中，使线性扫描次数进一步增加。

处理：

- 查询周期内分别维护只含包名的 `HashSet`，把重复检测降为均摊 O(1)；
- 仍按包管理器首次返回顺序保留每个包，随后执行原有排序；
- 只有主用户条目成功创建后才记录包名，保持异常时后续同包候选仍可重试；
- 双开条目的创建、用户 ID、标签、启用状态和最终排序语义不变。

局部验证：

- `.\gradlew.bat --no-daemon test assembleDebug`：成功；
- 33 项单元测试通过。

## 已完成批次

### 批次 1：`BatteryIndicator` 生命周期释放

状态：已完成，等待实机验证。提交：
`838bc56f4a9dff60b80353170c4352fa734bae2e`。

根因：`BatteryIndicator` 被加入 SystemUI 状态栏窗口后，会向 Context 注册广播接收器，并向
`ModuleHelper` 的进程级集合注册偏好观察器。原实现的 `onDetachedFromWindow()` 只取消协程，
因此 SystemUI 在同一进程中重建 View 时，两个注册点都会继续强引用失效的 View，并可能产生重复回调。

处理：

- 将匿名广播接收器改为可保存身份的命名内部接收器；
- 注册偏好观察器时使用现有的 owner 绑定能力；
- View 脱离窗口时成对移除偏好观察器、注销广播接收器并取消协程；
- 增加注册状态门，避免同一 View 的 `init()` 重入造成重复注册。

保持不变：

- 未改变 SystemUI Hook 注册点、优先级、before/after 顺序和异常传播；
- 未改变广播 action、导出属性、偏好 key、功能开关或用户配置格式；
- 未引入轮询、后台任务、反射、API 102 专属调用或 Hot Reload；
- 功能关闭时仍不会创建 `BatteryIndicator` 或注册相关资源。

### 批次 0：固定基线和结构化库存

- 从 `0e4c00b8b95f02cc5beaf8288bfe8c28a9423876` 创建
  `codex/r14.11-kotlin-performance`；未从远程覆盖本地代码。
- 活跃主源码共 91 个文件、31,267 行：86 个 Kotlin、5 个 Java。
- 最大且风险最高的 Hook 主干是 `System.kt`、`SystemUI.kt`、`Launcher.kt`；
  本轮没有整体读取或改写这些大文件，只使用符号搜索和局部上下文确认。
- 未发现 `TODO()`、`NotImplementedError` 或临时未实现 stub；编译也未发现损坏源码。
- 7 个 Devin 遗留的 `mods/*.java.bak` 始终保持未跟踪、未读取内容、未修改且未纳入构建或提交。
- API/构建边界保持为：libxposed API `102.0.0` 编译、min API 101、target API 102、
  Android min/target SDK 34、Hot Reload 关闭、Release 开启 R8 和资源压缩。
- 依赖版本已经由 `gradle/libs.versions.toml` 集中锁定；本轮未改依赖、Gradle、AGP、SDK 或 R8 规则。
- 活跃源码没有 Legacy Xposed API 调用；Manifest 中
  `de.robv.android.xposed.category.MODULE_SETTINGS` 仅是管理器入口 category。

### 批次 2：`Credentials` 等价 Kotlin 迁移

状态：已完成，等待实机验证。提交：
`ac5f523`。

- 保持完整类名、`AppCompatActivity` 继承、公开无参构造、Manifest 注册与 R8 keep name；
- 保持凭据确认、Keystore 回退、`finish()` / 密码设置页顺序、`onActivityResult()` 和 Toast 行为；
- Java 与 Kotlin 的构造器和生命周期方法 JVM 描述符一致；
- 完整测试、Lint、Debug、Release、R8、签名、zipalign 和 APK 元数据检查通过。

### 批次 3：`PreferenceFragmentBase` 等价 Kotlin 迁移

状态：已完成，等待实机验证。提交：
`d39f6e8`。

- 保持可继承类、公开无参构造、protected 字段、静态常量和方法 JVM 描述符；
- 保持 Fragment transaction、页面动画、菜单、Dialog、备份恢复和 Java 序列化行为；
- 仅对 `AboutFragment` 做必要的 Kotlin 调用兼容调整；
- 完整验证通过：33 个测试、Lint 0 errors、Debug、Release、R8、资源压缩、签名和 zipalign；
- 最终 Release APK 为 3,021,289 bytes，SHA-256
  `C72A3A2DFDC1D9B3AFCD903D4C36C4CC3CD36FCAC021AB9A70C69A3017E5E2E7`。

### 后续四阶段

1. 阶段 A：正确性、注册与生命周期（已完成静态/构建验证）；
2. 阶段 B：核心 Hook 与热路径（已完成静态/构建验证）；
3. 阶段 C：逻辑、算法、Kotlin 与设置 UI；
4. 阶段 D：清理、全量验证与最终签名测试 APK。

停止条件：P0 清零、明确 P1 已处理、高收益 P2 已处理，剩余仅为低收益 P3、
需要实机数据或风险高于收益的问题；不为清空清单无限优化。

### 进程 / 功能 / Hook 路径

| 进程范围 | 入口和主要路径 | 本轮结论 |
|---|---|---|
| `system_server` / `android` | `MainModule.onSystemServerStarting()`，转入 `System`、`Controls`、`Various`、`GlobalActions` 等 | 仅索引，不修改 |
| `com.android.systemui` | `MainModule.onPackageReady()`，按偏好注册 `SystemUI`、`System`、`Controls` 与状态栏子 Hook | 本轮只修改被 `SystemUIBatteryHooks` 创建的 `BatteryIndicator` 生命周期 |
| `com.miui.home` | 包加载后通过 `Application.attach` 进入 `handleLoadLauncher()` | 仅索引，不修改 |
| MIUI / Android 系统应用 | Settings、SecurityCenter、PowerKeeper、Installer、Screenshot、Gallery、InCallUI 等按 scope 和偏好分派 | 仅索引，不修改 |
| 模块自身进程 | 设置 UI、`PrefsProvider`、Remote Preferences 写入端 | 仅索引，不修改 |

结构化搜索仅作为定位索引，不直接等同于性能问题：活跃源码约有 614 个 Hook 注册引用、
2,425 个反射/兼容辅助引用、46 个 Receiver 注册、22 个 Observer 注册、32 个延迟任务引用和
18 个协程启动引用。候选问题必须再经过局部生命周期和调用链确认。

### 固定基线验证

在本轮创建并随后删除的隔离 detached worktree 中，对精确 commit `0e4c00b` 执行：

```powershell
.\gradlew.bat --no-daemon clean test lint lintRelease lintVitalRelease assembleDebug assembleRelease
```

- 构建成功；33 个单元测试全部通过。
- Release Lint：0 errors、485 warnings；`lintVitalAnalyzeRelease` 完成。
- Debug、Release、R8 和资源压缩均完成。
- 基线 Release APK：3,021,289 bytes；
  SHA-256 `627CCEACC1266AEE1E8D02D4ADC7263C300D04A0766330FE2129A58944F252CB`。

### 批次 1 静态验证

使用同一完整命令重新验证本批修改：

- 构建成功；33 个单元测试全部通过。
- Release Lint：0 errors、487 warnings；Debug Lint：0 errors、485 warnings。
- `lintVitalAnalyzeRelease` 完成；在同一任务图中，冗余的 `lintVitalReportRelease` /
  `lintVitalRelease` 由 Gradle 标记为 `SKIPPED`，总任务仍成功。
- Debug、Release、R8、资源压缩均完成。
- Release APK：`app/build/outputs/apk/release/CustoMIUIzer-A14-r14.10.0.apk`，
  3,021,289 bytes，SHA-256
  `3310C289956E6B2FBA9D09A0C622BA96C27B8778ECA5E50660A11D8962D10417`。
- APK 包名 `tv.withaibuild.customiuizer.r14`、版本 `r14.10.0`、minSdk 34。
- APK 内 Xposed 元数据仍为 min API 101 / target API 102 / `staticScope=false`；
  R8 后入口 `ro` 可由 mapping 追溯到 `MainModule`。
- APK DEX 未发现 `de.robv.android.xposed` 包；v2 签名、证书和 zipalign 检查通过。
- APK 大小相对固定基线变化为 0 bytes；这不作为运行性能结论。

## 候选问题

| 优先级 | 候选 | 证据和后续边界 | 状态 |
|---|---|---|---|
| P0 | 无已确认项 | 当前测试、Lint、Debug、Release 均可执行，无损坏入口或未完成 stub | 无 |
| P1 | `BatteryIndicator` 脱离窗口后仍被 Receiver / Observer 持有 | 局部调用链已确认；影响 SystemUI 内重建后的回调唯一性和对象释放 | 本批已修复 |
| P1 | `System.kt` 截图格式 DexKit 查询可能返回多个方法 | 已按回调真实参数契约筛选并要求唯一结果，不猜 ROM 混淆名称 | A6 已修复，待阶段/实机验证 |
| P1 | `AudioVisualizer` View 生命周期 | 已完成观察器、协程、动画和原生 `Visualizer` 的 dispose 闭环 | A1/A2 已修复，待阶段/实机验证 |
| P2 | `SystemUI.kt` 中若干匿名 Receiver | 截图状态/导航栏和锁屏专辑封面已修复；其余仅在能证明宿主重建泄漏时处理 | A4/A5 已处理高收益项 |
| P3 | `BitmapCachedLoader` Kotlin 空值与协程告警 | 队列已限制为 128、目标使用弱引用、核心线程允许超时；目前没有失控证据 | 暂不修改 |

## 尚未实机验证

- API 101 框架完整冷启动、重启和各作用域 Hook
- API 102 框架完整冷启动、重启和各作用域 Hook
- 开启电池指示器后，状态栏显示、偏好实时更新和测试广播行为
- SystemUI 重建/主题或配置变化后无重复指示器、重复 Receiver/Observer 或崩溃
- 截图期间隐藏状态栏功能与电池指示器组合

## 下一阶段准确入口

阶段 C 聚焦低风险、可证明的逻辑和设置 UI：

1. 从 Kotlin 编译告警和 Lint 中筛选真实空值、恒真/恒假条件与 JVM 互操作问题；
2. 审查 Preference、Fragment、Dialog、搜索和设置同步的重复遍历与主线程工作；
3. 一次只处理 10～20 个强相关文件，不引入 Flow、Sequence、DSL、新架构层或协程；
4. 已稳定的 Hook 主干只做调用方需要的最小调整，不继续风格化重写；
5. 无功能错误或可测收益的纯风格告警降为 P3，不为清零告警无限修改。
