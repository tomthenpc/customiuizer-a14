# C0 — Architecture C Status Bar Height Fact Audit

**Repository:** `tomthenpc/customiuizer-a14`  
**Oracle SHA:** `2c4efeafc8655855b824b72ecbf6106641b04a8e`  
**Branch:** `devin/a14-architecture-c-r14.20.0`  
**Generated:** 2026-08-10  
**Scope:** `SystemStatusBarInsetsHooks.kt` and its direct dependencies only  

> C0 does not implement Architecture C production code.  This document is a docs-only artifact.

---

## C0.1 Git Boundary

```text
local branch:  devin/a14-architecture-c-r14.20.0
local HEAD:    2c4efeafc8655855b824b72ecbf6106641b04a8e
merge-base:    2c4efeafc8655855b824b72ecbf6106641b04a8e
origin heads:
  aff0449b...  refs/heads/devin/a14-optimization-r14.18.8
  2c4efeaf...  refs/heads/devin/a14-production-quality-r14.18.8
  3f9d6ba6...  refs/heads/main
```

- Local HEAD equals the r14.18.8 oracle and equals `origin/devin/a14-production-quality-r14.18.8`.
- New branch ancestry is strictly from `2c4efeaf...`.
- Working tree is clean before this docs-only commit.

---

## C0.2 Hook Table

| # | Target class | Target method | Process | Hook phase | Callback frequency | Purpose |
|---|--------------|---------------|---------|------------|--------------------|---------|
| H1 | `android.view.InsetsSource` | `setFrame(Rect)` / `setFrame(int,int,int,int)` | system_server | `SYSTEM_SERVER_STARTING` | Per InsetsSource frame change (very high) | Rewrite the status-bar source frame bottom to `originalTop + configuredPx`. |
| H2 | `com.android.server.wm.DisplayPolicy` | `layoutWindowLw` | system_server | `SYSTEM_SERVER_STARTING` | Per window layout (high) | Set `WindowManager.LayoutParams.height = configuredPx` for the status bar window before the original layout runs. |
| H3 | `com.android.server.wm.WindowState` | `setFrames(ClientWindowFrames, ...)` | system_server | `SYSTEM_SERVER_STARTING` | Per WindowState frame commit (high) | Fallback: rewrite `ClientWindowFrames.frame.bottom` to `top + configuredPx` if the ROM layout did not honour `mAttrs.height`. |
| H4 | `com.android.server.wm.DisplayPolicy$DecorInsets$Info` | `update(DisplayContent, int, int, int)` | system_server | `SYSTEM_SERVER_STARTING` | Per rotation/config display frame build (medium) | Expand `mNonDecorInsets.top` and `mNonDecorFrame.top` so app bounds follow the taller status bar. |
| H5 | *(resource replacement)* | `status_bar_height_default`, `status_bar_height`, `status_bar_height_portrait`, `status_bar_height_landscape` | any package `PACKAGE_READY` | `PACKAGE_READY` | Once per package at load / once per preference change | Replace framework dimen resources so SystemUI and other packages read the configured dp height. |

### Hook wiring

```text
MainModule.handleLoadPackage
  -> SystemServerInstaller.install(lpparam)
       -> FeatureInstallRegistry.installAll(SYSTEM_SERVER, SYSTEM_SERVER_STARTING, mPrefs)
            -> StatusBarHeightInsetsFeature (enabled if system_statusbarheight > 11)
                 -> SystemStatusBarInsetsHooks.StatusBarInsetsHeightHook(lpparam)

MainModule.handleLoadPackage
  -> CommonPackageFeatures.hasEnabledFeature(mPrefs, pkg)
       -> StatusBarHeightFeature (enabled if system_statusbarheight > 11)
            -> System.StatusBarHeightHook(lpparam)
                 -> ModuleHelper.replacePkgAndFrameworkValue(...)
```

---

## C0.3 ABI Table

