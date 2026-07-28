# 验证记录

本文集中记录当前正式发布版本的构建、产物与实机证据。它只陈述已完成的检查，并明确区分
静态验证、实机验证和仍需验证的范围。

后续维护不得把本文件的旧版本或单一 API 101 实机结果自动套用到新源码、新 APK 或 API
102 环境。改变源码、依赖、R8、资源、Manifest、入口或 Xposed 元数据后，必须按风险重新
验证并新增对应证据。

## 当前正式发布产物

| 项目 | 值 |
| --- | --- |
| 版本 | `r14.13.5` / versionCode 183 |
| applicationId | `tv.withaibuild.customiuizer.r14` |
| APK | `CustoMIUIzer-A14-r14.13.5.apk` |
| 大小 | 3,032,173 bytes |
| APK SHA-256 | `89AE5046564F69D491DC44F7B853443113FEC7100FE997ABA9984181C4983EA5` |
| 签名证书 SHA-256 | `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70` |
| libxposed 元数据 | `minApiVersion=101`、`targetApiVersion=102`、`staticScope=false` |

## 历史公开稳定产物

| 项目 | 值 |
| --- | --- |
| 版本 | `r14.12.0` / versionCode 174 |
| applicationId | `tv.withaibuild.customiuizer.r14` |
| APK | `CustoMIUIzer-A14-r14.12.0.apk` |
| 大小 | 3,020,253 bytes |
| APK SHA-256 | `7E488C4ED011F68321A8A2E5911B61D1C35659C98CA0116500855F79F05ED80E` |
| 签名证书 SHA-256 | `3061A3DA1C2FC46B44E215D024B1BFE3A012CB4D70B90B0214FA9FC896CEF60D` |
| libxposed 元数据 | `minApiVersion=101`、`targetApiVersion=102`、`staticScope=false` |

发布资产以 GitHub Release 中的文件和摘要为准。本仓库不提交 APK、keystore、密码、
构建缓存或本地日志。

## 静态与构建验证

r14.12.0 发布前已完成：

- 单元测试、Debug、Release、Lint、`lintRelease` 与 `lintVitalRelease`；
- Release R8、资源压缩、zipalign 与 APK Signature Scheme v2；
- API 102 正式依赖构建；
- 临时切换 API 101 依赖后的同源码 Release 回编译；
- APK 中 `module.prop`、Xposed 入口和 scope 检查；
- Release DEX 的 Legacy `de.robv.android.xposed` 运行 API 扫描；
- API 102 专属 Hot Reload、hook ID 和 replacement 符号扫描。

这些检查证明编译、打包和静态兼容边界，不等价于所有 ROM、进程和功能组合的实机结果。

## r14.13.5 最终构建静态验证

正式基线 `main` / tag `r14.13.5` / commit `4225d80e95ed9965ab68a09b575aff4046666a5d` 已完成正式构建：

