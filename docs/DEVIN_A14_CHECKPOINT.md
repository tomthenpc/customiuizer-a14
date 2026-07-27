# DEVIN A14 CHECKPOINT

> 此文件记录当前开发线的已验证状态和下一步，不替代源码、Git、构建产物或实机证据。
> 每次继续工作前，必须重新核对本地分支、远端分支、工作区和 Release。

## 当前状态

- 仓库：`tomthenpc/customiuizer-a14`
- 正式发布版本：`r14.13.4` / versionCode `182`
- 正式发布日期：2026-07-28
- 源码仓库 tag：`r14.13.4` → `73ea3415`
- 源码仓库 Release：[r14.13.4](https://github.com/tomthenpc/customiuizer-a14/releases/tag/r14.13.4)
- LSPosed 模块仓库 tag：`182-r14.13.4` → `67e7d36`
- LSPosed 模块仓库 Release：[r14.13.4](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases/tag/182-r14.13.4)
- 两个 Release 均上传同一份正式 APK；当前 `main` 已包含正式发布提交。

## 当前开发分支

- 分支：`devin/r14.13-kotlin-refactor`
- 最近基线：`c075c26c`（记录最终发布状态）
- 本次文档整理开始时与 `origin/main` 的关系：ahead 1 / behind 0；merge base `73ea3415`
- 当前任务：统一公开文档格式和版本表述；不得将仅含维护文档的开发分支合回 `main`。

## 发布产物与签名

| 项目 | 值 |
| --- | --- |
| APK | `CustoMIUIzer-A14-r14.13.4.apk` |
| 大小 | 3,032,173 bytes |
| APK SHA-256 | `E8A2BD362C0540972441B8D1DE0BCACE8FE85FEF71F31406F3B4DA1A4027D26C` |
| 签名证书 SHA-256 | `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70` |
| 签名 | APK Signature Scheme v2，1 个签名者 |
| applicationId | `tv.withaibuild.customiuizer.r14` |
| ABI | `arm64-v8a` |
| SDK | min/target `34 / 34` |
| libxposed | min API `101` / target API `102` / `staticScope=false` |

`r14.12.0` 及更早公开版本的旧签名私钥已经遗失。它们不能直接覆盖安装
`r14.13.4`；升级时必须备份设置、卸载旧版、安装新版、重新启用作用域、恢复设置并完整重启。

## 已验证

- 正式构建：JDK 17、Gradle 9.6.1、AGP 9.2.1、Kotlin 2.3.21。
- `clean test lint lintRelease lintVitalRelease assembleDebug assembleDevelop assembleRelease` 退出码 0。
- 45 个单元测试通过；Lint / `lintRelease` / `lintVitalRelease` 为 0 errors（107 个依赖弃用 warnings）。
- Release R8、资源压缩、zipalign、APK v2 签名、APK 元数据与 Xposed metadata 均已检查。
- API 101 完整重启日志审计未发现可归因于模块的崩溃、ANR、入口、Hook 安装或链接错误。

完整命令、产物边界和日志结论见[验证记录](VERIFICATION.md)。构建或 API 101 日志不构成
API 102 的实机证明。

## 仍需实机验证

- API 102 独立框架环境：冷启动、RemotePreferences、`system_server`、SystemUI、Launcher。
- 设置应用：日间/夜间主题、系统栏图标、语言切换/跟随系统、搜索返回和 Fragment 重建。
- Root 重启：有/无 Root、目标未运行、多 PID、失败输出和退出页面后的 UI 安全。
- SystemUI/Launcher：状态栏文本图标在主题、密度、折叠和重启后的显示与更新；资源替换、BT/WiFi 列表及 `包名|活动` 解析。

## 维护规则

- 非 API 迁移任务不得改变 API 101/102、Hot Reload 关闭或 Legacy Xposed API 禁止边界。
- 只有取得新的代码、构建或实机证据后，才更新版本结论和验证状态。
- 不提交 keystore、密码、APK、私人日志、缓存或机器专属数据。
- 当前公开 Release 已完成；未获用户单独要求时，不创建新 tag、Release、PR 或修改 `main`。
