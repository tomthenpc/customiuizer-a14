# A14 Runtime Performance Audit

This document captures the A14 runtime-hardening work on the `devin/a14-runtime-hardening`
branch and the engineering principles used to keep the module safe in `system_server`,
SystemUI, the Launcher and other scoped processes.

## Scope

The audit focuses on four areas that directly affect process stability and resource cost:

1. **Receiver registration** — process-scoped receivers are installed and replaced atomically.
2. **Preference bootstrap** — remote preference state is loaded and watched without blocking or
   sleeping the caller.
3. **Reflection and cache state** — lookup results are memoised and nulls never stored in
   `ConcurrentHashMap`.
4. **Module entry-point split** — `MainModule` no longer carries the entire system-server hook
   list, and cold-path argument formatting is allocation-light.

## Invariants

The following rules are enforced by `tools/check-invariants.py` and by the unit tests added in
this branch:

- A `BroadcastReceiver` registered by the module is always reachable for cleanup (tracked by an
  owner or a process-scoped key).
- `registerModuleReceiver()` is idempotent and replacement is atomic: an old receiver is never
  left both registered with the framework and untracked.
- `unregisterModuleReceiver(key, expectedReceiver)` only removes the registration it expected; a
  concurrent replacement is not accidentally torn down.
- `PreferenceBootstrap` loads the snapshot, registers the listener, then loads a second snapshot
  to cover the registration window. `VALID_EMPTY` is only reached with a live listener.
- `ReflectionCache` uses sentinels for negative results so `ConcurrentHashMap` values are always
  non-null.
- `argList()` builds the diagnostic descriptor without allocating an intermediate list.

## Hot-path changes

| Area | Before | After |
|------|--------|-------|
| `ModuleReceiver` replacement | `unregister` → `put` (racy) | `ConcurrentHashMap.compute()` single replace, then cleanup old |
| `PreferenceBootstrap` | mixed `MainModule` state fields | explicit `State` enum, one lock, no sleep |
| `depInstanceCache` | `ConcurrentHashMap<Class<*>, Any?>`, no negative cache | `ConcurrentHashMap<Class<*>, Any>` with sentinels |
| `MainModule` | ~80-line `onSystemServerStarting` hook list | `SystemServerInstaller` stateless installer |
| `argList()` | `args.toList().dropLast(1).joinToString` | `StringBuilder` loop, no list copy |

## Tests

The following tests are part of this branch:

- `ModuleReceiverRegistrationTest` — idempotency, replacement, failure rollback, race loser
  self-unregister, condition-safe deletion, 100 race loops.
- `PreferenceBootstrapTest` — state transitions, listener idempotency, windowed-change capture,
  retry budgets, `VALID_EMPTY` gating.
- `ReflectionCacheTest` — negative-result memoisation, non-null sentinels, idempotency.
- `FeatureInstallRegistryTest` — target/phase matching, failure recording, preference-key
  invalidation.
- `ScopeListTest` — no duplicates, no blanks, core targets present.

## Verification status

| Task | Status |
|------|--------|
| `python tools/check-invariants.py` | passed |
| `./gradlew testDebugUnitTest` | passed |
| `./gradlew lintVitalRelease` | run in CI / pending |
| `./gradlew assembleDebug` | passed |
| `./gradlew assembleRelease` | requires project signing (not run) |
| real-device LSPosed log | not yet |

## Remaining work

- The `FeatureInstallRegistry` is currently a typed scaffold; individual hook categories can be
  migrated into `FeatureDefinition` implementations incrementally.
- `MainModule.onPackageReady()` and `onModuleLoaded()` still contain package-specific hook lists
  that can be moved to per-package installers once the feature registry is wired up.
- A real-device smoke test on HyperOS 1 / A14 is needed before declaring the build release-ready.
