# r14.12.0 LSPosed/Vector 运行日志审计

## 审计结论摘要

运行日志检查结果：**存在阻断问题**。

本次没有发现 A 类（确认由本模块引起）的崩溃、ANR、入口加载错误、Hook 安装异常或 API 101/102 链接错误。模块在 SystemUI、Launcher、设置等 11 次可见加载中全部成功，当前设备运行的 Xposed bridge API 为 101，说明 `targetApiVersion=102` 的同一 APK 至少能够在 API 101 框架中完成入口类加载。

但是，当前启动周期的 `system_server`（PID 2926）没有出现本模块的 `Loading module`、R8 后入口类 `yo` 或 `Loaded module ... successfully` 记录。导出时的作用域映射又明确包含 `system/1000 -> tv.withaibuild.customiuizer.r14 -> yo`，且当前偏好中存在需要 `onSystemServerStarting` 的功能。因此，本次不能认定 system_server Hook 已安装或功能已生效。该问题暂列 B 类；在重新确认作用域并重启、取得 system_server 成功加载证据前，不建议正式发布 r14.12.0。

本次不修改业务源码、不修改版本号，也不创建 r14.12.1。现有证据更接近 Vector/作用域映射时序或设备侧配置问题，尚不足以归因到模块源码。

## 日志来源

| 项目 | 值 |
| --- | --- |
| 日志目录 | `C:\Users\tv\Downloads\Peengeek\LSPosed_2026-07-26T16_11_07.106536` |
| 导出时间 | 2026-07-26 16:11:07（按目录名；日志内容延续到约 16:11:11） |
| 递归文件数 | 99 |
| 总大小 | 47,795,745 bytes（约 45.58 MiB） |
| 压缩包 | 未发现 ZIP/GZIP/7z/RAR/BZip2/XZ 文件头 |
| 主要内容 | `full.log`、当前及旧 Vector 日志、`dmesg.log`、`kmsg.log`、28 份文本 tombstone 及对应 protobuf、9 份 ANR 文本、`scopes.txt`、属性文件和 `modules_config.db` |
| 模块版本 | r14.12.0 / versionCode 174（任务基线；日志未独立记录 versionCode） |
| applicationId | `tv.withaibuild.customiuizer.r14` |
| 代码 commit | `c95e7a8`（当前工作区确认为 `c95e7a85b1713259a2454304d6c9f4f60952d10d`） |
| APK SHA-256 | `7E488C4ED011F68321A8A2E5911B61D1C35659C98CA0116500855F79F05ED80E`（任务基线；导出目录不含 APK，未重新计算） |
| 签名证书 SHA-256 | `3061A3DA1C2FC46B44E215D024B1BFE3A012CB4D70B90B0214FA9FC896CEF60D`（任务基线；导出目录不含 APK，未重新计算） |
| 设备 | Xiaomi 13，型号 `2211133G`，设备代号 `fuxi`，arm64 |
| ROM | `V816.0.7.0.UMCTWXM`，fingerprint `Xiaomi/fuxi_global/fuxi:14/UKQ1.230804.001/V816.0.7.0.UMCTWXM:user/release-keys` |
| Android | Android 14 / SDK 34 |
| Xposed 框架 | Vector 2.0（3046） |
| 实际 framework API | 101；由当前启动日志 `XSmsCode: Xposed bridge version: 101` 确认 |
| 模块元数据基线 | `minApiVersion=101`、`targetApiVersion=102`、`staticScope=false`、未启用 Hot Reload |

审计时递归盘点了全部文件。protobuf tombstone 以同编号文本副本作为主要可读证据，同时对二进制文件进行了模块包名/关键异常字符串扫描；未发现本模块命中。`modules_config.db` 显示本模块启用且保存了 `android`、SystemUI、Launcher、设置等作用域和 `customiuizer_prefs_remote` 数据。

## 模块相关日志数量

- `full.log` 中含完整 applicationId 的行：228。
- 当前 `modules_2026-07-26T15_58_33.435558.log` 中含 applicationId 的行：22，构成 11 次加载尝试和 11 次成功结果。
- 当前 `verbose_2026-07-26T15_58_33.426067.log` 另有 1 次设置应用的模块 Binder 下发事件。
- `modules` 与 `verbose` 会记录同一加载事件，因此不把两者简单相加。去重后的关键结果是：**11/11 可见加载成功，1 次 Binder 下发成功，system_server 0 次本模块加载**。

当前启动周期可见的成功加载进程包括：

