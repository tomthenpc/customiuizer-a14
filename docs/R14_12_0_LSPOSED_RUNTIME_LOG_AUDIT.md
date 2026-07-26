# r14.12.0 LSPosed/Vector 完整运行日志审计

## 最终结论

按用户确认，`full.log` 是本轮完整运行日志，也是是否修复、是否发布的最终错误判定源。

审计结果：**没有发现可归因于 CustoMIUIzer r14.12.0 的运行错误，不需要修改业务源码，可以发布 r14.12.0。**

本轮没有发现：

- 模块 Java/Kotlin 崩溃或 ANR；
- 模块入口、ClassLoader、R8 或 Hook 安装错误；
- libxposed API 101/102 二进制链接错误；
- 可归因于模块的重复 Receiver、Observer、Listener 或重复初始化；
- SystemUI、Launcher 或模块设置进程因本模块死亡、重启或进入循环；
- 需要通过扩大 R8 keep、吞异常或回退 API 版本规避的问题。

日志中存在 ROM、框架及其他应用自身的错误和警告，但没有模块包名、模块类或调用栈证据，不能归因于本项目，也不构成本版本发布阻断。

## 日志基线

| 项目 | 值 |
| --- | --- |
| 日志文件 | `C:\Users\tv\Downloads\Peengeek\LSPosed_2026-07-26T16_11_07.106536\full.log` |
| 文件大小 | 25,156,943 bytes |
| 行数 | 161,522 |
| SHA-256 | `CBCAB31B1E924EC686C02F4E487FC6FF1B4273AA98ED4FDD978D8804530C44AC` |
| 模块 applicationId | `tv.withaibuild.customiuizer.r14` |
| 模块包名命中 | 252 行 |
| 版本 | r14.12.0 / versionCode 174 |
| APK SHA-256 | `7E488C4ED011F68321A8A2E5911B61D1C35659C98CA0116500855F79F05ED80E` |
| 签名证书 SHA-256 | `3061A3DA1C2FC46B44E215D024B1BFE3A012CB4D70B90B0214FA9FC896CEF60D` |
| 设备 | Xiaomi 13 / `fuxi` / arm64 |
| ROM | `V816.0.7.0.UMCTWXM` / Android 14 / SDK 34 |
| 实际 framework API | 101 |
| 模块元数据 | `minApiVersion=101`、`targetApiVersion=102`、`staticScope=false` |

## 错误扫描

| 扫描项 | 命中 | 结论 |
| --- | ---: | --- |
| `FATAL EXCEPTION` | 0 | 无 Java/Kotlin 致命异常 |
| `am_crash` | 0 | 无系统记录的应用崩溃 |
| `am_anr` / `ANR in` | 0 | 无 ANR |
| `VerifyError`、`NoClassDefFoundError`、`NoSuchMethodError` 等链接错误 | 0 | 未见 API/R8/ClassLoader 兼容错误 |
| `Failed to hook`、`Hook failed`、`Cannot hook` | 0 | 未见 Hook 安装失败 |
| 模块包名与错误严重度组合 | 0 | 未发现模块归属错误 |
| native `Fatal signal` | 2 | 均为 root `init` 的 SIGABRT，无模块包名、类或调用链 |

链接错误扫描同时覆盖：

- `NoSuchFieldError`
- `AbstractMethodError`
- `IllegalAccessError`
- `IncompatibleClassChangeError`
- `LinkageError`
- `UnsatisfiedLinkError`
- `ExceptionInInitializerError`

## 归因复核

### 模块设置进程

模块设置 Activity 正常完成 create/start/resume、页面导航和 stop 生命周期，未发现 Java 异常、进程死亡或 ANR。与该进程相邻的 SELinux 拒绝属于 MIUI 显示属性及 RenderThread sysfs 探测，没有模块异常栈，Activity 随后继续正常运行。

### SystemUI

日志中的一次 `Receiver ... already registered` 明确标记调用包为 `com.android.systemui`，没有模块包名或模块栈。SystemUI 进程继续运行，没有崩溃、ANR 或重启循环，不归因于本模块。

### Launcher

`MarketIconCustomizer` 类缺失来自 MIUI Launcher 自身反射探测，异常链不包含模块代码，Launcher 随后继续运行。日志中没有模块 Hook target 查找失败或重复初始化证据。

### 其他应用与系统

钉钉的 `IntentReceiverLeaked`、夸克/支付宝/设置的重复 Receiver、ROM system_server 服务类探测失败及其他模块的 Hook/DexKit 日志均有明确的其他包名或调用栈，与 CustoMIUIzer 无关。

### system_server 记录边界

先前审计把独立 Vector verbose/modules 流中未出现本模块的 system_server 加载行列为发布阻断。用户随后确认 `full.log` 才是本轮完整运行错误判定源；在该完整日志中没有 system_server 崩溃、ANR、模块调用栈、Hook 失败或 API 链接错误。

因此，“缺少逐项成功日志”只能表示日志不能证明每个 system_server 功能都被人工操作覆盖，不能反向推定为运行错误，也不能在没有异常证据时修改稳定源码。本项从疑似模块问题降为实机覆盖边界，不再阻止发布。

## API 101/102 结论边界

- 本次日志来自 API 101 实机环境，未发现 `targetApiVersion=102` APK 在 API 101 框架中的入口、链接或运行错误。
- API 102 编译、元数据和构建兼容已经过工程验证。
- 本份日志不是 API 102 实机运行证明；后续如在 API 102 设备出现可归因日志，再进入 `r14.12.x` 针对性修复。
- Hot Reload 仍未启用，也不属于 r14.12.0 验收范围。

## 发布决定

| 问题 | 结论 |
| --- | --- |
| 是否存在模块崩溃或 ANR | 否 |
| 是否存在模块入口或 Hook 安装错误 | 否 |
| 是否存在 API 101 运行兼容错误 | 否 |
| 是否存在需要源码修复的问题 | 否 |
| 是否存在阻止 r14.12.0 发布的问题 | 否 |
| 是否发布 | 是，发布用户已安装验证的同一 APK |

发布资产必须保持为手机已安装版本对应的精确文件：

`CustoMIUIzer-A14-r14.12.0.apk`

SHA-256：

`7E488C4ED011F68321A8A2E5911B61D1C35659C98CA0116500855F79F05ED80E`

本轮不重编译并替换该资产，不修改业务源码，不创建 r14.12.1。
