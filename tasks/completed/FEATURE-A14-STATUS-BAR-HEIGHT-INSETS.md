# FEATURE-A14-STATUS-BAR-HEIGHT-INSETS

- Platform: A14
- Status: Done
- Priority: P0
- Owner: Devin
- Reviewer: ChatGPT / human
- Related version: A14
- Cross-repo task: no

## 目标

完整实现 A14 状态栏高度与 WindowInsets 的同步：

```text
用户配置的状态栏高度
= Android framework 状态栏资源高度
= SystemUI 实际状态栏高度
= WindowManager statusBars InsetsSource 高度
= 应用收到的 statusBars WindowInsets 高度
```

同时不伪造或错误修改 displayCutout、导航栏或其他 Insets 类型。

## 当前问题

- `system_statusbarheight` 已能修改 `status_bar_height*` 资源；
- SystemUI 状态栏视觉高度可能已变高；
- WindowManager 下发的 `WindowInsets.Type.statusBars()` InsetsSource 仍可能保持系统原始高度；
- 状态栏增高后，部分应用顶部被挤压、遮挡或布局拥挤。

## 允许修改

- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StatusBarHeightConfig.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemStatusBarInsetsHooks.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/System.kt`（StatusBarHeightHook 改为使用共享配置）
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/FeatureIds.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt`
- `app/src/test/...` 下的偏好、路由与 Insets 几何测试
- `tasks/completed/` 中的完成记录

## 必须保持

- preference key `system_statusbarheight`、默认值 11、sentinel 11 -> 27dp 语义；
- `ResourceHooks` 的 `ID`、`OBJECT`、fake resource、theme value replacement 能力；
- Hook 时序、参数改写和 `chain.proceed()` 次数；
- 不修改导航栏、IME、captionBar、displayCutout 等其他 Insets source；
- `MainModule.java`、`XposedHelpers.java`、`MemberUtilsX.java` 默认不改动；
- API 101/102 边界隔离。

## 实现要求

1. framework 资源边界：继续使用 `android` package-ready 路由的 `setThemeValueReplacement`，保持现有 `status_bar_height*` 资源替换；
2. system_server Insets 边界：Hook `android.view.InsetsSource.setFrame`，对 `ITYPE_STATUS_BAR` 源按配置高度调整 `Rect.bottom`；
3. SystemUI View 边界：暂不新增 View Hook，除非实机证据证明资源替换无法驱动；
4. 配置高度在冷路径解析并缓存，热路径只读；
5. 默认或未启用时零额外开销；
6. ROM target 无法解析时记录 unsupported 日志并跳过，异常不逃出。

## 非目标

- 不重建通用资源 Hook 框架；
- 不顺便修改其他状态栏功能（图标、背景、通知等）；
- 不做按包名白名单的应用侧 Insets 修补；
- 不强求无需重启的即时 preference 切换。

## 验收标准

- [ ] 默认值下行为与原系统一致
- [ ] 自定义状态栏高度实际改变
- [ ] 应用收到的 `statusBars()` Insets 与实际状态栏高度一致
- [ ] `statusBarsIgnoringVisibility()` 与配置高度一致
- [ ] displayCutout 几何未被伪造或覆盖
- [ ] 横竖屏切换后高度正确
- [ ] 未修改导航栏或其他 InsetsSource
- [ ] 功能关闭时不安装额外 Hook
- [ ] 完整验证通过
- [ ] Debug APK 构建成功
- [ ] 没有未解释的无关改动

## 验证

```powershell
python tools/verify.py fast --changed
python tools/verify.py fast --tests StatusBarHeightConfigTest,StatusBarInsetsGeometryTest,StatusBarInsetsRoutingTest
python tools/verify.py full
python -m compileall tools
python -m unittest discover -s tools/tests -p "test_*.py"
git diff --check
.\gradlew.bat :app:assembleDebug
```