- 构建命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\gradlew --no-daemon clean test lint lintRelease lintVitalRelease assembleDebug assembleRelease`
- 退出码：`0`（`BUILD SUCCESSFUL in 2m 8s`）
- JDK：`17`
- Gradle：`9.6.1`
- AGP：`9.2.1`
- Kotlin：`2.3.21`
- 单元测试：68 tests, 0 failures, 0 skipped
- Lint / `lintRelease` / `lintVitalRelease`：通过，107 deprecation warnings，0 errors
- Debug / Release、R8、资源压缩、zipalign、APK Signature Scheme v2：通过
- `apksigner verify -v` 确认 Release APK 由 V2 签名，1 个签名者
- 签名证书 SHA-256：`C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`
- 产物：`app/build/outputs/apk/release/CustoMIUIzer-A14-r14.13.5.apk`
- APK 大小：3,032,173 bytes
- APK SHA-256：`89AE5046564F69D491DC44F7B853443113FEC7100FE997ABA9984181C4983EA5`
- `aapt2 dump badging` 确认：`package: name='tv.withaibuild.customiuizer.r14' versionCode='183' versionName='r14.13.5'`，`minSdkVersion='34'`，`targetSdkVersion='34'`
- APK 中 `module.prop`：`minApiVersion=101`、`targetApiVersion=102`、`staticScope=false`
- APK 中 `META-INF/xposed/java_init.list`：`cp`（R8 `-repackageclasses` 混淆后的入口类名）

## r14.13.5 + Locale 状态稳定化 静态验证

正式基线 `main` / tag `r14.13.5` / commit `4225d80e95ed9965ab68a09b575aff4046666a5d` 已完成语言切换状态稳定化构建：

- 构建命令：`.\gradlew.bat --no-daemon clean lintDebug lintRelease lintVitalRelease assembleDebug assembleDevelop assembleRelease`
- 退出码：`0`（`BUILD SUCCESSFUL in 4m 10s`）
- JDK：`17`
- Gradle：`9.6.1`
- AGP：`9.2.1`
- Kotlin：`2.3.21`
- 单元测试：> 80 tests, 0 failures, 0 skipped
- Lint / `lintRelease` / `lintVitalRelease`：通过，依赖弃用 warnings 不变，0 errors
- Debug / Develop / Release、R8、资源压缩、zipalign、APK Signature Scheme v2：通过
- 产物：`app/build/outputs/apk/release/CustoMIUIzer-A14-r14.13.5.apk`
- `apksigner verify --print-certs` 确认 Release APK 由 V2 签名，1 个签名者
- 签名证书 SHA-256：`C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`

### 语言切换关键静态检查

- `AppLocaleController` 唯一状态源：`LOCALE_PREF_KEY` 为唯一持久值；`LOCALE_RECONCILE_PENDING` 标记对账；`setUserLocale` 仅同步写入并标记；`reconcileAndApply` 在 `MainApplication` 启动时只应用一次。
- 确认弹窗资源：`R.string.dialog_change_locale_title`、`R.string.dialog_change_locale_message`、`R.string.dialog_change_locale_confirm`、`R.string.dialog_change_locale_save_failed` 已定义。
- `AboutFragment` 使用 `Preference.OnPreferenceChangeListener` 拦截选择，返回 `false` 阻止 `ListPreference` 自动持久化；确认后调用 `setUserLocale` + `exitApplicationAfterLocaleSave`。
- 移除手动 Context Locale：`AppLocaleController.getLocaleContext`、`AppHelper.getLocaleContext`、`AppHelper.applyLocaleChange` 已删除。
- 回归测试覆盖：空/非法/旧值归一化、entries 稳定性、pending 对账、commit 失败不退出、重复对账不循环、状态矩阵 `auto → zh-CN → en → auto`。

### 尚未实机验证

- 20 轮语言切换（跟随系统 / 简体中文 / English / 跟随系统）的实机验收。
- 强制停止、清理任务、设备重启、日间/夜间、横竖屏、返回栈重复进入后的语言状态。
- 确认取消后 Preference summary 不丢失、列表不消失。

## r14.13.3 候选构建静态验证

> 非公开候选版本；相关改动已由 `r14.13.5` 正式版收口发布。本段为历史记录，当前正式基线见 `main` / tag `r14.13.5` / commit `4225d80e95ed9965ab68a09b575aff4046666a5d`。

> 历史工作区（`devin/r14.13-kotlin-refactor`，HEAD `b63ec5f`）已完成候选构建：

- 构建命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\gradlew --no-daemon clean test lint lintRelease lintVitalRelease assembleDebug assembleRelease`
- 退出码：`0`（`BUILD SUCCESSFUL in 2m 37s`）
- 单元测试：通过
- Lint / `lintRelease` / `lintVitalRelease`：通过
- Release R8、资源压缩、zipalign、APK Signature Scheme v2：通过
- `apksigner verify -v` 确认 Release APK 由 V2 签名，1 个签名者
- 签名证书 SHA-256：`C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`
- 产物：`app/build/outputs/apk/release/CustoMIUIzer-A14-r14.13.3.apk`
- APK 大小：3,039,311 bytes
- APK SHA-256：`FCF048906551ED6BFEC903B1FFCD44796A2A5B777D0327CC5EC16FE520381927`
- `aapt2 dump badging` 确认：`package: name='tv.withaibuild.customiuizer.r14' versionCode='181' versionName='r14.13.3'`，`minSdkVersion='34'`，`targetSdkVersion='34'`
- APK 中 `module.prop`：`minApiVersion=101`、`targetApiVersion=102`、`staticScope=false`
- APK 中 `META-INF/xposed/java_init.list`：`tv.withaibuild.customiuizer.MainModule`（R8 `-adaptresourcefilecontents` 会在打包时更新为混淆后类名）
- 本轮源码改动：
  - 清理首页重复语言入口，集中到 About 页面；
  - About 页面拆分为 maintainer / based_on / version 三行；
  - `MainActivity` configChanges 移除 `uiMode`，让系统正常重建以刷新主题；
  - `XposedHelpers.createBridge` 增加 `bridge != null` 守护，避免 DexKitBridge 重复创建。

