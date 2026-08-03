# A14 Status Bar / WindowInsets 一致性审计

## 1. 当前实现诊断

### 1.1 配置入口

- **Preference key:** `pref_key_system_statusbarheight`
- **默认值:** `11`
- **sentinel 11 映射为 27**
- **设置范围:** `11..80` dp
- **设置类型:** `SeekBarPreference`，格式 `%s dip`

参见：
- `app/src/main/res/xml/prefs_system.xml` 第 357-364 行
- `app/src/main/res/values/strings.xml` 第 79 行
- `app/src/main/res/values-zh-rCN/strings.xml` 第 70 行
- `app/src/main/res/values-zh-rTW/strings.xml` 第 57 行

### 1.2 Feature 注册

- **FeatureId:** `StatusBarHeightFeatureId`（id 146，name `status_bar_height`）
- **Target:** `FeatureTarget.ANY`
- **InstallPhase:** `PACKAGE_READY`
- **Feature factory:** `StatusBarHeightFeature`
- **启用条件:** `prefs.getInt("system_statusbarheight", 11) > 11`

参见：
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/FeatureIds.kt` 第 600-608 行
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/CommonPackageFeatures.kt` 第 14-61 行

### 1.3 运行时实现

`StatusBarHeightFeature.installHook()` 调用 `ModsSystem.StatusBarHeightHook(lpparam)`。

`System.kt` 第 183-192 行：

```kotlin
@JvmStatic
fun StatusBarHeightHook(lpparam: PackageReadyParam) {
    val opt = MainModule.mPrefs.getInt("system_statusbarheight", 11)
    val heightDpi = if (opt == 11) 27 else opt
    val pkgName = lpparam.packageName
    ModuleHelper.replacePkgAndFrameworkValue(pkgName, "dimen", "status_bar_height_default", heightDpi)
    ModuleHelper.replacePkgAndFrameworkValue(pkgName, "dimen", "status_bar_height", heightDpi)
    ModuleHelper.replacePkgAndFrameworkValue(pkgName, "dimen", "status_bar_height_portrait", heightDpi)
    ModuleHelper.replacePkgAndFrameworkValue(pkgName, "dimen", "status_bar_height_landscape", heightDpi)
}
```

### 1.4 Resource 替换机制

`ModuleHelper.replacePkgAndFrameworkValue`（`ModuleHelper.kt` 第 862-867 行）：

```kotlin
fun replacePkgAndFrameworkValue(pkg: String, type: String, name: String, resValue: Any?) {
    if (pkg != "android") {
        MainModule.resHooks.setThemeValueReplacement("android", type, name, resValue)
    }
    MainModule.resHooks.setThemeValueReplacement(pkg, type, name, resValue)
}
```

`ResourceHooks.setThemeValueReplacement`（`ResourceHooks.kt` 第 442-466 行）：
- 通过 `MiuiThemeHelper.parseDimension("${value}dp")` 把 dp 值转成 dimen pixel。
- 写入 `themeValueReplacements["$pkg:$type/$name"]`。
- 触发 `tryInitThemeHook()`，将 `Resources.getText/getString/getLayout/getDrawableForDensity` 等 getter 替换为模块值。

### 1.5 诊断结论

当前实现**仅替换 framework / package 视角的 `status_bar_height*` dimen 资源**。它**没有**：

- 修改 `WindowManager` 的状态栏 `InsetsSource` frame；
- 修改 `WindowInsets.Type.statusBars()`；
- 修改 `statusBarsIgnoringVisibility`；
- 修改 `displayCutout` safe inset；
- 修改 `DisplayPolicy` / `InsetsPolicy` / `InsetsSourceProvider`；
- 修改 `ViewRootImpl` / `DecorView` / 应用 root View 的 padding、margin、translation 或 `fitsSystemWindows`。

因此，当应用以 edge-to-edge 模式运行（`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` + `decorFitsSystemWindows=false`）时，它从 `WindowInsets` 读到的状态栏 inset 仍由 `InsetsSource` 决定，而不是由被替换的 `status_bar_height` dimen 直接决定。二者可能不一致。

---

## 2. ROM Hook 点静态审计

### 2.1 候选矩阵

