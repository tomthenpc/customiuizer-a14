# CustoMIUIzer A14 架构审计（r14.13 线）

> 基线：`devin/r14.13-kotlin-refactor`，审计起点 HEAD `58b21260`。
> 本文件是**当前真实架构 + 问题清单 + 目标架构**，结论以源码、Git 历史和构建结果为准，不复述历史文档。
> 每条结论标注证据强度：`已验证`（构建/测试/Git 可复现）、`代码确认`（静态可判定）、`待实机`。

## 1. 审计方法

1. 从 `META-INF/xposed` 元数据出发还原真实加载链，而不是从文档反推。
2. 用 Git 对比 Java 原版与当前 Kotlin 版，机械检查控制流漂移（`break`/`continue` 计数差异），再逐条人工判定 switch/loop。
   - 命令基线：`5fd111e0^`（mods Kotlin 迁移前）、`6bb2644d^`（utils Kotlin 迁移前）。
3. 按 `调用频率 × 单次成本 × 目标进程数 × 进程存活时间` 排序热路径，只处理长生命周期进程（system_server、SystemUI、Launcher）中会被放大的成本。
4. 只实施能由构建、测试或明确机制推导验证的修改；无法验证的改动写入"候选项"而不落地。

## 2. 当前架构地图

### 2.1 模块加载链

```
LSPosed
  └─ META-INF/xposed/module.prop      minApiVersion=101 / targetApiVersion=102 / staticScope=false
  └─ META-INF/xposed/scope.list       12 个默认作用域包（用户可在 LSPosed 中增删）
  └─ META-INF/xposed/java_init.list   tv.withaibuild.customiuizer.MainModule
        └─ MainModule extends io.github.libxposed.api.XposedModule
             ├─ onModuleLoaded()          记录 processName，设置 XposedHelpers.moduleInst
             ├─ onSystemServerStarting()  Android 版本闸门 → initPrefs() → PackagePermissions → 按开关注册 hook → watchPreferenceChange()
             └─ onPackageReady()          Android 版本闸门 → isFirstPackage 闸门 → 进程排除表 → 按包名分支注册 hook
```

- 版本闸门：`isSupportedAndroidVersion()` 只放行 `SDK_INT == 34`，其余仅打印一次日志后完全退出。
- 进程排除表（`onPackageReady` 前段）：`com.android.settings` 非主进程、`com.miui.securitycenter.bootaware`、`com.android.location.fused`、`com.android.networkstack*` 直接返回。
- Launcher 与"按应用生效"的功能（状态栏配色、禁用过度滚动、音量控制媒体）统一延后到 `Application.attach` after-hook 里注册，避免在 `onPackageReady` 阶段接触尚未就绪的应用类。
- SystemUI 状态栏初始化挂在 `com.android.systemui.SystemUIInitializer#init`，用回调内 `isHooked` 标志一次化。
- SystemUI 分支额外有一条 10 秒防抖：`Settings.System.systemui_restart_time` 距今 <10s 时只装状态栏相关 hook，其余全部跳过。

### 2.2 配置链

```
设置应用 (MainActivity/MainFragment/subs/*)
  └─ SharedPreferences (customiuizer_prefs)
       └─ PrefsProvider（RemotePreferences 提供方）
            └─ MainModule.getRemotePreferences("customiuizer_prefs_remote")
                 ├─ initPrefs()            一次性 getAll() 快照 → MainModule.mPrefs (PrefMap)
                 └─ watchPreferenceChange() OnSharedPreferenceChangeListener 增量更新快照
                      └─ ModuleHelper.handlePreferenceChanged(key)
                           └─ CopyOnWriteArraySet<PreferenceObserver>
```

- `PrefMap extends ConcurrentHashMap<String, Any>`：写入时把 `pref_key_` 前缀归一化掉，因此 hook 回调侧读的是短键，读取为 O(1) 且不产生字符串拼接。
- `getStringAsInt` 带一层 `parsedIntCache`（原 Java 实现即如此，非迁移引入）。
- Locale 属于纯设置应用状态，不进入 RemotePreferences。
- **单一数据源成立**：hook 侧没有第二份配置副本，也没有轮询。

### 2.3 反射与 Hook 基础设施

