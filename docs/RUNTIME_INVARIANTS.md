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

## 1b. `guard-deferred-callbacks`（第一轮漏掉的形状）

第一轮的规则只匹配 `override fun run()` 这类**具名**回调，于是漏掉了 lambda 形状：

```kotlin
mHandler!!.postDelayed(Runnable {   // 不是 override fun run()，规则看不见
    ...
}, delay)
```

漏掉的两处就在 `Controls.kt` 的电源键与音量键长按里，`mHandler` 取自
`MiuiPhoneWindowManager`，也就是说这两个 Runnable 跑在 **`system_server`** 的 handler 上。
里面有 `newWakeLock`、`sendBroadcast`、`GlobalActions.sendDownUpKeyEvent` 的反射，
以及 `mPrefs.getStringAsInt` 的 `toInt()`（偏好里存了非数字就抛 `NumberFormatException`）。

**在 `system_server` 里抛异常不是应用崩溃，是设备重启。** 这是整个仓库里最高危的一类。

规则现在覆盖：`Runnable {}`、`post/postDelayed/postAtTime/postOnAnimation/runOnUiThread {}`、
`Thread {}`、`setOnXxxListener {}`、`withEndAction/doOnLayout/addUpdateListener/postFrameCallback {}`。
`mods/` 下这些形状一律要 `ModuleHelper.guarded`。空 lambda 除外（不可能抛）。

需要返回值的回调用带兜底值的重载：

```kotlin
// handleNavBarAction 在未配置动作时返回 false，让 ROM 保留自己的长按行为。
// 兜底值必须是"不消费"，否则失败时会把宿主的默认行为一起吞掉。
view.setOnLongClickListener { v ->
    ModuleHelper.guarded(false) { handleNavBarAction(v.context, key) }
}
```

## 1c. `coroutine-scopes-handle-failure`

`SupervisorJob()` 只保证一个子协程失败不会连坐取消兄弟协程，**它不吞异常**。
`launch` 里未捕获的异常仍然会走到线程的默认异常处理器——在 SystemUI / Launcher 里就是进程死亡。

`StepCounterController`、`WeatherDataController`、`LockScreenAlbumArtController` 三个 scope
原本都只有 `SupervisorJob()`。

契约：`mods/` 下每个 `CoroutineScope(...)` 都要带 `+ ModuleHelper.coroutineFailureHandler`。
挂在 scope 上而不是包住每个 `launch`，这样以后新增的协程不可能忘。

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

## 6b. 实例级状态必须按身份存，不能按 `equals`

`setAdditionalInstanceField` 原来的后端是 `WeakHashMap` + 一把全局锁。锁是我去看它的原因，
**键的语义才是真正的 bug**。

`WeakHashMap` 按 `equals`/`hashCode` 找键。而"additional **instance** field"的含义是按实例。
对任何有值语义的被 hook 类：

- 两个不同但 `equals` 的对象会**共用同一份字段表**；
- 一旦改动了参与 `hashCode` 的字段，该对象的条目就落到别的桶里、**永远找不回来**，存进去的值直接丢。

Launcher 的重命名功能就是这个形状：在 `ShortcutInfo` 上存 `mLabelOrig`（`Launcher.kt:511`），
然后改写同一个对象的 `mLabel`（`Launcher.kt:456`），之后再读回 `mLabelOrig` 来恢复原名
（`Launcher.kt:453`）。这段能不能读到，取决于 ROM 的 `ShortcutInfo` 是否用 label 算 hash ——
模块不该依赖这种事。

现在的实现：键是**按身份比较的弱引用**，读路径无锁、零分配（线程内复用探针，用完即释放，
否则每个线程会钉住一个对象），引用队列在写入时清理（写入正是旧实例被替换的时刻）。
`null` 仍然是可存的值（调用方用它清槽位），通过哨兵绕过 `ConcurrentHashMap` 不接受 null 的限制。

