# 轻量 Hook 安装诊断能力设计

## 目标

让 LSPosed/实机日志能区分以下状态，而不引入 Hook DSL、DI 或动态注册框架：

1. 模块未加载 / 入口类未触发
2. 目标类不存在 / 方法签名变化
3. Hook 安装失败（异常、target 为空、版本不匹配）
4. 偏好未生效（默认值 / 远程偏好不可读 / 监听未注册）

## 约束

- 不分配对象在热路径。
- 不增加 hook 注册抽象层。
- 诊断信息只在冷路径写入统一日志，或通过不抛出的 side channel 返回。
- 保持现有 `MethodHook` 回调语义（before/after/intercept、proceed 次数）。

## 设计

### 1. 状态枚举

在 `mods/utils/ModuleHelper` 增加：

```kotlin
enum class HookInstallStatus {
    NOT_ATTEMPTED,
    TARGET_CLASS_MISSING,
    TARGET_METHOD_MISSING,
    INSTALLED,
    INSTALL_FAILED,
    PREF_DISABLED
}
```

热路径上不创建该对象；仅在安装期返回给 `MainModule` 的汇总表。

### 2. 包装入口

```kotlin
fun findAndHookMethodWithStatus(className: String, loader: ClassLoader, methodName: String, vararg args: Any): HookInstallStatus
```

内部先 `findClassIfExists`：

- 不存在 → 记录一次 `HookInstallStatus.TARGET_CLASS_MISSING` 并返回。
- 存在 → 尝试 `findAndHookMethod`：
  - 找不到方法 → `TARGET_METHOD_MISSING`
  - 成功 → `INSTALLED`
  - 抛异常 → `INSTALL_FAILED`

### 3. 入口汇总

`MainModule.handleLoadPackage` 结束时，按进程打印一次汇总：

```
CustoMIUIzer-A14 [com.android.systemui] hooks: installed=42 classMissing=3 methodMissing=1 failed=0
```

失败的包含目标类名和方法名，方便 ROM 变动定位。

### 4. 偏好生效诊断

- 在 `MainModule.watchPreferenceChange` 注册后记录 `PreferenceObserver registered: count=N`。
- 在 `MainModule.mPrefs` 首次读取时捕获 `RemotePreferences` 异常，若不可读则记录一次 `RemotePreferences unavailable`。
- 每个 hook 的入口判断 `if (!mPrefs.getBoolean(...))` 时，若功能关闭则不视为失败。

### 5. 日志级别

- `d/`: 正常安装成功，限流，避免刷日志。
- `e/`: 一次失败，包含完整 target 描述。
- `w/`: class/method 不存在，记录一次，禁用单项功能。

## 未实现原因

本轮未直接落地，因为：

- `findAndHookMethod` 被几十处直接调用，需要逐个确认返回值用法，属于一次较大的 hook 入口重构；
- 诊断输出格式需与现有 `XposedHelpers.log` 和 LSPosed 日志规则协调，避免误判；
- 需要与 `check-invariants.py` 新增规则同步，防止 `findAndHookMethod` 直接调用漏过。

建议作为下一阶段 `diagnostics/a14` 独立分支实施，先在 1–2 个 hook 族（如 GlobalActions / SystemStatusBarIconHooks）试点。
