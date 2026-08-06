# PERF-A14-P1-SLICE-B — NetworkSpeedView `setNetworkSpeed` hot-path preference and redundant-update elimination

## Scope

Only the `NetworkSpeedView.setNetworkSpeed` → `SystemUIStatusBarHooks.applyNetSpeedTextStyle()`
hot path in `SystemUIStatusBarHooks`. No other status-bar features, no global preference
architecture changes, no visual semantic changes.

## Call chain (pre-optimization)

### Per-second / per-interval path

```text
NetworkSpeedController / NetworkSpeedView.setNetworkSpeed(number, unit, visible)
  -> NetSpeedStyleHook.setNetworkSpeed.afterHook
     -> MainModule.mPrefs.getStringAsInt("system_detailednetspeed_style", 1)
     -> MainModule.mPrefs.getBoolean("system_netspeed_boldfont")
     -> MainModule.mPrefs.getInt("system_netspeed_fontsize", 13)
     -> MainModule.mPrefs.getInt("system_netspeed_fixedcontent_width", 10)
     -> MainModule.mPrefs.getInt("system_netspeed_leftmargin", 0)
     -> MainModule.mPrefs.getInt("system_netspeed_rightmargin", 0)
     -> MainModule.mPrefs.getInt("system_netspeed_verticaloffset", 8)
     -> MainModule.mPrefs.getStringAsInt("system_detailednetspeed_align", 1)
     -> MainModule.mPrefs.getInt("system_netspeed_rowspacing", 100)
     -> XposedHelpers.getObjectField(speedView, "mNetworkSpeedNumberText")
     -> XposedHelpers.getObjectField(speedView, "mNetworkSpeedUnitText")
     -> setTextSize, setTypeface, setTextAppearance, layoutParams, padding, translationY
     -> speedView.setNetworkSpeed(...) (original call)
```

This ran on every network-speed refresh, typically once per second (or per configured
interval).

### View creation / attach path

```text
NetworkSpeedView.onFinishInflate()
  -> NetSpeedStyleHook.onFinishInflate.afterHook
     -> MainModule.mPrefs.getBoolean("system_netspeed_use_clock_style")
     -> if true: setTextAppearance from system style
     -> if false: same 9 mPrefs reads + full applyNetSpeedTextStyle()
```

### setTextAppearance / configuration change path

```text
TextView.setTextAppearance(resId)
  -> NetSpeedStyleHook.setTextAppearance.afterHook
     -> same 9 mPrefs reads + full applyNetSpeedTextStyle()
```

## `mPrefs.get*` count by hot call site

| Call site | `mPrefs.get*` per call | Frequency |
|---|---|---|
| `setNetworkSpeed` per-interval path | 9 | every tick (≈ 1 s) |
| `onFinishInflate` view attach | 9 (plus `system_netspeed_use_clock_style`) | view create / inflate |
| `setTextAppearance` hook | 9 | style change / config change |

## Preference keys used by the snapshot

### Runtime style keys (snapshot refreshes on change)

- `system_detailednetspeed_style`
- `system_netspeed_boldfont`
- `system_netspeed_fontsize`
- `system_netspeed_fixedcontent_width`
- `system_netspeed_leftmargin`
- `system_netspeed_rightmargin`
- `system_netspeed_verticaloffset`
- `system_detailednetspeed_align`
- `system_netspeed_rowspacing`

### Cold-path / view-attach only

- `system_netspeed_use_clock_style` — only read in `onFinishInflate` to decide whether to
  apply the system clock text appearance instead of the custom snapshot.

## Hot path View setters / allocations (pre-optimization)

`applyNetSpeedTextStyle` per tick:

- `getObjectField` for `mNetworkSpeedNumberText` and `mNetworkSpeedUnitText`
- `setTextSize`, `setTypeface` (number and unit)
- `setGravity`, `textAlignment`
- `layoutParams` read/assign for number and unit
- `setPaddingRelative`, `translationY`
- `setSingleLine`, `setMaxLines`, `setLineSpacing` (speed style 2 only)

## Optimization target

1. `setNetworkSpeed` per-tick path must read `MainModule.mPrefs` **0** times after the first
   tick of a stable snapshot.
2. `applyNetSpeedTextStyle` must read `MainModule.mPrefs` **0** times.
3. Snapshot is built at most once per pref-set and updated only when a relevant key changes.
4. Per-tick redundant View setters are eliminated: the same `NetSpeedTextStyleSnapshot.id`
   applied to the same `speedView` with `typefaceOnly = true` returns early.
5. The text update (`setNetworkSpeed` itself) is never blocked or reordered by the style
   snapshot; `applyNetSpeedTextStyle` does not call `setText()`.
6. No new Xposed Hook, thread, HandlerThread, Timer, polling, or global Receiver.
7. Preference observer is process-scoped and owner-bound to `SystemUIStatusBarHooks`.

## Post-optimization call chain

### Per-interval / per-tick path

```text
NetworkSpeedView.setNetworkSpeed(number, unit, visible)
  -> NetSpeedStyleHook.setNetworkSpeed.afterHook
     -> currentOrBuildNetSpeedTextStyleSnapshot()   (one volatile read, 9 reads only on first call)
     -> if view not inited: applyNetSpeedTextStyle(speedView, snapshot, false)
        if view inited:   applyNetSpeedTextStyle(speedView, snapshot, true)
```

`applyNetSpeedTextStyle(speedView, snapshot, true)` (the common per-tick case):

