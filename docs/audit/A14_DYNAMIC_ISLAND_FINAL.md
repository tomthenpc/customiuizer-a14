# A14 Dynamic Island shell / dismiss order corrective

This document records the final implementation for the HyperOS 1 A14
`r14.20.0` Dynamic Island corrective.

## Problem statement

Runtime diagnosis identified two related defects:

1. **The Dynamic Island was not a true independent surface.**
   The module was transforming the ROM message container
   `cl_strong_toast_msg` in place and forcing it to become a rounded
   capsule. The result looked like a cropped or repurposed ROM container
   because `cl_strong_toast_msg` still contained ROM-owned children and
   backgrounds.

2. **Disappear animation was choppy or incomplete.**
   The module was racing with ROM `setValue()` because it animated alpha on
   every direct child of the ROM container. Dismiss also ran ROM `clearAll()`
   before the module exit animation finished, which mutated the view content
   and requested layout while the capsule was still transforming.

## Root causes

- `cl_strong_toast_msg` is the ROM content owner. ROM `setValue()` and
  `updateStrongToast()` mutate its children, visibility, text, images, video
  state and colors. Module-owned child alpha animations could be overwritten
  mid-animation, which matched the observed "standalone event incomplete /
  second event completes" behavior.
- The module did not create a separate visual shell. All transforms and
  background changes were applied to the ROM content view itself, keeping the
  island coupled to ROM layout and clipping.
- `realHideStrongToast` was calling ROM `clearAll()` before the module
  `ViewPropertyAnimator` exit started. `clearAll()` then removed the view and
  reset children while the exit transform was still running.

## Implementation

`SystemUIStrongToastHooks.kt` now:

1. **Wraps `cl_strong_toast_msg` in a module-owned `FrameLayout` shell**
   via `bindDynamicIslandShell()`. The shell receives the original container's
   outer `LinearLayout.LayoutParams` and is inserted at the original content
   index. The ROM content is reparented into the shell with
   `MATCH_PARENT` / `MATCH_PARENT` and its own background is set to `null` so
   the shell draws the black rounded pill.

2. **Owns all transforms on the shell only.**
   `prepareDynamicIslandCapsule()`, `runDynamicIslandEntrance()`,
   `handleDynamicIslandTouch()`, `animateDynamicIslandDismiss()` and
   `restoreDynamicIslandAfterDrag()` now all target the shell. The ROM content
   inside the shell is never scaled, translated or alpha-animated by the
   module.

3. **Removes module-owned direct child alpha animation.**
   `prepareDynamicIslandContent()`, `animateDynamicIslandContent()` and
   `animateDynamicIslandContentOut()` were deleted. The module no longer owns
   ROM child alpha.

4. **Fixes dismiss ordering.**
   `buildDynamicIslandDismissComplete()` constructs the completion runnable.
   The sequence is:
   1. `restoreStatusBarContents(strongToast)`
   2. `XposedHelpers.callMethod(strongToast, "clearAll")`
   3. `XposedHelpers.callMethod(strongToast, "onComplete")`

   `clearAll()` only runs inside the `withEndAction` callback, after the
   shell exit `ViewPropertyAnimator` has fully completed.

5. **Makes shell binding idempotent and teardown exact.**
   `DynamicIslandShellState` is stored on the `MIUIStrongToast` root via an
   additional instance field. `bindDynamicIslandShell()` returns an existing
   shell when the state is still valid. `restoreDynamicIslandShell()` returns
   the content to the original parent/index, restores the original
   `LayoutParams` and background, removes the shell, and cancels animations.
   `onDetachedFromWindow()` cancels the shell animator, removes listeners,
   removes the expanded touch region, restores status-bar contents, and then
   calls `restoreDynamicIslandShell()` before the ROM super method runs.

6. **Captures and restores every module-owned baseline.**
   `DynamicIslandShellState` now records the original parent padding, parent
   `LinearLayout.gravity`, the forehead bottom view reference and its exact
   original visibility, and the original `clipChildren` / `clipToPadding` of
   every ancestor up to the `MIUIStrongToast` root. `prepareDynamicIslandCapsule()`
   validates ROM dimensions before any hierarchy mutation and wraps post-bind
   setup in a single transaction: on any non-fatal failure it calls
   `restoreDynamicIslandShell()` and removes the shell state, leaving the ROM
   hierarchy intact. `bindDynamicIslandShell()` itself rolls back content
   reparenting and background clearing if the transaction fails.

