# DEVIN A14 CHECKPOINT

> 此文件记录当前开发线的已验证状态和下一步，不替代源码、Git、构建产物或实机证据。
> 每次继续工作前，必须重新核对本地分支、远端分支、工作区和 Release。

## 当前状态

- 仓库：`tomthenpc/customiuizer-a14`
- 正式发布版本：`r14.13.5` / versionCode `183`
- 正式发布日期：2026-07-28
- 源码仓库 tag：`r14.13.5` → 提交 `4225d80e`
- 源码仓库 Release：[r14.13.5](https://github.com/tomthenpc/customiuizer-a14/releases/tag/r14.13.5)
- LSPosed 模块仓库 tag：`183-r14.13.5`
- LSPosed 模块仓库 Release：[183-r14.13.5](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases/tag/183-r14.13.5)
- `r14.13.4` 已撤回；其 GitHub Release 与 tag 已删除，历史资产信息见
  [RELEASE_ARCHIVE.md](docs/RELEASE_ARCHIVE.md)。
- 当前 `main` 承载 `r14.13.5` 正式发布提交。

## 当前开发分支

- 分支：`devin/r14.13-kotlin-refactor`
- 最近基线：`321081d2`（r14.13.5 + Locale 状态修复 + 状态回归/内存审计）
- 与 `origin/main` 的关系：ahead 3（r14.13.5 发布后新增 Locale 状态修复与审计文档）
- 当前任务：`r14.13.5` 发布后停止功能与 Kotlin 迁移，优先完成 Locale 状态回归审计、状态/内存对照实验。

## 发布产物与签名

| 项目 | 值 |
| --- | --- |
| APK | `CustoMIUIzer-A14-r14.13.5.apk` |
| 大小 | 3,032,173 bytes |
| APK SHA-256 | `89AE5046564F69D491DC44F7B853443113FEC7100FE997ABA9984181C4983EA5` |
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
- `clean test lintDebug lintRelease lintVitalRelease assembleDebug assembleDevelop assembleRelease` 退出码 0。
- 新增 `AppLocaleControllerTest` 23 个测试通过；原有测试继续通过，总测试数 > 60。
- Lint / `lintRelease` / `lintVitalRelease` 为 0 errors（依赖弃用 warnings 不变）。
- Release R8、资源压缩、zipalign、APK v2 签名、APK 元数据与 Xposed metadata 均已检查。
- `AppLocaleController` 建立单一状态源：SharedPreferences 写路径使用 `commit()`；`ListPreference` 自动持久化关闭；`MainActivity` 移除手动 Context 包装；`auto` 语义保持。
- 新增 `tools/capture-memory-baseline.ps1`、`tools/compare-memory-baseline.py`、`docs/LOCALE_STATE_MACHINE.md`、`docs/STATE_REGRESSION_AUDIT_r14.13.md`、`docs/MEMORY_AUDIT_r14.13.md`。

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