- `com.miui.miwallpaper`；
- `com.android.systemui`；
- `com.miui.home`；
- `com.android.settings`；
- `com.android.settings:remote`（两个不同 PID，前一个被系统因 `WifiCloudSync` 正常回收后重新创建）；
- `com.miui.securitycenter` 及其 remote 进程；
- `com.miui.powerkeeper`；
- `com.miui.gallery`（两个不同 PID，均成功加载；进程被系统 AutoIdleKill 后按使用场景重建）。

## 进程加载状态

| 组件 | 状态 | 证据 | 异常 |
| --- | --- | --- | --- |
| 模块入口 | 正常 | 每个可见目标进程均依次出现 `Loading module tv.withaibuild.customiuizer.r14`、`Loading class class yo`、`Loaded module ... successfully`；11 次加载全部成功 | 未见 `VerifyError`、入口实例化失败或 R8/ClassLoader 错误 |
| system_server | 异常/待复核 | PID 2926 于 15:58:34.140 开始 `Loading Vector/Xposed for system`，15:58:35.087 完成框架注入；期间加载了其他现代/Legacy 模块，但没有本模块。`scopes.txt` 却包含 `system/1000 -> tv.withaibuild.customiuizer.r14 -> yo` | system_server Hook 未获得安装证据；很可能未执行，是当前发布阻断项 |
| SystemUI | 正常 | PID 5865 于 15:58:41.494 开始加载，15:58:41.531 成功；持续运行至日志末尾 16:11:10，无死亡或重启 | 未见模块栈、Hook 失败、崩溃或 ANR |
| Launcher | 正常 | PID 5890 于 15:58:41.622 开始加载，15:58:41.651 成功；持续运行至 16:11:09，无死亡或重启 | MIUI 自身 `MarketIconCustomizer` 类探测失败，但完整堆栈仅含 Launcher/Java 线程池，不含本模块 |
| Remote Preferences | 部分确认/日志不足 | `modules_config.db` 中存在 `customiuizer_prefs_remote` 数据；16:05:54.809 Vector 向模块设置进程下发 Binder；未见 Binder、权限、`RemoteException`、`DeadObjectException` 或 service 连接错误 | 本次日志没有记录一次明确的“设置写入 -> 目标进程 Observer 收到”链路，不能仅凭 Binder 下发证明端到端动态同步 |
| 设置应用 | 正常 | PID 3098 于 16:05:54 启动，Activity 完成 create/start/resume，多次进入设置子页，随后正常 stop；之后仍有 PSS 记录，无进程死亡 | 8 条普通 untrusted-app SELinux 拒绝来自 MIUI 显示属性/RenderThread sysfs 探测，无 Java 异常或功能失败 |

## 重点路径结论

### 模块入口与 API 101/102

- 当前实机框架 API 为 101。
- 编译目标 API 102 的入口类 `yo` 能在 API 101 环境被加载并成功实例化。
- 未发现 `VerifyError`、`NoClassDefFoundError`、`NoSuchMethodError`、`NoSuchFieldError`、`AbstractMethodError`、`IncompatibleClassChangeError`、`IllegalAccessError`、`LinkageError`、`UnsatisfiedLinkError` 或 `ExceptionInInitializerError` 与本模块相关。
- 未发现 Legacy `de.robv.android.xposed` 运行调用导致的错误；设置 Activity 使用的 `de.robv.android.xposed.category.MODULE_SETTINGS` 仅为管理器入口 category，不是 Legacy Hook API 调用。
- 这份日志只覆盖 API 101 实机环境，不能作为 API 102 实机运行证明。

### system_server

当前证据链如下：

1. 15:58:32.006，Vector 识别 system_server PID 2926。
2. 15:58:34.140，开始为 `system` 加载 Vector/Xposed。
3. 15:58:35.030 至 15:58:35.962，Vector 加载了若干 Legacy 和现代模块。
4. 15:58:35.087，Vector 报告已注入 system_server。
5. 15:58:39.110，Vector 才报告 `System services are ready. Mapping modules and scopes`。
6. 全部当前 Vector 日志中，PID 2926 没有本模块的 `Loading module`、`class yo` 或成功加载记录。
7. 导出时 `scopes.txt` 和数据库状态又显示本模块包含 `android/system` 作用域。

这说明框架本身成功进入 system_server，但本模块没有在本次 system_server 启动窗口被加载。日志无法确定作用域是在启动前就已保存，还是启动后才发生变化；也无法证明是 Vector 的早期映射时序缺陷。因此暂列 B 类，不直接修改模块源码。