实际运行记录：
- `python tools/verify.py full` -> PASS (static rules, check-invariants, compileDebugKotlin, testDebugUnitTest, lintDebug)
- `git diff --check` -> PASS
- `gradlew.bat --no-daemon :app:assembleDebug` -> BUILD SUCCESSFUL

## 构建产物

- `app/build/outputs/apk/debug/app-debug.apk`

## 完成记录

- Base SHA: cd935311621c07e660c159191302524c6795a177
- Final SHA: a2bc4b0d
- Commits: ebe0ba03, 2d4e2f90, a2bc4b0d
- Behavior changed:
  - 新增 `StatusBarHeightConfig` 统一解析与缓存 `system_statusbarheight` 配置 dp/px；
  - `System.StatusBarHeightHook` 复用该配置进行 `status_bar_height*` 资源替换；
  - 新增 `SystemStatusBarInsetsHooks.StatusBarInsetsHeightHook`，在 system_server hook `InsetsSource.setFrame` 调整 `ITYPE_STATUS_BAR` 源底部边界；
  - 几何逻辑 `computeStatusBarFrameBottom` 提取为纯函数，保留原 bottom 上限以避免进入 cutout 不安全区域；
  - 新增 `StatusBarHeightInsetsFeature` 注册到 `SystemServerFeatures`。
- Verification:
  - `python tools/verify.py full` PASS
  - `StatusBarHeightConfigTest` PASS
  - `StatusBarInsetsGeometryTest` PASS
  - `StatusBarInsetsRoutingTest` PASS
  - `SystemServerFeaturesWiringTest` PASS
- Device evidence: 未在实机验证；依赖 AOSP 14 `android.view.InsetsSource` / `WindowInsets.Type.statusBars()` 契约。
- Known limits:
  - 当前实现采用 `max(originalBottom, configuredPx)` 作为几何底线，因此不支持把状态栏高度缩到低于系统当前计算值以下；如需支持更小自定义高度，需额外引入 cutout-safe top 感知。
  - Insets 类型检测同时支持 public mask（AOSP 14）和 internal index（0）两种编码，ROM 若同时以 `1` 表示 navigation 则需要 `isTopAnchored` 兜底。

## R1 device-ineffective corrective closure

### 实机证据
- 日志目录：`C:\Users\tv\Downloads\Peengeek\LSPosed_log\r14\r14.16.1-debug\Vector-logs-release-20260805-154107`
- 设备：`Xiaomi 13 / fuxi / 2211133G`
- ROM：`HyperOS 1 V816.0.7.0.UMCTWXM`
- Android：`14` / SDK `34`
- 构建：`beb4417a`
- 指纹：`Xiaomi/fuxi_global/fuxi:14/UKQ1.230804.001/V816.0.7.0.UMCTWXM:user/release-keys`
- 显示：`1080x2400`, `440 dpi = 2.75 px/dp`, `Display cutout safe top: 104 px ≈ 37.82 dp`
- 模块和 system_server HookSummary 安装通过，但日志未出现 `[StatusBarInsets] install configuration`、`[StatusBarInsets] source hit`、`[StatusBarInsets] frame before/after` 等运行时命中标记，因此无法确认 status-bar source 回调已被修改。
- 本轮实机状态：`LOG_VERIFIED`, `INSTALL_OK`, `SOURCE_HIT_NOT_OBSERVED`, `DEVICE_EFFECTIVENESS_NOT_CONFIRMED`.