| Candidate | Process | Input | Output | Frequency | Rotation aware | Cutout aware | Multi-window aware | ROM stability | Failure impact | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|
| `com.android.server.wm.DisplayPolicy` system bar height | system_server | rotation, cutout, config | `status_bar_height` for policy | per config change | Yes | Yes | Yes | AOSP_ONLY / HYPEROS_ONLY | Status bar mis-layout, crash, boot loop | NEEDS_DEVICE_DEX_EVIDENCE |
| `com.android.server.wm.InsetsPolicy` status bar source | system_server | display state, windows | `InsetsSource` frame | per layout | Yes | Yes | Yes | AOSP_ONLY | Window insets wrong, ANR | NEEDS_DEVICE_DEX_EVIDENCE |
| `com.android.server.wm.InsetsSourceProvider` | system_server | window / display | `InsetsSource` control | per layout | Yes | Yes | Yes | AOSP_ONLY | Wrong insets, app crash | NEEDS_DEVICE_DEX_EVIDENCE |
| `com.android.server.wm.InsetsStateController` | system_server | InsetsState | control source dispatch | per layout | Yes | Yes | Yes | AOSP_ONLY | Inset dispatch wrong | NEEDS_DEVICE_DEX_EVIDENCE |
| `com.android.server.wm.WindowState.computeFrame` | system_server | window attrs, insets | content frame | per layout | Yes | Yes | Yes | AOSP_ONLY | Layout wrong | NEEDS_DEVICE_DEX_EVIDENCE |
| `com.android.internal.policy.SystemBarUtils.getStatusBarHeightForRotation` | framework | rotation | height | per rotation | Yes | No (caller handles) | No | AOSP_ONLY | Height per rotation wrong | HYPEROS_ONLY / NEEDS_DEVICE_DEX_EVIDENCE |
| `com.android.systemui.statusbar.phone.PhoneStatusBarView` height | com.android.systemui | config, resources | view height | per config | Yes | Yes | Yes | HYPEROS_ONLY | UI overlap/blank | NEEDS_DEVICE_DEX_EVIDENCE |
| `com.android.systemui.statusbar.StatusBarWindowView` / controller | com.android.systemui | window attrs | status bar window bounds | per layout | Yes | Yes | Yes | HYPEROS_ONLY | UI mis-align | NEEDS_DEVICE_DEX_EVIDENCE |
| `com.android.systemui.statusbar.StatusBarContentInsetsProvider` | com.android.systemui | insets, cutout | content insets | per layout | Yes | Yes | Yes | HYPEROS_ONLY | Icons cut off | NEEDS_DEVICE_DEX_EVIDENCE |
| ResourceHook `status_bar_height*` | any app process | resource id | dimen value | every resource read | Yes (resource values are per-rotation qualified) | Indirectly (ROM uses them) | Indirectly | PRESENT_SIGNATURE_UNCONFIRMED | Status bar drawing height mismatch, but not Insets | CURRENT IMPLEMENTATION |

标记说明：

- `PRESENT_SIGNATURE_UNCONFIRMED`：`SystemBarHeight` Feature 已存在，但 Insets 一致性未在实机验证。
- `AOSP_ONLY`：候选存在于 AOSP 12/13/14 framework 源码，但 HyperOS 重命名/重写概率高。
- `HYPEROS_ONLY`：候选在 MIUI/HyperOS 系统 UI 中存在，但签名未在本仓库或 ROM 样本中确认。
- `NEEDS_DEVICE_DEX_EVIDENCE`：需要把设备 framework / SystemUI APK 或 jar 复制到 TEMP 后用 jadx/javap/dexdump 提取真实类/方法名。

### 2.2 静态方法/类调用关系（需要 DEX 证据）

- `com.android.server.wm.DisplayPolicy` -> `getStatusBarHeight` / `getStatusBarHeightForRotation`
- `com.android.server.wm.InsetsState` -> `getSource(ITYPE_STATUS_BAR)`
- `com.android.server.wm.InsetsSourceProvider` -> `getOrCreateSource`
- `com.android.internal.policy.SystemBarUtils` -> `getStatusBarHeightForRotation(int)`
- `com.android.systemui.statusbar.phone.PhoneStatusBarView` -> `onMeasure` / `getBarHeight`
- `com.android.systemui.statusbar.phone.StatusBarContentInsetsProvider` -> `getStatusBarContentHeightForRotation`
- HyperOS 可能重命名为 `com.miui.*`、`com.android.systemui.statusbar.*` 或 `com.android.systemui.miui.*`。

---

## 3. 明确排除的危险方案

