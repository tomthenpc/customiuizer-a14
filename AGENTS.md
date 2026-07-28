# AGENTS.md — CustoMIUIzer A14

本文件是本仓库的执行规则。适用于 Devin、Claude Code 和任何自动化改动。

优先级：**用户本轮明确要求 > 本文件 > `docs/` 工程文档 > Git 历史 > 上游 `MonwF/customiuizer v24.10.12`**。

代码、当前分支 HEAD、构建产物和实机日志的效力高于任何文档。文档与代码冲突时，改文档。

---

## 0. 这个模块为什么容易被改坏

这不是普通 App。模块代码运行在 **`system_server`、SystemUI、Launcher** 的进程里。

- 这些进程崩溃 = 手机黑屏、重启、开机循环。用户要刷机才能恢复。
- 编译通过、lint 通过、单元测试通过，**完全不代表**不会崩。本仓库已经出现过多次「三绿但会崩」。
- 一个 hook 每天可能被调用几十万次。一次多余的对象分配 = 每天几十万次 GC 压力。
- 一个泄漏的 BroadcastReceiver 监听 `TIME_TICK`，就是每分钟一次无用唤醒，永久有效。

**所以本文件的规则不是风格偏好，是设备可用性的边界。**

---

## 1. 提交前必过的门（不可跳过）

```bash
python tools/check-invariants.py && ./gradlew test lintVitalRelease assembleDebug assembleRelease
```

`check-invariants.py` 检查的每一条规则，都对应本仓库真实出现过、且编译/lint/测试全绿的缺陷。
它退出码非 0 就是**不允许提交**，不允许通过放宽规则或加白名单来「通过」。

白名单 `ALLOWED` 只允许包含**实现该规则的文件本身**，以及模块自己的设置应用进程。
往里加 `mods/` 下的文件 = 违规。

纯文档改动只需：UTF-8、相对链接、`git diff --check`。不要重新生成已验证的 APK。

---

## 2. 固定边界（未经用户明确要求不得改动）

| 项 | 值 |
| --- | --- |
| 仓库 / 分支 | `tomthenpc/customiuizer-a14`，当前活跃分支为唯一代码基线 |
| 平台 | HyperOS 1 / Android 14 / SDK 34 |
| `applicationId` | `tv.withaibuild.customiuizer.r14` |
| `minSdk` / `targetSdk` | 34 / 34 |
| ABI | `arm64-v8a` |
| libxposed | 用 API 102 编译，**API 101 是最低运行基线** |
| `staticScope` | `false`，Hot Reload 关闭 |
| Legacy Xposed | `de.robv.android.xposed` 运行期 API 一律禁止 |

版本号、AGP/Gradle/Kotlin 版本、签名配置一律**实时读当前分支**，不得照抄历史文档。

保留 Java 的三个文件不迁移：`MainModule.java`（类名写死在 `java_init.list`）、
`XposedHelpers.java`（LSPosed 派生，需与上游可比对）、`MemberUtilsX.java`（commons-lang3 派生）。

**API 101 边界**：公共加载与 hook 路径只能用 API 101 已有能力；API 102 专属类型不得出现在
API 101 必经类的字段、方法签名或静态初始化中；版本判断只放在入口或冷路径；不反射调用 libxposed API。

---

## 3. Hook 编写契约

### 3.1 异常绝不能逃出模块

`MethodHook.intercept` / `before` / `after` 的**内部**有 try/catch。
但模块在 hook 里**注册出去的回调**没有：

`Handler.handleMessage`、`BroadcastReceiver.onReceive`、`ContentObserver.onChange`、
`Runnable.run`、`post {}`、`postDelayed {}`、`runOnUiThread {}`、各种 `setOnXxxListener {}`。

这些由框架直接调用。里面一次 `getObjectField` 打不中（ROM 改了字段名），就是 SystemUI 崩溃。

```kotlin
override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
    // 反射随便写，抛了只进日志
}
```

