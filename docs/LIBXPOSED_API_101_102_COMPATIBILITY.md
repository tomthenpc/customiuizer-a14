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

| Capability | Status |
|------------|--------|
| Stable hook ID (`setId`) | `READY_NOT_WIRED` |
| `replaceHook` | `NOT_USED` |
| `getId` in callback | `NOT_USED` |
| Hot reload | `NOT_ENABLED` |

`setId` is isolated behind `Api102HookBridge`, but it is **not** called from the
production install path because the project cannot yet statically prove that an
API 101 host will not resolve the 102-only `setId` symbol when `doHookMethod` is
verified. Once that proof is in place the status can be updated to `WIRED`.