| # | Class | Field / Method | Constant | How resolved | Where cached | Fallback |
|---|-------|----------------|----------|--------------|--------------|----------|
| A1 | `android.view.InsetsSource` | `setFrame(Rect)` and `setFrame(int,int,int,int)` | — | `getDeclaredMethods().filter { name == "setFrame" }` at install | Not cached as Method (Xposed `hookAllMethods` keeps its own handles) | If neither overload exists, log and return; do not install hook. |
| A2 | `android.view.InsetsSource` | `getType()` | — | `hasMethod()` reflection check at install | Not cached as Method; callback uses `typeField` or `XposedHelpers.callMethod` | If `getType()` fails, callback returns `TYPE_UNRESOLVED` and proceeds. |
| A3 | `android.view.InsetsSource` | `mType` | — | `resolveIntField(insetsSourceClass, "mType")` at install | `SetFrameCallback.typeField` (Field) | If field invalid, callback falls back to `XposedHelpers.callMethod(source, "getType")`. |
| A4 | `android.view.InsetsSource` | `getId()` | — | `hasMethod()` at install | `SetFrameCallback.hasGetId` (Boolean) | Diagnostic source id uses `null` when false. |
| A5 | `android.view.InsetsState` | `ITYPE_STATUS_BAR` / `ITYPE_NAVIGATION_BAR` / `ITYPE_DISPLAY_CUTOUT` | static int | `getStaticInt()` at install | Stored in `InsetsSourceAbi` only | If any missing, legacy encoding is rejected. |
| A6 | `android.view.WindowInsets.Type` | `statusBars()`, `navigationBars()`, `displayCutout()` | public masks | `safePublicType()` at install | Stored in `InsetsSourceAbi` only | If missing, modern encoding is rejected. |
| A7 | `com.android.server.wm.WindowState` | `mAttrs` | — | `resolveDeclaredField()` at install and lazily self-installs on first use | `SystemStatusBarInsetsHooks.windowStateAttrsField` (volatile Field) | Falls back to `XposedHelpers.getObjectField(win, "mAttrs")` if class differs. |
| A8 | `android.view.WindowManager.LayoutParams` | `type` | `TYPE_STATUS_BAR = 2000` | `resolveDeclaredField()` at install and lazily self-installs on first use | `SystemStatusBarInsetsHooks.layoutParamsTypeField` (volatile Field) | Falls back to `XposedHelpers.getIntField(attrs, "type")`. |
| A9 | `com.android.server.wm.WindowState` | `getFrame()` | — | `getMethod("getFrame")` at install | `windowStateGetFrameMethod` (volatile Method) | Falls back to `XposedHelpers.callMethod` and then `mWindowFrames.mFrame`. |
| A10 | `com.android.server.wm.WindowState` | `getDisplayMetrics()` | — | `getMethod("getDisplayMetrics")` at install | `windowStateGetDisplayMetricsMethod` (volatile Method) | Falls back to `XposedHelpers.getObjectField(win, "mDisplayContent")` -> `getDisplayMetrics()`. |
| A11 | `com.android.server.wm.WindowState` | `getDisplayId()` | — | `getMethod("getDisplayId")` at install | `windowStateGetDisplayIdMethod` (volatile Method) | Falls back to `XposedHelpers.callMethod(win, "getDisplayId")`. |
| A12 | `com.android.server.wm.ClientWindowFrames` | `frame` | — | `getField("frame")` via `resolveClientWindowFramesClass()` at install | `clientWindowFramesFrameField` | Falls back to `XposedHelpers.getObjectField(clientFrames, "frame")`. |
| A13 | `com.android.server.wm.ClientWindowFrames` | `displayFrame`, `parentFrame` | — | resolved at install | `clientWindowFramesDisplayFrameField`, `clientWindowFramesParentFrameField` | Not used by current hook (read only for completeness). |
| A14 | `com.android.server.wm.DisplayPolicy$DecorInsets$Info` | `mNonDecorInsets`, `mNonDecorFrame` | — | `getDeclaredField()` at install | `decorInfoNonDecorInsetsField`, `decorInfoNonDecorFrameField` | If missing, `installDecorInsetsInfoHook` returns; feature works without it. |

---

## C0.4 Runtime State Table

| # | Name | Type | Owner | Thread | Publication | Lifetime | Hot/Cold |
|---|------|------|-------|--------|-------------|----------|----------|
| S1 | `typeInfo` | `InsetsTypeInfo?` | `SystemStatusBarInsetsHooks` singleton | LSPosed init thread | plain var, written once after cold install | process | cold |
| S2 | `hookInstalled` | `Boolean` | `SystemStatusBarInsetsHooks` | LSPosed init thread | plain var | process | cold |
| S3 | `statusBarWindowRef` | `WeakReference<Any>?` | `SystemStatusBarInsetsHooks` | WMS layout / observer threads | `@Volatile` | until GC | hot (read in `requestStatusBarTraversal`) |
| S4 | `statusBarWindows` | `Array<WeakReference<Any>>` | `SystemStatusBarInsetsHooks` | WMS layout thread | `@Volatile`, rebuilt under `synchronized(this)` | bounded by `MAX_TRACKED_DISPLAYS` (4) | hot (identity fast path) |
| S5 | `typeMatchObserved` | `Boolean` | `SystemStatusBarInsetsHooks` | WMS layout thread | `@Volatile` | process | hot (gates fallback probe) |
| S6 | `fallbackProbeBudget` | `AtomicInteger` | `SystemStatusBarInsetsHooks` | WMS layout thread | atomic | process | warm (only until first type match) |
| S7 | `windowStateAttrsField` | `Field?` | `SystemStatusBarInsetsHooks` | WMS layout thread | `@Volatile` | process | hot (cached, self-installs) |
| S8 | `layoutParamsTypeField` | `Field?` | `SystemStatusBarInsetsHooks` | WMS layout thread | `@Volatile` | process | hot |
| S9 | `windowStateClass` | `Class<*>?` | `SystemStatusBarInsetsHooks` | LSPosed init / layout | `@Volatile` | process | hot (allocation-free type test) |
| S10 | `lastRefreshGeneration` | `AtomicLong` | `SystemStatusBarInsetsHooks` | observer / WMS thread | atomic | process | hot (coalesces traversal requests) |
| S11 | `clientWindowFramesClass` / `clientWindowFramesFrameField` | `Class<*>?` / `Field?` | `SystemStatusBarInsetsHooks` | LSPosed init / `setFrames` | plain vars, written at install | process | hot |
| S12 | `windowStateGetFrameMethod` / `getDisplayMetricsMethod` / `getDisplayIdMethod` | `Method?` | `SystemStatusBarInsetsHooks` | layout / `setFrames` | `@Volatile` | process | hot |
| S13 | `loggedCritical` / `loggedRejection` / `loggedLiveKeys` | `LinkedHashSet<String>` | `SystemStatusBarInsetsHooks` | any thread | `synchronized(set)` | bounded (16 each) | warm (only on first-hit logs) |
| S14 | `rejectionLoggingExhausted` | `Boolean` | `SystemStatusBarInsetsHooks` | any thread | `@Volatile` | process | warm |
| S15 | `layoutLogStamps` / `windowFrameLogStamps` / `clientFrameLogStamps` | `AtomicLongArray` (size 4) | `SystemStatusBarInsetsHooks` | WMS layout thread | atomic | process | hot (generation gating) |
| S16 | `statusSourceLogStamp` / `reflectionFailureLogStamp` / `invalidShapeLogStamp` | `AtomicLong` | `SystemStatusBarInsetsHooks` | any thread | atomic | process | hot (per-generation once) |
| S17 | `StatusBarHeightConfig.enabled` | `Boolean` | `StatusBarHeightConfig` object | preference observer thread | `@Volatile` | process | hot |
| S18 | `StatusBarHeightConfig.configuredDp` | `Int` | `StatusBarHeightConfig` object | preference observer thread | `@Volatile` | process | hot |
| S19 | `StatusBarHeightConfig.configuredPx` | `Int` | `StatusBarHeightConfig` object | preference observer thread | `@Volatile` | process | hot |
| S20 | `StatusBarHeightConfig.densityDpi` / `density` | `Int` / `Float` | `StatusBarHeightConfig` object | preference observer / layout | `@Volatile` | process | hot / warm |
| S21 | `StatusBarHeightConfig.generation` | `AtomicLong` | `StatusBarHeightConfig` object | preference observer | atomic | process | hot (log gating) |
| S22 | `StatusBarHeightInsetsFeature` instance | `BaseSystemServerFeature` | `FeatureInstallRegistry` / GC | LSPosed init thread | local var | install only | cold |