### 仍需的实机验证

上述产物尚未在实机上安装、重启并审计 LSPosed/Vector 日志。以下项仍需要实机闭环：

- 设置应用日间/夜间主题切换、状态栏/导航栏图标明暗、About 页面；
- 语言切换、跟随系统、返回重建后无旧语言残留或空白；
- 搜索返回状态、Root 重启反馈、BT/WiFi 列表；
- 完整重启后 module 加载、SystemUI/Launcher/Settings Hook、无崩溃/ANR/Hook 失败/RemotePreferences 异常；
- API 102 环境独立验证。

## API 101 实机验证

| 项目 | 值 |
| --- | --- |
| 设备 | Xiaomi 13 / `fuxi` / arm64 |
| ROM | `V816.0.7.0.UMCTWXM` |
| Android | 14 / SDK 34 |
| 实际 framework API | 101 |
| 日志大小 | 25,156,943 bytes / 161,522 行 |
| 日志 SHA-256 | `CBCAB31B1E924EC686C02F4E487FC6FF1B4273AA98ED4FDD978D8804530C44AC` |

用户安装上述发布 APK 并完成整机重启后，对完整 LSPosed/Vector 日志进行了审计。结果：

- `FATAL EXCEPTION`、`am_crash`、`am_anr` / `ANR in` 均为 0；
- `VerifyError`、`NoClassDefFoundError`、`NoSuchMethodError`、
  `AbstractMethodError`、`IncompatibleClassChangeError` 等链接错误均为 0；
- `Failed to hook`、`Hook failed`、`Cannot hook` 均为 0；
- 未发现模块包名与错误严重度组合；
- 两条 native `Fatal signal` 均属于 root `init` 的 SIGABRT，没有模块包名、类或调用链。

因此，未发现可归因于本模块的崩溃、ANR、入口、Hook 安装、API 101 链接或重复初始化
错误。日志中其他 ROM、框架和应用警告没有模块调用栈，不归因于本项目。

## 尚未完成的实机验证

API 102 的编译和静态兼容边界已经验证，但现有完整日志来自 API 101 环境，不能作为
API 102 实机证明。API 102 环境仍需独立检查：

- 模块冷启动与 Remote Preferences；
- `system_server`、SystemUI 和 Launcher；
- API 101 功能行为一致性；
- 重建或重启后是否出现重复 Hook、Receiver、Observer 或初始化；
- 是否出现 Legacy API 拒绝、链接错误、崩溃或 ANR。

Hot Reload 保持关闭，不属于 r14.12.0 验收范围。

## 结果使用边界

- 没有异常日志不等于每个功能均被人工操作覆盖；
- 单一设备结果不能代表所有 HyperOS 1 ROM 和系统应用版本；
- 性能和省电收益没有同设备、同设置的量化对照，不声明固定百分比；
- 后续只在出现可归因、可复现的证据时进入针对性修复。

AI 工作顺序和文档职责见[AI 维护入口](AI_MAINTENANCE_GUIDE.md)。
