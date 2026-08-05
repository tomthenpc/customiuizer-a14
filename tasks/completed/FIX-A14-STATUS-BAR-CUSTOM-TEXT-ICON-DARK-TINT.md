# FIX-A14-STATUS-BAR-CUSTOM-TEXT-ICON-DARK-TINT

- Platform: HyperOS 1 / Android 14 / SDK 34
- Status: Active
- Priority: P0
- Owner: Devin
- Reviewer: ChatGPT / human
- Related version: A14
- Cross-repo task: no

## 背景
type 91/92 自定义文字图标在左侧状态栏使用一段时间后，可能出现文字变黑并在深色背景下不可见；右侧布局没有观察到同类问题。重启桌面或 SystemUI 后可能暂时恢复。

## 目标
修复 type 91/92 自定义文字图标在左侧状态栏的 dark tint 失效问题，使其正确跟随 SystemUI 当前的 tint / darkIntensity / light/dark icon mode，同时保持右侧路径不变。

## 主要代码范围
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/DarkTintRegistrationState.kt`（待创建）
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StatusBarDisplayRegistry.kt`（参考/复用）
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/OwnedRegistrations.kt`（参考/复用）

## 调用链
完整跟踪：

```text
Preference
→ ConfigSnapshot.batteryAtLeft/tempAtLeft/batteryAtRight/tempAtRight
→ NetworkSpeedController
→ StatusBarIconController slot
→ IconManager / MiuiLightDarkIconManager
→ addHolder
→ createStatusbarTextIcon
→ View attach
→ DarkIconDispatcher registration
→ initial tint application
→ onDarkChanged
→ wallpaper/theme/shade/keyguard transition
→ View detach/recreate
→ receiver unregister
```

需分别区分左侧和右侧的 IconManager、ViewGroup 与 DarkIconDispatcher。

## 追踪到的真实失败路径

### 左侧路径
1. `StatusBarIconsPositionAdjustHook` 构造 `MiuiPhoneStatusBarView` 时，左侧容器与 `StatusBarIconController$DarkIconManager`（或 MIUI 变体）被建立并加入 `StatusBarIconController`。
2. `StatusBarIconList` 构造函数把 `battery_info`/`device_temp` slot 名插入到左侧 icon list。
3. `StatusBarIconController` 添加 icon 时调用 `IconManager.addHolder`。
4. `DeviceInfoMonitor.interceptAddHolder` 是 `addHolder` 的 `before` hook，返回自定义的 `NetworkSpeedView`（`R.layout.statusbar_text_icon`）并 `returnAndSkip`，跳过了 ROM 原本在 `addHolder` 内部执行的 `onIconAdded` / `DarkIconDispatcher.addDarkReceiver` 等生命周期。
5. 自定义 View 被 `addView` 到左侧 `MiuiStatusIconContainer`，因此文本能显示，但 **没有向 `DarkIconDispatcher` 注册 `DarkReceiver`**，也不会被主动同步当前 dark intensity / tint。
6. 壁纸/主题/锁屏/横竖屏/编辑模式等事件触发 `onDarkChanged` 时，左侧文本不会更新颜色；在深色背景下，SystemUI 其他图标反白，而自定义文本仍保持黑色，最终“变黑不可见”。

### 右侧路径
1. `DualRowsStatusbarHook.onFinishInflate` 中，当 `batteryAtRight`/`tempAtRight` 开启时，直接用 `createStatusbarTextIcon` 创建 View。
2. View 被 `addView` 到 `secondRight` 后，代码显式调用 `DarkIconDispatcher.addDarkReceiver(iconView)`，并通过 `state.registrations` 在 generation 替换时 `removeDarkReceiver`。
3. 右侧 View 能正常接收 `onDarkChanged`，颜色随 dark mode 变化，因此未观察到同类问题。

### 缺失不变量
1. `returnAndSkip(iconView)` 后必须补充 `DarkIconDispatcher.addDarkReceiver`，否则左侧缺少 dark tint 生命周期。
2. 每个自定义 View 每个 generation 只能注册一次 dark receiver。
3. View `onDetachedFromWindow`（包括状态栏重建、generation 替换、功能停止）时必须 `removeDarkReceiver`。
4. 注册时必须立即同步当前 tint，不能等下一次 `onDarkChanged`。
5. 不能写死白色/黑色；必须跟随 `DarkIconDispatcher` 的 `tint`/`darkIntensity`/`tintArea`。

## 重点不变量
1. type 91/92 自定义 View 必须兼容 `DarkIconDispatcher.DarkReceiver` 注册。
2. `returnAndSkip(iconView)` 后，外层 `MiuiLightDarkIconManager.onIconAdded` 仍需注册 dark receiver；如被绕过，应在明确 owner 下补充。
3. 左侧和右侧的注册路径不得混用。
4. 新 View 注册后立即获得当前 tint，不等待下一次主题事件。
5. View detach、状态栏 generation 替换或功能停止时释放注册。
6. 旧 View 不得继续收到 dark callback。
7. 同一 View 每个 generation 最多注册一次 dark receiver。
8. 非 91/92 holder 完全放行。
9. 不得通过写死白色/黑色颜色值解决。

## 实现约束
- 所有 `catch (Throwable)` 必须先调用 `FatalErrors.unwrapAndRethrowIfFatal(t)`。
- 不得吞掉 `OutOfMemoryError`、`ThreadDeath` 或 `VirtualMachineError`。
- 热路径不得新增反射扫描、同步 Binder 或高频对象分配。
- 不建立与 `StatusBarDisplayRegistry`、`OwnedRegistrations` 互不协调的第二套生命周期系统。
- 日志仅在安装、注册、替换和释放冷路径输出。

## 测试要求
- 左侧自定义 View 注册 dark receiver
- 右侧既有路径不重复注册
- 同一 View 重复 addHolder 不重复注册
- 新 generation 替换旧 generation
- 旧 View detach 后释放
- 新 View 获得当前 tint
- 非 91/92 holder 完全放行
- 普通反射异常安全降级
- fatal error 原样抛出

## 验证
- `python tools/verify.py fast --changed`
- `.\gradlew.bat :app:testDebugUnitTest`
- `python tools/verify.py full`
- `git diff --check`
- `.\gradlew.bat :app:assembleDebug`

## 完成记录
- Base SHA: 5d1a6f639c4daed94c301c8742e8903fc5791de0
- Engineering commit: (to be recorded after push)
- Additional closure commits: (to be recorded if any)