### Notes

- All `WeakReference` are to `WindowState` instances, not to Activities/Views, and the owning array is `@Volatile`.  This does not create a strong owner cycle.
- `statusBarWindowRef` is a single WeakReference used to request a traversal on the most recently laid-out status bar.  It is reset on `resetForTest()` only.
- `lastRefreshGeneration` is an `AtomicLong`, but `getAndSet` is used to coalesce duplicate requests.  This is correct but means the publication is the atomic itself, not an immutable snapshot.

---

## C0.5 Config Table

| Preference key | Default | Reader | Observer | Null-key behavior | Live update | Hot-path access |
|----------------|---------|--------|----------|-------------------|-------------|-----------------|
| `system_statusbarheight` | 11 (sentinel) | `StatusBarHeightConfig.resolveHeightDp()` / `isEnabled()` | `statusBarHeightObserver` registered via `PreferenceObserverRegistry` | `PrefMap.getInt(key, DEFAULT_SENTINEL)` returns default | `reconfigure(MainModule.mPrefs)` on observer; bumps `generation` only on effective change | `StatusBarHeightConfig.enabled` / `configuredPx` / `configuredDp` volatile fields only. No `PrefMap` in callbacks. |

- Sentinel 11 means disabled; the configured dp still resolves to 27 (framework default) for resource replacement.
- Any value > 11 enables the feature.
- Density is cached at `configure()` time.  `reconfigure()` preserves the cached density and re-computes px from it.
- `recomputePx(metrics)` is called from `onLayoutWindowLw` only for `displayId == 0` and only when `metrics.densityDpi != StatusBarHeightConfig.densityDpi`.

---

## C0.6 Behavior Table