`ModuleHelper.guarded` 是 `inline` 的：不分配对象、不增加栈帧。没有理由不用。

**唯一豁免**：`ModuleHelper.PreferenceObserver.onChange` —— `handlePreferenceChanged` 已经逐个隔离。

### 3.2 参数：不改就不要复制

`XposedHelpers.getArgsArray(chain)` 每次调用分配**两次**（`getArgs()` 的 List + `toArray` 的副本），
`chain.proceed(args)` 还要让框架把每个参数重新 marshal 一遍（基本类型逐个拆箱）。

```kotlin
// 只读参数
val view = chain.getArg(0) as View          // 零分配
result = chain.proceed()                     // 零 re-marshal

// before() 里同理
override fun before(param: BeforeHookCallback) {
    val holder = param.getArg(3)             // 不要 param.getArgs()[3]
}

// 确实要改写参数时——也只有这时——才用数组
val args = XposedHelpers.getArgsArray(chain)
args[0] = newValue
result = chain.proceed(args)
```

### 3.3 注册必须绑定所有者

**这是本仓库最容易重犯的错误。**

错误写法（曾在 6 处出现）：

```kotlin
// hookAllConstructors 里：thisObject 每次都是新实例，
// 所以这个 old 永远查不到上一个实例注册的东西 —— 清理代码从未生效过
val old = XposedHelpers.getAdditionalInstanceField(thisObject, "myReceiver")
if (old is BroadcastReceiver) mContext.unregisterReceiver(old)
mContext.registerReceiver(myReceiver, filter, flags)
```

每次主题切换、密度变化、面板重建，都多一个活的 Receiver，各自钉住一个已死的 Context。

正确写法，按目标的存活语义三选一：

```kotlin
// 目标是进程单例（KeyguardViewMediator、BluetoothControllerImpl…）
ModuleHelper.registerModuleReceiver(mContext, "myReceiver", receiver, filter, flags)

// 同时可能有多个合法存活的目标（多个时钟控制器、多屏状态栏）
ModuleHelper.registerOwnedReceiver(mContext, thisObject, "myReceiver", receiver, filter, flags)

// ContentObserver、ROM 监听器等非 Receiver 的注册
resolver.registerContentObserver(uri, false, observer)
ModuleHelper.replaceModuleRegistration("myObserver") { resolver.unregisterContentObserver(observer) }
```

同样的道理适用于 `observePreferenceChange(observer, owner)`：观察者只被弱引用持有，
强引用挂在 owner 的 additional instance field 上，owner 死了就一起消失。**不要改回强引用集合。**

自己管生命周期也可以（`ScreenStateController`、`WeatherDataController` 就是），
但必须是字段 + 成对 register/unregister + 幂等标志，并且 unregister 可重复调用。

### 3.4 hook 语义不得漂移

改动必须保持：hook target、注册顺序与条件、before/after、参数改写、提前返回、
`result` / `throwable`、`Chain.proceed()` 的**调用次数**、回调次数。

`chain.proceed()` 永远放在 try 之外或只调用一次 —— 异常路径二次执行原方法是灾难。

同时保持：FQCN、构造器、重载、JVM descriptor、primitive/boxed 类型、`@JvmStatic`/`@JvmField`/`@JvmName`、
反射用到的类名与成员名、DexKit 字符串、Manifest、authority、preference key、资源名、
`META-INF/xposed/java_init.list`、`module.prop`、scope、R8 可达性。

ROM 上目标不存在时：**记录一次，停用该单项功能**。不得高频重试，不得拖垮宿主进程。

---

## 4. 热路径

成本模型：`触发频率 × 单次成本 × 进程数 × 存活时间`。

绘制、动画、触摸、通知绑定、状态栏、控制中心、网速、音频、资源读取里**禁止**：

- 反射、DexKit、磁盘 I/O、同步 Binder 调用
- 重复读 SharedPreferences、重复判断 API/ROM 版本
- 临时集合/数组、`Pair`/`Triple`、装箱、捕获型 lambda、重复格式化
- 大锁、正常运行日志、重复兼容探测

