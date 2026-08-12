# ST0 StrongToast Semantic + Geometry Audit

## 1. Authority / Scope

- 阶段：ST0（AUDIT ONLY）。
- 目标：建立 StrongToast 新旧两套实现的完整语义与几何依赖链，冻结 ST0 结论，为后续 ST1/ST2 提供依据。
- 禁止：ST0 不修改生产代码、测试、资源、Preference XML、feature 注册。
- 允许产出：`docs/strong-toast/ST0_STRONG_TOAST_SEMANTIC_GEOMETRY_AUDIT.md`。
- 参考平台：HyperOS 1 / Android 14 / SDK 34。

## 2. Repository / Branch / Base Evidence

- Repository：`tomthenpc/customiuizer-a14`（本地工作区 `C:\Users\tv\Downloads\Peengeek\customiuizer-a14-forDevin`）。
- 目标分支：`devin/a14-strong-toast-geometry-r14.20.0`。
- 基础提交：`85e33243929a9853a0b5a787865c108f35d80959`。
- 分支创建点：从 `85e33243929a9853a0b5a787865c108f35d80959` 直接切出，`git rev-parse HEAD` 与 base 一致。

## 3. StrongToast Feature Inventory

### 3.1 新实现：`StrongToastPresentationFeature`

| 项 | 内容 |
| --- | --- |
| FeatureId | `StrongToastPresentationFeatureId`，id = 246，name = `strong_toast_presentation` (`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/FeatureIds.kt:1028-1031`) |
| Feature 类 | `StrongToastPresentationFeature` (`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt:738-762`) |
| Preference key | `system_strong_toast_mode` (`app/src/main/res/xml/prefs_system.xml:369-375`) |
| UI 位置 | 系统 → 状态栏 → 灵动额头 / HyperOS status capsule |
| 可选值 | `0` SYSTEM_DEFAULT、`1` MATCH_STATUS_BAR_HEIGHT、`2` HIDE (`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StrongToastPresentationMode.kt:9-12`; arrays `app/src/main/res/values/arrays.xml:691-700`) |
| 安装入口 | `SystemUiFeatures.all()` → `LazyFeatureSpec(...)` (`SystemUiFeatures.kt:2293-2301`) → `SystemUiInstaller.install()` (`app/src/main/java/tv/withaibuild/customiuizer/installers/SystemUiInstaller.kt:23-53`) |
| Hook 实现 | `SystemUIStrongToastHooks.install(...)` (`app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt:24-90`) |
| 目标 ROM 类 | `com.android.systemui.toast.MIUIStrongToast` / `com.android.systemui.toast.MIUIStrongToastControl` |
| 测试 | `StrongToastPresentationModeTest` (`app/src/test/java/tv/withaibuild/customiuizer/mods/utils/StrongToastPresentationModeTest.kt`) |

### 3.2 旧实现：`DisableStrongToastFeature`

| 项 | 内容 |
| --- | --- |
| FeatureId | `DisableStrongToastFeatureId`，id = 106，name = `disable_strong_toast` (`FeatureIds.kt:439-442`) |
| Feature 类 | `DisableStrongToastFeature` (`SystemUiFeatures.kt:1174-1191`) |
| Preference keys | `system_notif_disable_strong_toast`（主开关）、`system_notif_disable_strong_toast_always`、`system_notif_disable_strong_toast_dnd` (`app/src/main/res/xml/prefs_system.xml:591-605`) |
| UI 位置 | 系统 → 通知 |
| 安装入口 | `SystemUiFeatures.all()` → `LazyFeatureSpec(...)` (`SystemUiFeatures.kt:2491-2499`) |
| Hook 实现 | `SystemUI.DisableStrongToastHook(lpparam)` (`app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUI.kt:110-126`) |
| 目标 ROM 类 / 方法 | `com.android.systemui.toast.MIUIStrongToastControl.showCustomStrongToast` |

### 3.3 旧实现：`TweakStrongToastHook`（宽度 tweak）

