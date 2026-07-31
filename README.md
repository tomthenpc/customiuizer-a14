# CustoMIUIzer A14

简体中文 | [English](README_EN.md)

面向 **HyperOS 1 / Android 14** 的 CustoMIUIzer Kotlin 重构维护版。

本项目以 [MonwF/customiuizer v24.10.12](https://github.com/MonwF/customiuizer/releases/tag/v24.10.12)
作为 Android 14 功能语义参考，使用独立包名、版本线、签名与现代 libxposed API。
它不是上游官方版本，也不支持 Android 15、Android 16 或其他 MIUI / HyperOS 大版本。

## 当前版本

当前工作分支为 `release/r14.15.3`，是本地正式签名候选版本。该版本尚未完成公开 Release，待 Android 14 / HyperOS 实机及 LSPosed 日志验证。

| 项目 | 值 |
| --- | --- |
| 分支 | `release/r14.15.3` |
| APK | `CustoMIUIzer-A14-r14.15.3.apk` |
| versionCode / versionName | `191 / r14.15.3` |
| applicationId | `tv.withaibuild.customiuizer.r14` |
| 系统 | HyperOS 1 / Android 14（SDK 34） |
| ABI | `arm64-v8a` |
| libxposed | 最低 API 101 / 目标 API 102 |
| Hot Reload | 关闭 |
| SHA-256 | 构建后补全，见 `BUILD_INFO_R14_15_3.txt` |
| 签名证书 SHA-256 | 与 A14 既有正式发布线一致，见 `BUILD_INFO_R14_15_3.txt` |

上一个公开稳定版本为 `r14.13.8`，其历史资产与校验信息见 [CHANGELOG](CHANGELOG.md) 与 [RELEASE_ARCHIVE](docs/RELEASE_ARCHIVE.md)。

## r14.15.3 主要变更（候选）

- 整合 `hardening/a14-lts-foundation` 安全加固与 `integration/a14-r14.15.1` 运行时代码基线。
- 从 `devin/r14-netspeed-font-spacing-i18n` 纳入双行网速行距 `70%–130%`、前置提示本地化与 `feature-semantics` 元数据。
- 从 `fix/a14-ui-text-inheritance-and-about-wrap` 修复 About 页面署名/版本文字换行，保留 SeekBar 系统文本样式继承。
- 版本号：`versionCode 191 / versionName r14.15.3`。

**已知边界：** 系统 Toast 屏蔽相关逻辑未改动；网速显示、About 布局与完整人工冒烟测试尚待实机验证。

完整变更与历史记录见 [CHANGELOG](CHANGELOG.md)。

## 安装前必须知道

`r14.12.0` 及更早公开版本使用的旧签名私钥已经遗失，无法直接覆盖安装 `r14.15.3`。
如果正在使用这些旧版本：

1. 在旧版模块中备份设置；
2. 记录当前 LSPosed / Vector 作用域；
3. 卸载旧版；
4. 安装 `CustoMIUIzer-A14-r14.15.3.apk`；
5. 恢复作用域与设置；
6. 完整重启设备。

不要在备份前卸载旧版。候选版本的 APK 应从本地构建产物 `../release-output/A14/` 或仓库维护者提供的正式通道获取。

## 功能范围

- 状态栏、图标、电池、信号、网速、日期与温度；
- 控制中心、音量面板、亮度与通知行为；
- 锁屏、充电信息、媒体界面与快捷操作；
- Launcher、最近任务、文件夹、图标与桌面手势；
- 导航栏、按键、自定义动作、电源菜单与系统动画；
- 应用、权限、安装器、分享、隐私应用与应用锁行为。

具体功能是否可用取决于 ROM 和系统应用版本。厂商更新可能改变 Hook 目标。
不要与上游版或其他 CustoMIUIzer 派生模块同时启用。

## 安装

1. 下载并安装 APK；
2. 在 LSPosed / Vector 中启用模块并确认推荐作用域；
3. 打开一次模块设置；
4. 完整重启设备；
5. 检查 `system_server`、SystemUI、Launcher 和常用功能。

## 验证边界

`r14.15.3` 计划通过仓库静态门禁、单元测试、三档 lint，以及 Debug / Release 构建后，
才生成正式签名候选 APK。APK 需核对 R8、资源压缩、zipalign、v2 签名、包信息、SHA-256 与实际签名证书。

当前状态：`r14.15.3` 本地正式签名候选版本，静态检查和构建通过，待 Android 14 / HyperOS 实机及 LSPosed 日志验证。
详细证据与边界见 [验证记录](docs/VERIFICATION.md) 与 `BUILD_INFO_R14_15_3.txt`。

## 开发与构建

需要 JDK 17 和 Android SDK：

```powershell
python tools/check-invariants.py
.\gradlew.bat --no-daemon test lintVitalRelease assembleDebug assembleRelease
```

正式签名配置位于仓库之外。不得提交 keystore、密码、令牌、私人日志、缓存或本地构建状态。

工程文档：

- [项目谱系](docs/PROJECT_LINEAGE.md)
- [libxposed API 101 / 102 兼容边界](docs/LIBXPOSED_API_101_102_COMPATIBILITY.md)
- [LSPosed 服务 binder 投递与失效模式](docs/LSPOSED_BINDER_DELIVERY.md)
- [工程方法](docs/ENGINEERING_METHOD.md)
- [维护检查点](docs/MAINTENANCE_CHECKPOINT.md)

## 许可与致谢

本项目派生自 Mikanoshi/CustoMIUIzer，并参考
[MonwF/customiuizer](https://github.com/MonwF/customiuizer) 的 Android 14 工作。
感谢 LSPosed / libxposed、DexKit 及相关开源项目维护者。

项目依据 [GPL-3.0](LICENSE) 分发。来源与独立维护关系见 [NOTICE.md](NOTICE.md)。
