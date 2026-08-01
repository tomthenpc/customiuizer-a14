# A14 Device Regression Checklist

This checklist is a static/build review of the A14 branch. Items that cannot be proven by static or build verification are marked **NEEDS_DEVICE**; all other items are **YES** or **NO** with the file and line evidence.

## a) Battery temperature/current and status bar custom View

| Item | Verdict | Evidence |
|------|---------|----------|
| a.1 Duplicate attach prevented | NO | `SystemUIStatusBarHooks.kt` adds a new `MiuiStatusIconContainer` and calls `StatusBarIconController.addIconGroup` on every `MiuiPhoneStatusBarView.onAttachedToWindow` without an idempotency guard; `DeviceInfoMonitor.kt` creates/inserts a new `StatusBarIconHolder` per `addHolder` callback. |
| a.2 Safe view index used | NO | `DeviceInfoMonitor.kt` `addHolder` before-hook inserts `iconView` at index `i` (`mGroup.addView(iconView, i)`) without clamping against `mGroup.childCount`; this is the same path as upstream #660. |
| a.3 IconGroup rebuild guarded | NO | The left-icons `DarkIconManager` is constructed and registered with `addIconGroup` in `MiuiPhoneStatusBarView.onAttachedToWindow` with no check for an existing group; `DualRowsStatusbarHook` builds new layouts per `onFinishInflate` but does not guard `IconGroup` rebuilds. |
| a.4 Owner replacement works | YES | `ReceiverRegistry.kt` `registerOwnedReceiver` replaces stale or same-owner entries and unregisters displaced receivers; `BatteryIndicator.kt` registers its receiver with itself as owner and a `broadcastReceiver == null` guard. |
| a.5 Idempotency after configuration change | NEEDS_DEVICE | Some paths are one-time (`CentralSurfacesImpl.start` for `BatteryIndicator`) or create new view instances, but the unguarded `onAttachedToWindow` left-icon and `addIconGroup` path needs runtime verification across rotation/theme change. |

## b) Status bar seconds

| Item | Verdict | Evidence |
|------|---------|----------|
| b.1 Continuous tick | YES | `SystemClockHooks.kt` `SecondTicker.scheduleNextTick` reschedules `this` with `1000 - currentTime % 1000L`, and `run()` calls `scheduleNextTick()` again. |
| b.2 Per-second update, not one-shot | YES | `SecondTicker` runs every ~1s while `running` is true and calls `updateTime` on every clock marked with `showSeconds`. |
| b.3 Detach cancellation | YES | `SecondTicker.dispose` removes callbacks, removes the screen-state listener, and is called from `initSecondTicker` when a new `MiuiStatusBarClockController` is created. |
| b.4 Reattach idempotency | YES | `initSecondTicker` disposes the previous `SecondTicker` stored in `clockController` additional instance field and replaces the `TIME_SET` owned receiver through `ModuleHelper.registerOwnedReceiver`. |
| b.5 Clock feature installed once | YES | `FeatureInstallState.beginInstall` returns `ALREADY_INSTALLED` for subsequent attempts; the hook is installed per `MiuiStatusBarClockController` instance but never duplicated for the same controller. |

## c) Feature Registry

| Item | Verdict | Evidence |
|------|---------|----------|
| c.1 Duplicate package ready handled | YES | `MainModule.java` returns early unless `lpparam.isFirstPackage()`; `FeatureInstallState.kt` is a per-process singleton keyed by `FeatureId.id`. |
| c.2 INSTALLING not stuck | YES | `FeatureInstallRegistry.kt` `installOne` catches `Throwable`, records, and sets `FAILED_TRANSIENT`; `OutOfMemoryError` is also set to `FAILED_TRANSIENT` before rethrow. Unrecoverable `Error` subtypes could in theory leave `INSTALLING`, but these are not caught. |
| c.3 No residual active definition on failure | YES | `FeatureInstallRegistry` holds `FeatureSpec` (metadata + factory lambda); `FeatureDefinition` objects are created only in `spec.create().install()` and are not retained after `installOne` returns. |
| c.4 Disabled feature does not allocate business objects | YES | All feature specs reviewed are `LazyFeatureSpec`; `isEnabled(prefs)` is checked before `factory()` is invoked (e.g., `CommonPackageFeatures.kt`, `SystemUiFeatures.kt`). |
| c.5 No cross-process state pollution | YES | `FeatureInstallState`, `ReceiverRegistry`, and `PreferenceObserverRegistry` are module-class singletons; `MainModule.mPrefs` is per-process and `PrefMap` is not shared across processes. |

## d) Lifecycle

| Item | Verdict | Evidence |
|------|---------|----------|
| d.1 Receiver/Observer/Handler/CoroutineScope release | NEEDS_DEVICE | Static review shows release paths (`ReceiverRegistry.unregisterModuleReceiver`, `PreferenceObserverRegistry.dropOwnedObserver`, `SecondTicker.dispose`, `DeviceInfoMonitor` `removeMessages`, and `ModuleHelper.coroutineFailureHandler` use in controllers), but actual GC timing and lifecycle ordering cannot be build-verified. |
| d.2 WeakOwner no callbacks after invalid | NEEDS_DEVICE | `WeakOwnerReceiver.onReceive` checks `ownerRef.get()` and the `active` flag, then unregisters if the owner is gone, but this depends on actual GC timing and concurrent broadcast delivery. |
| d.3 Launcher / SystemUI no duplicate registration | NO | `MainModule.java` has `isFirstPackage` and an `isHooked` guard for `SystemUIInitializer.init`, but `SystemUIStatusBarHooks.kt` `MiuiPhoneStatusBarView.onAttachedToWindow` can create a new left-icons `DarkIconManager` and call `addIconGroup` on re-attach without a matching cleanup. |

## Recommended device checks

1. Enable battery temperature/current on A14 and rotate the device; confirm no `IndexOutOfBoundsException` and no duplicate icons.
2. Enable status-bar seconds, rotate/change theme, and confirm the seconds continue to tick and stop cleanly when the screen is off.
3. Trigger `MiuiPhoneStatusBarView.onAttachedToWindow` multiple times (e.g., by entering/exiting split-screen or multi-display) and check `adb logcat` for repeated `addIconGroup` / duplicate icon warnings.
4. Change preferences for battery temperature and clock seconds while SystemUI is running; confirm no stuck `INSTALLING` feature entries in LSPosed logs.
5. Leave the device idle with screen on and confirm `WeakOwnerReceiver` cleanup does not leak broadcast receivers or stale handlers.