| 方案 | 拒绝原因 |
|---|---|
| 全局 Hook `WindowInsets.getInsets()` | 所有应用共享同一 Insets 对象语义；会导致非目标应用、Dialog/Popup、全屏视频/游戏出现双重或缺失 inset。 |
| 全局 Hook `WindowInsetsCompat.getInsets()` | Jetpack 层不会单独解决 server 侧 InsetsSource；Compose 可能重复消费，导致空白或裁剪。 |
| Hook `ViewRootImpl.dispatchApplyInsets()` | 进入应用进程根 view 的私有路径；Edge-to-edge 应用会收到错误 insets，导致内容被状态栏/刘海遮挡。 |
| Hook `DecorView.onApplyWindowInsets()` | DecorView 已不存在于所有目标应用；且会与应用自己的 inset 消费冲突。 |
| 给所有 Activity root View 强制加 `paddingTop` | 与 Compose/RecyclerView/CoordinatorLayout 等 scroll container 冲突；分屏/小窗会重复叠加。 |
| 把新高度和 `displayCutout.safeInsetTop` 直接相加 | 刘海和状态栏是不同 Insets 源；直接相加会高估顶部安全区，导致横屏空白、视频/游戏异常。 |
| 对所有 App 强制 `decorFitsSystemWindows=true` | 破坏 edge-to-edge 设计，导致全屏模式、沉浸模式、手势提示区冲突。 |
| 修改所有 App 的 `layoutInDisplayCutoutMode` | 改变应用本身声明的 cutout 策略，可能遮挡重要内容或产生 legal/safety 问题。 |
| 状态栏隐藏时仍强制保留顶部 inset | 与 `SYSTEM_UI_FLAG_FULLSCREEN`、全屏视频、游戏冲突，产生不可见 inset 消费。 |
| 使用固定 px 或固定 27dp 假设所有设备一致 | 不同设备 density/rotation/cutout 差异大，固定值会导致多设备错位。 |

---

## 4. 设备证据计划

本阶段未连接设备，所有结论为静态推理。

| # | 配置 | 目标 | 记录项 |
|---|---|---|---|
| 1 | 默认 27dp + 普通 App | 一个传统 View App | package, activity, orientation, configured dp/px, statusBars source height, stable/content top, cutout safe top, mismatch, 截图 |
| 2 | 增高状态栏 + 普通 App | 同一传统 View App | 同上，确认 UI 是否被状态栏遮挡 |
| 3 | 默认 27dp + edge-to-edge App | 一个声明 `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` 的 App | 同上，并记录 `decorFitsSystemWindows`、`requestedVisibleTypes` |
| 4 | 增高状态栏 + edge-to-edge App | 同一 edge-to-edge App | 确认 Insets 是否仍跟随旧 statusBars 高度 |
| 5 | 默认 + Jetpack Compose App | Compose App | 记录 `WindowInsets.statusBars` 返回值 |
| 6 | 默认 + WebView App | WebView App | 确认网页内容是否被状态栏遮挡 |
| 7 | 横屏 | 任意全屏 App | `rotation=1/3`，statusBars source frame 宽高交换 |
| 8 | 分屏/小窗 | 支持 multi-window 的 App | `windowingMode`，窗口 `mFrame` 偏移 |

DeviceEvidence: `PENDING`

---

## 5. 候选 Hook 矩阵结论

**C. NEEDS_DEVICE_DEX_EVIDENCE**

理由：

- AOSP framework 的 `DisplayPolicy` / `InsetsSourceProvider` 理论上是最稳定的切入口，但 HyperOS 对 `com.android.server.wm` 包有大量重写，真实方法签名无法仅凭源码确定。
- `SystemUI` 侧候选存在，但属于应用进程 hook，不能解决 system_server 向应用分发 `WindowInsets` 的问题。
- 资源替换路径不会真正修改 `InsetsSource`，在 edge-to-edge / cutout / multi-window 场景下无法保证一致。
- 没有任何候选在不修改 system_server 的前提下，能安全、全局地同步 `WindowInsets`。

如 DEX 证据确认以下任一函数存在且稳定，则推荐升级到 `A. SAFE_SYSTEM_SERVER_CANDIDATE_FOUND`：

- `com.android.server.wm.InsetsSourceProvider.setOverriddenFrame(Rect)`
- `com.android.server.wm.InsetsPolicy.getStatusBarSource()`
- `com.android.server.wm.DisplayPolicy.getStatusBarHeightForRotation(int)`
- 或 HyperOS 等价方法。

否则结论保持 **D. NO_SAFE_GLOBAL_HOOK_FOUND**。

---

## 6. 推荐 UX2B 范围

1. 使用 `tools/a14_status_bar_insets_probe.py` 在至少 2 台 HyperOS Android 14 设备上采集证据。
2. 将 device framework / SystemUI APK 复制到 TEMP 后用 `jadx` / `javap` / `dexdump` 确认候选类/方法签名。
3. 根据 DEX 证据选择：
   - 若存在稳定的 `InsetsSource` 覆盖点，则实施最小 `system_server` hook（需重新评估风险等级为 R2/R3）。
   - 若不存在，则在 UX2B 给出 `NO_SAFE_GLOBAL_HOOK_FOUND`，并建议改为仅对 SystemUI 进程调整 status bar content insets，或保持当前仅资源替换。
4. 不得开始非诊断性布局修复、不得 Hook `WindowInsets.getInsets()`、不得修改应用 padding/margin/fitsSystemWindows。