当前偏好数据中存在 `system_downgrade`、`system_disableintegrity` 等依赖 `onSystemServerStarting` 的功能，所以不能把“其他进程加载成功”替代成“system_server 功能已验证”。

### SystemUI

- 模块仅加载一次，入口成功。
- PID 5865 从加载后持续存活到日志末尾，没有 `am_proc_died`、`Killing 5865` 或 SystemUI 重启循环。
- 15:58:40.230 的 `Receiver ... already registered for pid 5865` 发生在模块 15:58:41.494 开始加载之前，时间上不可能由本模块这次初始化引起。
- 三次 `RecentsTransitionHandler: Duplicate call to finish` 都处于 MIUI Shell transition merge/cancel/cleanup 路径，没有本模块栈，也没有导致 SystemUI 重启。
- 未出现 `BatteryIndicator`、`AudioVisualizer`、音量面板模糊、截图/导航栏 Receiver、锁屏专辑封面 Receiver 相关异常、泄漏或 detach 后任务报错。
- 日志没有逐个输出本模块 Hook 安装成功信息，因此只能确认入口加载、无失败日志和进程稳定，不能把所有具体功能都标记为已操作验证。

### Launcher

- 模块仅加载一次，入口成功。
- PID 5890 持续存活到日志末尾，无启动崩溃、ANR 或重启循环。
- 16:10:05.864 的 `miui.content.res.MarketIconCustomizer` 缺失由 `com.miui.home.launcher.operationicon.MarketCustomizeReflectHelper` 自己捕获；完整栈没有本模块类，Launcher 随后继续正常处理手势和桌面状态。
- 未发现本模块 Hook target 查找失败、手势异常或重复初始化证据。

### Remote Preferences

- 模块设置进程能取得 Vector 下发的 service Binder。
- 数据库中存在远程偏好组和大量当前值。
- 目标进程加载时没有 Remote Preferences 权限、Binder 或 service 连接异常。
- `MainModule` 对偏好加载和 listener 注册有进程内防重复标志；日志没有出现可归因的重复 Observer。
- 由于日志中没有一次明确的偏好变更事件，本轮仍需把“设置修改立即传播到已运行目标进程”列为实机补测项。

## 异常清单

| ID | 时间 | 进程 | 异常/日志 | 分类 | 重复次数 | 影响 |
| --- | --- | --- | --- | --- | ---: | --- |
| B-01 | 15:58:34–15:58:39 | `system_server` PID 2926 / Vector PID 3497 | 作用域映射包含本模块，但 system_server 注入期间没有加载本模块；映射完成日志晚于注入 | B - 疑似模块问题 | 1 个启动周期 | system_server Hook 未验证且很可能未生效；阻止当前发布结论 |
| C-01 | 15:58:32.901、15:58:40.957 | root `init` PID 3346、8368 | native SIGABRT；`crash_dump` 因 SELinux/执行失败没有生成可用栈 | C - 系统或其他模块 | 2 | 无本模块包名、类或调用链；system_server/SystemUI/Launcher 均继续运行 |
| C-02 | 15:58:38.678、15:58:38.771 | `system_server` PID 2926 | ROM 服务 `LocationPolicyManagerService$Lifecycle`、`MiuiDragAndDropStubImpl...` 类缺失 | C - 系统或其他模块 | 2 | HyperOS/ROM 组件探测问题，无本模块加载或栈 |
| C-03 | 16:10:05.864 | Launcher PID 5890 | `MarketIconCustomizer` ClassNotFoundException | C - 系统或其他模块 | 1 | MIUI Launcher 自身反射路径捕获，进程继续运行 |
| C-04 | 15:59:04–15:59:05 | 微信/钉钉进程 | FkWeChat 尝试 Hook abstract method、DexKit 多匹配；另一模块报告钉钉方法/类未找到 | C - 系统或其他模块 | 5 条核心错误/警告 | 完整标签和栈指向其他模块，与本模块无关 |
| C-05 | 2026-06-20 至 2026-07-25 | 多个第三方应用/旧进程 | 28 份历史 tombstone、9 份 ANR 文本 | C - 系统或其他模块 | 37 个文件 | 时间早于本次启动；未发现本模块包名或调用栈 |
| C-06 | 15:58:39.110–15:58:39.653 | Vector daemon PID 3497 | `Manager is not installed` 与 Closeable resource warning | C - 系统或其他模块 | 2 条 | Vector/寄生管理器环境日志；可能有助于框架侧排查，但不能归因本模块 |
| D-01 | 15:58:40.230 | SystemUI PID 5865 | `Receiver ... already registered` | D - 正常或无害 | 1 | 发生在本模块加载前约 1.26 秒，不是模块重复 Receiver |
| D-02 | 16:01:14、16:05:35、16:07:07 | SystemUI PID 5865 | `RecentsTransitionHandler: Duplicate call to finish` | D - 正常或无害 | 3 | MIUI transition merge 路径，无崩溃或重启 |
| D-03 | 当前 Vector 日志 | 多进程 | 相同 handle 的 `dispatchPackageReady` 成对出现 | D - 正常或无害 | 31 组 | 框架范围现象，不等于模块重复加载；本模块每个 PID 仅见一次 loading/success，入口还通过 `isFirstPackage()` 限制处理 |
| D-04 | 16:05:54–16:05:56 | 模块设置进程 PID 3098 | 显示属性读取和 RenderThread sysfs `avc: denied` | D - 正常或无害 | 8 | 常见 MIUI 应用/渲染探测拒绝；Activity 正常运行，无异常栈 |