| Scenario | Expected behavior | Current oracle evidence |
|----------|-------------------|-------------------------|
| disabled (11) | `chain.proceed()` immediately; no status-bar discovery; only known status bar gets original height restored | `onLayoutWindowLw` returns `chain.proceed()` after `StatusBarHeightConfig.enabled` check; disabled path restores via `restoreStatusBarWindowHeight` if known. |
| enabled target | `WindowState.mAttrs.height` set before layout; `ClientWindowFrames.frame.bottom` adjusted; InsetsSource frame bottom adjusted | H2 sets attrs.height, H3 rewrites frame bottom, H1 rewrites source frame. |
| non-target (app window) | `isStatusBarWindow()` returns false; `chain.proceed()` untouched | `isStatusBarWindow` returns false for non-STATUS_BAR type and non-fallback package/toString. |
| unknown WindowState (not yet known, not status type) | discovery via `mAttrs.type` fallback; if `typeMatchObserved == true` and type != status, early reject; otherwise bounded packageName/toString probe | `isStatusBarWindow` first checks `isKnownStatusBarWindow`, then `readAttrsType`, then `typeMatchObserved` short-circuit, then `fallbackProbeBudget` probe. |
| density unchanged | uses cached `configuredPx` | `onLayoutWindowLw` only calls `recomputePx` when `metrics.densityDpi != StatusBarHeightConfig.densityDpi`. |
| density changed | `recomputePx(metrics)` updates `StatusBarHeightConfig.configuredPx` and `densityDpi` | `recomputePx` is synchronized and bumps generation only on effective change. |
| display change (secondary display) | local px computed from the display's metrics without mutating global config | `onLayoutWindowLw` uses `StatusBarHeightConfig.configuredPxFor(configuredDp, metrics)` for `displayId != 0`. `onSetFrames` also uses local metrics for non-zero display. |
| frame unchanged | `chain.proceed()` with original args, no Rect allocation | `SetFrameCallback.adjustArgs` returns `null` when `newBottom == oldBottom`; `onSetFrames` returns `chain.proceed()` when bottom unchanged. |
| config live update | preference observer calls `reconfigure()` then `requestStatusBarTraversal()` | `statusBarHeightObserver.onChange` filters to `PREF_KEY`, calls `reconfigure`, and if `change.changed` calls `requestStatusBarTraversal`. |
| null preference invalidation | `PrefMap` returns default 11; feature disabled | `getInt(key, DEFAULT_SENTINEL)` returns default. |
| fallback ABI (legacy InsetsState) | `selectTypeEncoding` picks `LEGACY_INTERNAL` if one-int constructor, no modern constructor, getType, and both legacy status/nav constants exist | `resolveInsetsSourceAbi` and `selectTypeEncoding` implement this. |
| partial ABI (missing nav/cutout) | missing types filled with -1; status type is sufficient for the hook | `InsetsTypeInfo` constructor uses `takeIf { it.isResolvedType() } ?: -1` for nav/cutout. |
| original RuntimeException | propagates through `chain.proceed()` without suppression | `onLayoutWindowLw` / `onSetFrames` / `onDecorInsetsInfoUpdate` do not catch exceptions from `chain.proceed()`. |
| OOM/fatal | propagates; `FatalErrors.unwrapAndRethrowIfFatal(t)` in all `catch (Throwable)` blocks before logging | Verified by code and unit tests. |
| nested/reentry | `hookInstalled` guard and `FeatureInstallState` ensure one install per process; callback does not re-enter itself | `StatusBarInsetsHeightHook` returns immediately if `hookInstalled`. `FeatureInstallRegistry` checks `FeatureInstallState.beginInstall`. |
| lifecycle cleanup | observer is process-scoped, no explicit unregistration; feature is killed with the process | `PreferenceObserverRegistry.observePreferenceChange(statusBarHeightObserver, StatusBarHeightConfig)` uses the object as owner (StatusBarHeightConfig is a Kotlin object, lives for the process). |

---

## C0.7 Fatal / Fallback Table

| # | Location | Original chain / module reflection | Fatal behavior | Recoverable fallback |
|---|----------|-----------------------------------|----------------|----------------------|
| F1 | `StatusBarInsetsHeightHook()` | class/method reflection at install | OOM/VirtualMachineError/ThreadDeath rethrown via `FatalErrors.unwrapAndRethrowIfFatal`; other exceptions logged and `return` (feature fails closed) | If `InsetsSource` class or `setFrame` missing, return without installing hooks. |
| F2 | `installDisplayPolicyHook()` | `findClassIfExists` for `DisplayPolicy` | class not found -> log and return; no crash | Hook not installed; InsetsSource still works. |
| F3 | `installWindowStateHook()` | `findClassIfExists` for `WindowState`, `resolveClientWindowFramesClass()` | class not found or ClientWindowFrames missing -> log and return | setFrames fallback not installed; layout hook still works. |
| F4 | `installDecorInsetsInfoHook()` | `findClassIfExists` + declared method + fields | class/method/fields missing -> log and return | Decor insets adjustment not installed; core height still works. |
| F5 | `SetFrameCallback.intercept()` | `readSourceType()` / `adjustArgs()` | Fatal rethrown before log; ordinary Throwable logged and `chain.proceed()` | If type read fails, `TYPE_UNRESOLVED` and proceed. If arg rewrite throws, log and `chain.proceed()`. |
| F6 | `onLayoutWindowLw()` | `tryGetWindowDisplayMetrics()` / `getDisplayId()` / `applyStatusBarWindowHeight()` | `FatalErrors.unwrapAndRethrowIfFatal` before fallbacks; exceptions caught and `chain.proceed()` | If metrics or display id unreadable, return `chain.proceed()`. |
| F7 | `onSetFrames()` | `readClientWindowFrame()` / `getDisplayId()` | Fatal rethrown; exceptions caught and `chain.proceed()` | If ClientWindowFrames mismatch or frame unreadable, `chain.proceed()`. |
| F8 | `onDecorInsetsInfoUpdate()` | `decorInfoNonDecorInsetsField.get()` etc. | Fatal rethrown; other caught and result returned | If fields unavailable, `return result`. |
| F9 | `isStatusBarWindow()` | `readWindowAttrs()` / `readAttrsType()` / packageName/toString | Fatal rethrown; `Throwable` returns `false` | If attrs unreadable, treated as non-status-bar. |
| F10 | `requestStatusBarTraversal()` | reflection to `mDisplayContent`, `mWmService`, `mWindowPlacerLocked`, `requestTraversal` | Fatal rethrown; others logged but no crash | If traversal unavailable, feature waits for natural layout. |

---

## C0.8 Test Audit

