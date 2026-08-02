# libxposed API 101/102 compatibility boundary

This project compiles against `io.github.libxposed:api:102.0.0` but keeps
`minApiVersion=101` so it can run on LSPosed and other hosts that only expose
API 101.

## Compatibility statement

- `module.prop`: `minApiVersion=101`, `targetApiVersion=102`, `staticScope=false`.
- `MainModule.java` does not import or reference `HotReloadingParam`,
  `HotReloadedParam`, `HookBuilder.setId`, `HookHandle.getId` or
  `HookHandle.replaceHook`.
- The module does not override `onHotReloading` or `onHotReloaded`.
- The module does not create a global `HookHandle` registry.

## What the AARs actually contain

The AARs were unpacked from the Gradle cache and inspected with `javap`.

### API 101 (`api-101.0.1.aar`)

```java
interface XposedInterface$HookBuilder {
    HookBuilder setPriority(int);
    HookBuilder setExceptionMode(ExceptionMode);
    HookHandle intercept(Hooker);
}

interface XposedInterface$HookHandle {
    Executable getExecutable();
    void unhook();
}
```

`setId`, `getId`, `replaceHook` are **not present**.

### API 102 (`api-102.0.0.aar`)

```java
interface XposedInterface$HookBuilder {
    HookBuilder setPriority(int);
    HookBuilder setExceptionMode(ExceptionMode);
    HookBuilder setId(String);   // NEW
    HookHandle intercept(Hooker);
}

interface XposedInterface$HookHandle {
    Executable getExecutable();
    void unhook();
    String getId();              // NEW
    HookHandle replaceHook(Hooker); // NEW
}
```

### Runtime version

`XposedInterface.getApiVersion()` returns the host API version. It is **not**
`XposedInterface.LIB_API`, which is the compile-time constant of the AAR
(`102` for the 102 AAR, `101` for the 101 AAR).

## Capability gate

`XposedApiCapabilities` is a process-level, fixed-bit snapshot.

```kotlin
internal object XposedApiCapabilities {
    fun initialize(apiVersion: Int)
    fun supportsStableHookId(): Boolean
    fun supportsReplaceHook(): Boolean
}
```

It is initialized once in `MainModule.onModuleLoaded` from `getApiVersion()`.
It uses no `Map`, no reflection, no thread, and holds no `Context`.

## API 102 bridge

`Api102HookBridge` is the **only** source file that references `HookBuilder.setId`.
It contains fixed, short stable hook IDs for infrastructure hooks:

- `res.text`
- `res.string`
- `res.layout`
- `res.drawable_density`
- `res.theme_merge`
- `systemui.init`
- `launcher.init`

The bridge does not hold `HookHandle`, `Context`, or any long-lived state. It has
no `Map`, no `Handler`, no thread, and no `Receiver`.

## Wiring status

| Capability | Status | Rationale |
|------------|--------|-----------|
| Stable hook ID (`setId`) | `INTENTIONALLY_UNWIRED_DOCUMENTED` | Bridge (`Api102HookBridge`) exists and is isolated; not called from production install path because the module keeps `minApiVersion=101` and cannot statically prove an API 101 host will not attempt to verify the 102-only `setId` symbol. |
| `replaceHook` | `INTENTIONALLY_UNWIRED_DOCUMENTED` | Capability is reported by `XposedApiCapabilities` but no production code calls `HookHandle.replaceHook`. Hot-swapping hooks is not required. |
| `getId` in callback | `INTENTIONALLY_UNWIRED_DOCUMENTED` | No caller reads `HookHandle.getId()`; stable IDs are stored only in the bridge constants. |
| Hot reload (`onHotReloading` / `onHotReloaded`) | `INTENTIONALLY_UNWIRED_DOCUMENTED` | Module does not override either callback and does not import `HotReloadingParam` or `HotReloadedParam`. |

`setId` remains isolated behind `Api102HookBridge`. To move to `WIRED_WITH_SAFE_FALLBACK`, every `HookInstallerFacade` call must first prove (via a focused unit test on a stub `XposedInterface.HookBuilder`) that the 102-only symbol is resolved lazily or guarded by `supportsStableHookId()`.