- `mods/utils/XposedHelpers.java`：LSPosed 派生，保留 Java。类/字段/方法/构造器四张 `ConcurrentHashMap` 缓存，键为结构化对象（避免字符串拼接）。
- `mods/utils/HookerClassHelper.java`：把 libxposed `XposedInterface.Hooker` 适配为 `MethodHook{before/after/intercept}`。
- `mods/utils/ModuleHelper.kt`：hook 注册的统一容错入口（失败只记一条日志并返回 null），外加 Context/资源/进程工具。
- DexKit 仅用于 `com.miui.guardprovider` 与 `com.miui.screenshot` 两个包，且是"用完即 `closeBridge()`"的短生命周期用法。

### 2.4 资源链

```
ResourceHooks（MainModule.resHooks，进程内单例）
  ├─ addFakeResource / setResReplacement / setObjectReplacement
  │     └─ applyHooks(type) 按需 hook Resources.getText/getString/getLayout/getDrawableForDensity
  │          └─ mReplaceHook.intercept()  ← 该进程**每一次**资源读取都会经过
  └─ setThemeValueReplacement
        └─ hook miui.content.res.ThemeResources#mergeThemeValues（只在包名匹配时合并）
```

- 资源 hook 是**按需安装**的：没有任何 string/layout/drawable 替换注册时，`Resources` 上不挂 hook。
- 一旦安装，`mReplaceHook` 就是全进程最热的模块代码，其单次成本直接乘以进程资源读取次数。

### 2.5 生命周期与所有权

- 长生命周期静态状态集中在：`MainModule.mPrefs`、`MainModule.resHooks`、`ModuleHelper.mCachedContext/mModuleContext/cachedModuleRes`、`XposedHelpers` 四张缓存、`SystemUI` 的图标注册表。
- 设置应用侧（`subs/BTList`、`subs/WiFiList`、`subs/AppSelector`）的广播接收器都有成对 `register/unregister` 与 `scope.cancel()`。
- SystemUI 侧 hook 内注册的接收器多数绑定在被 hook 对象上，并用 `additional instance field` 做旧实例反注册。

### 2.6 构建链

```
JDK 17 → Gradle 9.6.1 → AGP 9.2.1（内置 Kotlin 支持）→ Kotlin 2.3.21
compileSdk/buildTools 37 · minSdk/targetSdk 34 · abi arm64-v8a
compileOnly: lib/framework.jar + libxposed api 102
implementation: libxposed service 102 / dexkit 2.2.0 / androidx / coroutines / commons-lang3
release: R8 + resource shrink + localeFilters(9 语言) + 正式 v2 签名（外部 ../keystore.properties，缺失即 GradleException）
packaging: merges META-INF/xposed/*
```

## 3. 问题清单（按实际影响排序）