| # | Test file | Type | What it actually executes | What it does NOT prove |
|---|-----------|------|---------------------------|------------------------|
| T1 | `StatusBarHeightConfigTest` | structural / behavioral | dp/px conversion, enable semantics, reconfigure, recompute, generation bumping, sentinel handling. | Does not exercise the real `PrefMap`/`RemotePreferences` update thread or SystemUI/system_server lifecycle. |
| T2 | `StatusBarInsetsGeometryTest` | structural / behavioral | pure `computeStatusBarFrameBottom`, `computeNonDecorTop`, `computeNonDecorFrameTop` for fuxi/default densities. | Does not prove real WindowState/InsetsSource geometry on a ROM. |
| T3 | `StatusBarInsetsDecisionTest` | behavioral / structural | `SetFrameCallback.intercept` with fake `XposedInterface.Chain` and fake `InsetsSource`; type encoding, one/four arg overloads, disabled passthrough, reflection failure, original exception propagation, OOM propagation, logging bounds. | Does not prove real `android.view.InsetsSource` ABI selection on a device; does not prove DisplayPolicy/WindowState paths. |
| T4 | `StatusBarInsetsResolverTest` | structural | `selectTypeEncoding()` logic for MODERN_PUBLIC / LEGACY_INTERNAL / UNSUPPORTED and sentinel normalization. | Does not prove actual reflection results on a ROM. |
| T5 | `StatusBarWindowStateHotPathTest` | behavioral | `onLayoutWindowLw` and `onSetFrames` with fake WindowState; disabled/known/unknown paths, fatal propagation, single `proceed` calls. | Fakes `WindowState` with direct `mAttrs` fields; does not prove real WMS class loading or field layout. |
| T6 | `StatusBarHeightLiveTest` | behavioral | Combined `onLayoutWindowLw` + `onSetFrames` + `requestStatusBarTraversal` with fake `WindowState` and `ClientWindowFrames`; secondary display, density change, preference reconfigure, coalescing. | Does not prove real Handler/Looper or `WindowSurfacePlacer` traversal on a device. |
| T7 | `StatusBarInsetsRoutingTest` | structural | `SystemServerFeatures` and `CommonPackageFeatures` register the correct `FeatureId`, target, phase, and preference key. | Does not prove that the feature is installed in a real process or that preference changes propagate. |

### Verdict

- The tests cover the contract surface of the current implementation well for a JVM test harness.
- No test exercises the real `system_server` class loader, real `WindowSurfacePlacer`, or real `RemotePreferences` listener thread.
- No allocation/profiling test exists for the InsetsSource hot path.

---

## C0.9 Hot-path Helper Graph

### H1: `InsetsSource.setFrame`

```text
SetFrameCallback.intercept(chain)
  -> StatusBarHeightConfig.enabled   (volatile Boolean read)
  -> statusBarType == TYPE_UNRESOLVED?   (local Int compare)
  -> StatusBarHeightConfig.configuredPx  (volatile Int read)
  -> readSourceType(source)
       -> typeField?.getInt(source)      (cached Field read)
       -> or XposedHelpers.callMethod(source, "getType")
            -> findMethodBestMatch(...)  (cached in XposedHelpers.noArgMethodCache)
            -> Method.invoke
  -> adjustArgs(chain, ...)
       -> chain.getArg(0)
       -> computeStatusBarFrameBottom(...)  (pure primitive arithmetic)
       -> copyRect(firstArg)             (only if changed; allocates one Rect)
       -> arrayOf(adjusted)              (only if changed; allocates one Array)
  -> chain.proceed() / chain.proceed(adjusted)
```

Hot-path cost when no status bar source:
- 1 volatile Boolean read
- 2-3 Int compares
- 1 volatile Int read
- 1 Int field read (cached `mType`) OR 1 cached method invoke
- 1 Int compare
- `chain.proceed()`

No `PrefMap`, no String lookup, no reflection discovery, no temporary WeakReference, no HashMap, no Pair/Triple, no per-call diagnostic String allocation once the generation-stamp gate has fired.

### H2: `DisplayPolicy.layoutWindowLw`

```text
onLayoutWindowLw(chain)
  -> chain.getArg(0)
  -> isWindowState(win)                (cached Class.isInstance or name compare)
  -> StatusBarHeightConfig.enabled      (volatile Boolean)
  -> isKnownStatusBarWindow(win)        (WeakReference array scan, max 4)
  -> isStatusBarWindow(win)
       -> isKnownStatusBarWindow        (second scan if unknown)
       -> readWindowAttrs(win)          (cached Field or fallback)
       -> readAttrsType(attrs)          (cached Field or fallback)
       -> typeMatchObserved / fallbackProbeBudget (AtomicInteger only until first match)
       -> rememberStatusBarWindow(win)  (synchronized rebuild of Array<WeakReference>, allocates ArrayList once per new status bar)
  -> statusBarWindowRef = WeakReference(win)  (only on first match / mismatch)
  -> tryGetWindowDisplayMetrics(win)    (cached Method invoke or fallback)
  -> getDisplayId(win)                  (cached Method invoke or fallback)
  -> recomputePx(metrics)               (only on displayId==0 and density changed; synchronized)
  -> StatusBarHeightConfig.configuredPx (volatile Int)
  -> claimLiveLogStamp(...)             (AtomicLongArray read/update)
  -> applyStatusBarWindowHeight(win, configuredPx)
       -> readWindowAttrs(win)
       -> XposedHelpers.getAdditionalInstanceField(win, ORIGINAL_STATUS_BAR_HEIGHT_KEY)
       -> XposedHelpers.setAdditionalInstanceField(win, ..., currentHeight)   (first hit only)
       -> XposedHelpers.setIntField(attrs, "height", configuredPx)
  -> chain.proceed()
  -> readWindowFrame(win)               (cached Method invoke)
  -> claimLiveLogStamp(...)             (log window frame, once per generation/display)
```

