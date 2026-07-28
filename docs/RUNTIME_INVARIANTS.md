# 运行期不变量与静态门禁

> 本文件记录 `tools/check-invariants.py` 每条规则背后的**真实缺陷**。
> 规则不是风格偏好；每一条都对应本仓库里编译通过、lint 通过、单元测试通过，但会在设备上出问题的代码。

执行：

```bash
python tools/check-invariants.py            # 全量
python tools/check-invariants.py --staged   # 只查 git 暂存区
```

退出码非 0 = 不允许提交。**不允许通过放宽规则或往 `ALLOWED` 里加 `mods/` 文件来「通过」。**

---

## 1. `guard-framework-callbacks`

### 缺陷

`HookerClassHelper.MethodHook` 的 `intercept` / `beforeHook` / `afterHook` 都有 try/catch，
所以 hook 主体抛异常只会进日志。**但模块在 hook 里注册出去的回调不在这个保护里。**

`Handler.handleMessage`、`BroadcastReceiver.onReceive`、`ContentObserver.onChange`、
`Runnable.run`、`post {}` / `runOnUiThread {}` / `setOnLongClickListener {}` 的 lambda，
都是框架直接调用的。里面一次 `XposedHelpers.getObjectField` 打不中（ROM 换了字段名、
对象已经 detach），异常就直接冒到 `system_server` / SystemUI / Launcher 的主线程。

审计时找到 30 处这样的回调没有任何保护。其中：

- `ModuleHelper.handlePreferenceChanged` 逐个调用观察者且不 catch。一个观察者抛异常，
  既会杀掉远端偏好监听线程，**也会让后面所有观察者拿不到这次变更** —— 一个静默的功能失效。
- `DeviceInfoMonitor` 的监控 tick 里 `Integer.parseInt(props.getProperty("POWER_SUPPLY_TEMP"))`
  没有判空。厂商 sysfs 少一个键，就是每 2 秒一次 `NumberFormatException`，直接崩 SystemUI；
  并且 `scheduleNextTick()` 在异常路径上不会执行，ticker 永久停摆。

### 契约

```kotlin
override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
    // 反射随便写
}
```

`ModuleHelper.guarded` 是 `inline`：不分配对象、不增加栈帧。

豁免：`ModuleHelper.PreferenceObserver.onChange`（分发处已逐个隔离）；
模块自己的设置应用进程（`MainApplication`、`tasker/UnlockReceiver`）。

---

## 2. `no-raw-register-receiver`

### 缺陷

原来的清理方式是把上一个 receiver 存在**被 hook 实例**的 additional instance field 上：

```kotlin
val old = XposedHelpers.getAdditionalInstanceField(thisObject, "myReceiver")
if (old is BroadcastReceiver) mContext.unregisterReceiver(old)
```

在 `hookAllConstructors` 里，`thisObject` **每次都是新实例**，这个字段是空的。
清理代码从写下那天起就没有生效过。

每次主题切换、密度变化、折叠态变化、面板重建，都多留一个活的 Receiver，
各自强引用一个已经死掉的 Context 和被 hook 对象。

实测受影响的注册点：`unlockStrongAuth`、`noScreenLock`、`fetchCachedDevices`、
三个 freeform receiver、时钟 `TIME_SET`、`MiuiPhoneStatusBarPolicy` 的闹钟 receiver。

最后一个监听 **`TIME_TICK`** —— 每分钟一次，泄漏 N 个就是每分钟 N 次无用唤醒 + 对已 detach
对象做反射，永久有效。这是纯粹的耗电。

同一类缺陷也存在于偏好观察者（`prefObservers` 是强引用 `CopyOnWriteArraySet`）
和键盘守卫手电筒的 `ContentObserver`。

### 契约

按目标的存活语义三选一：

| 场景 | API |
| --- | --- |
| 目标是进程单例 | `ModuleHelper.registerModuleReceiver(context, key, receiver, filter, flags)` |
| 多个目标可以合法共存（多时钟控制器、多屏状态栏） | `ModuleHelper.registerOwnedReceiver(context, owner, key, receiver, filter, flags)` |
| 非 Receiver 的注册（ContentObserver、ROM 监听器） | `ModuleHelper.replaceModuleRegistration(key, cleanup)` |

