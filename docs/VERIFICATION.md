# 验证记录

本文集中记录当前稳定版本的构建、产物与实机证据。它只陈述已完成的检查，并明确区分
静态验证、实机验证和仍需验证的范围。

## 当前发布产物

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