Concerns:
- `isStatusBarWindow` for an unknown window does reflection to `mAttrs` and `type`.  After `typeMatchObserved == true`, only `readAttrsType` is done for unknown windows; the expensive packageName/toString probe stops.
- `rememberStatusBarWindow` uses `synchronized(this)` and `ArrayList<WeakReference<Any>>` allocation.  This is a warm path (only when a new status bar WindowState appears).
- `applyStatusBarWindowHeight` uses `XposedHelpers.getAdditionalInstanceField` / `setAdditionalInstanceField` on the **WindowState instance** (not a static map) for the original height.  This is the intended Xposed mechanism but is a per-frame additional-field read.

### H3: `WindowState.setFrames`

```text
onSetFrames(chain)
  -> StatusBarHeightConfig.enabled      (volatile Boolean)
  -> chain.thisObject
  -> isKnownStatusBarWindow(win)        (WeakReference array scan, max 4)
  -> chain.getArg(0)
  -> clientFramesClassMismatch(...)     (Class.isInstance or simpleName compare)
  -> getDisplayId(win)                  (cached Method)
  -> StatusBarHeightConfig.configuredPx or configuredPxFor(...)  (volatile Int or per-display compute)
  -> readClientWindowFrame(clientFrames) (cached Field get)
  -> computeStatusBarFrameBottom(...)    (primitive arithmetic)
  -> if (changed) frame.bottom = newBottom
  -> chain.proceed()
```

No allocation on the steady-state hot path.

### H4: `DecorInsets.Info.update`

```text
onDecorInsetsInfoUpdate(chain)
  -> chain.proceed()                    (original must run first)
  -> StatusBarHeightConfig.enabled
  -> args[0] null check, args[1] Int check
  -> decorInfoNonDecorInsetsField.get(info)   (cached Field)
  -> decorInfoNonDecorFrameField.get(info)    (cached Field)
  -> configuredPxForDecorInfo(args[0])   (DisplayMetrics lookup, or fallback to global)
  -> computeNonDecorTop / computeNonDecorFrameTop
  -> if changed, mutate the two Rects
  -> logLive (once per generation/rotation)
```

Original runs first.  No allocation; direct Rect mutation.

### Helper allocation summary

| Hot path | Steady-state allocation | First-hit / cold allocation |
|----------|-------------------------|----------------------------|
| H1 InsetsSource.setFrame | `chain.proceed()` only if unchanged; one `Rect` + one `Array<Any>` if changed and only for status bar source. | `typeField` read may self-install on first call. |
| H2 layoutWindowLw | `WeakReference(win)` only when a new status bar is seen; `ArrayList` + new `Array<WeakReference>` during `rememberStatusBarWindow` for a new bar. | `windowStateAttrsField` / `layoutParamsTypeField` self-install on first `mAttrs` read. |
| H3 setFrames | None. | `clientWindowFramesClass` resolved at install. |
| H4 decorInfoUpdate | None. | `decorInfo*` fields resolved at install. |

---

## C0.10 Existing Evidence

| # | Evidence | Status |
|---|----------|--------|
| E1 | StatusBarHeight device evidence (fuxi / Xiaomi 13) | NO BASELINE EVIDENCE.  `tasks/completed/FEATURE-A14-STATUS-BAR-HEIGHT-INSETS.md` states `NOT DEVICE_VERIFIED` for R3/R4.  The r14.18.8 release APK smoke test is claimed in the Architecture C start doc but no device log is in this repo. |
| E2 | SystemUI/system_server memory evidence | NO BASELINE EVIDENCE.  `docs/performance/P0_RUNTIME_BASELINE_AND_AUDIT_PROTOCOL.md` provides process-level counts but no per-feature memory measurement for StatusBarHeightInsets. |
| E3 | Feature catalog evidence | `docs/performance/P0_RUNTIME_BASELINE_AND_AUDIT_PROTOCOL.md` lists 245 registered features, 50 system_server, 8 ANY.  `StatusBarHeightInsetsFeature` is one of the 50 `SYSTEM_SERVER_STARTING` features. |
| E4 | R8 evidence | `docs/performance/M4_3_R8_KEEP_NARROWING_2026-08-09.md` documents keep-rule narrowing but does not explicitly cover `SystemStatusBarInsetsHooks` or `StatusBarHeightConfig`.  No r14.18.8 R8 mapping for these classes is in the repo. |
| E5 | Provenance | `app/build.gradle.kts` `BuildConfig.BUILD_REVISION` is driven by `buildRevision` property; r14.18.8 release was built from a detached worktree with SHA `2c4efeaf` and versionCode 197.  The release APK SHA-256 is `39FCAE4D9213A24192F79B31C1EF78F6955A5A41E6E1BE9839BC535B02ECA989` per the Architecture C start doc. |

---

## C0.11 C1 Architecture Split Proposal

Proposed future homes for the existing members of `SystemStatusBarInsetsHooks`:

```text
mods/statusbarheight/
├ StatusBarHeightAbi.kt              (A1-A14 frozen ABI description)
├ StatusBarHeightResolver.kt         (resolveInsetsSourceAbi, resolveWindowManagerAbi,
│                                      resolveClientWindowFramesClass, resolveDeclaredField,
│                                      resolveIntField, selectTypeEncoding)
├ StatusBarHeightConfig.kt           (existing, but made immutable snapshot + volatile ref)
├ StatusBarHeightRuntime.kt          (statusBarWindowRef, statusBarWindows, typeMatchObserved,
│                                      fallbackProbeBudget, lastRefreshGeneration,
│                                      loggedCritical, loggedRejection, loggedLiveKeys, log stamps)
├ StatusBarHeightPolicy.kt           (computeStatusBarFrameBottom, computeNonDecorTop,
│                                      computeNonDecorFrameTop, isStatusBarWindow, isKnownStatusBarWindow,
│                                      applyStatusBarWindowHeight, restoreStatusBarWindowHeight)
└ StatusBarHeightHooks.kt            (StatusBarInsetsHeightHook, install* methods,
│                                      SetFrameCallback, onLayoutWindowLw, onSetFrames,
│                                      onDecorInsetsInfoUpdate, requestStatusBarTraversal)
```

### Member-to-layer mapping

| Current member | Proposed layer | Reason |
|----------------|----------------|--------|
| `InsetsSourceAbi`, `InsetsTypeInfo`, `InsetsTypeEncoding` | `StatusBarHeightAbi` | Frozen ABI description, no behavior. |
| `resolveInsetsSourceAbi`, `resolveWindowManagerAbi`, `resolveClientWindowFramesClass`, `selectTypeEncoding`, `resolvePublicTypes`, `resolveLegacyTypes`, `getStaticInt`, `hasMethod`, `safePublicType`, `RawTypeInfo` | `StatusBarHeightResolver` | Cold-path reflection and ABI selection only. |
| `StatusBarHeightConfig` | `StatusBarHeightConfig` (keep) | Already a process config; C1 should make it an immutable `State` snapshot published through a `@Volatile` reference instead of six separate volatile fields. |
| `enabled`, `configuredDp`, `configuredPx`, `densityDpi`, `density`, `generation` | `StatusBarHeightConfig.State` / `StatusBarHeightRuntime` | The snapshot belongs to Config; the runtime keeps the latest reference.  `generation` is an `AtomicLong` used for log gating — it can stay with Config or move to Runtime. |
| `statusBarWindowRef`, `statusBarWindows`, `typeMatchObserved`, `fallbackProbeBudget`, `lastRefreshGeneration`, logged sets/stamps | `StatusBarHeightRuntime` | Process-scoped, bounded, owner-safe runtime state. |
| `computeStatusBarFrameBottom`, `computeNonDecorTop`, `computeNonDecorFrameTop` | `StatusBarHeightPolicy` | Pure, allocation-free geometry decisions. |
| `isStatusBarWindow`, `isKnownStatusBarWindow`, `rememberStatusBarWindow` | `StatusBarHeightPolicy` | Status-bar identity policy, uses Runtime state. |
| `applyStatusBarWindowHeight`, `restoreStatusBarWindowHeight` | `StatusBarHeightPolicy` | Policy actions on the framework object. |
| `StatusBarInsetsHeightHook`, `installDisplayPolicyHook`, `installWindowStateHook`, `installDecorInsetsInfoHook` | `StatusBarHeightHooks` | Xposed wiring, cold only. |
| `SetFrameCallback` | `StatusBarHeightHooks` | Thin hot hook. |
| `onLayoutWindowLw`, `onSetFrames`, `onDecorInsetsInfoUpdate` | `StatusBarHeightHooks` | Thin hot hook shells; should delegate to Policy. |
| `requestStatusBarTraversal`, `invalidateDecorInsets` | `StatusBarHeightHooks` (cold request) or `StatusBarHeightRuntime` | The request is a cold action driven by the observer; keep it close to the hook wiring. |
| `tryGetWindowDisplayMetrics`, `getDisplayId`, `readWindowFrame`, `readWindowAttrs`, `readAttrsType`, `readClientWindowFrame`, `readSourceType`, `readSourceId` | `StatusBarHeightResolver` or `StatusBarHeightPolicy` | These are ABI-reader helpers.  The cached Field/Method belongs to Resolver; the invocation on a live object belongs to Policy/Hooks.  A clear split: `Resolver` returns `WindowStateAbi` with `getFrame/getDisplayMetrics/getDisplayId/mAttrs/type` invokers; `Policy` uses them. |
| `logInstall`, `logLive`, `logRejection`, `logStatusSource` | `StatusBarHeightRuntime` (diagnostics) | Bounded logging is runtime state. |

### Important: this must not become "one big file split into many files"

For C1 to be a valid Architecture C PoC, the split must change the runtime contract:

1. **Resolver must run only at install** and produce a frozen `StatusBarHeightAbi` object.
2. **Config must become an immutable snapshot** published through one `@Volatile` reference (or `AtomicReference<State>`), replacing six separate `@Volatile` fields.
3. **Runtime state must be explicitly bounded** and owned; no process singleton strongly referencing a `WindowState`.
4. **Policy must be pure-ish**; geometry decisions must not allocate.
5. **Hooks must be thin**; no per-call reflection discovery, no per-call `PrefMap`/String lookup, no per-call WeakReference creation.

