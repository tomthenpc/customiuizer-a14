# A14 Hook Ownership Inventory

Total hook call sites scanned: 760

| Category | Count |
|---|---|
| REGISTRY_FEATURE | 724 |
| INSTALLER_INFRASTRUCTURE | 25 |
| API_BRIDGE | 9 |
| RESOURCE_INFRASTRUCTURE | 2 |

## REGISTRY_FEATURE

| File | Line | Function | Snippet |
|---|---|---|---|
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 84 | `onReceive` | `ModuleHelper.hookAllMethods("com.android.server.policy.PhoneWindowManager", lpparam.classLoader, "init", object : Method` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 105 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.policy.MiuiPhoneWindowManager", lpparam.classLoader, "interceptKeyBef` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 199 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.policy.MiuiPhoneWindowManager", lpparam.classLoader, "interceptKeyBef` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 296 | `intercept` | `ModuleHelper.findAndHookMethod(MediaPlayerCls, "pause", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 323 | `intercept` | `ModuleHelper.findAndHookMethod("android.inputmethodservice.InputMethodService", lpparam.classLoader, "onKeyDown", Int::c` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 351 | `intercept` | `ModuleHelper.findAndHookMethod("android.inputmethodservice.InputMethodService", lpparam.classLoader, "onKeyUp", Int::cla` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 548 | `addCustomNavBarKeys` | `ModuleHelper.findAndHookMethod("com.android.systemui.navigationbar.NavigationBarView", lpparam.classLoader, "onFinishInf` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 579 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.navigationbar.NavigationBarTransitions", lpparam.classLoader, "appl` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 623 | `intercept` | `XposedHelpers.setAdditionalInstanceField(navbar, NAV_BAR_DARK_STATE_FIELD, isDark)` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 630 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.navigationbar.NavigationBarView", lpparam.classLoader, "onConfigura` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 684 | `intercept` | `ModuleHelper.hookAllMethods("com.android.server.policy.BaseMiuiPhoneWindowManager", lpparam.classLoader, "postKeyLongPre` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 723 | `intercept` | `ModuleHelper.hookAllMethods("com.android.server.policy.BaseMiuiPhoneWindowManager", lpparam.classLoader, "removeKeyLongP` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 751 | `intercept` | `ModuleHelper.hookAllMethods("com.android.server.biometrics.sensors.AuthenticationClient", lpparam.classLoader, "onAuthen` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 784 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.biometrics.sensors.AcquisitionClient", lpparam.classLoader, "vibrateE` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 806 | `intercept` | `ModuleHelper.hookAllMethods(authClient, lpparam.classLoader, "onAuthenticated", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 835 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.recents.GestureStubView", lpparam.classLoader, "getGestureStubWindowParam"` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 860 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.recents.GestureStubView", lpparam.classLoader, "initScreenSizeAndDensity",` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 879 | `intercept` | `XposedHelpers.setIntField(thisObject, "mGestureStubDefaultSize", mGestureStubDefaultSize)` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 880 | `intercept` | `XposedHelpers.setIntField(thisObject, "mGestureStubSize", mGestureStubSize)` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 888 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.recents.GestureStubView", lpparam.classLoader, "setSize", Int::class.javaP` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 915 | `intercept` | `ModuleHelper.hookAllConstructors("com.android.systemui.recents.OverviewProxyService", lpparam.classLoader, object : Meth` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 930 | `intercept` | `ModuleHelper.findAndHookMethod(callback.javaClass, "setWindowState", Integer::class.javaPrimitiveType!!, Integer::class.` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 936 | `intercept` | `XposedHelpers.setObjectField(GestureObserver, "mGestureLineEnable", true)` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 952 | `intercept` | `ModuleHelper.hookAllMethods("com.android.systemui.navigationbar.NavigationBarController", lpparam.classLoader, "createNa` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 978 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.server.input.util.ShortCutActionsUtils", lpparam.classLoader, "triggerFunction"` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 1007 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.policy.MiuiShortcutTriggerHelper", lpparam.classLoader, "getDoubleVol` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 1008 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.input.shortcut.singlekeyrule.VolumeDownKeyRule", lpparam.classLoader,` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 1014 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.policy.MiuiPhoneWindowManager", lpparam.classLoader, "processBackFing` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 1036 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.assist.AssistManager", lpparam.classLoader, "startAssist", Bundle::` |
| `tv/withaibuild/customiuizer/mods/Controls.kt` | 1063 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.assist.ui.DefaultUiController", lpparam.classLoader, "logInvocation` |
| `tv/withaibuild/customiuizer/mods/GlobalActions.kt` | 347 | `onReceive` | `XposedHelpers.setBooleanField(miuiVolumeDialog, "mNeedReInit", true)` |
| `tv/withaibuild/customiuizer/mods/GlobalActions.kt` | 350 | `onReceive` | `XposedHelpers.setBooleanField(miuiVolumeDialog, "mNeedReInit", true)` |
| `tv/withaibuild/customiuizer/mods/GlobalActions.kt` | 515 | `onReceive` | `XposedHelpers.callMethod(context, "startActivityAsUser", launchIntent, XposedHelpers.newInstance(UserHandle::class.java,` |
| `tv/withaibuild/customiuizer/mods/GlobalActions.kt` | 533 | `onReceive` | `ModuleHelper.findAndHookMethod("com.android.settings.MiuiSettings", lpparam.classLoader, "updateHeaderList", List::class` |
| `tv/withaibuild/customiuizer/mods/GlobalActions.kt` | 553 | `intercept` | `val header = XposedHelpers.newInstance(headerCls)` |
| `tv/withaibuild/customiuizer/mods/GlobalActions.kt` | 554 | `intercept` | `XposedHelpers.setLongField(header, "id", 666L)` |
| `tv/withaibuild/customiuizer/mods/GlobalActions.kt` | 558 | `intercept` | `XposedHelpers.setObjectField(header, "intent", intent)` |
| `tv/withaibuild/customiuizer/mods/GlobalActions.kt` | 559 | `intercept` | `XposedHelpers.setIntField(header, "iconRes", settingsIconResId)` |
| `tv/withaibuild/customiuizer/mods/GlobalActions.kt` | 560 | `intercept` | `XposedHelpers.setObjectField(header, "title", modRes.getString(R.string.app_name))` |
| `tv/withaibuild/customiuizer/mods/GlobalActions.kt` | 563 | `intercept` | `users.add(XposedHelpers.newInstance(UserHandle::class.java, 0) as UserHandle)` |
| `tv/withaibuild/customiuizer/mods/GlobalActions.kt` | 565 | `intercept` | `XposedHelpers.setObjectField(header, "extras", bundle)` |
| `tv/withaibuild/customiuizer/mods/GlobalActions.kt` | 595 | `intercept` | `ModuleHelper.hookAllMethods("com.android.settings.MiuiSettings\$HeaderAdapter", lpparam.classLoader, "setIcon", object :` |
| `tv/withaibuild/customiuizer/mods/GlobalActions.kt` | 623 | `intercept` | `ModuleHelper.hookAllConstructors("com.android.systemui.statusbar.policy.NetworkSpeedController", lpparam.classLoader, ob` |
| `tv/withaibuild/customiuizer/mods/GlobalActions.kt` | 638 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.systemui.functions.MiuiTopActivityObserver", lpparam.classLoader, "updateTopAct` |
| `tv/withaibuild/customiuizer/mods/GlobalActions.kt` | 664 | `intercept` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.SystemBarAttributesListener", lpparam.classLoader, "on` |
| `tv/withaibuild/customiuizer/mods/GlobalActionSystemServerHooks.kt` | 31 | `?` | `ModuleHelper.hookAllMethods("com.android.server.policy.BaseMiuiPhoneWindowManager", lpparam.classLoader, "initInternal",` |
| `tv/withaibuild/customiuizer/mods/GlobalActionSystemServerHooks.kt` | 230 | `onReceive` | `ModuleHelper.findAndHookMethod(statusBarClass, "start", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/GlobalActionSystemServerHooks.kt` | 293 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.wm.shell.miuifreeform.MiuiFreeformModeController", lpparam.classLoader, "onI` |
| `tv/withaibuild/customiuizer/mods/GlobalActionSystemServerHooks.kt` | 358 | `run` | `ModuleHelper.findAndHookMethod("com.android.wm.shell.sosc.SoScSplitScreenController", lpparam.classLoader, "onInit", obj` |
| `tv/withaibuild/customiuizer/mods/GlobalActionSystemServerHooks.kt` | 411 | `onReceive` | `ModuleHelper.hookAllConstructors("com.android.systemui.controlcenter.policy.AutoBrightnessController", lpparam.classLoad` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 40 | `?` | `ModuleHelper.findAndHookMethod("com.miui.home.recents.NavStubView", lpparam.classLoader, "onSystemUiFlagsChanged", Int::` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 62 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.recents.views.RecentsContainer", lpparam.classLoader, "showLandscapeOvervi` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 63 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.recents.NavStubView", lpparam.classLoader, "isImmersive", object : MethodH` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 83 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.recents.NavStubView", lpparam.classLoader, "onPointerEvent", MotionEvent::` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 94 | `intercept` | `XposedHelpers.setObjectField(thisObject, "mHideGestureLine", true)` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 106 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.recents.NavStubView", lpparam.classLoader, "updateScreenSize", object : Me` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 113 | `intercept` | `XposedHelpers.setObjectField(thisObject, "mHideGestureLine", false)` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 127 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.home.launcher.shortcuts.ShortcutMenuManager", lpparam.classLoader, "startAppDetail` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 159 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "onCreate", Bundle::class.java, o` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 191 | `intercept` | `ModuleHelper.findAndHookMethod(ActivityManagerWrapper!!, "needRemoveTask", TaskInfoCompat, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 240 | `intercept` | `ModuleHelper.hookAllMethods(utilsClass, "fastBlurWhenEnterRecents", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 267 | `intercept` | `ModuleHelper.hookAllMethods(utilsClass, "fastBlurWhenGestureResetTaskView", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 273 | `intercept` | `XposedHelpers.setAdditionalStaticField(utilsClass, "customBlurRatio", true)` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 284 | `intercept` | `ModuleHelper.hookAllMethods(utilsClass, "fastBlur", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 311 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.common.DeviceLevelUtils", lpparam.classLoader, "isHideStatusBarWh` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 312 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "keepStatusBarShowingForBette` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 317 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.home.launcher.AnalyticalDataCollectorJobService", lpparam.classLoader, "onStartJob` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 318 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.AnalyticalDataCollector", lpparam.classLoader, "canTrackLaunchApp` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 321 | `intercept` | `XposedHelpers.setStaticObjectField(OneTrackInterfaceUtils, "IS_ENABLE", false)` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 327 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "registerBroadcastReceivers", obj` |
| `tv/withaibuild/customiuizer/mods/Launcher.kt` | 391 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "onDestroy", object : MethodHook(` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 25 | `scaleStiffness` | `ModuleHelper.hookAllMethods("com.miui.home.launcher.animate.SpringAnimator", lpparam.classLoader, "getSpringForce", obje` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 56 | `intercept` | `XposedHelpers.setFloatField(thisObject, "mCenterXStiffness", scaleStiffness(XposedHelpers.getFloatField(thisObject, "mCe` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 57 | `intercept` | `XposedHelpers.setFloatField(thisObject, "mCenterYStiffness", scaleStiffness(XposedHelpers.getFloatField(thisObject, "mCe` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 58 | `intercept` | `XposedHelpers.setFloatField(thisObject, "mWidthStiffness", scaleStiffness(XposedHelpers.getFloatField(thisObject, "mWidt` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 59 | `intercept` | `XposedHelpers.setFloatField(thisObject, "mRadiusStiffness", scaleStiffness(XposedHelpers.getFloatField(thisObject, "mRad` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 60 | `intercept` | `XposedHelpers.setFloatField(thisObject, "mAlphaStiffness", scaleStiffness(XposedHelpers.getFloatField(thisObject, "mAlph` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 62 | `intercept` | `XposedHelpers.setFloatField(thisObject, "mRatioStiffness", scaleStiffness(XposedHelpers.getFloatField(thisObject, "mRati` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 64 | `intercept` | `XposedHelpers.setFloatField(thisObject, "mRadioStiffness", scaleStiffness(XposedHelpers.getFloatField(thisObject, "mRadi` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 77 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.home.recents.util.RectFSpringAnim", lpparam.classLoader, "initAllAnimations", hook` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 82 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.launcher.utils.MiuiSettingsUtils", lpparam.classLoader, "isSystemAnimationOpen", H` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 87 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.home.recents.util.SpringAnimationUtils", lpparam.classLoader, "startShortcutMenuLa` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 88 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.home.recents.util.SpringAnimationUtils", lpparam.classLoader, "startShortcutMenuLa` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 93 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.home.recents.QuickstepAppTransitionManagerImpl", lpparam.classLoader, "hasControlR` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 98 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.miwallpaper.manager.WallpaperServiceController", lpparam.classLoader, "noNeedDe` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 105 | `intercept` | `XposedHelpers.setStaticBooleanField(WallpaperZoomManagerKtClass, "ZOOM_ENABLED", false)` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 106 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.recents.DimLayer", lpparam.classLoader, "isSupportDim", HookerClassHelper.` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 109 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.home.recents.OverviewState", lpparam.classLoader, "onStateEnabled", object : Metho` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 116 | `intercept` | `XposedHelpers.setStaticBooleanField(WallpaperZoomManagerKtClass, "ZOOM_ENABLED", false)` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 132 | `intercept` | `XposedHelpers.setStaticBooleanField(WallpaperZoomManagerKtClass, "ZOOM_ENABLED", true)` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 145 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.WallpaperUtils", lpparam.classLoader, "setCurrentStatusBarAreaCol` |
| `tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt` | 165 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.WallpaperUtils", lpparam.classLoader, "setCurrentWallpaperColorMo` |
| `tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt` | 27 | `?` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "launch", "com.miui.home.launcher` |
| `tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt` | 68 | `setFolderWidth` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Folder", lpparam.classLoader, "onFinishInflate", object : MethodH` |
| `tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt` | 100 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Folder", lpparam.classLoader, "resetViewsLayoutParams", object : ` |
| `tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt` | 122 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.home.launcher.Folder", lpparam.classLoader, "onLayout", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt` | 151 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "registerBroadcastReceivers", obj` |
| `tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt` | 179 | `intercept` | `XposedHelpers.setAdditionalInstanceField(owner, "fromSecretCode", true)` |
| `tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt` | 192 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "startSecurityHide", object : Met` |
| `tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt` | 220 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "onDestroy", object : MethodHook(` |
| `tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt` | 242 | `intercept` | `ModuleHelper.hookAllMethods(BlurUtils, "getLauncherBlur", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt` | 268 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.FolderCling", lpparam.classLoader, "open", object : MethodHook() ` |
| `tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt` | 294 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.FolderCling", lpparam.classLoader, "close", Boolean::class.javaPr` |
| `tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt` | 317 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "cancelShortcutMenu", Int::class.` |
| `tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt` | 349 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.shortcuts.AppShortcutMenuItem", lpparam.classLoader, "getOnClickL` |
| `tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt` | 400 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.allapps.category.fragment.AppsListFragment", lpparam.classLoader,` |
| `tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt` | 401 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.allapps.category.fragment.RecommendCategoryAppListFragment", lppa` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 39 | `?` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Workspace", lpparam.classLoader, "onVerticalGesture", Int::class.` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 233 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.overlay.assistant.AssistantOverlaySwipeController", lpparam.class` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 266 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.hotseats.HotSeats", lpparam.classLoader, "dispatchTouchEvent", Mo` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 323 | `onFling` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "onResume", object : MethodHook()` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 339 | `intercept` | `XposedHelpers.setAdditionalInstanceField(thisObject, shakeMgrKey, shakeMgr)` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 353 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "onPause", object : MethodHook() ` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 381 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "usingFsGesture", HookerClass` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 427 | `intercept` | `XposedHelpers.setObjectField(thisObject, "mNavStubView", null)` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 453 | `intercept` | `XposedHelpers.setAdditionalStaticField(` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 471 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.recents.GestureStubView", lpparam.classLoader, "onTouchEvent", MotionEvent` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 550 | `intercept` | `ModuleHelper.hookAllConstructors("com.miui.home.launcher.Workspace", lpparam.classLoader, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 568 | `intercept` | `XposedHelpers.setAdditionalInstanceField(thisObject, "mDoubleTapControllerEx", mDoubleTapControllerEx)` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 577 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Workspace", lpparam.classLoader, "dispatchTouchEvent", MotionEven` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 605 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.shared.recents.system.AssistManager", lpparam.classLoader, "isSuppo` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 607 | `intercept` | `ModuleHelper.findAndHookMethod(FsGestureHelper!!, "canTriggerAssistantAction", Float::class.javaPrimitiveType!!, Float::` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 644 | `intercept` | `ModuleHelper.hookAllMethods(FsGestureHelper!!, "handleTouchEvent", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 671 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.recents.SystemUiProxyWrapper", lpparam.classLoader, "startAssistant", Bund` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 704 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.recents.GestureBackArrowView", lpparam.classLoader, "setReadyFinish", Read` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 716 | `intercept` | `XposedHelpers.setObjectField(view, "mRecentTaskIcon", null)` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 727 | `intercept` | `XposedHelpers.setObjectField(view, "mReadyState", readyState)` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 739 | `intercept` | `ModuleHelper.findAndHookMethod(GestureStubViewClass, "disableQuickSwitch", Boolean::class.javaPrimitiveType!!, object : ` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 756 | `intercept` | `ModuleHelper.findAndHookMethod(GestureStubViewClass, "isDisableQuickSwitch", HookerClassHelper.returnConstant(false))` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 758 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.recents.GestureStubView\$3", lpparam.classLoader, "onSwipeStop", Boolean::` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 794 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.recents.GestureStubView", lpparam.classLoader, "getNextTask", Context::cla` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 827 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Workspace", lpparam.classLoader, "onPinching", Float::class.javaP` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 850 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Workspace", lpparam.classLoader, "onPinchingEnd", Float::class.ja` |
| `tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt` | 869 | `intercept` | `XposedHelpers.setObjectField(thisObject, "mState", stateFollow)` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 41 | `modifyTitle` | `if (!TextUtils.isEmpty(newTitle)) XposedHelpers.setObjectField(thisObject, "mLabel", newTitle)` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 46 | `modifyTitle` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "updateStatusBarClock", Long::cla` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 51 | `modifyTitle` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "onCreate", Bundle::class.java, o` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 85 | `onChange` | `XposedHelpers.setObjectField(shortcutObj, "mLabel", newStr)` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 111 | `onChange` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "onDestroy", object : MethodHook(` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 126 | `intercept` | `ModuleHelper.hookAllConstructors("com.miui.home.launcher.ShortcutInfo", lpparam.classLoader, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 140 | `intercept` | `XposedHelpers.setAdditionalInstanceField(thisObject, "mLabelOrig", XposedHelpers.getObjectField(thisObject, "mLabel"))` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 163 | `intercept` | `XposedHelpers.setAdditionalInstanceField(thisObject, "mLabelOrig", XposedHelpers.getObjectField(thisObject, "mLabel"))` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 186 | `intercept` | `XposedHelpers.setAdditionalInstanceField(thisObject, "mLabelOrig", chain.getArg(0))` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 196 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.ShortcutInfo", lpparam.classLoader, "load", Context::class.java, ` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 244 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.WallpaperUtils", lpparam.classLoader, "getIconTitleShadowColor", ` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 266 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.WallpaperUtils", lpparam.classLoader, "getTitleShadowColor", Int:` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 294 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.ShortcutIcon", lpparam.classLoader, "restoreToInitState", object ` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 320 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.ItemIcon", lpparam.classLoader, "onFinishInflate", object : Metho` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 369 | `afterTextChanged` | `XposedHelpers.setAdditionalInstanceField(thisObject, "mMessageAnimationOrig", XposedHelpers.getObjectField(thisObject, "` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 370 | `afterTextChanged` | `XposedHelpers.setObjectField(thisObject, "mMessageAnimation", object : Runnable {` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 392 | `run` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.ItemIcon", lpparam.classLoader, "getIconLocation", object : Metho` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 447 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.ItemIcon", lpparam.classLoader, "onFinishInflate", object : Metho` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 470 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.home.launcher.ShortcutIcon", lpparam.classLoader, "fromXml", object : MethodHook()` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 495 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.home.launcher.ShortcutIcon", lpparam.classLoader, "createShortcutIcon", object : M` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 519 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.home.launcher.common.Utilities", lpparam.classLoader, "adaptTitleStyleToWallpaper"` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 545 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.ItemIcon", lpparam.classLoader, "onFinishInflate", object : Metho` |
| `tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt` | 583 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.ItemIcon", lpparam.classLoader, "onFinishInflate", object : Metho` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 30 | `?` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.pageindicators.AllAppsIndicator", lpparam.classLoader, "shouldHid` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 31 | `?` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.pageindicators.AllAppsIndicator", lpparam.classLoader, "hideAllAp` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 64 | `handleMessage` | `XposedHelpers.setAdditionalInstanceField(workspace, "mHandlerEx", mHandler)` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 92 | `handleMessage` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.ScreenView", lpparam.classLoader, "getSnapToScreenIndex", Int::cl` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 120 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.ScreenView", lpparam.classLoader, "getSnapUnitIndex", Int::class.` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 161 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "onCreate", Bundle::class.java, o` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 183 | `intercept` | `ModuleHelper.findAndHookMethod(DeviceConfigClass, "loadCellsCountConfig", Context::class.java, Boolean::class.javaPrimit` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 198 | `intercept` | `XposedHelpers.setStaticObjectField(DeviceConfigClass, "sFolderCellHeight", cellHeight)` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 207 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.ScreenUtils", lpparam.classLoader, "getScreenCellsSizeOptions", C` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 238 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.compat.LauncherCellCountCompatNoWord", lpparam.classLoader, "setL` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 256 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "isCellSizeChangedByTheme", obje` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 263 | `intercept` | `nowordHook = ModuleHelper.findAndHookMethod("com.miui.home.launcher.common.Utilities", lpparam.classLoader, "isNoWordMod` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 305 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "calcHotSeatsMarginTop", Cont` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 329 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "calcHotSeatsMarginBottom", C` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 353 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "calcHotSeatsHeight", Context` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 399 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "getWorkspaceCellPaddingTop",` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 407 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.util.DimenUtils1X", lpparam.classLoader, "getDimensionPixelSize",` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 433 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "getMiuiWidgetSizeSpec", object ` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 461 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.home.launcher.MIUIWidgetUtil", lpparam.classLoader, "getMiuiWidgetPadding", object` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 486 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.CellLayout", lpparam.classLoader, "setScreenType", Int::class.jav` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 507 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "getHotseatMaxCount", HookerC` |
| `tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt` | 512 | `intercept` | `ModuleHelper.findAndHookMethod("android.appwidget.AppWidgetHostView", lpparam.classLoader, "getAppWidgetInfo", object : ` |
| `tv/withaibuild/customiuizer/mods/PackagePermissions.kt` | 24 | `?` | `XposedHelpers.setStaticObjectField(dpgpiClass, "MIUI_SYSTEM_APPS", mySystemApps.toTypedArray())` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 41 | `?` | `ModuleHelper.hookAllMethods("com.android.settings.wifi.SavedAccessPointPreference", lpparam.classLoader, "onBindViewHold` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 67 | `intercept` | `ModuleHelper.findAndHookMethod("miuix.appcompat.app.AlertDialog\$Builder", lpparam.classLoader, "setTitle", Int::class.j` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 87 | `intercept` | `ModuleHelper.findAndHookMethod("miuix.appcompat.app.AlertDialog\$Builder", lpparam.classLoader, "setMessage", CharSequen` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 108 | `intercept` | `ModuleHelper.hookAllMethods("miuix.appcompat.app.AlertDialog", lpparam.classLoader, "onCreate", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 132 | `intercept` | `ModuleHelper.hookAllMethods("com.android.settings.wifi.MiuiSavedAccessPointsWifiSettings", lpparam.classLoader, "showDel` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 198 | `intercept` | `ModuleHelper.findAndHookMethod(raClass, lpparam.classLoader, "setupVisible", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 238 | `checkToast` | `ModuleHelper.hookAllMethods("com.android.server.notification.NotificationManagerService", lpparam.classLoader, "tryShowT` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 266 | `intercept` | `ModuleHelper.hookAllConstructors("com.android.server.policy.BaseMiuiPhoneWindowManager", lpparam.classLoader, object : M` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 301 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.policy.MiuiScreenOnProximityLock", lpparam.classLoader, "showHint", H` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 302 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.policy.MiuiScreenOnProximityLock", lpparam.classLoader, "prepareHintW` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 307 | `intercept` | `ModuleHelper.hookAllMethods("android.content.ContentResolver", lpparam.classLoader, "update", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 333 | `intercept` | `ModuleHelper.findAndHookMethod("android.content.ContentResolver", lpparam.classLoader, "insert", Uri::class.java, Conten` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 434 | `intercept` | `ModuleHelper.hookAllMethods("android.graphics.Bitmap", lpparam.classLoader, "compress", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 464 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.notification.NotificationManagerService", lpparam.classLoader, "showN` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 498 | `intercept` | `ModuleHelper.hookAllMethods(windowClass, lpparam.classLoader, "adjustWindowParamsLw", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 507 | `intercept` | `XposedHelpers.setAdditionalInstanceField(thisObject, "mPrevHideTimeout", XposedHelpers.getLongField(lp, "hideTimeoutMill` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 529 | `intercept` | `if (dur != 0L) XposedHelpers.setLongField(lp, "hideTimeoutMilliseconds", dur)` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 542 | `intercept` | `ModuleHelper.hookAllMethods(wpuClass, lpparam.classLoader, "getPerceptibleRecentAppList", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 575 | `intercept` | `XposedHelpers.setStaticObjectField(MIUIStorageConstants, "DIRECTORY_SCREENSHOT_PATH", ssPath)` |
| `tv/withaibuild/customiuizer/mods/System.kt` | 603 | `intercept` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarWifiView", lpparam.classLoader, "applyWifiState", h` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 38 | `?` | `ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "click", View::class.` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 85 | `checkVibration` | `ModuleHelper.findAndHookMethod("com.android.server.vibrator.VibratorManagerService", lpparam.classLoader, "systemReady",` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 98 | `intercept` | `XposedHelpers.setAdditionalInstanceField(thisObject, "mVibrationMode", Integer.parseInt(MainModule.mPrefs.getString("sys` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 102 | `onChange` | `XposedHelpers.setAdditionalInstanceField(thisObject, "mVibrationMode", MainModule.mPrefs.getStringAsInt("system_vibratio` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 107 | `onChange` | `XposedHelpers.setAdditionalInstanceField(thisObject, "mVibrationApps", MainModule.mPrefs.getStringSet("system_vibration_` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 111 | `onChange` | `XposedHelpers.setAdditionalInstanceField(thisObject, "mVibrationApps", MainModule.mPrefs.getStringSet("system_vibration_` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 123 | `onChange` | `ModuleHelper.hookAllMethods("com.android.server.vibrator.VibratorManagerService", lpparam.classLoader, "vibrate", object` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 148 | `intercept` | `ModuleHelper.hookAllMethods("com.android.server.audio.FocusRequester", lpparam.classLoader, "handleFocusLoss", object : ` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 194 | `updateAudioVisualizerState` | `ModuleHelper.findAndHookMethod("com.android.systemui.shade.MiuiNotificationPanelViewController", lpparam.classLoader, "o` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 257 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.CentralSurfacesImpl", lpparam.classLoader, "start",` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 272 | `intercept` | `ModuleHelper.findAndHookMethod(ScreenObserverCls, "onScreenTurnedOff", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 294 | `intercept` | `ModuleHelper.findAndHookMethod(ScreenObserverCls, "onScreenTurnedOn", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 322 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.CentralSurfacesImpl", lpparam.classLoader, "updateD` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 345 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.KeyguardStateControllerImpl", lpparam.classLoader,` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 372 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.shade.MiuiNotificationPanelViewController", lpparam.classLoader, "u` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 399 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.NotificationMediaManager", lpparam.classLoader, "updateMe` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 454 | `removeListener` | `XposedHelpers.setIntField(record, "events", newEvents)` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 461 | `removeListener` | `ModuleHelper.hookAllMethods("com.android.server.audio.AudioService", lpparam.classLoader, "requestAudioFocus", object : ` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 495 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.TelephonyRegistry", lpparam.classLoader, "notifyCallState", Int::clas` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 513 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.TelephonyRegistry", lpparam.classLoader, "notifyCallStateForPhoneId",` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 534 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.audio.AudioService\$VolumeController", lpparam.classLoader, "suppress` |
| `tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt` | 566 | `intercept` | `ModuleHelper.hookAllMethods("com.android.server.VibratorService", lpparam.classLoader, "doVibratorOn", object : MethodHo` |
| `tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` | 189 | `run` | `XposedHelpers.setObjectField(clockController, "mIs24", DateFormat.is24HourFormat(context))` |
| `tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` | 219 | `initSecondTicker` | `XposedHelpers.setAdditionalInstanceField(clockController, "secondTicker", ticker)` |
| `tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` | 225 | `initWeatherInfoHook` | `ModuleHelper.hookAllConstructors("com.android.systemui.statusbar.policy.MiuiStatusBarClockController", lpparam.classLoad` |
| `tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` | 296 | `intercept` | `ModuleHelper.hookAllConstructors("com.android.systemui.statusbar.policy.MiuiStatusBarClockController", lpparam.classLoad` |
| `tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` | 300 | `intercept` | `ModuleHelper.hookAllConstructors("com.android.systemui.statusbar.views.MiuiClock", lpparam.classLoader, object : MethodH` |
| `tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` | 454 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.views.MiuiClock", lpparam.classLoader, "updateTime", upda` |
| `tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` | 455 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.views.MiuiStatusBarClock", lpparam.classLoader, "updateTi` |
| `tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` | 457 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.views.MiuiClock", lpparam.classLoader, "onAttachedToWindo` |
| `tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` | 470 | `intercept` | `XposedHelpers.setObjectField(thisObject, "mAttached", true)` |
| `tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` | 483 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "onAt` |
| `tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` | 508 | `intercept` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.views.MiuiClock", lpparam.classLoader, "onDarkChanged", obje` |
| `tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` | 532 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.FakeStatusBarClockController", lpparam.classLoader` |
| `tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` | 598 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.qs.MiuiNotificationHeaderView", lpparam.classLoader, "updateResourc` |
| `tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` | 634 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.qs.MiuiNotificationHeaderView", lpparam.classLoader, "updateLayout"` |
| `tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` | 673 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.qs.MiuiNotificationHeaderView", lpparam.classLoader, "onFinishInfla` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 59 | `intercept` | `XposedHelpers.setObjectField(mColors, "mProtectionColor", XposedHelpers.getAdditionalInstanceField(mN, "mProtectionColor` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 60 | `intercept` | `XposedHelpers.setObjectField(mColors, "mPrimaryTextColor", XposedHelpers.getAdditionalInstanceField(mN, "mPrimaryTextCol` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 61 | `intercept` | `XposedHelpers.setObjectField(mColors, "mSecondaryTextColor", XposedHelpers.getAdditionalInstanceField(mN, "mSecondaryTex` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 72 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.row.MiuiExpandableNotificationRow", lpparam.` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 90 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", lpparam.clas` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 109 | `intercept` | `XposedHelpers.setObjectField(thisObject, "mNotificationColor", overflowColor)` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 129 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.row.NotificationBackgroundView", lpparam.cla` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 150 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.row.wrapper.NotificationViewWrapper", lppara` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 170 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.row.NotificationContentView", lpparam.classL` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 204 | `intercept` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.notification.row.NotificationContentInflaterInjector", lppar` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 242 | `intercept` | `val cs = XposedHelpers.newInstance(ColorScheme, primaryColor, dark, finalContentStyle)` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 258 | `intercept` | `XposedHelpers.setObjectField(mColors, "mProtectionColor", mProtectionColor)` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 259 | `intercept` | `XposedHelpers.setAdditionalInstanceField(mN, "mProtectionColor", mProtectionColor)` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 260 | `intercept` | `XposedHelpers.setObjectField(mColors, "mPrimaryTextColor", mPrimaryTextColor)` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 261 | `intercept` | `XposedHelpers.setAdditionalInstanceField(mN, "mPrimaryTextColor", mPrimaryTextColor)` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 262 | `intercept` | `XposedHelpers.setObjectField(mColors, "mSecondaryTextColor", mSecondaryTextColor)` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 263 | `intercept` | `XposedHelpers.setAdditionalInstanceField(mN, "mSecondaryTextColor", mSecondaryTextColor)` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 264 | `intercept` | `XposedHelpers.setAdditionalInstanceField(mN, "mNotifyBackgroundColor", bgColor)` |
| `tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt` | 278 | `intercept` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.notification.row.NotificationContentInflaterInjector", lppar` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 28 | `?` | `ModuleHelper.findAndHookMethod("com.android.server.display.DisplayPowerController", lpparam.classLoader, "initialize", o` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 36 | `intercept` | `XposedHelpers.setObjectField(thisObject, "mColorFadeEnabled", true)` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 37 | `intercept` | `XposedHelpers.setObjectField(thisObject, "mColorFadeFadesConfig", true)` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 79 | `onChange` | `ModuleHelper.hookAllMethods("com.android.server.power.PowerManagerService", lpparam.classLoader, "wakePowerGroupLocked",` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 109 | `intercept` | `ModuleHelper.hookAllConstructors("com.android.systemui.shade.MiuiNotificationPanelViewController", lpparam.classLoader, ` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 138 | `onChange` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.NotificationShadeDepthController\$updateBlurCallback\$1",` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 147 | `intercept` | `XposedHelpers.setAdditionalInstanceField(mBlurUtils, "mCustomBlurModifier", mCustomBlurModifier[0])` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 172 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.BlurUtilsExt", lpparam.classLoader, "applyBlur", V` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 196 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.controlcenter.phone.ControlPanelWindowManager", lpparam.classLoader` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 218 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.charge.container.MiuiChargeAnimationView", lpparam.classLoader, "getAnimationDu` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 247 | `constrainValue` | `ModuleHelper.findAndHookMethod("com.android.server.display.AutomaticBrightnessController", lpparam.classLoader, "clampSc` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 272 | `intercept` | `ModuleHelper.hookAllConstructors("com.android.server.display.AutomaticBrightnessController", lpparam.classLoader, object` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 285 | `intercept` | `XposedHelpers.setLongField(thisObject, "mBrighteningLightDebounceConfig", 1000L)` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 286 | `intercept` | `XposedHelpers.setLongField(thisObject, "mDarkeningLightDebounceConfig", 1200L)` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 295 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.display.DisplayPowerController", lpparam.classLoader, "clampScreenBri` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 320 | `intercept` | `ModuleHelper.hookAllConstructors("com.android.server.display.DisplayPowerController", lpparam.classLoader, object : Meth` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 346 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.display.DisplayPowerController", lpparam.classLoader, "setScreenState` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 397 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.power.PowerManagerService", lpparam.classLoader, "readConfigurationLo` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 411 | `intercept` | `XposedHelpers.setIntField(thisObject, "mMaximumScreenDimDurationConfig", 600000)` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 412 | `intercept` | `XposedHelpers.setFloatField(thisObject, "mMaximumScreenDimRatioConfig", opt)` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 424 | `intercept` | `ModuleHelper.hookAllMethods("com.android.server.ForceDarkAppListProvider", lpparam.classLoader, "fillDarkModeAppSettings` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 445 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.ForceDarkAppListManager", lpparam.classLoader, "getDarkModeAppList", ` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 451 | `intercept` | `XposedHelpers.setStaticBooleanField(Build::class.java, "IS_INTERNATIONAL_BUILD", true)` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 465 | `intercept` | `XposedHelpers.setStaticBooleanField(Build::class.java, "IS_INTERNATIONAL_BUILD", false)` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 474 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.ForceDarkAppListManager", lpparam.classLoader, "shouldShowInSettings"` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 502 | `intercept` | `ModuleHelper.hookAllConstructors("com.android.server.wm.WallpaperController", lpparam.classLoader, object : MethodHook()` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 516 | `intercept` | `XposedHelpers.setObjectField(thisObject, "mMaxWallpaperScale", scale)` |
| `tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt` | 521 | `onChange` | `XposedHelpers.setObjectField(thisObject, "mMaxWallpaperScale", value / 10.0f)` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 66 | `?` | `ModuleHelper.findAndHookMethod("com.android.keyguard.KeyguardPINView", lpparam.classLoader, "onFinishInflate", object : ` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 119 | `intercept` | `XposedHelpers.setObjectField(thisObject, "mViews", mViews)` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 133 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.internal.widget.LockPatternUtils\$StrongAuthTracker", lpparam.classLoader, i` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 134 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.internal.widget.LockPatternUtils", lpparam.classLoader, isAllowed, Int::clas` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 139 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.policy.PhoneWindowManager", lpparam.classLoader, "interceptPowerKeyDo` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 191 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.policy.PhoneWindowManager", lpparam.classLoader, "powerLongPress", Lo` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 192 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.policy.PhoneWindowManager", lpparam.classLoader, "showGlobalActions",` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 193 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.policy.PhoneWindowManager", lpparam.classLoader, "showGlobalActionsIn` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 260 | `isUnlocked` | `ModuleHelper.findAndHookMethod("com.android.systemui.keyguard.KeyguardViewMediator", lpparam.classLoader, "handleKeyguar` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 285 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.keyguard.KeyguardUpdateMonitor", lpparam.classLoader, "onFingerprintAuthenti` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 306 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.keyguard.KeyguardSecurityContainerController", lpparam.classLoader, "onInit"` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 341 | `onReceive` | `ModuleHelper.findAndHookMethod("com.android.systemui.keyguard.KeyguardViewMediator", lpparam.classLoader, "doKeyguardLoc` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 373 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.keyguard.KeyguardViewMediator", lpparam.classLoader, "setupLocked",` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 456 | `onReceive` | `ModuleHelper.findAndHookMethod("com.android.keyguard.KeyguardSecurityModel", lpparam.classLoader, "getSecurityMode", Int` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 495 | `intercept` | `ModuleHelper.hookAllConstructors("com.android.systemui.statusbar.policy.BluetoothControllerImpl", lpparam.classLoader, o` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 543 | `onReceive` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.BluetoothControllerImpl", lpparam.classLoader, "up` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 571 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.shade.NotificationsQuickSettingsContainer", lpparam.classLoader, "o` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 624 | `intercept` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.notification.interruption.KeyguardNotificationVisibilityProv` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 631 | `intercept` | `XposedHelpers.setObjectField(notification, "mHasShownAfterUnlock", false)` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 645 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.server.SecurityManagerService", lpparam.classLoader, "removeAccessControlPassLocke` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 673 | `saveLastCheck` | `XposedHelpers.setAdditionalInstanceField(userState, "mAccessControlLastCheckSaved",` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 692 | `checkLastCheck` | `XposedHelpers.setObjectField(userState, "mAccessControlLastCheck", mAccessControlLastCheck)` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 696 | `checkLastCheck` | `XposedHelpers.setObjectField(userState, "mAccessControlLastCheck", mAccessControlLastCheck)` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 703 | `checkLastCheck` | `ModuleHelper.findAndHookMethod("com.miui.server.SecurityManagerService", lpparam.classLoader, "addAccessControlPassForUs` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 734 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.server.SecurityManagerService", lpparam.classLoader, "checkAccessControlPassLoc` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 765 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.server.SecurityManagerService", lpparam.classLoader, "activityResume", Intent::` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 826 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.keyguard.clock.KeyguardClockContainer", lpparam.classLoader, "setVisibility"` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 827 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.keyguard.clock.KeyguardClockContainer", lpparam.classLoader, "onFinishInflat` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 846 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.keyguard.clock.KeyguardClockContainer", lpparam.classLoader, "doAnimationToA` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 880 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.ExpandedNotification", lpparam.classLoader, ` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 881 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.NotificationSettingsManager", lpparam.classL` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 918 | `hookUpdateTime` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.KeyguardIndicationController", lpparam.classLoader, "setI` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 952 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.KeyguardIndicationController", lpparam.classLoader, "upda` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 976 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.keyguard.injector.KeyguardBottomAreaInjector", lpparam.classLoader, "handleB` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 1014 | `intercept` | `ModuleHelper.findAndHookMethod(mTouchHandlerField.type, "handleMiuiTouch", MotionEvent::class.java, object : MethodHook(` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 1065 | `intercept` | `ModuleHelper.hookAllConstructors("com.miui.applicationlock.widget.MiuiNumericInputView", lpparam.classLoader, object : M` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 1137 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.charge.ChargeUtils", lpparam.classLoader, "getChargingHintText", Int::class.jav` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 1207 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.KeyguardIndicationTextView", lpparam.classLoader, "` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 1251 | `isKeyguardIndicationCaller` | `ModuleHelper.findAndHookMethod("com.android.keyguard.EmergencyButtonController", lpparam.classLoader, "updateEmergencyCa` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 1278 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.server.AccessController", lpparam.classLoader, "skipActivity", object : MethodHook` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 1313 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.KeyguardIndicationController", lpparam.classLoader, "upda` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 1320 | `intercept` | `XposedHelpers.setObjectField(thisObject, "mPersistentUnlockMessage", "")` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 1334 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView", lpparam.classLoader, "o` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 1361 | `intercept` | `ModuleHelper.hookAllMethods("com.android.server.wallpaper.WallpaperManagerService", lpparam.classLoader, "setWallpaper",` |
| `tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt` | 1461 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.locksettings.LockSettingsStrongAuth", lpparam.classLoader, "reschedul` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 44 | `?` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", lpparam.classLo` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 73 | `intercept` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", lpparam.classLo` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 103 | `intercept` | `XposedHelpers.setAdditionalInstanceField(thisObject, "expandNotifyRunnable", expandNotify)` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 119 | `intercept` | `ModuleHelper.hookAllConstructors("com.android.systemui.statusbar.policy.HeadsUpManager", lpparam.classLoader, object : M` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 134 | `intercept` | `XposedHelpers.setIntField(thisObject, "mMinimumDisplayTime", delay)` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 135 | `intercept` | `XposedHelpers.setIntField(thisObject, "mHeadsUpNotificationDecay", delay)` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 141 | `onChange` | `XposedHelpers.setIntField(thisObject, "mMinimumDisplayTime", delay2)` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 142 | `onChange` | `XposedHelpers.setIntField(thisObject, "mHeadsUpNotificationDecay", delay2)` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 157 | `onChange` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.HeadsUpManager", lpparam.classLoader, "removeHeads` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 158 | `onChange` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.HeadsUpManager", lpparam.classLoader, "removeOldHe` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 160 | `onChange` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.HeadsUpManager\$HeadsUpEntry", lpparam.classLoader` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 167 | `intercept` | `XposedHelpers.setObjectField(thisObject, "mRemoveHeadsUpRunnable", Runnable { })` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 178 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.HeadsUpManager", lpparam.classLoader, "onExpanding` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 185 | `intercept` | `XposedHelpers.setBooleanField(thisObject, "mReleaseOnExpandFinish", true)` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 210 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.row.MiuiNotificationMenuRow", lpparam.classL` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 320 | `intercept` | `ModuleHelper.findAndHookMethod("android.app.NotificationChannel", lpparam.classLoader, "isBlockable", HookerClassHelper.` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 321 | `intercept` | `ModuleHelper.findAndHookMethod("android.app.NotificationChannel", lpparam.classLoader, "setBlockable", Boolean::class.ja` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 342 | `intercept` | `ModuleHelper.findAndHookMethod("android.app.NotificationChannel", lpparam.classLoader, "isBlockable", HookerClassHelper.` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 343 | `intercept` | `ModuleHelper.findAndHookMethod("android.app.NotificationChannel", lpparam.classLoader, "setBlockable", Boolean::class.ja` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 366 | `intercept` | `XposedHelpers.setStaticBooleanField(NotifyManagerCls, "USE_WHITE_LISTS", false)` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 368 | `intercept` | `ModuleHelper.hookAllMethods("miui.util.NotificationFilterHelper", lpparam.classLoader, "isNotificationForcedEnabled", Ho` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 369 | `intercept` | `ModuleHelper.findAndHookMethod("miui.util.NotificationFilterHelper", lpparam.classLoader, "isNotificationForcedFor", Con` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 370 | `intercept` | `ModuleHelper.findAndHookMethod("miui.util.NotificationFilterHelper", lpparam.classLoader, "canSystemNotificationBeBlocke` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 371 | `intercept` | `ModuleHelper.findAndHookMethod("miui.util.NotificationFilterHelper", lpparam.classLoader, "containNonBlockableChannel", ` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 372 | `intercept` | `ModuleHelper.findAndHookMethod("miui.util.NotificationFilterHelper", lpparam.classLoader, "getNotificationForcedEnabledL` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 394 | `intercept` | `ModuleHelper.hookAllMethods("com.android.settings.notification.BaseNotificationSettings", lpparam.classLoader, "setPrefV` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 417 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.settings.notification.ChannelNotificationSettings", lpparam.classLoader, "se` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 431 | `intercept` | `XposedHelpers.setObjectField(thisObject, "mImportance", pref)` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 443 | `intercept` | `XposedHelpers.setObjectField(thisObject, "mBackupImportance", mBackupImportance2)` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 473 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.NotificationIconContainer", lpparam.classLoader, "r` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 484 | `intercept` | `XposedHelpers.setIntField(thisObject, "mMaxStaticIcons", opt)` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 485 | `intercept` | `XposedHelpers.setIntField(thisObject, "mMaxIconsOnLockscreen", opt)` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 500 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.HeadsUpManagerPhone\$HeadsUpEntryPhone", lpparam.cl` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 533 | `intercept` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.StatusBarNotificationPresenter", lpparam.classLoader, ` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 574 | `intercept` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.StatusBar", lpparam.classLoader, "updateNotification",` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 609 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.row.MiuiNotificationMenuRow", lpparam.classL` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 665 | `intercept` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.notification.policy.MiuiAlertManager", lpparam.classLoader, ` |
| `tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt` | 692 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.HeadsUpManagerInjector", lpparam.classLoader, "miu` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 20 | `?` | `ModuleHelper.hookAllMethods("com.android.server.logcat.LogcatManagerService", lpparam.classLoader, "onLogAccessRequested` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 44 | `intercept` | `ModuleHelper.hookAllMethods("com.android.server.pm.PackageManagerServiceUtils", lpparam.classLoader, "checkDowngrade", H` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 49 | `intercept` | `ModuleHelper.findAndHookMethod("android.util.apk.ApkSignatureVerifier", lpparam.classLoader, "getMinimumSignatureSchemeV` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 56 | `intercept` | `ModuleHelper.hookAllMethods(SignDetails, "checkCapability", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 81 | `intercept` | `ModuleHelper.hookAllConstructors("android.util.jar.StrictJarVerifier", lpparam.classLoader, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 94 | `intercept` | `XposedHelpers.setObjectField(thisObject, "signatureSchemeRollbackProtectionsEnforced", false)` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 102 | `intercept` | `ModuleHelper.hookAllMethods("android.util.jar.StrictJarVerifier", lpparam.classLoader, "verifyMessageDigest", HookerClas` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 103 | `intercept` | `ModuleHelper.hookAllMethods("android.util.jar.StrictJarVerifier", lpparam.classLoader, "verify", HookerClassHelper.retur` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 104 | `intercept` | `ModuleHelper.hookAllMethods("com.android.server.pm.PackageManagerServiceUtils", lpparam.classLoader, "verifySignatures",` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 105 | `intercept` | `ModuleHelper.hookAllMethods("com.android.server.pm.InstallPackageHelper", lpparam.classLoader, "doesSignatureMatchForPer` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 128 | `intercept` | `ModuleHelper.hookAllMethods("com.android.server.pm.InstallPackageHelper", lpparam.classLoader, "cannotInstallWithBadPerm` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 129 | `intercept` | `ModuleHelper.hookAllMethods("com.android.server.pm.permission.PermissionManagerServiceImpl", lpparam.classLoader, "shoul` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 150 | `intercept` | `ModuleHelper.findAndHookMethod("android.content.pm.ApplicationInfo", lpparam.classLoader, "isSignedWithPlatformKey", obj` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 180 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.wm.WindowState", lpparam.classLoader, "isSecureLocked", HookerClassHe` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 181 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.wm.WindowSurfaceController", lpparam.classLoader, "setSecure", Boolea` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 198 | `intercept` | `ModuleHelper.hookAllConstructors("com.android.server.wm.WindowSurfaceController", lpparam.classLoader, object : MethodHo` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 218 | `intercept` | `ModuleHelper.hookAllMethods("com.android.server.wm.WindowManagerServiceImpl", lpparam.classLoader, "notAllowCaptureDispl` |
| `tv/withaibuild/customiuizer/mods/SystemSecurityHooks.kt` | 240 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.server.SecurityManagerService\$LocalService", lpparam.classLoader, "checkAllowStar` |
| `tv/withaibuild/customiuizer/mods/SystemShareMenuHooks.kt` | 29 | `?` | `ModuleHelper.hookAllMethods("miui.securityspace.XSpaceResolverActivityHelper.ResolverActivityRunner", lpparam.classLoade` |
| `tv/withaibuild/customiuizer/mods/SystemShareMenuHooks.kt` | 125 | `intercept` | `ModuleHelper.hookAllMethods(ActQueryService, lpparam.classLoader, "queryIntentActivitiesInternal", hook)` |
| `tv/withaibuild/customiuizer/mods/SystemShareMenuHooks.kt` | 186 | `isRemoveApp` | `ModuleHelper.hookAllMethods("miui.securityspace.XSpaceResolverActivityHelper.ResolverActivityRunner", lpparam.classLoade` |
| `tv/withaibuild/customiuizer/mods/SystemShareMenuHooks.kt` | 285 | `intercept` | `ModuleHelper.hookAllMethods(ActQueryService, lpparam.classLoader, "queryIntentActivitiesInternal", hook)` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarBackgroundHooks.kt` | 62 | `hookWindowDecor` | `ModuleHelper.findAndHookMethod("com.android.internal.policy.PhoneWindow", lpparam.classLoader, "generateLayout", "com.an` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarBackgroundHooks.kt` | 90 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.internal.policy.PhoneWindow", lpparam.classLoader, "setStatusBarColor", Int:` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarBackgroundHooks.kt` | 109 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.internal.app.ToolbarActionBar", lpparam.classLoader, "setBackgroundDrawable"` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarBackgroundHooks.kt` | 127 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.internal.app.WindowDecorActionBar", lpparam.classLoader, "setBackgroundDrawa` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarIconHooks.kt` | 26 | `?` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.views.MiuiBatteryMeterView", lpparam.classLoader, "update` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarIconHooks.kt` | 55 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "onFi` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarIconHooks.kt` | 77 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.KeyguardStatusBarView", lpparam.classLoader, "onFin` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarIconHooks.kt` | 102 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.views.MiuiBatteryMeterView", lpparam.classLoader, "update` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarIconHooks.kt` | 174 | `updateAlarmVisibility` | `ModuleHelper.hookAllConstructors("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarPolicy", lpparam.classLoader, o` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarIconHooks.kt` | 207 | `intercept` | `XposedHelpers.setAdditionalInstanceField(mNextAlarmCallback, ALARM_POLICY_OWNER, WeakReference(thisObject))` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarIconHooks.kt` | 209 | `intercept` | `ModuleHelper.findAndHookMethod(mNextAlarmCallback.javaClass, "onAlarmChanged", Boolean::class.javaPrimitiveType!!, objec` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarIconHooks.kt` | 216 | `after` | `XposedHelpers.setAdditionalInstanceField(policy, ALARM_LAST_STATE, newState)` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarIconHooks.kt` | 219 | `after` | `XposedHelpers.setAdditionalInstanceField(policy, ALARM_NEXT_TIME, ModuleHelper.getNextMIUIAlarmTime(mContext))` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarIconHooks.kt` | 224 | `after` | `ModuleHelper.findAndHookMethod(mNextAlarmCallback.javaClass, "onNextAlarmChanged", AlarmManager.AlarmClockInfo::class.ja` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarIconHooks.kt` | 231 | `after` | `XposedHelpers.setAdditionalInstanceField(policy, ALARM_LAST_STATE, false)` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarIconHooks.kt` | 235 | `after` | `XposedHelpers.setAdditionalInstanceField(policy, ALARM_NEXT_TIME, ModuleHelper.getNextMIUIAlarmTime(mContext))` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarIconHooks.kt` | 251 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarWifiView", lpparam.classLoader, "applyWifiState", o` |
| `tv/withaibuild/customiuizer/mods/SystemStatusBarIconHooks.kt` | 262 | `intercept` | `XposedHelpers.setObjectField(wifiState, "showWifiStandard", opt == 2 && wifiStandard > 0)` |
| `tv/withaibuild/customiuizer/mods/SystemUI.kt` | 55 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.globalactions.GlobalActionsDialogLite", lpparam.classLoader, "creat` |
| `tv/withaibuild/customiuizer/mods/SystemUI.kt` | 59 | `after` | `val fastbootAction = XposedHelpers.newInstance(PowerActionClass, param.getThisObject())` |
| `tv/withaibuild/customiuizer/mods/SystemUI.kt` | 61 | `after` | `val recoveryAction = XposedHelpers.newInstance(PowerActionClass, param.getThisObject())` |
| `tv/withaibuild/customiuizer/mods/SystemUI.kt` | 67 | `after` | `ModuleHelper.findAndHookMethod(PowerActionClass, "onPress", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemUI.kt` | 75 | `before` | `val confirmDlg = XposedHelpers.newInstance(SystemUIDialogClass, mContext) as AlertDialog` |
| `tv/withaibuild/customiuizer/mods/SystemUI.kt` | 99 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.plugins.PluginEnablerImpl", lpparam.classLoader, "isEnabled", Compo` |
| `tv/withaibuild/customiuizer/mods/SystemUI.kt` | 111 | `before` | `ModuleHelper.hookAllMethods("com.android.systemui.toast.MIUIStrongToastControl", lpparam.classLoader, "showCustomStrongT` |
| `tv/withaibuild/customiuizer/mods/SystemUI.kt` | 134 | `before` | `ModuleHelper.hookAllMethods("com.android.systemui.toast.MIUIStrongToast", lpparam.classLoader, "showCustomStrongToast", ` |
| `tv/withaibuild/customiuizer/mods/SystemUI.kt` | 144 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.toast.MIUIStrongToast", lpparam.classLoader, "getWindowParam", obje` |
| `tv/withaibuild/customiuizer/mods/SystemUI.kt` | 156 | `after` | `ModuleHelper.findAndHookMethod("com.miui.charge.MiuiChargeController", lpparam.classLoader, "shouldShowChargeAnim", Hook` |
| `tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt` | 23 | `?` | `ModuleHelper.findAndHookMethod(StatusBarCls, lpparam.classLoader, "start", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt` | 33 | `after` | `XposedHelpers.setAdditionalInstanceField(param.getThisObject(), "mBatteryIndicator", indicator)` |
| `tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt` | 35 | `after` | `XposedHelpers.setAdditionalInstanceField(mNotificationIconAreaController, "mBatteryIndicator", indicator)` |
| `tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt` | 37 | `after` | `XposedHelpers.setAdditionalInstanceField(mBatteryController, "mBatteryIndicator", indicator)` |
| `tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt` | 43 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.shade.MiuiNotificationPanelViewController", lpparam.classLoader, "u` |
| `tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt` | 53 | `after` | `ModuleHelper.findAndHookMethod(StatusBarCls, lpparam.classLoader, "updateIsKeyguard", Boolean::class.javaPrimitiveType!!` |
| `tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt` | 61 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.NotificationIconAreaController", lpparam.classLoader, ` |
| `tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt` | 68 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.MiuiBatteryControllerImpl", lpparam.classLoader, "` |
| `tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt` | 78 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.BatteryControllerImpl", lpparam.classLoader, "fire` |
| `tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt` | 88 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.views.MiuiBatteryMeterView", lpparam.classLoader, "update` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 122 | `?` | `ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.MiuiVolumeDialogImpl", classLoader, "computeTimeoutH", ` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 172 | `onChange` | `ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.MiuiVolumeDialogImpl", classLoader, "updateDialogWindow` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 190 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.MiuiVolumeDialogImpl", classLoader, "showH", Int::class` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 203 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.Util", classLoader, "isSupportBlurS", HookerClassHelper` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 209 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.miui.volume.MiuiVolumeDialogImpl", loader, "vibrateH", HookerClassHelp` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 296 | `after` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.header.StatusHeaderController", classLoader, "adj` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 299 | `after` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.header.StatusHeaderController", classLoader, "onE` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 324 | `after` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.header.StatusHeaderController", classLoader, "cre` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 326 | `after` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.header.StatusHeaderController", classLoader, "upd` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 334 | `after` | `val constraintSet = XposedHelpers.newInstance(ConstraintSetClass)` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 397 | `after` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.tileview.QSTileItemView", pluginLoader, "updateSize", res` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 398 | `after` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.tileview.QSTileItemView", pluginLoader, "onFinishInflate"` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 399 | `after` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.tileview.QSTileItemView", pluginLoader, "updateContainerH` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 402 | `after` | `XposedHelpers.setObjectField(param.getThisObject(), "containerHeight", iconSize)` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 406 | `after` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelController", pluginLoader, "setUseSepara` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 418 | `before` | `XposedHelpers.setObjectField(param.getThisObject(), "useSeparatedPanels", bool)` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 431 | `before` | `XposedHelpers.setObjectField(param.getThisObject(), "panelMargin", marginEnd)` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 440 | `before` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelContentDistributor", pluginLoader, "dist` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 469 | `after` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelController", pluginLoader, "updatePanelS` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 508 | `before` | `ModuleHelper.hookAllMethods("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter\$Factory", pluginLoade` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 513 | `after` | `XposedHelpers.setAdditionalInstanceField(param.getResult(), "leftAdapter", true)` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 517 | `after` | `ModuleHelper.findAndHookMethod(spanSizeLookup.javaClass, "getSpanSize", Int::class.javaPrimitiveType!!, spanSizeHook)` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 527 | `before` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.header.HeaderSpaceController", pluginLoader, "get` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 528 | `before` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.security.SecurityFooterController", pluginLoader,` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 529 | `before` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.qs.EditButtonController", pluginLoader, "getSpanS` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 530 | `before` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.qs.QSListController\$EditModeDividerTextItem", pl` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 533 | `before` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelAnimController", pluginLoader, "updateVi` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 545 | `after` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelAnimController", pluginLoader, "forceToS` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 551 | `after` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelAnimController", pluginLoader, "onAnimUp` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 565 | `after` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelAnimController", pluginLoader, "onConfig` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 600 | `after` | `ModuleHelper.hookAllConstructors("miui.systemui.controlcenter.panel.main.MainPanelContentDistributor", pluginLoader, obj` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 622 | `after` | `XposedHelpers.setObjectField(thisObj, "childControllers", childControllers)` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 631 | `after` | `ModuleHelper.hookAllMethods("miui.systemui.controlcenter.panel.main.MainPanelContentDistributor", pluginLoader, "distrib` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 661 | `after` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.QSController", pluginLoader, "getCardStyleTileSpecs", obj` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 692 | `before` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", loader, "updateResources", ` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 694 | `after` | `XposedHelpers.setObjectField(param.getThisObject(), "iconColor", iconColor)` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 717 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.MiuiVolumeTimerDrawableHelper", pluginLoader, "initTime` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 735 | `after` | `XposedHelpers.setObjectField(param.getThisObject(), "mTimeSegmentTitle", mTimeSegmentTitle)` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 738 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.TimerItem", pluginLoader, "getTimePos", Int::class.java` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 763 | `before` | `XposedHelpers.setIntField(param.getThisObject(), "mCurrentSegment", 0)` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 767 | `after` | `XposedHelpers.setIntField(param.getThisObject(), "mCurrentSegment", prevSeg)` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 771 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.MiuiVolumeTimerDrawableHelper", pluginLoader, "updateDr` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 776 | `after` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", pluginLoader, "getCornerRad` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 792 | `after` | `ModuleHelper.hookAllMethods("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", pluginLoader, "getDisabledBack` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 793 | `after` | `ModuleHelper.hookAllMethods("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", pluginLoader, "getActiveBackgr` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 840 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBarView", lpparam.classLoader, "onInterc` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 841 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBarView", lpparam.classLoader, "onTouchE` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 842 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBarView", lpparam.classLoader, "onAttach` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 850 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBarView", lpparam.classLoader, "onDetach` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 908 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "click", View::class.` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 909 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "longClick", View::cl` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 910 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "secondaryClick", Vie` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 964 | `startShowPct` | `ModuleHelper.hookAllMethods("com.android.systemui.controlcenter.policy.MiuiBrightnessController", lpparam.classLoader, "` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 971 | `before` | `ModuleHelper.hookAllMethods("com.android.systemui.controlcenter.policy.MiuiBrightnessController", lpparam.classLoader, "` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 981 | `after` | `ModuleHelper.findAndHookMethod(mOnSeekBarChangeListener.javaClass, "onStartTrackingTouch", SeekBar::class.java, object :` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 992 | `before` | `ModuleHelper.hookAllMethods("com.android.systemui.controlcenter.policy.MiuiBrightnessController", lpparam.classLoader, "` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 999 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.controlcenter.policy.MiuiBrightnessController", lpparam.classLoader, "` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 1016 | `after` | `ModuleHelper.findAndHookMethod(MiuiVolumeDialogImpl, "showVolumeDialogH", Int::class.javaPrimitiveType!!, object : Metho` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 1024 | `after` | `ModuleHelper.findAndHookMethod(MiuiVolumeDialogImpl, "dismissH", Int::class.javaPrimitiveType!!, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 1030 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.miui.volume.MiuiVolumeDialogImpl\$VolumeSeekBarChangeListener", plugin` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 1071 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.volume.VolumeUI", lpparam.classLoader, "start", object : MethodHook` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 1075 | `after` | `XposedHelpers.setObjectField(volumeDialogControllerImpl, "mShowSafetyWarning", false)` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 1084 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "handleLongClick", Vi` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 1111 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "click", View::class.` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 1128 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "hand` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 1147 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.controlcenter.phone.ControlPanelWindowManager", lpparam.classLoader` |
| `tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt` | 1160 | `before` | `XposedHelpers.setObjectField(param.getThisObject(), "mDownX", motionEvent.rawX)` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 43 | `?` | `ModuleHelper.findAndHookMethod("com.android.systemui.SystemUIApplication", lpparam.classLoader, "onCreate", object : Met` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 50 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView", lpparam.classLoader, "u` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 59 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView", lpparam.classLoader, "o` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 105 | `registerLockScreenAlbumArtReceiver` | `ModuleHelper.hookAllConstructors(panelClass, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 157 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.NotificationMediaManager", lpparam.classLoader, "updateMe` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 162 | `after` | `XposedHelpers.setAdditionalStaticField(MiuiThemeUtilsClass, "mAlbumArtSource", null)` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 163 | `after` | `XposedHelpers.setAdditionalStaticField(MiuiThemeUtilsClass, "mAlbumArt", null)` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 217 | `isDefaultLockScreenTheme` | `ModuleHelper.findAndHookMethod("com.android.keyguard.injector.KeyguardBottomAreaInjector", lpparam.classLoader, "updateL` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 237 | `after` | `ModuleHelper.hookAllConstructors("com.android.keyguard.injector.KeyguardBottomAreaInjector", lpparam.classLoader, object` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 273 | `after` | `ModuleHelper.findAndHookMethod("com.android.keyguard.injector.KeyguardBottomAreaInjector", lpparam.classLoader, "updateR` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 274 | `after` | `ModuleHelper.findAndHookMethod("com.android.keyguard.injector.KeyguardBottomAreaInjector", lpparam.classLoader, "updateR` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 280 | `after` | `ModuleHelper.findAndHookMethod("com.android.keyguard.injector.KeyguardBottomAreaInjector", lpparam.classLoader, "updateI` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 311 | `after` | `ModuleHelper.findAndHookMethod("com.android.keyguard.KeyguardMoveHelper", lpparam.classLoader, "setTranslation", Float::` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 323 | `before` | `ModuleHelper.findAndHookMethod("com.android.keyguard.KeyguardMoveHelper", lpparam.classLoader, "endMotion", Float::class` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 340 | `before` | `ModuleHelper.hookAllMethods("com.android.keyguard.KeyguardMoveRightController", lpparam.classLoader, "onTouchDown", obje` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 345 | `before` | `ModuleHelper.hookAllMethods("com.android.keyguard.KeyguardMoveRightController", lpparam.classLoader, "onTouchMove", obje` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 355 | `before` | `ModuleHelper.findAndHookMethod(Activity::class.java, "onCreate", Bundle::class.java, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 369 | `after` | `XposedHelpers.setAdditionalInstanceField(act.application, "wasStartedFromLockScreen", true)` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 411 | `before` | `XposedHelpers.callMethod(mContext, "startActivityAsUser", intent, XposedHelpers.newInstance(UserHandle::class.java, user` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 424 | `before` | `ModuleHelper.findAndHookMethod("com.miui.systemui.util.CommonUtil", lpparam.classLoader, "startSettingsApp", openAppHook` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 427 | `before` | `ModuleHelper.findAndHookMethod("com.miui.systemui.util.CommonUtil", lpparam.classLoader, "startCalendarApp", Context::cl` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 430 | `before` | `ModuleHelper.findAndHookMethod("com.miui.systemui.util.CommonUtil", lpparam.classLoader, "startClockApp", openAppHook)` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 436 | `before` | `ModuleHelper.hookAllConstructors("com.android.keyguard.KeyguardEditorHelper", lpparam.classLoader, object : MethodHook()` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 441 | `after` | `XposedHelpers.setObjectField(param.getThisObject(), "mIsMagazinePreViewVisibility", true)` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 448 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.zen.ZenModeViewController", lpparam.classLoa` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 452 | `before` | `XposedHelpers.setObjectField(param.getThisObject(), "manuallyDismissed", true)` |
| `tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt` | 455 | `after` | `XposedHelpers.setObjectField(param.getThisObject(), "manuallyDismissed", manuallyDismissed)` |
| `tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | 41 | `?` | `ModuleHelper.findAndHookMethod("com.android.systemui.SystemUIApplication", lpparam.classLoader, "onCreate", object : Met` |
| `tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | 58 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.MiuiQSFactory", lpparam.classLoader, "createTile", Stri` |
| `tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | 65 | `before` | `XposedHelpers.setAdditionalInstanceField(tile, "customName", tileName)` |
| `tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | 73 | `before` | `ModuleHelper.findAndHookMethod(NfcTileCls, lpparam.classLoader, "isAvailable", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | 86 | `before` | `ModuleHelper.findAndHookMethod(NfcTileCls, lpparam.classLoader, "getTileLabel", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | 100 | `before` | `ModuleHelper.findAndHookMethod(NfcTileCls, lpparam.classLoader, "handleSetListening", Boolean::class.javaPrimitiveType!!` |
| `tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | 126 | `onChange` | `XposedHelpers.setAdditionalInstanceField(param.getThisObject(), "mSurfaceFlinger", mSurfaceFlinger)` |
| `tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | 150 | `onChange` | `ModuleHelper.findAndHookMethod(NfcTileCls, lpparam.classLoader, "handleShowStateMessage", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | 158 | `before` | `ModuleHelper.findAndHookMethod(NfcTileCls, lpparam.classLoader, "getLongClickIntent", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | 171 | `before` | `ModuleHelper.findAndHookMethod(NfcTileCls, lpparam.classLoader, "handleClick", View::class.java, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | 218 | `before` | `ModuleHelper.hookAllMethods(NfcTileCls, lpparam.classLoader, "handleUpdateState", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | 248 | `before` | `XposedHelpers.setObjectField(booleanState, "value", isEnable)` |
| `tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | 249 | `before` | `XposedHelpers.setObjectField(booleanState, "state", if (isEnable) 2 else 1)` |
| `tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | 251 | `before` | `XposedHelpers.setObjectField(booleanState, "label", tileLabel)` |
| `tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | 252 | `before` | `XposedHelpers.setObjectField(booleanState, "contentDescription", tileLabel)` |
| `tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | 253 | `before` | `XposedHelpers.setObjectField(booleanState, "expandedAccessibilityClassName", Switch::class.java.name)` |
| `tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | 256 | `before` | `XposedHelpers.setObjectField(booleanState, "icon", mIcon)` |
| `tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt` | 29 | `?` | `ModuleHelper.findAndHookMethod("com.android.systemui.shade.MiuiNotificationPanelViewController", lpparam.classLoader, "u` |
| `tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt` | 51 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.qs.MiuiQSHeaderView", lpparam.classLoader, "updateShortCutVisibilit` |
| `tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt` | 52 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.qs.MiuiNotificationHeaderView", lpparam.classLoader, "updateShortCu` |
| `tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt` | 57 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout", lppara` |
| `tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt` | 73 | `before` | `ModuleHelper.hookAllMethods(PendingIntent::class.java, "sendAndReturnResult", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt` | 85 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.StatusBarNotificationActivityStarter", lpparam.classLo` |
| `tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt` | 119 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.NotificationIconAreaController", lpparam.classLoade` |
| `tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt` | 132 | `before` | `XposedHelpers.setObjectField(param.getThisObject(), "mNotificationEntries", arrayList)` |
| `tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt` | 141 | `before` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.notification.collection.coordinator.CountLimitCoordinator", ` |
| `tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt` | 146 | `before` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.notification.collection.coordinator.FoldCoordinator", lppara` |
| `tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt` | 147 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.NotificationUtil", lpparam.classLoader, "sho` |
| `tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt` | 160 | `before` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProvider` |
| `tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt` | 161 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarPolicy", lpparam.classLoader, "up` |
| `tv/withaibuild/customiuizer/mods/SystemUIScreenshotHooks.kt` | 30 | `?` | `ModuleHelper.hookAllMethods("com.android.wm.shell.pip.PipTaskOrganizer", lpparam.classLoader, "onTaskAppeared", object :` |
| `tv/withaibuild/customiuizer/mods/SystemUIScreenshotHooks.kt` | 116 | `bindScreenshotVisibility` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment", lpparam.class` |
| `tv/withaibuild/customiuizer/mods/SystemUIScreenshotHooks.kt` | 132 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.navigationbar.NavigationBar", lpparam.classLoader, "onInit", hideNa` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 226 | `getIconTextView` | `XposedHelpers.setObjectField(iconView, "mNetworkSpeedNumberText", mNumber)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 228 | `getIconTextView` | `XposedHelpers.setObjectField(iconView, "mNetworkSpeedUnitText", mUnit)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 394 | `applyNetworkSpeedToRow` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "onFi` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 445 | `after` | `XposedHelpers.setObjectField(sbView, "mStatusBarLeftContainer", leftLayout)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 460 | `after` | `XposedHelpers.setObjectField(param.getThisObject(), "mSystemIconArea", rightLayout)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 523 | `after` | `XposedHelpers.setAdditionalInstanceField(param.getThisObject(), "leftLayout", leftLayout)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 524 | `after` | `XposedHelpers.setAdditionalInstanceField(param.getThisObject(), "rightLayout", rightLayout)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 525 | `after` | `XposedHelpers.setAdditionalInstanceField(param.getThisObject(), "dualRowsLayoutAdded", true)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 536 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "updateC` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 599 | `initDigitalSignalView` | `ModuleHelper.findAndHookMethod(mCallback.type, "onMobileStatusChanged", Boolean::class.javaPrimitiveType!!, "com.android` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 659 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyMobileState` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 660 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateState", st` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 661 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyDarkness` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 672 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.StatusBarIconControllerImpl", lpparam.classLoader, "se` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 693 | `before` | `XposedHelpers.setObjectField(subIconState, "visible", false)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 697 | `before` | `XposedHelpers.setObjectField(mainIconState, field, XposedHelpers.getObjectField(subIconState, field))` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 795 | `applyDualSignalDrawables` | `ModuleHelper.findAndHookMethod("com.android.systemui.SystemUIApplication", lpparam.classLoader, "onCreate", object : Met` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 822 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.StatusBarIconControllerImpl", lpparam.classLoader, "se` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 842 | `before` | `XposedHelpers.setObjectField(subIconState, "visible", false)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 849 | `before` | `XposedHelpers.setObjectField(mainIconState, field, XposedHelpers.getObjectField(subIconState, field))` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 886 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyMobileState` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 887 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateState", st` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 900 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyDarkness` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 913 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "onDarkChanged", ` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 949 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "setDripEnd", ` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1044 | `before` | `XposedHelpers.setStaticObjectField(MiuiIconManagerUtils, "RIGHT_BLOCK_LIST", rightBlockList)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1062 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "onAt` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1090 | `after` | `XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconRegistrationHandle", handle)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1106 | `after` | `XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconContainer", null)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1107 | `after` | `XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconManager", null)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1108 | `after` | `XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconRegistrationHandle", null)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1112 | `after` | `XposedHelpers.newInstance(IconsContainer, mStatusBar.context) as LinearLayout` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1136 | `after` | `XposedHelpers.newInstance(DarkIconManager,` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1162 | `after` | `XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconContainer", iconContainer)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1163 | `after` | `XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconManager", mDarkIconManager)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1167 | `after` | `XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconRegistrationHandle", handle)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1171 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiCollapsedStatusBarFragment", lpparam.classLoade` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1190 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView", lpparam.classLoader, "m` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1206 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "onFi` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1231 | `after` | `XposedHelpers.setAdditionalInstanceField(sbView, "clockPositionInitialized", true)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1234 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBarView", lpparam.classLoader, "updateLa` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1284 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "upda` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1289 | `before` | `XposedHelpers.setObjectField(param.getThisObject(), "mCurrentStatusBarType", 1)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1294 | `after` | `XposedHelpers.setObjectField(param.getThisObject(), "mCurrentStatusBarType", originType)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1366 | `humanReadableByteCount` | `ModuleHelper.findAndHookMethod(mBgHandlerField.type, "handleMessage", Message::class.java, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1406 | `before` | `ModuleHelper.hookAllMethods(NetworkSpeedController, "updateText", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1591 | `applyNetSpeedTextStyle` | `ModuleHelper.hookAllMethods("android.widget.TextView", lpparam.classLoader, "setTextAppearance", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1603 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.views.NetworkSpeedView", lpparam.classLoader, "setNetworkSpe` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1615 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.views.NetworkSpeedView", lpparam.classLoader, "onFinishIn` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1650 | `after` | `ModuleHelper.findAndHookMethod(mBgHandlerField.type, "handleMessage", Message::class.java, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1665 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.connectivity.MobileSignalController", lpparam.classLoader` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1681 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.shade.MiuiNotificationPanelViewController", lpparam.classLoader, "s` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1686 | `after` | `XposedHelpers.setObjectField(mFakeClock, "ncSwitching", true)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1694 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateMobileType` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1699 | `before` | `XposedHelpers.setObjectField(param.getArg(0), "showMobileDataTypeSingle", true)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1718 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyMobileState` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1719 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateState", st` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1755 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "setDripEnd", ` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1769 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.StatusBarContentInsetsProvider", lpparam.classLoade` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1774 | `before` | `ModuleHelper.hookAllConstructors("com.android.systemui.MiuiOperatorCustomizedPolicy\$MiuiOperatorConfig", lpparam.classL` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1794 | `before` | `XposedHelpers.setObjectField(mobileIconState, "visible", false)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1805 | `before` | `XposedHelpers.setObjectField(mobileIconState, "visible", false)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1809 | `before` | `XposedHelpers.setObjectField(mobileIconState, "roaming", false)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1812 | `before` | `XposedHelpers.setObjectField(mobileIconState, "volte", false)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1813 | `before` | `XposedHelpers.setObjectField(mobileIconState, "speechHd", false)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1817 | `before` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyMobileState` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1818 | `before` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateState", st` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1856 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.StatusBarIconControllerImpl", lpparam.classLoader, ` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1861 | `before` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.CommandQueue", lpparam.classLoader, "setIcon", String::cl` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1870 | `before` | `XposedHelpers.setObjectField(param.getArg(1), "visible", false)` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1888 | `before` | `ModuleHelper.findAndHookMethod("com.miui.clock.MiuiBaseClock", lpparam.classLoader, "updateViewsTextSize", object : Meth` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1894 | `after` | `ModuleHelper.findAndHookMethod("com.miui.clock.MiuiLeftTopLargeClock", lpparam.classLoader, "onLanguageChanged", String:` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1978 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyMobileState` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1979 | `after` | `ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateState", hi` |
| `tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt` | 1984 | `after` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.privacy.MiuiPrivacyControllerImpl", lpparam.classLoader, ` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 36 | `?` | `ModuleHelper.hookAllMethods(windowClass, lpparam.classLoader, rotMethod, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 72 | `intercept` | `ModuleHelper.hookAllConstructors("com.android.server.wm.DisplayRotation", lpparam.classLoader, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 85 | `intercept` | `XposedHelpers.setIntField(thisObject, "mAllowAllRotations", if (MainModule.mPrefs.getStringAsInt("system_allrotations2",` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 117 | `intercept` | `ModuleHelper.hookAllConstructors(sblCls, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 133 | `intercept` | `try { XposedHelpers.setBooleanField(thisObject, "mSpringBackEnable", false) } catch (ignore: Throwable) {}` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 147 | `intercept` | `ModuleHelper.hookAllConstructors(rrvCls, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 164 | `intercept` | `try { XposedHelpers.setBooleanField(thisObject, "mSpringEnabled", false) } catch (ignore: Throwable) {}` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 176 | `intercept` | `ModuleHelper.findAndHookMethod("android.widget.AbsListView", lpparam.classLoader, "initAbsListView", object : MethodHook` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 197 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.ExpandedNotification", lpparam.classLoader, ` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 198 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.NotificationSettingsManager", lpparam.classL` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 204 | `intercept` | `ModuleHelper.hookAllConstructors("com.android.server.wm.WindowSurfaceController", lpparam.classLoader, object : MethodHo` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 233 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.row.MiuiExpandableNotificationRow", lpparam.` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 292 | `intercept` | `ModuleHelper.hookAllMethods("android.util.MiuiMultiWindowAdapter", cl, "getFreeformBlackList", clearHook)` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 293 | `intercept` | `ModuleHelper.hookAllMethods("android.util.MiuiMultiWindowAdapter", cl, "getFreeformBlackListFromCloud", clearHook)` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 294 | `intercept` | `ModuleHelper.hookAllMethods("android.util.MiuiMultiWindowAdapter", cl, "setFreeformBlackList", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 313 | `intercept` | `ModuleHelper.findAndHookMethod("android.util.MiuiMultiWindowUtils", cl, "isForceResizeable", HookerClassHelper.returnCon` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 326 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.wm.MiuiFreeformUtilImpl", lpparam.classLoader, "supportsFreeform", Ho` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 381 | `shouldOpenInFreeForm` | `ModuleHelper.findAndHookMethod("com.android.server.wm.ActivityTaskManagerService", lpparam.classLoader, "onSystemReady",` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 416 | `onReceive` | `ModuleHelper.findAndHookMethod("com.miui.server.SecurityManagerService\$LocalService", lpparam.classLoader, "checkGameBo` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 452 | `intercept` | `ModuleHelper.hookAllMethods("com.android.server.wm.ActivityStarterImpl", lpparam.classLoader, "checkStartActivityByFreeF` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 486 | `intercept` | `ModuleHelper.findAndHookMethod(AtmClass, "updateResizeBlackList", Context::class.java, HookerClassHelper.DO_NOTHING)` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 487 | `intercept` | `ModuleHelper.findAndHookMethod(AtmClass, "getSplitScreenBlackListFromXml", HookerClassHelper.DO_NOTHING)` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 488 | `intercept` | `ModuleHelper.hookAllMethods(AtmClass, "inResizeBlackList", HookerClassHelper.returnConstant(false))` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 496 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.home.recents.views.RecentMenuView", lpparam.classLoader, "onMessageEvent", object ` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 522 | `intercept` | `XposedHelpers.setAdditionalInstanceField(thisObject, "multiWindowEnableRunnable", multiWindowEnableRunnable)` |
| `tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt` | 536 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.wm.WindowState", lpparam.classLoader, "getTouchOcclusionMode", object` |
| `tv/withaibuild/customiuizer/mods/utils/ControlCenterPluginRuntime.kt` | 113 | `?` | `ModuleHelper.hookAllMethods(` |
| `tv/withaibuild/customiuizer/mods/utils/ControlCenterPluginRuntime.kt` | 285 | `before` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl", classLoader, "handl` |
| `tv/withaibuild/customiuizer/mods/utils/ControlCenterPluginRuntime.kt` | 286 | `before` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl", classLoader, "onAtt` |
| `tv/withaibuild/customiuizer/mods/utils/ControlCenterPluginRuntime.kt` | 287 | `before` | `ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl", classLoader, "onDet` |
| `tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt` | 222 | `hookIconSlots` | `ModuleHelper.hookAllConstructors(` |
| `tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt` | 236 | `after` | `iconHolder = XposedHelpers.newInstance(StatusBarIconHolder)` |
| `tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt` | 237 | `after` | `XposedHelpers.setObjectField(iconHolder, "mType", iconType)` |
| `tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt` | 245 | `after` | `ModuleHelper.hookAllMethods(` |
| `tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt` | 280 | `hookNetworkSpeedView` | `ModuleHelper.findAndHookMethod(` |
| `tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt` | 308 | `hookMonitor` | `ModuleHelper.hookAllConstructors(` |
| `tv/withaibuild/customiuizer/mods/utils/LockScreenAlbumArtController.kt` | 141 | `onViewDetachedFromWindow` | `XposedHelpers.setAdditionalStaticField(cls, "mAlbumArtSource", art)` |
| `tv/withaibuild/customiuizer/mods/utils/LockScreenAlbumArtController.kt` | 142 | `onViewDetachedFromWindow` | `XposedHelpers.setAdditionalStaticField(cls, "mAlbumArt", null)` |
| `tv/withaibuild/customiuizer/mods/utils/LockScreenAlbumArtController.kt` | 185 | `ensureLifecycleListener` | `XposedHelpers.setAdditionalInstanceField(view, LIFECYCLE_LISTENER_FIELD, backgroundLifecycleListener)` |
| `tv/withaibuild/customiuizer/mods/utils/LockScreenAlbumArtController.kt` | 195 | `setViewBackground` | `XposedHelpers.setAdditionalInstanceField(view, APPLIED_DRAWABLE_FIELD, drawable)` |
| `tv/withaibuild/customiuizer/mods/utils/LockScreenAlbumArtController.kt` | 424 | `applyResult` | `XposedHelpers.setAdditionalStaticField(cls, "mAlbumArt", processed)` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 117 | `intercept` | `ModuleHelper.findAndHookMethod(amaCls, "onCreate", Bundle::class.java, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 161 | `intercept` | `ModuleHelper.hookAllMethods(frag.javaClass, "onPreferenceTreeClick", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 210 | `intercept` | `XposedHelpers.callMethod(act, "startActivityAsUser", launchIntent, XposedHelpers.newInstance(UserHandle::class.java, use` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 256 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.appmanager.AppManagerMainActivity", lpparam.classLoader, "onCreate", Bundle::cl` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 276 | `intercept` | `ModuleHelper.hookAllMethods(fragCls, lpparam.classLoader, "onActivityCreated", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 326 | `setAppState` | `ModuleHelper.findAndHookMethod("com.android.server.pm.PackageManagerServiceImpl", lpparam.classLoader, "canBeDisabled", ` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 349 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.appmanager.ApplicationsDetailsActivity", lpparam.classLoader, "onCreateOptionsM` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 389 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.appmanager.ApplicationsDetailsActivity", lpparam.classLoader, "onOptionsItemSel` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 439 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.appmanager.ApplicationsDetailsActivity", lpparam.classLoader, "onCreateOptionsM` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 495 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.networkassistant.ui.fragment.ShowAppDetailFragment", lpparam.classLoader, "init` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 502 | `intercept` | `if (mAppInfo != null) XposedHelpers.setBooleanField(mAppInfo, "isSystemApp", false)` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 513 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.networkassistant.service.FirewallService", lpparam.classLoader, "setSystemAppWifiR` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 519 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.powerkeeper.provider.PowerKeeperConfigureManager", lpparam.classLoader, "pkgHas` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 521 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.powerkeeper.provider.PreSetGroup", lpparam.classLoader, "initGroup", object : M` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 544 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.powerkeeper.provider.PreSetApp", lpparam.classLoader, "isPreSetApp", String::cl` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 545 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.powerkeeper.utils.Utils", lpparam.classLoader, "pkgHasIcon", HookerClassHelper.ret` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 550 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.powerkeeper.utils.CommonAdapter", lpparam.classLoader, "addPowerSaveWhitelistApps"` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 551 | `intercept` | `ModuleHelper.hookAllMethods("com.miui.powerkeeper.millet.MilletPolicy", lpparam.classLoader, "dealSleepModeWhiteList", o` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 571 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.powerkeeper.statemachine.ForceDozeController", lpparam.classLoader, "restoreWhi` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 617 | `showSideBar` | `ModuleHelper.hookAllConstructors(RegionSamplingHelper, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 668 | `onReceive` | `ModuleHelper.findAndHookMethod(mOnTouchListener.javaClass, "onTouch", View::class.java, MotionEvent::class.java, object ` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 689 | `intercept` | `ModuleHelper.findAndHookMethod(bgDrawable, "draw", Canvas::class.java, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 719 | `intercept` | `ModuleHelper.findAndHookMethod(RegionSamplingHelper, "onViewDetachedFromWindow", View::class.java, object : MethodHook()` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 776 | `intercept` | `ModuleHelper.hookAllConstructors(HandlerClass, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 818 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.privacyapps.ui.PrivacyAppsActivity", lpparam.classLoader, "onCreate", Bundle::c` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 849 | `intercept` | `ModuleHelper.findAndHookMethod(ContentResolver::class.java, "call", Uri::class.java, String::class.java, String::class.j` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 875 | `intercept` | `ModuleHelper.findAndHookMethod(URLConnection::class.java, "setUseCaches", Boolean::class.javaPrimitiveType!!, object : M` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 918 | `intercept` | `ModuleHelper.hookAllMethods(Settings.Global::class.java, "getInt", settingHook)` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 919 | `intercept` | `ModuleHelper.hookAllMethods(Settings.System::class.java, "getInt", settingHook)` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 920 | `intercept` | `ModuleHelper.hookAllMethods(Settings.Global::class.java, "getString", settingHook)` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 926 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.appmanager.ApplicationsDetailsActivity", lpparam.classLoader, "initView", objec` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 947 | `intercept` | `val defaultView = XposedHelpers.newInstance(BannerItem, actionContainer.context, null) as LinearLayout` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 995 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.securityscan.model.ModelFactory", lpparam.classLoader, "produceSystemGroupModel` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 996 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.securityscan.model.ModelFactory", lpparam.classLoader, "produceManualGroupModel` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 997 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.common.customview.ScoreTextView", lpparam.classLoader, "setScore", Int::class.j` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1013 | `intercept` | `ModuleHelper.findAndHookMethod(ContentResolver::class.java, "call", Uri::class.java, String::class.java, String::class.j` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1038 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.securityscan.ui.main.MainContentFrame", lpparam.classLoader, "onClick", View::c` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1113 | `intercept` | `ModuleHelper.findAndHookMethod(HandlerClass, "handleMessage", Message::class.java, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1180 | `intercept` | `ModuleHelper.findAndHookMethod(Settings.System::class.java, "getStringForUser", ContentResolver::class.java, String::cla` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1203 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.server.alarm.AlarmManagerService", lpparam.classLoader, "onBootPhase", Int::` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1227 | `onChange` | `XposedHelpers.setAdditionalInstanceField(thisObject, "mNextAlarmTime", ModuleHelper.getNextMIUIAlarmTime(mContext))` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1241 | `onChange` | `ModuleHelper.findAndHookMethod("com.android.server.alarm.AlarmManagerService", lpparam.classLoader, "getNextAlarmClockIm` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1274 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.incallui.InCallPresenter", lpparam.classLoader, "answerIncomingCall", Contex` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1303 | `intercept` | `ModuleHelper.hookAllMethods("com.android.incallui.InCallPresenter", lpparam.classLoader, "startUi", object : MethodHook(` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1345 | `intercept` | `ModuleHelper.findAndHookMethod("com.android.incallui.InCallActivity", lpparam.classLoader, "onCreate", Bundle::class.jav` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1509 | `intercept` | `ModuleHelper.findAndHookMethod("android.app.SharedPreferencesImpl", lpparam.classLoader, "getBoolean", String::class.jav` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1531 | `intercept` | `ModuleHelper.findAndHookMethod(Settings.System::class.java, "getInt", ContentResolver::class.java, String::class.java, I` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1551 | `intercept` | `ModuleHelper.findAndHookMethod(Settings.Secure::class.java, "getInt", ContentResolver::class.java, String::class.java, I` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1571 | `intercept` | `ModuleHelper.findAndHookMethod("com.miui.packageInstaller.ui.listcomponets.SafeModeTipViewObject\$ViewHolder", lpparam.c` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1602 | `intercept` | `ModuleHelper.findAndHookMethod(XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader), "get", Strin` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1631 | `intercept` | `ModuleHelper.hookAllMethods(InputMethodServiceInjectorClass, "addMiuiBottomView", object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1651 | `intercept` | `XposedHelpers.setStaticBooleanField(InputMethodUtil, "sIsGestureLineEnable", false)` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1652 | `intercept` | `ModuleHelper.findAndHookMethod(InputMethodUtil, "updateGestureLineEnable", Context::class.java, object : MethodHook() {` |
| `tv/withaibuild/customiuizer/mods/Various.kt` | 1658 | `intercept` | `XposedHelpers.setStaticBooleanField(InputMethodUtil, "sIsGestureLineEnable", false)` |

## INSTALLER_INFRASTRUCTURE

| File | Line | Function | Snippet |
|---|---|---|---|
| `tv/withaibuild/customiuizer/installers/GenericAppInstaller.kt` | 35 | `?` | `ModuleHelper.findAndHookMethod(` |
| `tv/withaibuild/customiuizer/mods/utils/HookInstallerFacade.kt` | 27 | `?` | `fun findAndHookMethod(className: String, classLoader: ClassLoader?, methodName: String, vararg parameterTypesAndCallback` |
| `tv/withaibuild/customiuizer/mods/utils/HookInstallerFacade.kt` | 42 | `?` | `val unhooker = XposedHelpers.findAndHookMethod(hookClass, methodName, *parameterTypesAndCallback)` |
| `tv/withaibuild/customiuizer/mods/utils/HookInstallerFacade.kt` | 87 | `?` | `fun findAndHookMethod(clazz: Class<*>, methodName: String, vararg parameterTypesAndCallback: Any?): CustomMethodUnhooker` |
| `tv/withaibuild/customiuizer/mods/utils/HookInstallerFacade.kt` | 90 | `?` | `val unhooker = XposedHelpers.findAndHookMethod(clazz, methodName, *parameterTypesAndCallback)` |
| `tv/withaibuild/customiuizer/mods/utils/HookInstallerFacade.kt` | 151 | `?` | `val unhooker = XposedHelpers.findAndHookMethod(hookClass, methodName, *parameterTypesAndCallback)` |
| `tv/withaibuild/customiuizer/mods/utils/HookInstallerFacade.kt` | 184 | `?` | `val unhooker = XposedHelpers.findAndHookMethod(clazz, methodName, *parameterTypesAndCallback)` |
| `tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt` | 115 | `?` | `fun findAndHookMethod(className: String, classLoader: ClassLoader?, methodName: String, vararg parameterTypesAndCallback` |
| `tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt` | 116 | `?` | `HookInstallerFacade.findAndHookMethod(className, classLoader, methodName, *parameterTypesAndCallback)` |
| `tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt` | 132 | `?` | `fun findAndHookMethod(clazz: Class<*>, methodName: String, vararg parameterTypesAndCallback: Any?): CustomMethodUnhooker` |
| `tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt` | 133 | `?` | `HookInstallerFacade.findAndHookMethod(clazz, methodName, *parameterTypesAndCallback)` |
| `tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt` | 161 | `?` | `val unhooker = XposedHelpers.findAndHookConstructor(hookClass, *parameterTypesAndCallback)` |
| `tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt` | 194 | `?` | `fun hookAllConstructors(className: String, classLoader: ClassLoader?, callback: MethodHook) {` |
| `tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt` | 209 | `?` | `val unhookers = XposedHelpers.hookAllConstructors(hookClass, callback)` |
| `tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt` | 248 | `?` | `fun hookAllConstructors(hookClass: Class<*>?, callback: MethodHook) {` |
| `tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt` | 262 | `?` | `val unhookers = XposedHelpers.hookAllConstructors(hookClass, callback)` |
| `tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt` | 301 | `?` | `fun hookAllMethods(className: String, classLoader: ClassLoader?, methodName: String, callback: MethodHook) {` |
| `tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt` | 316 | `?` | `val unhookers = XposedHelpers.hookAllMethods(hookClass, methodName, callback)` |
| `tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt` | 355 | `?` | `fun hookAllMethods(hookClass: Class<*>?, methodName: String, callback: MethodHook) {` |
| `tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt` | 369 | `?` | `val unhookers = XposedHelpers.hookAllMethods(hookClass, methodName, callback)` |
| `tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt` | 435 | `?` | `val ok = XposedHelpers.hookAllMethods(hookClass, methodName, callback).isNotEmpty()` |
| `tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt` | 484 | `?` | `val ok = XposedHelpers.hookAllMethods(hookClass, methodName, callback).isNotEmpty()` |
| `tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt` | 603 | `?` | `val userHandle = XposedHelpers.newInstance(UserHandle::class.java, user) as UserHandle` |
| `tv/withaibuild/customiuizer/mods/utils/PreferenceObserverRegistry.kt` | 55 | `?` | `XposedHelpers.setAdditionalInstanceField(owner, PREF_OBSERVER_FIELD, prefObserver)` |
| `tv/withaibuild/customiuizer/mods/utils/SystemUiBootstrapCoordinator.kt` | 103 | `before` | `ModuleHelper.findAndHookMethod(` |

## API_BRIDGE

| File | Line | Function | Snippet |
|---|---|---|---|
| `tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java` | 598 | `findFirstFieldByExactType` | `* Look up a method and hook it. See {@link #findAndHookMethod(String, ClassLoader, String, Object...)}` |
| `tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java` | 601 | `findAndHookMethod` | `public static CustomMethodUnhooker findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallb` |
| `tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java` | 621 | `findAndHookMethod` | `public static CustomMethodUnhooker findAndHookMethod(String className, ClassLoader classLoader, String methodName, Objec` |
| `tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java` | 622 | `findAndHookMethod` | `return findAndHookMethod(findClass(className, classLoader), methodName, parameterTypesAndCallback);` |
| `tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java` | 649 | `findMethodExactIfExists` | `* <p>See {@link #findAndHookMethod(String, ClassLoader, String, Object...)} for details about` |
| `tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java` | 877 | `findMethodBestMatch` | `public static Set<CustomMethodUnhooker> hookAllConstructors(Class<?> hookClass, MethodHook callback) {` |
| `tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java` | 894 | `findMethodBestMatch` | `public static Set<CustomMethodUnhooker> hookAllMethods(Class<?> hookClass, String methodName, MethodHook callback) {` |
| `tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java` | 1035 | `getParametersString` | `* Look up a constructor and hook it. See {@link #findAndHookMethod(String, ClassLoader, String, Object...)}` |
| `tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java` | 1065 | `doHookConstructor` | `* Look up a constructor and hook it. See {@link #findAndHookMethod(String, ClassLoader, String, Object...)}` |

## RESOURCE_INFRASTRUCTURE

| File | Line | Function | Snippet |
|---|---|---|---|
| `tv/withaibuild/customiuizer/mods/utils/ResourceHooks.kt` | 226 | `initThemeHook` | `return ModuleHelper.findAndHookMethod(` |
| `tv/withaibuild/customiuizer/mods/utils/ResourceHooks.kt` | 340 | `installGetter` | `ModuleHelper.findAndHookMethod(Resources::class.java, kind.methodName, *kind.paramTypes, hook)` |