```text
  -> XposedHelpers.getAdditionalInstanceField(speedView, NETSPEED_LAST_STYLE_SNAPSHOT_ID)
  -> if lastAppliedId == snapshot.id: return   // 0 setter calls
  -> getNetSpeedNumberView / getNetSpeedUnitView
  -> ensureNetSpeedTypeface(numberView, bold)  // only if typeface changed
  -> setAdditionalInstanceField(..., snapshot.id)
```

No `MainModule.mPrefs` reads, no `XposedHelpers.getObjectField` after the first apply because
`getNetSpeedNumberView` caches the result as a keyed tag.

### View attach / config change path

```text
NetworkSpeedView.onFinishInflate() / TextView.setTextAppearance()
  -> currentOrBuildNetSpeedTextStyleSnapshot()
  -> applyNetSpeedTextStyle(speedView, snapshot, false)
     -> full style apply (text size, layout params, padding, translationY, typeface)
```

### Snapshot refresh path

```text
PreferenceObserver.onChange(key)
  -> if key in NETSPEED_TEXT_STYLE_PREFERENCE_KEYS:
       currentNetSpeedTextStyleSnapshot.set(null)
       currentOrBuildNetSpeedTextStyleSnapshot()   // 9 reads, cold path
```

The observer is registered with `SystemUIStatusBarHooks` as the owner. The next
`setNetworkSpeed` tick picks up the new snapshot.

## `mPrefs.get*` count after optimization

| Call site | `mPrefs.get*` per hot call | Frequency |
|---|---|---|
| `setNetworkSpeed` per tick (view inited, same snapshot) | **0** | every tick |
| `applyNetSpeedTextStyle` | **0** | every tick / attach |
| `buildNetSpeedTextStyleSnapshot` | 9 | cold path: first tick, view attach, or preference change |
| `onFinishInflate` `system_netspeed_use_clock_style` | 1 | view attach only |

## Evidence

- `SystemUIStatusBarHotPathTest.buildNetSpeedTextStyleSnapshot_readsAllNineRelevantKeysAndProducesEquivalentValues`
  confirms the 9 relevant keys are read exactly once and the snapshot carries the same
  semantics as the old direct `mPrefs.get*` calls.
- `SystemUIStatusBarHotPathTest.applyNetSpeedTextStyle_100HotCalls_withSnapshot_doesNotReadPrefs`
  runs 100 per-tick style applies against a counting `PrefMap` and records 0 reads.
- `SystemUIStatusBarHotPathTest.applyNetSpeedTextStyle_100HotCalls_sameViewSameSnapshot_skipsAllSetters`
  verifies that all layout/text-size setters are applied once and skipped on the next 100
  identical ticks.
- `SystemUIStatusBarHotPathTest.applyNetSpeedTextStyle_doesNotTriggerTextChange` proves the
  hot path does not call `setText()` and therefore cannot suppress the network-speed value
  update.
- `SystemUIStatusBarHotPathTest.netSpeedTextStyleObserver_relevantKey_rebuildsSnapshotOnce`
  and `netSpeedTextStyleObserver_irrelevantKey_doesNotRebuildSnapshot` cover the
  owner-bound preference observer lifecycle.
- `SystemUIStatusBarHotPathTest.applyNetSpeedTextStyle_usesCurrentResourcesForPixelConversion`
  confirms `dp -> px` conversion uses the View's current `Resources.getDisplayMetrics()`
  rather than any cached or stale density.
- `SystemUIStatusBarHotPathTest.applyNetSpeedTextStyle_invalidValues_fallsBackSafely` checks
  invalid style/font/position values complete without crashing.

## Preference observer lifecycle review

The `netSpeedTextStyleObserver` is registered with `SystemUIStatusBarHooks` as the owner:

1. **Initial snapshot availability**: the first `setNetworkSpeed` tick (or `onFinishInflate`)
   calls `currentOrBuildNetSpeedTextStyleSnapshot()`. The snapshot is built once and cached.
2. **Actual value changes**: `onChange` invalidates the snapshot by setting it to `null`. The
   next tick rebuilds with the new values and re-applies the full style if the view has not
   yet been seen, or re-applies only the typeface on the next per-tick call.
3. **Owner-bound disposal**: `PreferenceObserverRegistry` holds a weak reference keyed by the
   owner `SystemUIStatusBarHooks` object, which lives for the SystemUI process lifetime. The
   registry drops the reference when the owner is garbage collected.
4. **Duplicate registration**: `PreferenceObserverRegistry.observePreferenceChange` removes
   any existing observer for the same owner before registering, so process recreation does not
   accumulate duplicates.
5. **Unbounded accumulation**: the process-scoped observer set contains one weak reference per
   module owner. No static list of Views, Contexts, or controllers is kept.

## Status

`ENGINEERING_COMPLETE_DEVICE_EVIDENCE_PENDING`

Engineering verification is complete:

- `NetSpeedTextStyleSnapshot` eliminates `MainModule.mPrefs` reads from the per-tick
  `applyNetSpeedTextStyle` path.
- View setter idempotency is enforced by the per-View `NETSPEED_LAST_STYLE_SNAPSHOT_ID`
  additional instance field.
- The preference observer has owner-bound registration, deduplication, and no strong View
  references.
- `python tools/verify.py full`, `python tools/source_hazard_scan.py --strict-all`, and
  `gradlew :app:testDebugUnitTest --tests SystemUIStatusBarHotPathTest` all pass.

Device-level PSS / USS / CPU measurements remain pending per the protocol in
`P0_RUNTIME_BASELINE_AND_AUDIT_PROTOCOL.md`.