---

## C0.12 Risk Register

| # | Risk | Current evidence | Architecture C risk | Planned guard |
|---|------|------------------|---------------------|---------------|
| R1 | P0 behavior parity | Unit tests pass on JVM; `NOT DEVICE_VERIFIED` | Split could change InsetsSource / WindowState interaction order, especially if Policy/Hooks are extracted separately. | Lock the exact `chain.proceed()` count and arg-rewrite order in C1 tests before changing code. |
| R2 | P0 system_server crash | Fatal errors propagate; `catch (Throwable)` are bounded | A regression in Resolver could install a callback with `TYPE_UNRESOLVED` that does extra reflection per call, or a Policy error could suppress fatal. | Keep `FatalErrors.unwrapAndRethrowIfFatal` at every boundary; never add `catch` that swallows OOM. |
| R3 | P1 lifecycle | `WeakReference` to `WindowState`, `additionalInstanceField` for original height | Refactor could accidentally turn `WeakReference` into strong reference or forget to clear `statusBarWindows`. | Code review every field in the new `Runtime` class for strong/weak semantics. |
| R4 | P1 concurrency | `@Volatile` fields + `AtomicLongArray`; `reconfigure` is `synchronized` | Publishing six separate volatiles is not atomic; a multi-field update can be observed partially. | Move `StatusBarHeightConfig` to a single immutable `State` snapshot with one `@Volatile` reference or `AtomicReference`. |
| R5 | P1 hot regression | H1/H2 hot path analysis shows minimal allocation | C1 abstraction could add interface dispatch, `Pair`, `data class` copies, or per-call reflection. | Audit helper graph after C1; require 0 allocation on unchanged steady-state paths. |
| R6 | P1 ROM ABI fallback | Resolver supports MODERN_PUBLIC, LEGACY_INTERNAL, UNSUPPORTED | Refactor could drop a fallback branch or change `selectTypeEncoding` sentinel rules. | Preserve `StatusBarInsetsResolverTest` assertions and add C1 ABI structural tests. |
| R7 | P2 cold-start regression | Feature catalog has 50 system_server specs; StatusBarHeight is one | Splitting into 5-6 files does not change cold cost if object count stays the same; but adding a big `StatusBarHeightAbi` data class may increase `clinit` and retained heap. | Measure install-time allocation and R8 shrink; do not add unnecessary fields. |

---

## C0.13 C1 Validation Plan

### Behavior

- Re-run and extend `StatusBarInsetsDecisionTest` and `StatusBarHeightLiveTest` to cover disabled, enabled target, non-target, unknown WindowState, target identity, density unchanged/changed, config live update, null preference, fallback ABI, partial ABI, original RuntimeException, OOM/fatal, and lifecycle cleanup.

### Structural

- Verify that `StatusBarHeightResolver` produces a frozen `StatusBarHeightAbi` with all A1-A14 fields before any hook is installed.
- Verify that `StatusBarHeightConfig` publishes an immutable `State` snapshot.
- Verify that `StatusBarHeightRuntime` does not contain strong `WindowState` references.

### Concurrency

- Test that preference changes publish the new `State` snapshot atomically and that the hot path never observes a mixed old/new config.
- Test that `statusBarWindows` updates do not cause the hot path to skip a known status bar or observe a stale array.

### Fatal

- For every new boundary (Resolver, Config, Runtime, Policy, Hooks), verify `FatalErrors.unwrapAndRethrowIfFatal` is called first in any `catch (Throwable)`.
- Verify `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` propagate through `chain.proceed()` without modification.

### Device A/B

- A: r14.18.8 oracle APK (`BUILD_REVISION=2c4efeaf`).
- B: Architecture C C1 PoC APK (new `BUILD_REVISION`).
- Same device, same ROM, same Xposed environment.
- Verify: SystemUI restart, reboot, status bar height, fullscreen, rotation, notification shade, lockscreen, launcher, freeform, IME, screen on/off, SystemUI/system_server crash/ANR, PSS / Java Heap / Native Heap, no new frame/layout regression.

### Rollback rule

- C1 must be a single feature slice; any regression in StatusBarHeight must be rollback-able by reverting the single commit.
- No changes to CommonPackageFeatures, SystemServerFeatures, or FeatureId registries should be required.

---

## C0.14 Verification Status

```text
python tools/verify.py fast --tests StatusBarHeightConfigTest,StatusBarInsetsGeometryTest,StatusBarInsetsDecisionTest,StatusBarInsetsResolverTest,StatusBarWindowStateHotPathTest,StatusBarHeightLiveTest,StatusBarInsetsRoutingTest
```

- Python static gates (`check-invariants`, `audit-feature-semantics`, `check_observer_key_contract`) passed.
- Gradle unit test step **blocked** by missing JDK 25 toolchain on this Windows environment.  No code change is required; the build itself is not the C0 subject.
- `git diff --check` not yet run (no files modified before this doc).

---

## C0.15 Final Statement

```text
C0_READY_FOR_INDEPENDENT_AUDIT
```

This C0 audit is a docs-only artifact.  No production code was modified.  C1 implementation must not begin until an independent audit confirms this inventory and the proposed split is valid.