| # | 影响 | 位置 | 说明 | 状态 |
| --- | --- | --- | --- | --- |
| P1 | SystemUI 内存泄漏 + 无效工作 | `mods/SystemUI.kt` `mStatusbarTextIcons`、`mods/SystemUIMonitorAndTileHooks.kt` | 静态 `ArrayList<View>` 只增不减。`MiuiPhoneStatusBarView#onFinishInflate` 与 `IconManager#addHolder` 在主题/密度/显示/折叠变化时会重新创建图标，旧 View 及其 Context 被永久持有；2 秒监控回调还会遍历这些已分离的 View 做反射调用。 | **本轮已修复** |
| P2 | 全进程资源读取热路径 | `mods/utils/ResourceHooks.kt` `mReplaceHook` | 每次 `Resources.getString/getText/...` 都会：① 先取 `chain.executable.name`（JNI）再判断是否命中；② 用 `ConcurrentHashMap<Int, _>` 查表，资源 id 恒在 Integer 缓存范围外，**每次读取装箱一个 Integer**。未命中是绝对多数情况。 | **本轮已修复** |
| P3 | 迁移语义回归 + 周期性 I/O | `mods/utils/ModuleHelper.kt` `getCPUThermalId()` | Java 原版命中后 `break`；Kotlin 迁移把读取放进 `use{}` lambda 后**丢掉了 break**，导致：① 取到的是最后一个匹配的 thermal zone 而非第一个；② 每次调用固定打开 19 个 sysfs 文件；③ 无匹配时 `thermalId` 保持 -1，SystemUI 的 2 秒 tick 会**永久重复整轮扫描**。 | **本轮已修复** |
| P4 | 每次调用编译正则 | `utils/Helpers.kt`、`mods/System.kt`、`mods/SystemUI.kt`、`mods/GlobalActionsIntentHelper.kt` | Java 的 `String.split("\\|")` 走单字符快路径不编译 Pattern；Kotlin 迁移写成 `split("\\|".toRegex())`，每次调用都 `Pattern.compile` + `Matcher`。`Helpers.containsStringPair` 在 BT/WiFi 列表适配器的 `isEnabled` 与锁屏可信网络判断里被逐行调用。 | **本轮已修复** |
| P5 | 失败类探测重复反射 | `mods/utils/XposedHelpers.java` `getApplicationClassLoader` | 类查找未命中时回退到 `ActivityThread.currentApplication().getClassLoader()`，而未命中会被负缓存，回退路径却**每次都重做** `Class.forName` + 两次反射调用。ROM 差异导致的合法未命中会持续付费。 | **本轮已修复** |
| P6 | 数据竞争（潜在） | `mods/utils/ResourceHooks.kt` | `fakes: SparseIntArray` 与替换表原先在注册线程写、在任意 UI 线程读，`SparseIntArray` 非线程安全且无安全发布。 | **本轮已修复**（copy-on-write + volatile） |
| P7 | 构建配置过期 | `gradle.properties` | `org.gradle.unsafe.configuration-cache` 为已废弃属性名；`android.enableResourceOptimizations` 在 AGP 8+ 已移除。 | **本轮已修复** |
| P8 | 息屏仍以 2s 周期唤醒 | `mods/SystemUIMonitorAndTileHooks.kt` `MonitorDeviceInfoHook` | 息屏时回调内不做任何工作，但仍固定 2 秒重投消息。可改为息屏退避 + `ACTION_SCREEN_ON` 立即恢复，但会引入新的接收器与生命周期，且亮屏后首次刷新延迟属于用户可见行为。 | **候选项，需实机验证后再做** |
| P9 | 单文件职责过载 | `mods/System.kt` 4401 行 / `mods/SystemUI.kt` 3675 行 / `mods/Launcher.kt` 2678 行 | 影响可维护性，不影响运行时（Kotlin `object` 的方法不会因为同文件而额外加载）。拆分收益是人的收益，风险是 hook 注册顺序与 R8 可达性变化。 | **不在本轮实施**，见 §5 |
| P10 | `initPrefs()` 空快照会被永久固化 | `MainModule.java` | `mPrefsLoaded` 在 `getAll()` 返回空时同样置 true，若 system_server 启动早于 provider 可用则该进程整轮运行在空配置上（仅打印 `Empty preferences!`）。此为上游既有行为。 | **仅记录**，改动需实机覆盖直到解锁前的启动窗口 |

## 4. 本轮实施的修改

全部改动都保持 hook target、注册顺序、before/after 语义、`Chain.proceed()` 次数与 R8 keep 规则不变。

1. **`mods/SystemUI.kt` / `mods/SystemUIMonitorAndTileHooks.kt`**
   - `mStatusbarTextIcons: ArrayList<View>` → 私有 `ArrayList<WeakReference<View>>` + `registerStatusbarTextIcon()` / `updateStatusbarTextIcons()`。
   - 注册与更新时清理已回收条目；更新逻辑从监控 Handler 移到注册表所有者内部，调用方不再持有原始列表。
   - 匹配条件由 `tagData as Int` 改为 `getTag(...) != iconType` 的等值比较，语义相同且不会因异常 tag 抛 `ClassCastException`。

2. **`mods/utils/ResourceHooks.kt`**
   - `resourceIdReplacements` 由 `ConcurrentHashMap<Int, ResourceValue>` 改为 `@Volatile SparseArray`，`fakes` 由可变 `SparseIntArray` 改为 `@Volatile` copy-on-write，写入在私有锁下 `clone()` 后整体替换。
   - `chain.executable.name` 推迟到确认命中之后才取。
   - `chain.proceed()` 仍在 `try/catch` 之外，避免异常路径二次执行原方法。
   - 删除未被调用的 `getModuleResValue(..., Array<Any?>)` 重载。

3. **`mods/utils/ModuleHelper.kt`**
   - `getCPUThermalId()` 恢复"命中即停"（与 `6bb2644d^` 的 Java 原版一致），并增加 `thermalIdScanned` 使失败扫描只发生一次。

4. **`utils/Helpers.kt` 等 4 个文件**
   - 新增 `Helpers.PAIR_DELIMITER = '|'`，`split("\\|".toRegex())` → 字面量分隔符。
   - `containsStringPair` 改为 `indexOf` + `regionMatches`，命中判断零分配。
   - 语义等价：Kotlin 的 `split(Regex)` 与 `split(字面量)` 都保留尾部空串，两者行为一致。