热路径只读**已经准备好的**不可变或原子状态。反射、解析、资源查找、兼容探测全部放冷路径。

功能关闭时必须接近零运行成本；无关进程不初始化无关功能。
事件与生命周期回调优先于轮询；息屏时长期 ticker 必须停。

`Map<Int, *>` / `Map<Long, *>` 在热路径上换 `SparseArray` / `LongSparseArray`（Kotlin 的 `map[intKey]` 每次装箱）。

---

## 5. Kotlin 迁移的已知回归（审计任何迁移代码前先读）

机械翻译 Java → Kotlin 会**静默**引入运行时退化。已在本仓库实际发生过：

| 症状 | 原因 | 检查方法 |
| --- | --- | --- |
| 取到最后一个匹配而非第一个；每次扫描全部文件 | 循环体内的 `break` 在 `use{}`/`forEach{}` 里被丢掉，Kotlin lambda 无法非局部跳出 | 与迁移前 Java 比对 `break`/`continue` 计数 |
| 每次调用 `Pattern.compile` | `split("\\|")` 被翻成 `split("\\|".toRegex())` | 门禁规则 `no-regex-split-on-literal` |
| 每次读取装箱一个 Integer | `ConcurrentHashMap<Int, _>` 替代了 `SparseArray` | 人工审查热路径容器类型 |
| 线程可见性丢失 | 迁移时以「看起来只在初始化期写入」为由去掉了 `volatile`/同步 | 与原 Java 字段语义逐个核对 |

比对命令：

```bash
git show "<迁移commit>^:app/src/main/java/<path>.java" | grep -nE '\bbreak\b|\bcontinue\b' -A 6 -B 1
```

判定：`switch` 里的 `break` 迁移到 `when` 后消失属正常；**循环体内的 `break`/`continue` 消失一律先按回归处理**。

---

## 6. 代码风格

Kotlin-first，但**不追求 100% Kotlin**，不为了减少行数破坏 JVM / 反射 / hook 语义。

方向：低抽象、强边界、状态显式、控制流直接、热路径可预测、资源所有权清楚、兼容代码集中。

用 Kotlin 换取：空安全、显式状态建模、不可变性、资源释放、分支完整性、可测试性。

避免：`!!`（尤其对 ROM 返回值）、深层 scope function、复杂 DSL、隐藏副作用、
热路径上的长集合链和无必要 `Sequence`、用 Flow/coroutine 替代简单回调而增加调度与生命周期成本。

不得曲解为超长函数、全局可变状态、复制逻辑或吞掉异常而不记录。

---

## 7. 改动纪律

**改之前必须先有证据。** 至少确认：具体代码与调用链、所属包/进程/生命周期、
当前功能开关与触发条件、相关 Git 历史、可复现场景或日志、是否属于 ROM 或其他模块的问题、
是否影响 R8 / 反射 / ClassLoader / 动态入口。

**不构成修改理由**：Java 文件还存在、代码不够函数式、日志出现 error 级别、
上游 A14 实现不同、理论上可能更慢、Lint 报了「未使用资源」。

**范围**：只做用户要求的事。不顺手重构、不顺手改配置、不顺手升级依赖。
用最小但完整、可解释、可验证的改动。行尾、格式化、资源清理单独提交。

**资源不得批量删除**。删之前必须搜索：XML 引用、代码 `R.*`、`getIdentifier`、
反射与字符串名、ROM/Xposed 动态访问、R8/resource shrink 输出。

**不是默认目标**（除非用户本轮明确要求）：再做一次全项目 Java→Kotlin 迁移、
迁移剩余稳定 Java 边界、重做 Kotlin DSL / version catalog、启用 Hot Reload、
Android 15/16 适配、无证据的全仓重构或微优化。

---

## 8. 验证

按风险覆盖，用仓库里**实际存在**的任务，不伪造结果：