**可复用结论**：需要"每个实例一份"的状态时，用身份比较；`WeakHashMap`、`HashMap`、
`HashSet` 都是 `equals` 语义，对可变的 ROM 对象都不安全。

## 7. 门禁覆盖不到的东西

静态检查只能拦住已知形状的缺陷。以下仍然只能靠人和实机：

- `Chain.proceed()` 的调用次数、before/after 语义、参数改写时机
- hook 注册顺序、注册条件、R8 可达性
- 迁移引入的控制流漂移（`break`/`continue` 在 lambda 里被吞掉）—— 见 `AGENTS.md` §5
- 热路径上的装箱、临时集合、重复偏好读取
- 多屏 / 折叠 / 主题切换下的真实生命周期

**Gradle 退出码 0 不等于设备上行为正确。**

---

## 8. 大文件按功能域拆分：怎么做才是可证明安全的

`mods/System.kt` 曾经是 4898 行 / 129 个成员。此前一直没做，理由写的是"要等有实机回归能力"。
实际卡住的**不是设备，是验证手段**。

关键认识：**hook 注册顺序是 `MainModule` 调用序列的属性，与被调用者在哪个文件无关。**
所以一次拆分只需要机械证明两件事：

1. 每个被搬动的成员，文本逐字节不变；
2. `MainModule` 的有序调用序列不变。

两条都能脚本化，都不需要设备。

### 先量，再动

拆之前先量耦合，`System.kt` 的实际结果比它的行数好得多：

- 94 个 public 入口，**全部且仅**被 `MainModule` 调用；
- 19 个私有辅助函数，每个只被**同域**函数调用；
- **零 public→public 调用**；
- 16 处共享状态，每处只被 1–2 个函数使用，且都落在同一个域内。

没有任何东西跨域。**如果这几项不成立，就不要拆**。拆分脚本 `tools/split-hook-domain.py` 与
`tools/repoint-hook-calls.py` 已在 A14 文档清理中移除；当前 `MainModule` 的结构见
[A14_RUNTIME_HARDENING.md](A14_RUNTIME_HARDENING.md)。

### 工具与保证

| 工具 / 当前替代 | 保证 |
| --- | --- |
| `tools/split-hook-domain.py` / `tools/repoint-hook-calls.py` | 拆分脚本已在 A14 文档清理中移除。当前由 `check-invariants.py` 的 `check_main_module_calls_covered` 与 `check_no_direct_hook_installation` 等规则保证注册点可追溯，具体结构见 [A14_RUNTIME_HARDENING.md](A14_RUNTIME_HARDENING.md)。 |

### 本次证据

- 119/119 个成员逐字节一致，无遗漏；`System.kt` 的 diff 是**纯删除，新增 0 行**
- `MainModule` 前后各 268 个调用点，85 个接收者变化，**序列完全一致**
- R8 保留方法数前后均 **7887**；唯一的 15 处差异是 Kotlin `access$` 桥接方法的参数类型
  从 `mods.System` 变成新的宿主类，一一对应。**没有方法丢失，没有入口不可达**
- Release APK **3,065,633 字节，与拆分前完全相同**；`META-INF/xposed` 完好
- `proguard-rules.pro` 无需改动：`-keepclassmembers class tv.withaibuild.customiuizer.mods.**` 是通配的

### 结果

```
System.kt              4898 -> 593
SystemLockScreenHooks       1445   锁屏 / 解锁 / 应用锁
SystemNotificationHooks      730   通知栏 / 悬浮通知
SystemAudioHooks             634   可视化 / 媒体会话 / 振动
SystemWindowHooks            566   旋转 / 小窗 / 分屏 / 覆盖层
SystemDisplayHooks           534   息屏动画 / 亮度 / 壁纸
SystemShareMenuHooks         288   分享面板 / 打开方式
SystemSecurityHooks          243   签名 / 完整性 / FLAG_SECURE
```

`SystemUI.kt`（3681 行）和 `Launcher.kt` 可以用同一套工具照做，但**必须先量耦合**，
不要假定它们和 `System.kt` 一样干净。