5. **`mods/utils/XposedHelpers.java`**
   - `getApplicationClassLoader` 结果进程内记忆化（只缓存非 null，避免应用对象尚未创建时把 null 固化）。

6. **`gradle.properties`**
   - `org.gradle.unsafe.configuration-cache` → `org.gradle.configuration-cache`；移除 `android.enableResourceOptimizations`；启用 `org.gradle.caching`。

## 5. 目标架构与后续阶段

### 5.1 保持不变的边界（复核后确认）

- `MainModule.java`、`XposedHelpers.java`、`MemberUtilsX.java` 继续保留 Java。理由不是"历史结论"，而是：它们分别是 libxposed 入口（类名写死在 `java_init.list`，且 R8 `-adaptresourcefilecontents` 依赖其可达性）、LSPosed 上游派生代码（需要保持可与上游比对）、commons-lang3 派生代码。
- `MainModule` 里"按开关注册"的巨型分支不改成表驱动/注解扫描：当前形式是**零抽象、零反射、关闭即零成本**，任何注册表抽象都会引入初始化期的对象与查表成本，与"俄式系统代码"方向相反。

### 5.2 建议的下一阶段（按收益/风险排序）

1. **P8 息屏退避**：需要实机确认亮屏后首帧刷新延迟可接受，再实施。
2. **`mods/System.kt` 按功能域垂直拆分**：只有在具备实机回归能力时才做，且必须一次只搬一个功能域，保持 hook 注册顺序，配合 Release R8 产物 diff 验证方法数与入口可达性。
3. **配置类型化**：把高频 `mPrefs.getBoolean("...")` 字符串键收敛为常量或值类，收益是编译期防错，代价接近零；但改动面覆盖全部 mods，建议在拆分之前单独成阶段。
4. **实机可测量的性能基线**：当前所有性能结论都是机制推导，缺少同条件 systrace/Profiler。建议先建立"SystemUI 冷启动 + 状态栏 1 分钟"的固定采样脚本，再谈进一步优化。

## 6. Kotlin 化评估

- 当前 Kotlin/Java 比例不是目标，本轮不做新的迁移。
- 本轮找到的三类**迁移引入的实际退化**都已修复：控制流丢失（P3）、隐藏正则编译（P4）、以及由 Kotlin 写法掩盖的装箱（P2 中的 `ConcurrentHashMap<Int, _>`）。
- 审计方法本身值得固化：`git show <迁移commit>^:<file>.java` 与当前 Kotlin 做控制流关键字计数比对，可在后续任何迁移阶段复用（见 §7）。
- 结论：**Kotlin 收益真实存在（空安全、资源释放、分支完整性），但机械翻译会同时引入不可见的运行时成本**，后续迁移必须附带"原 Java 控制流 diff"这一步。

## 7. 可复用的审计手法

```powershell
# 找出某次 Kotlin 迁移提交前的 Java 原版，与当前 Kotlin 做控制流关键字计数比对
git show "<migration_commit>^:app/src/main/java/<path>.java" |
  Select-String -Pattern '\bbreak\b|\bcontinue\b' -Context 6,1
```

差异出现时逐条判定：Java `switch` 里的 `break` 迁移到 `when` 后消失属正常；**循环体内的 `break` 消失一律视为回归**，因为 Kotlin 无法在 lambda（`use{}`、`forEach{}`）中做非局部跳出。

## 8. 上游关系

- 产品设计与早期实现血统：Mikanoshi。
- Android 14 / HyperOS 1 功能集合、Hook target、preference key、ROM 兼容分支：`MonwF/customiuizer v24.10.12`。
- 本轮修复的 P3 是**回到上游 Java 语义**（首个匹配的 thermal zone），P4 是**回到上游 `String.split` 的非正则快路径**，二者都不是偏离上游，而是修正迁移偏移。
- 构建体系已完全独立于上游（Kotlin DSL / Version Catalog / AGP 9 / libxposed 102 / 独立签名与版本线）。

## 9. 验证与限制

- 见 `docs/DEVIN_A14_CHECKPOINT.md` 的"最新绿色验证"小节获取本轮构建证据。
- 全部性能结论为**机制推导 + 构建验证**，没有同条件实机测量。
- P1/P2 的收益需要实机长时间运行（多次主题/密度切换后观察 SystemUI 内存）才能量化。
- P3 的行为变化（取第一个而非最后一个 cpu thermal zone）在多 CPU thermal zone 机型上属**用户可见变化**，需实机确认状态栏温度读数合理。