1. `python tools/check-invariants.py`
2. `./gradlew test`
3. `./gradlew lintDebug lintRelease lintVitalRelease`
4. `./gradlew assembleDebug assembleRelease`（含 R8 与 resource shrink）
5. 核对 applicationId、versionName/Code、SDK、ABI、Xposed metadata、scope、动态入口
6. 核对 zipalign、APK SHA-256、**实际签名证书**
7. 扫描 Legacy Xposed API 残留

涉及 hook、入口、反射、R8、Manifest、资源、Locale、主题、Fragment 生命周期或 libxposed 时，
**必须**加 Release 构建 + 实机验证。

设置 UI 改动额外验证：日间/夜间、状态栏与导航栏图标明暗、Toolbar/Preference/Switch/弹窗/About、
主页面与子页面、搜索、旋转、返回栈、Fragment 重建、应用内语言切换与跟随系统、
普通/分享/打开方式选择器、BT/WiFi 列表、资源收缩与多语言 fallback。

**Gradle 退出码 0 或生成了 APK，不等于目标进程和实机行为正确。旧的 rc 日志不能证明当前 HEAD。**

最终结论必须区分四档：**已验证 / 代码层面确认 / 待实机验证 / 无法确认**。

---

## 9. Git、签名与发布

普通的读取、搜索、编辑、测试、构建、当前开发分支的 commit 与已授权 push，**不要逐项询问**。

未经用户明确要求，禁止：`git reset --hard`、`git restore .`、`git checkout -- .`、`git clean -fd`、
force push、用远端旧状态覆盖未知本地改动、修改或合并 `main`、创建平行分支、
创建 PR / tag / GitHub Release、公开上传或替换 APK、删除分支或旧 Release。

禁止提交或公开：keystore、密码、token、真实 `keystore.properties`、APK、签名备份、
私人日志、缓存与构建目录、设备专属敏感信息。

正式签名配置在仓库之外。**缺少正式签名配置时不得伪造 Release 成功，不得用 Debug 证书 APK 冒充候选版。**
每个候选 APK 都要核对实际签名证书 SHA-256。新旧签名线不能覆盖安装时，必须在用户文档里写清备份、卸载、重装要求。

未完成当前 HEAD 实机验证的版本，不得称为稳定版。

---

## 10. 任务连续性

长任务在 `.devin/ACTIVE_TASK.md` 记录实时状态（本地文件，不提交）：
用户目标、分支与起始 HEAD、已完成项、正在做的、未完成项、最后一条命令及退出码、
已修改文件、已有验证证据、当前阻塞、下一条精确动作。

任务闭环后把长期有效的结论同步到 `docs/DEVIN_A14_CHECKPOINT.md`，然后清空 `ACTIVE_TASK.md`。

**「完成」必须有工作区 diff、commit、构建产物、日志或实机结果支撑**，
不得仅凭之前的计划文本或任务计数声称完成。

命令被取消只代表**那条命令**结束，不代表任务取消。用户说「继续」时：
检查残留进程和已产出的部分结果，从第一个未完成项接着做，不重复已完成的修改，
不重新询问用户已经给过的路径、分支和目标。

被问「卡了吗」时：先查真实进程状态，简短报告当前步骤，然后**立即继续**；
不要以「是否继续？」结尾，不要把等待命令审批误报成代码卡死。

只在这些情况停下来问：破坏性操作、凭据或签名材料未知、清除应用/设备数据、
改动 `main`、创建 PR/tag/Release/公开 APK、以及产品行为无法从证据判断时。

---

## 11. 汇报

只报有价值的事实：分支 / HEAD / ahead-behind / 工作区状态、证据与根因、
改了哪些文件与行为变化、对 hook/JVM/API/R8/生命周期的影响、实际跑过的验证、
APK 与签名证书、commit/push 状态、以及**已验证 / 待实机 / 无法确认**的分档。

不要输出命令流水账。长任务只在闭环、发现重要风险、改变路线或遇到硬阻塞时汇报。