### 修正范围
1. 修复 `StatusBarHeightConfig.enabled` 语义：默认 `sentinel 11` 为 disabled，任何合法自定义值为 enabled，不再使用 `resolvedDp > DEFAULT_DP` 导致 12–27dp 静默失效。
2. 修复 `computeStatusBarFrameBottom` 几何：状态栏高度应从 `originalTop + configuredHeightPx` 计算，不再无条件 `max(originalBottom, configuredPx)`。
3. 保持 `displayCutout` source 不变、navigation/IME/caption/gesture sources 不变；明确记录 `statusBars` 与 `displayCutout` 是不同 source/type。
4. 增加低频冷路径诊断日志：raw preference、resolved dp、configured px、density、enabled、public/internal status bar type、InsetsSource class、setFrame overloads、hook install result。
5. 首次命中 source 后按 source identity/display/rotation 限频记录：source id/type、overload、old/new frame、configured dp/px、changed 与未修改原因。
6. 所有 `catch (Throwable)` 先调用 `FatalErrors.unwrapAndRethrowIfFatal(t)`。

## R2 callback-boundary corrective closure

### 修正范围
1. `SetFrameCallback.intercept()` 拆分为：
   - 前置兼容层：读取 type、检查 source、计算替换参数；普通异常只生成 `ProceedOriginal` 决策。
   - `chain.proceed()` 只调用一次，不捕获 `chain.proceed()` 抛出的异常。
   - 原方法普通异常原样传播且 proceed count = 1；fatal 原样传播且 proceed count = 1。
2. `Rect` 参数不可变性：
   - 不再修改调用者传入的 `Rect`；改为 `Rect(firstArg).apply { bottom = newBottom }`。
   - 纯几何函数同时支持 `Rect` 和四参数 overload，两者共享同一 `originalTop + configuredPx` 计算。
3. Insets type encoding 冷路径判定：
   - 新增 `InsetsTypeEncoding`：`MODERN_PUBLIC`、`LEGACY_INTERNAL`、`UNSUPPORTED`。
   - 在安装时通过 ABI 能力（`InsetsSource` 构造函数签名、`getId()`、`InsetsState` 常量是否存在）冻结唯一模式。
   - `MODERN_PUBLIC` 模式只匹配 `WindowInsets.Type.statusBars()`。
   - `LEGACY_INTERNAL` 模式只匹配真实反射到的 `ITYPE_STATUS_BAR`，不再假设 `0`。
   - 解决 `ITYPE_NAVIGATION_BAR = 1` 与 `WindowInsets.Type.statusBars() = 1` 的冲突。
4. source 几何判断：
   - 优先用精确 type encoding 判定；不再依赖 `source.getFrame()` 旧值做 top-anchored 兜底。
   - 如需方向防线，使用当前 incoming frame 参数，而非 source 内部旧 frame。
5. 诊断改进：
   - `StatusBarHeightConfig` 增加 `rawPreferenceDp` 缓存，安装日志区分 `raw` / `resolved` / `enabled`。
   - 日志基于 incoming 参数生成不可变 `oldFrame` / `newFrame` 快照。
   - `loggedFirstHit` 改为有界集合（上限 32），使用稳定 key（encoding / source id / type / overload / result）。
   - 拒绝原因枚举：`HOOK_NOT_INSTALLED`、`UNSUPPORTED_ENCODING`、`STATUS_SOURCE_CHANGED`、`STATUS_SOURCE_NO_CHANGE`、`NON_STATUS_TYPE`、`INVALID_ARGUMENT_SHAPE`、`PREPROCESSING_REFLECTION_FAILED`、`DISABLED`。
6. 所有 `catch (Throwable)` 先 `FatalErrors.unwrapAndRethrowIfFatal(t)`，且不再用 catch 重试 `chain.proceed()`。

### 测试要求
- modern public status / navigation / displayCutout 判定
- legacy internal status / navigation 判定
- unsupported encoding 完全放行
- Rect 输入对象不被修改
- 四参数顺序正确
- 前置异常 proceed 一次
- 日志异常 proceed 一次
- 原方法普通异常向上传播且 proceed 一次
- 原方法 fatal 向上传播且 proceed 一次
- changed=false / disabled proceed 一次
- diagnostic key 上限生效
- raw=11 / resolved=27 / enabled=false
- raw=27 / resolved=27 / enabled=true
