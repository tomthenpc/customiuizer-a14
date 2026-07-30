# 轻量 Hook 安装诊断能力设计

## 目标

让 LSPosed/实机日志能区分以下状态，而不引入 Hook DSL、DI 或动态注册框架：

1. 目标类不存在 / 方法签名变化；
2. Hook 安装失败（异常、target 为空、版本不匹配）；
3. 模块入口是否已加载、是否已处理到某个包；
4. DexKit / RemotePreferences 等边界失败；
5. 用户未开启的功能不记录为安装失败。

## 约束

- 不分配对象在 Hook 回调热路径。
- 不增加 hook 注册抽象层。
- 诊断信息只在冷路径写入统一日志。
- 保持现有 `MethodHook` 回调语义（before/after/intercept、proceed 次数）。
- 记录容量有界，不引用 `Context`、`ClassLoader`、`MethodHook` 或用户数据。

## 设计

### 1. 状态枚举

`mods/utils/HookDiagnostics.kt`：

```kotlin
enum class Status {
    INSTALLED,
    TARGET_CLASS_MISSING,
    TARGET_MEMBER_MISSING,
    INSTALL_FAILED,
    SILENTLY_SKIPPED,
    DEXKIT_FAILED,
    PREFERENCES_UNAVAILABLE,
}
```

### 2. 收集器

单例 `HookDiagnostics`：

- 进程内唯一；
- `ConcurrentHashMap` 去重，键为 `process|kind|class|member|descriptor|status`；
- 256 条上限；
- 暴露 `record()`、`summary()`、`snapshot()`、`reset()`、`printSummaryOnce()`。

### 3. 包装入口

`ModuleHelper` 中的 `findAndHookMethod*`、`hookAllMethods*`、`hookAllConstructors*`、`hookMethod` 均先定位 class，再尝试 hook，并按结果写入对应状态。

- class 不存在 → `TARGET_CLASS_MISSING`；
- class 存在但 member 找不到 → `TARGET_MEMBER_MISSING`；
- 其他异常 → `INSTALL_FAILED`；
- `*Silently` 失败 → `SILENTLY_SKIPPED`；
- 成功 → `INSTALLED`。

### 4. 入口汇总

`MainModule.onSystemServerStarting()` 和 `MainModule.onPackageReady()` 结束时调用 `HookDiagnostics.printSummaryOnce()`，输出一行统计，如：

```text
CustoMIUIzer HookSummary process=com.android.systemui installed=42 classMissing=2 memberMissing=1 failed=0 silentSkipped=3
```

### 5. 直接 hook 安装禁止

`tools/check-invariants.py` 新增 `no-direct-hook-installation`：除 `ModuleHelper.kt` 和 `XposedHelpers.java` 内部实现外，业务代码必须走 `ModuleHelper` 包装器，确保所有 hook 安装都被记录。

### 6. 测试

`HookDiagnosticsTest.kt` 覆盖安装、缺失、失败、去重、有界、异常分类等。
