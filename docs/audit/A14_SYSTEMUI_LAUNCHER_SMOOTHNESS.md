# A14 SystemUI / Launcher Smoothness Audit

Scope: HyperOS 1 / Android 14 (SDK 34), branch `devin/a14-rom-intelligence-audit`.
Date: 2026-08-02.

## Actual hot paths

- `SystemUIControlCenterHooks.StatusBarGesturesHook`
  - `MiuiNotificationPanelViewController.setExpandedHeightInternal` (`before`)
  - `PhoneStatusBarView.onTouchEvent` (`before`)
  - `ControlCenterWindowViewImpl.handleMotionEvent` (`before`)
- `SwitchCCAndNotificationHook`
  - `MiuiPhoneStatusBarView.handleEvent`
  - `ControlPanelWindowManager.dispatchToControlPanel`
- `SystemUIStatusBarHooks`
  - `MiuiPhoneStatusBarView.onAttachedToWindow`
  - `MiuiPhoneStatusBarView.onFinishInflate`
- `MainModule.onPackageReady`
  - `CommonPackageFeatures` / `GenericAppInstaller` install loops

## First-touch cold initialization points

1. `StatusBarGesturesHook` `ACTION_DOWN` resolves `ControlCenterControllerImpl` and `BrightnessController` on the first `ACTION_DOWN` of a touch sequence if `mBrightnessController == null`.
2. `DeviceInfoMonitor.hookIconSlots` resolves `StatusBarIconHolder` and `StatusBarIconController.IconManager` at `PACKAGE_READY`, but the `addHolder` view is created when SystemUI first inflates the status bar.
3. `MainModule` `onPackageReady` runs `initPrefs()` and per-package `FeatureInstallRegistry` for the package being attached.

## Duplicate event entry points

- `StatusBarGesturesHook` installs the **same** `MethodHook` object into both `PhoneStatusBarView.onTouchEvent` and `ControlCenterWindowViewImpl.handleMotionEvent`. The logic uses `param.getThisObject()!!.javaClass.simpleName` to branch, but the same `isSlidingStart`/`isSliding`/`tapStartX`/`tapStartY`/`nextBrightNess` shared state means a single gesture could be processed once per view it traverses. The existing state machine is currently view-agnostic and does not de-duplicate by `event.downTime` or `event.eventTime`.
- `SwitchCCAndNotificationHook` intercepts `MiuiPhoneStatusBarView.handleEvent` and `ControlPanelWindowManager.dispatchToControlPanel`. `handleEvent` is the entry point; `dispatchToControlPanel` is called from within `handleEvent` on some paths. The `ControlPanelWindowManager.dispatchToControlPanel` hook reads `controlCenterController.useControlCenter` and can short-circuit the dispatch before `handleMotionEvent`. This is the correct order (notification vs. control center), but the same `MotionEvent` is evaluated twice by module hooks.

## Repeated attach risks

- `MiuiPhoneStatusBarView.onAttachedToWindow` left-icon container: previously added a new `MiuiStatusIconContainer`, `DarkIconManager`, and `addIconGroup` on every attach. Now guarded by `XposedHelpers.getAdditionalInstanceField(sbView, "leftIconContainer")` and early return if the container is still parented.
- `MiuiPhoneStatusBarView.onFinishInflate` (`DualRowsStatusbarHook` and `StatusBarClockPositionHook`): previously could execute twice on orientation/reattach. Now guarded by `dualRowsLayoutAdded` / `clockPositionInitialized` additional instance fields.
- `BatteryIndicator` owns a `BroadcastReceiver` per view and a `CoroutineScope`. The receiver is unregistered in `onDetachedFromWindow` and the scope is cancelled; no static `statusbarTextIcons` are held.

## Startup redundancy

- `MainModule.onPackageReady` called `initPrefs()` twice for `com.miui.home` (once at the top of the method, once after `LauncherInstaller.install`). The second call has been removed; `mPrefs` is already bootstrapped by the first call.
- `CommonPackageFeatures.hasEnabledFeature` short-circuits `FeatureInstallRegistry` creation if no common feature is enabled for the package.
- `ReflectionCache.onSafeLifecycle` is triggered only for the launcher package.
- No global permanent `FeatureInstallRegistry` is retained; a new per-package registry is created only when needed.

## Restart / kill behavior

- **No automatic restart on ordinary preference changes.** `systemui_restart_time` is only a read-only marker used to skip non-essential hooks in the first 10 seconds after a restart.
- `GlobalActions` exposes explicit user-triggered actions: `RestartSystemUI` (`Process.killProcess(Process.myPid())`), `RestartLauncher` (`forceStopPackage`), `RestartSecurityCenter` (`forceStopPackage`).
- `SystemNotificationHooks` `forceStopPackage` is user-triggered via notification context menu.
- `AppLocaleController` kills the app process after locale save; this is unrelated to SystemUI/Launcher.

## Fixed in this round

1. **Custom status icon insertion index clamped** (`DeviceInfoMonitor.hookIconSlots`) via `StatusbarViewMaths.clampStatusIconInsertIndex`.
2. **Duplicate custom icon insertion prevented** by checking `mGroup` for an existing child with the same `textIconTagId`.
3. **Repeated `MiuiPhoneStatusBarView` attach** guarded by additional instance fields for left icon container, dual rows, and clock position.
4. **Launcher redundant `initPrefs()`** removed.
5. **Shade expansion threshold** converted to a `ShadeExpansionTracker` state machine so `setExpandedHeightInternal` only resets touch state once per threshold crossing instead of every frame above 0.33.

## Still needs Xiaomi 13 device verification

1. **Custom temperature/battery icon** (#660, #538) does not crash or duplicate across `MiuiPhoneStatusBarView` reattach after orientation/lock/unlock.
2. **Status bar brightness gesture** no longer performs cold `ControlCenterControllerImpl` / `BrightnessController` resolution on the first `ACTION_DOWN`; the pre-attach/idle resolution path should be tested on a real ROM.
3. **Shade drop-down first 20% latency** should be measured with `systrace` or `gfxinfo` before/after the `ShadeExpansionTracker` and `StatusBarGesturesHook` changes.
4. **Double `handleEvent` → `handleMotionEvent` path** should be verified with a single gesture to ensure the brightness slider does not process the same `MotionEvent` twice.