分类计数按上表“独立问题项”统计：

- A - 确认模块问题：0
- B - 疑似模块问题：1
- C - 系统、框架或其他模块：6
- D - 正常或无害：4

## 崩溃、ANR 与进程稳定性

- 本次启动后没有本模块设置进程、SystemUI、Launcher 或 system_server 的 Java `FATAL EXCEPTION`。
- 没有本模块包名对应的 `am_crash`、`am_anr` 或 tombstone。
- SystemUI PID 5865、Launcher PID 5890、system_server PID 2926 均持续到日志末尾，没有死亡或重启。
- 模块设置进程 PID 3098 正常完成 Activity 生命周期；日志停止记录该 PID 是因为 Activity 进入后台，不是进程崩溃。
- Gallery 与 `com.android.settings:remote` 的第二次加载发生在不同 PID。日志明确显示前一进程由系统 AutoIdleKill/WifiCloudSync 回收，不属于模块重复初始化或崩溃循环。

## 最终结论

| 问题 | 结论 |
| --- | --- |
| 模块是否成功加载 | 是，在 SystemUI、Launcher、设置和其他可见目标进程中 11/11 成功；但 system_server 未加载 |
| 是否存在模块崩溃 | 否，未发现 |
| 是否存在模块 ANR | 否，未发现 |
| 是否存在入口加载错误 | 否，未发现 |
| 是否存在 Hook 安装失败 | 未发现本模块 Hook 失败；日志不提供每个 Hook 的逐项成功证明 |
| 是否存在重复初始化 | 未发现本模块重复加载或重复初始化证据 |
| 是否存在 Receiver/Observer 重复注册 | 未发现可归因到本模块的重复注册 |
| 是否存在 API 101/102 兼容问题 | API 101 环境未发现兼容错误；API 102 环境未由本份日志覆盖 |
| 是否存在阻止 r14.12.0 发布的问题 | 是：system_server 没有加载本模块，核心 system_server 功能无法确认 |
| 是否建议直接发布 r14.12.0 | 否，先补充一次 system_server 成功加载的重启日志 |
| 是否需要修改模块源码 | 当前不需要；没有 A 类问题，也没有足够证据把 B-01 归因到源码 |

## 发布前最小复核步骤

1. 在 Vector 管理界面确认本模块启用，作用域中的“系统框架 / Android（`android`）”已勾选并保存。
2. 若界面已有勾选，先不要改模块源码；仅关闭再开启本模块，重新保存作用域，然后完整重启手机。
3. 开机后不要在导出前临时修改作用域；直接导出新的 Vector `modules` 与 `verbose` 日志。
4. 新日志必须在 `Loading Vector/Xposed for system (UID: 1000)` 附近出现：

   ```text
   Loading module tv.withaibuild.customiuizer.r14
   Loading class class yo
   Loaded module tv.withaibuild.customiuizer.r14 successfully.
   ```

5. 同时操作一个依赖 system_server 的已启用功能，并修改一个设置项，验证 Remote Preferences 的动态传播。
6. 如果重新保存作用域并完整重启后仍缺少上述三行，再把 B-01 升级为框架/模块集成问题，保留新日志和当时的 `modules_config.db`、`scopes.txt` 做进一步根因分析。

在完成以上复核前，结论保持为：**没有发现明确的模块源码错误，但 system_server 未加载构成发布阻断，暂不建议正式发布 r14.12.0。**
