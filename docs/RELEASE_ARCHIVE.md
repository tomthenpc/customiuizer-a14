# 历史 Release 归档

本文归档不再保留于 GitHub Releases 页面中的公开版本。清理日期为 2026-07-26。

清理只删除 GitHub Release 条目及其二进制资产，不删除对应 Git tag、commit、源码历史
或许可证记录。以下 SHA-256 来自删除前 GitHub 资产摘要，并与原 Release 说明一致。

当前公开保留版本为：

- `r14.12.0`
- `r14.8.0`
- `r14.7.4`
- `r14.5.0`

## 归档资产索引

| 版本 | 发布日期 | 原资产 | 大小 | SHA-256 |
| --- | --- | --- | ---: | --- |
| `r14.6.4` | 2026-07-25 | `CustoMIUIzer-A14-r14.6.4.apk` | 2,949,361 bytes | `E7E6A23A04E709DF269DF1087FB3128435F532CF35BE53C1FE051595249B3280` |
| `r14.3.1` | 2026-07-24 | `CustoMIUIzer-A14-r14.3.1.apk` | 2,886,165 bytes | `E1ED1FEF9108E9A94D1B532F5B3BCDBD71AF5DC32E610A239CF108A9ABEC57D8` |
| `r14.2.9` | 2026-07-23 | `CustoMIUIzer-A14-r14.2.9.apk` | 2,886,165 bytes | `DABC71B2E5B5353F03DDF2BA513567888B6F87FC8F71994C3122DC3304CF6E10` |
| `r14.1.3` | 2026-07-22 | `CustoMIUIzer-A14-r14.1.3.apk` | 2,886,250 bytes | `17D1F71607E06E5BEB7939C17819932E558BD34C622F369EA87BEBFE7B0EBA57` |
| `r14.0.0` | 2026-07-20 | `Pengeek-HyperOS1-A14-API101-r14.0.0.apk` | 2,901,900 bytes | `9B6FAF9F4934273873F1973078912D37B7FD082FFAE1C434DCFE2B25DC52C8CB` |

## r14.6.4

状态：r14.6.x 最终稳定版，合并 r14.6.2、r14.6.3。

主要内容：

- 保留 r14.6.1 对早期 `Context` 数据目录崩溃的回退，继续使用已验证的系统设置存储路径。
- 完成 owner 级 PreferenceObserver、Receiver 和延迟 Runnable 生命周期治理。
- `SystemUI.java`、`System.java`、`GlobalActions.java` 拆分为低耦合 helper 文件，保留原调用入口。
- 修复双排移动信号资源 ID、SIM1 空图标和状态栏明暗着色问题。
- 补充反射缓存、偏好缓存和工具方法单元测试。
- 补充 Manifest 组件的必要 R8 `-keepnames`，没有扩大到无边界 keep。

验证记录：

- versionCode 164 / versionName `r14.6.4`。
- `gradlew test`、`assembleRelease`、`lintVitalRelease` 通过。
- 完整重启后未发现模块相关崩溃、ANR 或异常栈。
- 双排信号开关移动数据及明暗着色完成实机验证。

归档原因：其生命周期和双排信号修复已被后续 Kotlin 迁移版本包含；相对于保留的
`r14.5.0`、`r14.7.4` 和 `r14.8.0` 不再提供独立回退层级。

## r14.3.1

状态：r14.3.x 稳定版。

主要内容：

- 约束锁屏充电数据 Hook 的调用栈，只修改 `KeyguardIndicationController` 路径。
- 增加充电提示 Hook 防重复和文本去重。
- 为日期、大小写转换指定稳定 Locale。
- 将天气查询从每次 `TIME_TICK` 新建线程改为单一 ExecutorService，并复用主线程 Handler。
- 重建时先注销旧天气 Receiver，显式使用 `RECEIVER_NOT_EXPORTED`。
- 在未启用状态栏相关功能时跳过假资源和主题资源替换。

验证记录：

- versionCode 130 / versionName `r14.3.1`。
- `assembleRelease`、`lintRelease`、API 101、签名覆盖安装检查通过。
- 完整重启日志中未发现模块相关崩溃、ANR 或异常栈。

归档原因：属于独立包名迁移前的旧稳定点；`r14.5.0` 已成为当前包名和签名线的长期
回退基线。

## r14.2.9

状态：r14.2.x 系列合并稳定版。

主要内容：

- 合并 r14.2.1–r14.2.8 的热路径、生命周期、无效 Hook 和绘制缓存优化。
- StepCounter Receiver 在重复初始化时先注销再注册，Handler 绑定主线程 Looper。
- 修复列表遍历期间删除导致的并发修改风险。
- BatteryIndicator 缓存 density、状态栏高度和资源 ID，移除绘制路径临时 Matrix。
- Remote Preferences 变化按原值类型单键读取，减少复制完整偏好表。
- 全局动作相关 Hook 只在对应动作配置时注册。

验证记录：

- versionCode 127 / versionName `r14.2.9`。
- `gradlew test`、`assembleRelease`、`lintRelease` 通过。
- 完整重启验证通过，签名与当时版本线一致。

归档原因：其修复已进入后续版本，且不属于当前独立包名回退链。

## r14.1.3

状态：API 101 稳定性、轻量化与资源治理合并版。

主要内容：

- 修复 R8 改写回调方法名后 `after` Hook 被跳过的问题，改按返回类型和参数签名识别。
- 保持 SystemUI 兼容层与其他模块原生 `intercept(Chain)` 的已验证边界。
- 修复移动网络图标初始化阶段空状态读取和 Android 14 动态广播标志。
- 将四类应用列表的独立无界线程池合并为共享有界池。
- 图标缓存限制为 1–16 MiB，移除主动 `Runtime.gc()`。
- 音频静音 FFT 判断由每个频段一次收敛为每帧一次。
- 删除模块联网能力、上游赞助入口及相关 WebView 资源。

验证记录：

- versionCode 117 / versionName `r14.1.3`。
- APK 相对 r14.1.2 减少 48,378 bytes。
- `clean assembleRelease lintRelease lintVitalRelease`、R8、资源压缩、zipalign 和 v2
  签名通过。
- 两轮完整启动日志中模块异常为 0，SystemUI、Launcher 和设置进程未出现模块相关死亡。

归档原因：属于早期包名和 API 101 迁移阶段，当前回退价值已由 `r14.5.0` 及后续三个
Kotlin 阶段版本覆盖。

## r14.0.0

状态：Android 14 / libxposed API 101 独立版本线起点。

主要内容：

- 以 `MonwF/customiuizer@v24.10.12` 为 Android 14 功能参考建立独立维护线。
- Hook 接口更新到 libxposed API 101。
- 初始化范围限制为 Android 14。
- 使用当时的独立 applicationId `name.monwf.customiuizer.r14`。
- 完成首轮类、参数、Context、资源、主题值和常量 Hook 缓存。

归档原因：仅用于确认项目谱系和最初 API 101 迁移历史；包名、签名和工程结构均早于
当前独立维护基线，不适合作为普通用户回退版本。

## 获取历史源码

历史 Release 二进制不再公开下载。需要审计旧实现时，应从仓库 tag 检出源码：

```powershell
git fetch --tags
git switch --detach <tag>
```

历史 tag 只用于审计和行为对照，不应用来覆盖当前工作树或回退当前 Kotlin/API 101/102
实现。