| 项 | 内容 |
| --- | --- |
| Preference key | `system_notif_strong_toast_width` (`app/src/main/res/xml/prefs_system.xml:607-616`，当前被注释） |
| Hook 实现 | `SystemUI.TweakStrongToastHook(lpparam)` (`SystemUI.kt:128-152`) |
| 目标 ROM 类 / 方法 | `com.android.systemui.toast.MIUIStrongToast.showCustomStrongToast`（after hook）、`MIUIStrongToast.getWindowParam()`（after hook，设置 `lp.width`） |
| 调用点 | 当前 base 中无调用点；`SystemUI.kt:128-152` 仅定义未引用；`MainModule.java` 无相关调用 |

## 4. Semantic Ownership Trace

### 4.1 新 `StrongToastPresentationFeature` 调用链

```text
MainModule.onPackageReady
  -> SystemUiBootstrapCoordinator.install
       -> 10s restart guard 之后 -> SystemUiInstaller.install
            -> SystemUiFeatures.all(lpparam, mPrefs)
                 -> StrongToastPresentationFeature 若 enabled
                      -> resolveMode(mPrefs) 读取 system_strong_toast_mode
                      -> installHook()
                           -> SystemUIStrongToastHooks.install
                                MATCH: setThemeValueReplacement(strong_toast_height) + hook MIUIStrongToast.getWindowParam()
                                HIDE:  hook MIUIStrongToastControl.showCustomStrongToast 并 returnAndSkip(null)
```

### 4.2 旧 `DisableStrongToastFeature` 调用链

```text
MainModule.onPackageReady
  -> SystemUiBootstrapCoordinator.install
       -> SystemUiInstaller.install
            -> SystemUiFeatures.all(lpparam, mPrefs)
                 -> DisableStrongToastFeature 若 system_notif_disable_strong_toast == true
                      -> SystemUI.DisableStrongToastHook
                           -> hook MIUIStrongToastControl.showCustomStrongToast
                                before 中读取 system_notif_disable_strong_toast_always / _dnd
                                若 blockToast == true -> returnAndSkip(null)
```

### 4.3 `TweakStrongToastHook` 调用链

无。当前 `MainModule.java` 和 `SystemUiFeatures` 均未调用 `TweakStrongToastHook`。

## 5. Semantic Ownership Audit（A1-A6）

### A1. Notification ownership

判定：`系统 → 通知 → 灵动额头` 这些旧设置在实现语义上**不属于 notification domain**。

依据：
- Hook target：`SystemUI.DisableStrongToastHook` 与 `SystemUIStrongToastHooks.install(HIDE)` 均 hook `com.android.systemui.toast.MIUIStrongToastControl.showCustomStrongToast` (`SystemUI.kt:111`; `SystemUIStrongToastHooks.kt:76-84`)。该类位于 `com.android.systemui.toast` 包，是 SystemUI 顶部胶囊（StrongToast / 灵动额头）的控制器，与通知流水线（`NotificationEntry` / `StatusBarNotification`）无直接代码关联。
- Runtime object：`MIUIStrongToast` / `MIUIStrongToastControl` 负责绘制顶部状态胶囊，而非通知列表项。
- Policy semantics：旧实现虽然放在“通知”设置页，但其运行时策略是“按条件阻止 SystemUI 顶部状态胶囊弹出”。DND 检查 (`ZenModeController.isZenModeOn`) 仅决定拦截时机，并不涉及通知排序、渠道、展开等通知语义。
- 结论：旧 `DisableStrongToastFeature` 的语义所有者是**SystemUI 状态栏 / 顶部状态胶囊**，不是通知子系统。

### A2. Surface equivalence

`DisableStrongToastFeature` 与 `StrongToastPresentationFeature.HIDE` **控制同一个 surface / 调用入口**：

| 维度 | `DisableStrongToastFeature` | `StrongToastPresentationFeature.HIDE` |
| --- | --- | --- |
| Hook class | `com.android.systemui.toast.MIUIStrongToastControl` | 相同 |
| Hook method | `showCustomStrongToast` | 相同 |
| Hook phase | `before` | `before` |
| Short-circuit 行为 | `returnAndSkip(null)` | `returnAndSkip(null)` |
| Return 语义 | 原方法被跳过，Toast 不创建/不显示 | 相同 |
| 条件 | `always` 或 DND 触发时 block | 无条件 block |

差异：
- 旧实现支持 `always` / `dnd` 两种条件；新 `HIDE` 仅无条件隐藏。
- 两者 hook 的是同一 class / method / phase，因此阻止的是**同一条 `showCustomStrongToast` 调用路径**。未发现其它独立入口被两者分别 hook。

### A3. Simultaneous enablement

两套 feature **可同时启用**：

- `StrongToastPresentationFeature` 的 enable 条件为 `system_strong_toast_mode != 0` (`StrongToastPresentationModeTest.kt:27-38`；`SystemUiFeatures.kt:756-757`)。
- `DisableStrongToastFeature` 的 enable 条件为 `system_notif_disable_strong_toast == true` (`SystemUiFeatures.kt:1186-1187`)。
- `FeatureInstallRegistry` 独立安装每个 `FeatureSpec`，不存在按 preference key 的互斥 (`FeatureInstallRegistry.kt:53-69`)。
- 两套 feature 的 preference key 不同，UI 互相独立。

最终 runtime policy 取决于 hook 注册顺序与 libxposed/LSPosed 的 `before` 链分发顺序：
- `SystemUiFeatures.all()` 中 `StrongToastPresentationFeature`（id 246）在列表中位于 `DisableStrongToastFeature`（id 106）之前 (`SystemUiFeatures.kt:2293-2301` vs `2491-2499`)。
- `FeatureInstallRegistry.installAll` 按列表顺序安装 (`FeatureInstallRegistry.kt:62-68`)。
- 若两者均启用，新 `HIDE` 的 `before` hook 大概率先于旧 `Disable` 的 `before` hook 进入链。
- 由于 `returnAndSkip(null)` 会直接设置 `BeforeHookCallback.skipped = true` 并返回结果、不调用 `chain.proceed()` (`HookerClassHelper.kt:73-77` 与 `167-201`)，先执行的 hook 会 short-circuit，后续 `before` 回调可能不会被调用。
- 因此，若新 `HIDE` 注册在前，旧 `Disable` 的 `always` / `dnd` 条件**将不会运行**；若旧 `Disable` 注册在前（例如用户手动或通过其他 installer），旧实现条件决定结果。

### A4. Duplicate hook / policy conflict

两个实现都 hook `MIUIStrongToastControl.showCustomStrongToast`：

- **Duplicate hook 存在**：`SystemUIStrongToastHooks.installHide` 与 `SystemUI.DisableStrongToastHook` 均使用 `ModuleHelper.hookAllMethods(..., "showCustomStrongToast", ...)` (`SystemUIStrongToastHooks.kt:75-84`; `SystemUI.kt:111-125`)。
- **Hook 顺序重要**：如前所述，先注册的 `before` 回调若 `returnAndSkip`，可能直接终止链。
- **一个 hook 可能使另一个失效**：若新 `HIDE` 注册在前，旧 `Disable` 的 DND-only 条件无法执行；若旧 `Disable` 注册在前且条件成立，新 `HIDE` 不会执行。
- **行为交叉 / 重复 policy**：两套 feature 同时启用时，两个 `before` 回调会读取不同 preference，可能产生“重复拦截”或“条件覆盖”。
- **异常风险**：旧 `Disable` 每次调用都反射获取 `ZenModeController.isZenModeOn` (`SystemUI.kt:115-118`)。若新 `HIDE` 先 short-circuit，原方法不会执行，但旧 `before` 仍可能被 libxposed 调用并执行反射；若调用频次高，存在每次 show 都发生一次 `callMethod` 的潜在开销与 ROM 字段/方法缺失风险。

### A5. DND-only policy

`system_notif_disable_strong_toast_dnd` **存在独立保留价值**：

- 旧 `DisableStrongToastHook` 逻辑：`always` 为 false 且 `dnd` 为 true 时，查询 `ZenModeController.isZenModeOn`，仅在 DND 开启时隐藏 StrongToast (`SystemUI.kt:113-122`)。
- 新 `StrongToastPresentationFeature` 仅提供三种模式：SYSTEM_DEFAULT / MATCH / HIDE，**没有 DND-only 模式**。
- 因此 DND-only 隐藏是当前新 feature 无法替代的独立 runtime semantics。
- 若未来移除旧实现，必须将 DND-only 逻辑迁移到新 feature 或明确声明放弃该语义；否则删除会导致行为能力丢失。

### A6. Legacy Width Hook Analysis

`system_notif_strong_toast_width` + `SystemUI.TweakStrongToastHook` 状态：**DEAD_LEGACY**。

依据：
- `app/src/main/res/xml/prefs_system.xml:607-616` 中对应 `SeekBarPreference` 被注释，UI 不存在。
- `SystemUI.TweakStrongToastHook` 仅定义未调用：当前 base 下 `MainModule.java` 无调用，`SystemUiFeatures` 无注册，`grep TweakStrongToastHook\(` 仅命中定义点 (`SystemUI.kt:129`)。
- Hook 仍引用 `MIUIStrongToast.showCustomStrongToast` 与 `getWindowParam()`，并设置 `strong_toast_width_window`、`strong_toast_width` 两个 dimen 和 `mStrongToastBottomView`、`mRLLeft` 字段，但 install path 不存在，因此运行时不会执行。
- 与新 `StrongToastPresentationFeature` 无重叠；新 feature 不修改宽度。

结论：`system_notif_strong_toast_width` 当前是死代码 / 死 preference；仅保留在未来“是否恢复”决策中参考。

## 6. Duplicate Hook / Policy Analysis

| 场景 | `system_strong_toast_mode` | `system_notif_disable_strong_toast` | 运行时效果 |
| --- | --- | --- | --- |
| 默认 | 0 (SYSTEM_DEFAULT) | false | 无 hook，ROM 默认行为。 |
| 仅新 MATCH | 1 | false | 替换 `strong_toast_height` + 修改 `getWindowParam().height`。 |
| 仅新 HIDE | 2 | false | `MIUIStrongToastControl.showCustomStrongToast` 直接 return null。 |
| 仅旧 | 0 | true | `showCustomStrongToast` 按 `always` / `dnd` 条件拦截。 |
| 新 MATCH + 旧 | 1 | true | `getWindowParam().height` 被改；`showCustomStrongToast` 按旧条件拦截（两者 hook 不同方法，无直接冲突）。 |
| 新 HIDE + 旧 | 2 | true | 两套 hook 同方法；注册顺序决定哪个条件生效；DND-only 大概率失效。 |

关键风险：新 HIDE 与旧 `DisableStrongToast` 的 `before` hook 注册在同一方法，先注册者 short-circuit 后后者不执行，导致用户可见策略不一致。

## 7. Legacy Width Hook Analysis

见 A6。附加说明：
- `TweakStrongToastHook` 使用了硬编码字段名 `mStrongToastBottomView`、`mRLLeft` (`SystemUI.kt:136-138`)，这些字段名未在新 `SystemUIStrongToastHooks` 中引用。
- 若未来恢复宽度 tweak，需要重新验证 ROM 字段与资源名在 A14/HyperOS 1 下是否存在。

## 8. ROM Geometry Evidence

- 仓库中**没有** `MIUIStrongToast` / `MIUIStrongToastControl` 的反编译源码、smali、布局 XML 或 drawable 文件。
- `find` 与 `grep` 未在 `docs/rom-intelligence/`、`tools/tests/fixtures/rom-smali/` 或仓库其它位置找到相关 ROM 证据。
- 现有证据全部来自模块自身源码与资源名：
  - `strong_toast_height`（dimen，被 `setThemeValueReplacement` 替换）
  - `strong_toast_width`、`strong_toast_width_window`（被旧 `TweakStrongToastHook` 引用）
  - `MIUIStrongToast.getWindowParam()` 返回 `WindowManager.LayoutParams`
  - `MIUIStrongToastControl.showCustomStrongToast()` 是入口/拦截点
- 因此，对 ROM 内部 `View hierarchy / background / corner radius / animation` 的任何断言均标记为 `UNKNOWN`，除非能从模块代码推导。

## 9. Geometry Layer Model

为便于分离“视觉胶囊高度”与“窗口高度”，将 StrongToast 几何分为五层：

| 层 | 定义 | 当前模块可见性 |
| --- | --- | --- |
| OUTER_WINDOW_GEOMETRY | `MIUIStrongToast.getWindowParam()` 返回的 `WindowManager.LayoutParams`（width/height/gravity/x/y） | 可见，模块直接设置 `lp.height` |
| CAPSULE_ROOT_GEOMETRY | 实际绘制胶囊的 root `View` 的 `LayoutParams` / measured bounds | 不可见，无 ROM 源码 |
| BACKGROUND_GEOMETRY | 胶囊背景 drawable / shape / nine-patch / vector 的 bounds 与绘制行为 | 不可见 |
| CORNER_GEOMETRY | 胶囊圆角半径来源（drawable corner、outline、`ViewOutlineProvider`、计算值等） | 不可见 |
| ANIMATION_GEOMETRY | enter/exit/morph 动画读取/修改的 bounds / scale / translation / corner progress | 不可见 |

## 10. `strong_toast_height` Dependency Graph

### 10.1 直接修改点

模块对 `strong_toast_height` 的直接操作：

1. `setThemeValueReplacement("com.android.systemui", "dimen", "strong_toast_height", statusBarHeightDp)` (`SystemUIStrongToastHooks.kt:44-49`)
   - 实现位于 `ResourceHooks.setThemeValueReplacement()` (`ResourceHooks.kt:442-463`)。
   - 通过 hook `miui.content.res.ThemeResources.mergeThemeValues` 将 `strong_toast_height` 的整数值替换为 `statusBarHeightDp`（已转换为 px 并写入 `mIntegers` HashMap）(`ResourceHooks.kt:225-298`，特别是 `255-280`)。
2. `getWindowParam()` 的 `after` hook 中将 `WindowManager.LayoutParams.height` 设为 `targetHeightPx(statusBarHeightDp, densityDpi)` (`SystemUIStrongToastHooks.kt:51-71`，`87-90`)。

### 10.2 几何层影响矩阵

以“当前 MATCH 实现”为对象，判断 `strong_toast_height` 替换与 `getWindowParam().height` 覆盖分别影响哪些几何层：

| 几何层 | `setThemeValueReplacement("strong_toast_height")` | `getWindowParam().height override` | 说明 |
| --- | --- | --- | --- |
| OUTER_WINDOW_GEOMETRY | **YES** | **YES** | 外层窗口高度。Theme 替换会影响 ROM 初始化 `LayoutParams` 时的默认值；`after` hook 再显式覆盖为 `targetHeightPx`。 |
| CAPSULE_ROOT_GEOMETRY | **INDIRECT** | **INDIRECT** | 胶囊 root View 的高度很可能由 `strong_toast_height` 读取而来（资源名暗示），但无 ROM 代码证明；`getWindowParam().height` 仅当 root 使用 `match_parent` / 填充窗口时间接影响。 |
| BACKGROUND_GEOMETRY | **INDIRECT** | **INDIRECT** | 背景 bounds 跟随 root View bounds；若背景是 shape/drawable 且直接引用 `strong_toast_height`，则资源替换会进一步影响。无 ROM 证据。 |
| CORNER_GEOMETRY | **UNKNOWN** | **NO** | 圆角可能来自 shape corner radius 或 `ViewOutlineProvider`。若 corner radius 也读取 `strong_toast_height`，替换会改变；否则 `lp.height` 不直接改变圆角。 |
| ANIMATION_GEOMETRY | **UNKNOWN** | **NO** | 动画可能读取 root 高度 / 窗口高度做 scale/translation。`lp.height` 不直接修改动画；但资源替换改变的整体 bounds 可能被动画读取。 |

关键发现：模块通过“全局 dimen 替换 + 显式 window height 覆盖”两条路径同时推高高度。若 ROM 把同一个 `strong_toast_height` 用于 CAPSULE_ROOT / BACKGROUND / CORNER 等多层，则会出现视觉上的“整体等比放大/圆角变大/边缘失真”。当前静态证据足以支撑这一**结构性怀疑**，但缺少 ROM 源码无法完成因果闭环。

## 11. Current MATCH Implementation Analysis

`SystemUIStrongToastHooks.installHeightMatch` 当前行为（`SystemUIStrongToastHooks.kt:43-72`）：

1. 调用 `MainModule.resHooks.setThemeValueReplacement(..., "strong_toast_height", statusBarHeightDp)`。
   - 该调用是进程级 ThemeResources 替换，**热路径上所有读取 `strong_toast_height` 的地方都会拿到新值**。
2. 调用 `ModuleHelper.findAndHookMethod(..., "getWindowParam", ..., after hook)`。
   - `after` 中从 `callback.getResult()` 取出 `WindowManager.LayoutParams`。
   - 从 `thisObject`（`MIUIStrongToast`，视为 `View`）读取 `resources.displayMetrics.densityDpi`。
   - 计算 `targetHeightPx = StatusBarHeightConfig.dpToPx(statusBarHeightDp, densityDpi)` (`StatusBarHeightConfig.kt:116-118`)。
   - 将 `layoutParams.height` 设为 `targetHeightPx`。
3. `getWindowParam()` 的 `after` hook 中未修改 `lp.width`、`lp.x`、`lp.y`、`lp.gravity`、`scaleX`、`scaleY`。

`MATCH_STATUS_BAR_HEIGHT` 模式的当前实现：
- 依赖 `StatusBarHeightConfig.resolveHeightDp(mPrefs)` 读取 `system_statusbarheight` (`StatusBarHeightConfig.kt:133-136`)。
- 当 `system_statusbarheight` 未设置时，返回 `DEFAULT_DP = 27`；用户设值后返回该 dp 值（范围 11-80）。
- 目标视觉高度 = 用户设置的状态栏高度。

当前实现的副作用（从代码与视觉观察推导）：
- 资源替换会一次性影响 ROM 中所有 `strong_toast_height` 消费者，不能精确区分“窗口高度”与“胶囊内容高度”。
- 显式 `lp.height` 覆盖仅能控制 outer window，对 ROM 内部 root View / background / corner 是否同步放大无直接约束。
- `getWindowParam()` after hook 每次调用都反射获取 `thisObject.resources.displayMetrics`，在每次显示 StrongToast 时执行一次（虽然量级低，但非 install-time resolve）。

## 12. Device Visual Observation

来自任务描述与项目当前记录：

- `MATCH_STATUS_BAR_HEIGHT` 并非简单纵向增高。
- 视觉表现：
  - 左右圆角明显变大
  - capsule 边缘失真
  - 部分边缘像 clipping
  - 整体比例发生变化
- 这些现象指向 **CAPSULE_ROOT_GEOMETRY / BACKGROUND_GEOMETRY / CORNER_GEOMETRY 被同一资源驱动放大**，而非仅 OUTER_WINDOW_GEOMETRY 增高。

## 13. Desired Geometry Contract

`MATCH_STATUS_BAR_HEIGHT` 的成功标准：

```text
target visual capsule height = configured status-bar height
width  = ROM stock width
horizontal position = ROM stock
scaleX = 1.0
scaleY = 1.0
corner geometry 不得随目标高度无限制同比放大
```

明确拒绝两种失败形态：

- **Invalid A**：只把 outer window 变高，capsule visual height 保持 stock。
- **Invalid B**：整体等比缩放（height↑、width↑、radius↑、icon↑、content↑）。

正确目标：**仅垂直几何适配，不整体缩放胶囊**。

## 14. Candidate Fix Directions

### Candidate 1：继续替换 `strong_toast_height`

- 优点：最小代码改动；一次资源替换即可让 ROM 所有相关消费者读取新高度。
- 风险：无法精确控制哪一层消费该 dimen；已知会导致 CAPSULE_ROOT / BACKGROUND / CORNER 等层同步变化，产生视觉缩放/失真。
- 已知副作用：圆角变大、边缘失真、clipping。
- 精确性：低，无法区分 WINDOW HEIGHT 与 VISUAL CAPSULE HEIGHT。

### Candidate 2：停止替换 `strong_toast_height`，仅修改 `WindowManager.LayoutParams.height`

- 操作：移除 `setThemeValueReplacement`，保留 `getWindowParam()` after hook 的 `lp.height = targetHeightPx`。
- 优点：最小 hook surface；不污染 ROM 资源系统；热路径仅一次 after hook；最容易验证（只需测量 window bounds）。
- 风险：如果 ROM 内部 capsule root View 仍从 `strong_toast_height` 读取高度，则会出现 **“window 高了，capsule 没高”**（Invalid A）。
- 精确性：中；可用于快速诊断资源替换是否为视觉缩放主因，但可能不满足最终 contract。

### Candidate 3：精确修改 capsule root `LayoutParams.height`

- 操作：在 `MIUIStrongToast.showCustomStrongToast()` 或 `getWindowParam()` 之后，定位到实际渲染胶囊的 root `View`，设置其 `LayoutParams.height` 为目标高度，同时保持 `width`、`scaleX`、`scaleY`、水平位置不变。
- 优点：直接作用于 VISUAL CAPSULE HEIGHT，最符合 contract；不依赖全局 dimen。
- 风险：需要知道 root View 的字段名或 ID；ROM 字段可能在不同 HyperOS 版本变化；需要额外 hook/反射。
- 精确性：高（如果 root 可稳定定位）。

### Candidate 4：仅扩展背景的垂直中间区域

- 操作：假设背景是可拉伸 nine-patch / shape，只让 middle 区域纵向拉伸，保持上下 cap 与圆角。
- 优点：最自然实现“高度变、圆角不变”。
- 风险：需要 ROM 背景 drawable 类型与资源名证据；当前仓库无任何 ROM drawable 信息。
- 精确性：高（前提是可拉伸背景且圆角独立）。

### Candidate 5：保持或限制 corner radius

- 操作：若 corner radius 是独立 dimen，保持 stock radius 或引入 `min(stock-derived radius, targetHeight / 2)`。
- 优点：直接解决“圆角随高度变大”问题。
- 风险：需要知道 corner radius 来源；若半径来自 `strong_toast_height` 或 `height / 2`，单独替换不现实。
- 精确性：中（依赖 ROM 圆角实现）。

### Candidate 6：同步 animation bounds / layout bounds

- 操作：调整 enter/exit 动画的 bounds / layout bounds，但不使用 `scaleX/scaleY` 或 whole-view matrix。
- 优点：避免动画期间出现比例失调或 clipping。
- 风险：需要 ROM 动画实现细节；可能涉及 ValueAnimator/ObjectAnimator 的 field/arg 修改。
- 精确性：中（依赖动画实现）。

## 15. Preferred / Fallback / Rejected Direction

### PREFERRED DIRECTION

**Candidate 2（停止 `strong_toast_height` 全局替换，仅保留 `getWindowParam().height` 显式覆盖）作为下一步诊断/验证步骤。**

理由：
- 当前静态证据下最小、最安全。
- 只需删除/注释一行 `setThemeValueReplacement`，hook surface 最小。
- 可立即通过 runtime dump 区分“视觉缩放是否由资源替换引起”。
- 若验证显示仅 window 变高而 capsule 未变高，则资源替换是视觉放大的主因，再转向 Candidate 3；若仅 window 变高已满足 contract，则问题定位完成。

注意：Candidate 2 **本身不一定是最终修复**，因为存在 Invalid A 风险；但它是当前证据下最有价值的下一步。

### FALLBACK DIRECTION

**Candidate 3（定位 capsule root View 并直接设置其 `LayoutParams.height`）。**

理由：最符合“只改变胶囊视觉高度、不整体缩放”的 contract。前提是：
1. 获取 `MIUIStrongToast` 的 ROM 源码或 smali，确认 root View 的字段名 / layout ID。
2. 通过 runtime View dump 确认修改 root height 不会导致背景/圆角自动等比放大。

### REJECTED DIRECTIONS

- **Candidate 1（继续 `strong_toast_height` 全局替换）**：当前视觉缩放已证明（至少结构性地）该 dimen 控制多个几何层，继续此方向无法避免 Invalid B。
- **Candidate 4 / 5 / 6**：均依赖仓库中不存在的 ROM drawable / corner / animation 证据；在拿到 ROM 源码前无法评估其可行性，因此当前阶段不选为首选或 fallback，但保留在 ROM 证据补齐后复评。

## 16. Lifecycle / Concurrency / Retention / Cost

### 16.1 生命周期

- `StrongToastPresentationFeature` 与 `DisableStrongToastFeature` 均在 `PACKAGE_READY` 阶段安装一次 (`InstallPhase.PACKAGE_READY`)。
- `FeatureInstallState` 保证每个 `FeatureId` 在同一进程仅安装一次 (`FeatureInstallRegistry.kt:77-80`)。
- `StrongToastPresentationFeature` 没有注册 `PreferenceObserver`；preference 变化后需要 SystemUI 重启生效（与 strings 提示一致：`A full reboot is required`）。
- `SystemUIStrongToastHooks` 不持有 `Activity`、`View`、`Window` 或 `Context` 的长期引用；hook 回调内使用 `callback.getThisObject()` 的局部 View 引用获取 density。
- `DisableStrongToastHook` 每次 show 都通过 `ModuleHelper.getDepInstance()` 获取 `ZenModeController` (`SystemUI.kt:115-117`)。`ReflectionCache` 对结果做 ClassLoader 级缓存，但 `callMethod(...)` 仍会在每次被调用时执行一次 Binder/同步调用（`isZenModeOn` 可能触发同步 Binder）。这属于每次 toast 显示的 hot path 开销。

### 16.2 并发

- 两套 feature 的 hook 注册在 `SystemUiInstaller` 的单线程安装路径完成 (`SystemUiInstaller.kt:23-53`)，并发风险低。
- 运行时 `before`/`after` 回调跑在 ROM 调用线程（SystemUI 主线程）。`SystemUIStrongToastHooks` 的 `after` hook 中仅做赋值与异常捕获；`DisableStrongToastHook` 的 `before` 做两次 `mPrefs` 读取与一次 `callMethod`。

### 16.3 性能 / 热路径

- `getWindowParam()` `after` hook 每次 StrongToast 显示都会执行：
  - 一次 `callback.getResult()`。
  - 一次 `callback.getThisObject()` 与 `resources.displayMetrics.densityDpi` 读取（反射/字段访问）。
  - 一次 `StatusBarHeightConfig.dpToPx`。
  - 一次 `layoutParams.height` 赋值。
- `setThemeValueReplacement` 在安装期替换一次 ThemeResources，之后所有 `Resources`/`Theme` 读取 `strong_toast_height` 时通过已安装的 `Resources` hook 拦截。热路径为一次 `SparseArray` 查找与一次 HashMap 查找 (`ResourceHooks.kt:155` 与 `ResourceHooks.kt:172`)。
- 若 `DisableStrongToastHook` 与 `HIDE` 同时启用且注册顺序不利，旧 `Disable` 的 `before` 仍可能被 libxposed 调用，导致每次 show 都发生一次 `ZenModeController` 反射/同步调用，存在重复开销。

### 16.4 失败语义

- 若 `MIUIStrongToast` 或 `MIUIStrongToastControl` 类/方法在 ROM 中不存在，`ModuleHelper.hookAllMethods` / `findAndHookMethod` 会记录一次 TARGET_CLASS_MISSING / TARGET_MEMBER_MISSING 并返回，不会抛异常 (`ModuleHelper.kt:300-352`)。
- `SystemUIStrongToastHooks` `after` hook 用 try/catch 包裹，`OutOfMemoryError` 重抛，其它异常记录日志 (`SystemUIStrongToastHooks.kt:65-68`)。

## 17. Runtime Evidence Contract

ST0 不进行实机测试，但为后续 ST2/ST3 定义必须采集的 runtime evidence：

### 17.1 模式矩阵

对以下模式/参数组合各至少采集 3 组：

- `SYSTEM_DEFAULT`
- `MATCH_STATUS_BAR_HEIGHT`（stock 高度 / medium 增加 / large 增加）
- `HIDE`

### 17.2 每组必须记录

| 项 | 推荐采集方式 |
| --- | --- |
| window bounds px | `WindowManager.LayoutParams` dump / `View.getWindowToken()` + `getLocationOnScreen` |
| capsule visual height | 屏幕截图测量 / `root.getHeight()` |
| capsule width | `root.getWidth()` / 截图 |
| left / right / top / bottom bound | `root.getLocationOnScreen()` + width/height |
| corner appearance | 截图 + 主观记录 |
| clipping | 截图 + 检查是否与状态栏/凹口重叠 |

### 17.3 必须触发场景

- 充电 StrongToast
- 系统模式 StrongToast（如勿扰、省电、飞行模式切换）
- enter animation
- exit animation

### 17.4 推荐 View dump 字段

如果可通过运行时 dump 获取 View 树：

```text
window bounds px
root bounds px
background bounds px
measuredWidth
measuredHeight
layout params
scaleX
scaleY
translationX / translationY
```

### 17.5 关键比较项

- `SYSTEM_DEFAULT` vs `MATCH` 的 window bounds 与 root bounds 差异。
- `MATCH` 下 `WindowManager.LayoutParams.height` 是否等于 `StatusBarHeightConfig.configuredPx`。
- `MATCH` 下 `scaleX/scaleY` 是否仍为 1.0。
- `MATCH` 下 corner radius / background bounds 是否随高度同比变化。

### 17.6 ROM / 反编译证据补充

为完成几何因果链，建议 ST1 补充：

- HyperOS 1 A14 `com.android.systemui` apk 反编译：
  - `MIUIStrongToast` 类完整字段与方法。
  - `MIUIStrongToastControl` 类完整字段与方法。
  - `getWindowParam()` 实现及 `strong_toast_height` / `strong_toast_width` / `strong_toast_width_window` 的读取点。
  - 胶囊 root View 的字段名与 `LayoutParams` 来源。
  - 背景 drawable 类型（nine-patch / shape / vector）与圆角计算。
  - enter/exit 动画实现及其读取的 bounds。

## 18. Frozen ST0 Conclusions

```text
ST0_RESULT = HOLD

NOTIFICATION_STRONG_TOAST = KEEP_SEPARATE_JUSTIFIED

MATCH_HEIGHT_ROOT_CAUSE = STRUCTURAL_SUSPECT

MATCH_HEIGHT_FIX_DIRECTION = NONE

PRODUCTION_CHANGE = NO
TEST_CHANGE = NO

ST1_AUTHORIZATION = NO
ST2_AUTHORIZATION = NO
```

### 18.1 结论说明

- **NOTIFICATION_STRONG_TOAST = KEEP_SEPARATE_JUSTIFIED**：旧 `DisableStrongToastFeature` 的 DND-only 语义（`system_notif_disable_strong_toast_dnd`）是当前新 `StrongToastPresentationFeature` 未提供的独立能力；在将该语义迁移或明确放弃之前，不能安全移除旧实现。同时旧实现与 UI 位置（通知页）虽然错配，但这不是删除的充分条件；保留的核心理由是 DND-only 无法替代。
- **MATCH_HEIGHT_ROOT_CAUSE = STRUCTURAL_SUSPECT**：当前 `strong_toast_height` 通过 `setThemeValueReplacement` 在 ThemeResources 层被替换，是一个单一 dimen 可能被 ROM 的 OUTER_WINDOW / CAPSULE_ROOT / BACKGROUND / CORNER 等多层同时消费；`getWindowParam().height` 显式覆盖只能控制 OUTER_WINDOW。视觉上的“整体比例变化 / 圆角变大 / 边缘失真”与资源替换的结构化副作用一致，但仓库缺少 ROM 源码无法完成最终因果闭环。
- **MATCH_HEIGHT_FIX_DIRECTION = NONE**：候选方向中，Candidate 2 是当前最安全的诊断步骤，但不足以作为最终修复；Candidate 3/4/5/6 均依赖 ROM 源码或运行时 View dump 证据。在拿到这些证据前，不应硬选实现方向。

### 18.2 最小 Blocker

1. 缺少 `MIUIStrongToast` / `MIUIStrongToastControl` 的 ROM 反编译源码 / smali，无法确认 `strong_toast_height` 在 ROM 中的真实消费者。
2. 缺少运行时 View dump 证据，无法区分 `strong_toast_height` 替换 vs `getWindowParam().height` 覆盖各自的几何后果。
3. 在决定修复方向前，需先通过 Candidate 2 的 runtime 验证确认资源替换是否为视觉缩放主因。

---

*ST0 审计文档，仅用于独立审计与 ST1 输入，不包含任何生产代码改动。*