7. **Updates UI labels and removes the obsolete center-pop resource.**
   The `system_strong_toast_mode_dynamic_island_center_pop` string resource
   was removed from all locale files and from the localization test fixture
   after confirming no production, array, reflection or dynamic reference.
   Locale labels for `system_strong_toast_mode_dynamic_island` were shortened
   to just "Dynamic Island" (and translated equivalents).

## Test coverage

New tests:

- `app/src/test/java/tv/withaibuild/customiuizer/mods/DynamicIslandShellContractTest.kt`
  - Source-contract assertions for `bindDynamicIslandShell`,
    `restoreDynamicIslandShell`, `prepareDynamicIslandCapsule`,
    `startDynamicIslandEntrance`, `runDynamicIslandEntrance`,
    `animateDynamicIslandDismiss`, `buildDynamicIslandDismissComplete`,
    `realHideStrongToast`, `installExpandedWindowTouchRegion`,
    `findDynamicIslandShell` and `onDetachedFromWindow`.
  - Verifies the shell is a `FrameLayout`, idempotent, preserves original
    background / layout params, restores hierarchy, and that Dynamic Island
    no longer owns direct child alpha.
  - Verifies `DynamicIslandShellState` captures parent padding/gravity,
    bottom-view visibility, and ancestor `clipChildren`/`clipToPadding`
    baselines, and that `restoreDynamicIslandShell()` restores them exactly.
  - Verifies `prepareDynamicIslandCapsule()` validates ROM dimensions before
    binding, and that both `prepare` and `bind` fail open with rollback.

- `app/src/test/java/tv/withaibuild/customiuizer/mods/DynamicIslandDismissLifecycleTest.kt`
  - Functional test for `buildDynamicIslandDismissComplete()`:
    `clearAll()` runs before `onComplete()`, each runs once, and `onComplete()`
    still runs even if `clearAll()` throws.

Existing `SystemUIStrongToastHooksTest` MATCH-status-bar tests continue to
pass.

## Verification

```powershell
python tools/verify.py fast --changed
python tools/verify.py fast --tests SystemUIStrongToastHooksTest
python tools/verify.py fast --tests DynamicIslandShellContractTest DynamicIslandDismissLifecycleTest
python tools/verify.py full
```

Static rules, invariants, `compileDebugKotlin`, `testDebugUnitTest`, and
`lintDebug` all pass.

## Files changed

Production:

- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt`

Resources:

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh-rCN/strings.xml`
- `app/src/main/res/values-zh-rTW/strings.xml`
- `app/src/main/res/values-vi-rVN/strings.xml`
- `app/src/main/res/values-tr-rTR/strings.xml`
- `app/src/main/res/values-ru-rRU/strings.xml`
- `app/src/main/res/values-pt-rBR/strings.xml`
- `app/src/main/res/values-ja-rJP/strings.xml`
- `app/src/main/res/values-es-rES/strings.xml`
- `app/src/main/res/values-cs-rCZ/strings.xml`

Tests and fixtures:

- `app/src/test/java/tv/withaibuild/customiuizer/mods/DynamicIslandShellContractTest.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/mods/DynamicIslandDismissLifecycleTest.kt`
- `tools/tests/test_recent_feature_localizations.py`

Documentation:

- `docs/audit/A14_DYNAMIC_ISLAND_FINAL.md`

## Runtime limitations

- This corrective is verified statically and via JVM unit tests. A device
  runtime smoke test is still required to confirm the visual result on the
  target HyperOS 1 build.
- The shell relies on `WindowManager` adding the `MIUIStrongToast` root once
  and reusing it for consecutive events. If a future ROM revision reparents
  `cl_strong_toast_msg` outside `mDarkToast` the `bindDynamicIslandShell()`
  copy logic will need a matching update.