`registerOwnedReceiver` 对 owner 持弱引用；每次新注册时顺带清掉已被 GC 的 owner 的 receiver。
之所以不能一律用单槽位的 `registerModuleReceiver`：如果两个目标真的同时存活，
单槽位会**静默注销掉其中一个**，把内存问题换成功能问题。

偏好观察者同理：`ownedPrefObservers` 只持弱引用，强引用挂在 owner 的
additional instance field 上（`XposedHelpers` 用 `WeakHashMap` 保存），owner 死了就一起消失。
**不要改回强引用集合。**

自己管生命周期是允许的 —— `ScreenStateController`、`WeatherDataController`、
`StepCounterController`、`SystemUI.ScreenshotVisibilityReceiver` 就是正确示范：
字段 + 成对 register/unregister + 幂等标志 + 可重复调用的释放路径。
门禁据此放行：receiver 是具名字段，且同文件里有 `unregisterReceiver`。

匿名 receiver 无法被注销，**一律**要走注册表。

---

## 3. `no-redundant-arg-marshalling`

### 缺陷

```kotlin
val args = XposedHelpers.getArgsArray(chain)   // Chain.getArgs() 的 List + toArray 的副本
...
result = chain.proceed(args)                   // 框架重新 marshal 每个参数，逐个拆箱
```

`getArgsArray` = `chain.getArgs().toArray(EMPTY)`，**每次调用两次分配**。
把数组传回 `proceed(args)` 又让框架把每个参数重新编排一遍。

只读参数的 hook 完全不需要这些。审计时 340 个 `intercept` 里有 117 个属于这种情况，
包括状态栏背景、图标、通知着色这类会在绘制/滚动频率上被调用的 hook。

`before()` 一侧同样：`HookerClassHelper.intercept` 里
`if (before.hasMaterializedArgs()) chain.proceed(before.getArgs()) else chain.proceed()` ——
只要调用过 `param.getArgs()`，就会走到重编排那条分支。

### 契约

```kotlin
// intercept：只读单个参数
val view = chain.getArg(0) as View
result = chain.proceed()

// intercept：读多个参数
val args = chain.args        // List，不复制
result = chain.proceed()

// before：只读参数
val holder = param.getArg(3)  // 不要 param.getArgs()[3]

// 确实要改写参数 —— 也只有这时 —— 才用数组
val args = XposedHelpers.getArgsArray(chain)
args[0] = newValue
result = chain.proceed(args)
```

`Chain.getArg(int)` 在 **libxposed API 101** 就存在，不影响最低运行基线。

这个转换是**编译期可验证**的：被写入的参数数组、被 `*args` 展开成 vararg 的、
被当作 `Array<Any?>` 传出去的，改成 List / 单值访问后都会编译失败。

---

## 4. `no-looperless-handler`

`Handler()` 无参构造绑定的是**当前线程**的 Looper。在 hook 里，当前线程是谁完全取决于
ROM 什么时候构造了那个对象；没有 Looper 就直接抛。已在锁屏手电筒的 `ContentObserver` 上出现。

契约：永远显式传 Looper，例如 `Handler(context.mainLooper)`。

---

## 5. `no-legacy-xposed`

模块运行在 libxposed API 101/102 上，`de.robv.android.xposed` 在运行期不存在。

---

## 6. `no-regex-split-on-literal`

Java 的 `String.split("\\|")` 走单字符快路径，不碰正则引擎。
机械翻译成 `split("\\|".toRegex())` 后，**每次调用都 `Pattern.compile` + 建 `Matcher`**。
`Helpers.containsStringPair` 会在蓝牙/WiFi 列表适配器的 `isEnabled` 和锁屏可信网络判断里逐行调用。

只有单字符分隔符会被拦截；`"\\s+"` 这种真正的模式仍然应该是 Regex。

---

## 7. 门禁覆盖不到的东西

静态检查只能拦住已知形状的缺陷。以下仍然只能靠人和实机：

- `Chain.proceed()` 的调用次数、before/after 语义、参数改写时机
- hook 注册顺序、注册条件、R8 可达性
- 迁移引入的控制流漂移（`break`/`continue` 在 lambda 里被吞掉）—— 见 `AGENTS.md` §5
- 热路径上的装箱、临时集合、重复偏好读取
- 多屏 / 折叠 / 主题切换下的真实生命周期

**Gradle 退出码 0 不等于设备上行为正确。**
