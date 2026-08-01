# A14 Process Exceptions

Scope: HyperOS 1 / Android 14 (SDK 34), `tv.withaibuild.customiuizer.r14`.
Source of truth: `docs/rom-intelligence/A14_PROCESS_MATRIX.md` and the installer/feature classes in `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/`.

This document records the process-level boundaries that CustoMIUIzer A14 observes: which process/package is allowed to host specific features, which are explicitly denied, and the code evidence and test status for each boundary.

## 1. Settings main process / `:remote`

- **Allowed:** Package `com.android.settings` through `SettingsInstaller` at `PACKAGE_READY`.
- **Denied:** Running `SystemServerInstaller` targets in Settings; no `system_server` class is loaded here.
- **Feature exceptions:** `fake_ic_*` resource hooks and `Settings` UX tweaks are installed with `SettingsInstaller`.
- **ClassLoader:** `lpparam.getClassLoader()`.
- **Install phase:** `InstallPhase.PACKAGE_READY`.
- **Code evidence:** `SettingsFeatures.kt` maps preferences to `SettingsInstaller.install` calls.
- **Test status:** STATIC_VERIFIED via `SystemUiFeaturesWiringTest` and `FeatureRegistryWiringTest`.

## 2. SecurityCenter main process / `:remote` / `:bootaware`

- **Allowed:** Package `com.miui.securitycenter` through `SecurityCenterInstaller` at `PACKAGE_READY`.
- **Denied:** `system_server` or `com.android.systemui` hooks are not installed here.
- **Feature exceptions:** App info, app disable, restrict, and permission UI hooks.
- **ClassLoader:** `lpparam.getClassLoader()`.
- **Install phase:** `InstallPhase.PACKAGE_READY`.
- **Code evidence:** `SecurityCenterFeatures.kt`.
- **Test status:** STATIC_VERIFIED.

## 3. SystemUI main process / `plugin`

- **Allowed:** Package `com.android.systemui` through `SystemUiInstaller` at `PACKAGE_READY` and `MainModule` post-init callbacks.
- **Denied:** SystemUI package is never treated as `system_server` or `launcher`; `SystemServerInstaller` targets are skipped.
- **Feature exceptions:** `StatusBar`, `ControlCenter`, `LockScreen`, `Notification`, `Volume`, and `Battery` features; `miui.systemui.plugin` classes are loaded through the plugin `ClassLoader` extracted by `extractPluginLoader`.
- **ClassLoader:** `lpparam.getClassLoader()` for main process, dedicated `pluginLoader` for `miui.systemui.plugin` classes.
- **Install phase:** `InstallPhase.PACKAGE_READY` plus `SystemUIInitializer.init` post-init.
- **Code evidence:** `SystemUiFeatures.kt`, `SystemUIStatusBarHooks.kt`, `SystemUIControlCenterHooks.kt`, `SystemUIStatusBarIconHooks.kt`, `SystemUIBatteryHooks.kt`, `DeviceInfoMonitor.kt`.
- **Test status:** STATIC_VERIFIED; some `NOT_EXERCISED` paths require device samples for `PLUGIN` class resolution.

## 4. Launcher main process

- **Allowed:** Package `com.miui.home` through `LauncherInstaller` at `PACKAGE_READY` and `GenericAppInstaller` at `APPLICATION_ATTACHED`.
- **Denied:** `LauncherInstaller` does not install `com.android.systemui` or `system_server` hooks.
- **Feature exceptions:** Folder columns, grid spacing, dock indicators, gesture scaling, hide titles.
- **ClassLoader:** `lpparam.getClassLoader()`.
- **Install phase:** `InstallPhase.PACKAGE_READY` and `InstallPhase.APPLICATION_ATTACHED`.
- **Code evidence:** `LauncherPackageReadyFeatures.kt`, `LauncherPostAttachFeatures.kt`.
- **Test status:** STATIC_VERIFIED.

## 5. PowerKeeper

- **Allowed:** Package `com.miui.powerkeeper` through `PowerKeeperInstaller` at `PACKAGE_READY`.
- **Denied:** No `system_server` or UI-level hooks are installed here.
- **Feature exceptions:** App restrict and battery optimization UI.
- **ClassLoader:** `lpparam.getClassLoader()`.
- **Install phase:** `InstallPhase.PACKAGE_READY`.
- **Code evidence:** `PowerKeeperFeatures.kt`.
- **Test status:** STATIC_VERIFIED.

## 6. Wallpaper

- **Allowed:** Package `com.miui.miwallpaper` (and `com.miui.screenshot`, `com.miui.gallery`) through `MediaInstaller` at `PACKAGE_READY`.
- **Denied:** No SystemUI or Launcher hooks are installed here.
- **Feature exceptions:** Wallpaper scale, screenshot config, gallery path hooks.
- **ClassLoader:** `lpparam.getClassLoader()`.
- **Install phase:** `InstallPhase.PACKAGE_READY`.
- **Code evidence:** `MediaFeatures.kt`.
- **Test status:** STATIC_VERIFIED.

## 7. NetworkStack

- **Allowed:** Package `com.android.networkstack` is not a primary target in A14.
- **Denied:** No CustoMIUIzer installer targets `com.android.networkstack`.
- **Feature exceptions:** None.
- **ClassLoader:** N/A.
- **Install phase:** N/A.
- **Code evidence:** Installer registries do not list this package.
- **Test status:** STATIC_VERIFIED (no expected hooks).

## 8. Input method

- **Allowed:** Any input method package (e.g. `com.google.android.inputmethod.pinyin`, `com.google.android.inputmethod.latin`) through `InputMethodInstaller` at `PACKAGE_READY`.
- **Denied:** `CommonPackageFeatures`/`GenericAppInstaller` `statusbarcolor`, `nooverscroll`, and `media` features are not installed here.
- **Feature exceptions:** Volume cursor, fix bottom margin, Gboard padding.
- **ClassLoader:** `lpparam.getClassLoader()`.
- **Install phase:** `InstallPhase.PACKAGE_READY`.
- **Code evidence:** `InputMethodFeatures.kt`.
- **Test status:** STATIC_VERIFIED.

## 9. Generic ANY application

- **Allowed:** Packages selected by `CommonPackageFeatures.hasEnabledFeature` at `PACKAGE_READY`, plus packages selected by `GenericAppInstaller` at `APPLICATION_ATTACHED` (`statusbarcolor`, `nooverscroll`, `media` player).
- **Denied:** `system_server`, `com.android.systemui`, `com.miui.home`, `com.miui.securitycenter`, `com.miui.powerkeeper`, `com.android.settings` receive their dedicated installer, not the generic one.
- **Feature exceptions:** `statusbarcolor`, `nooverscroll`, `volumemedia` per-app.
- **ClassLoader:** `lpparam.getClassLoader()`.
- **Install phase:** `InstallPhase.PACKAGE_READY` or `InstallPhase.APPLICATION_ATTACHED`.
- **Code evidence:** `MainModule.java` `onPackageReady` routing, `CommonPackageFeatures.kt`, `GenericAppFeatures.kt`.
- **Test status:** STATIC_VERIFIED.
