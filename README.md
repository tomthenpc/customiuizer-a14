# 米客 A14 Kotlin 重构

简体中文 | [English](README_EN.md)

面向 **HyperOS 1 / Android 14** 的 CustoMIUIzer Kotlin 重构维护版。

本项目以 MonwF/customiuizer v24.10.12 作为 Android 14 功能语义参考，使用独立包名、版本线、签名和现代 libxposed API。项目不是上游官方版本，也不支持 Android 15、Android 16 或其他 MIUI / HyperOS 大版本。

## 当前版本

| 项目           | 值                                          |
| ------------ | ------------------------------------------ |
| 版本           | `r14.15.3`                                 |
| versionCode  | `191`                                      |
| 系统           | HyperOS 1 / Android 14（API 34）             |
| ABI          | `arm64-v8a`                                |
| 应用 ID        | `tv.withaibuild.customiuizer.r14`          |
| libxposed    | `minApiVersion=101`、`targetApiVersion=102` |
| staticScope  | `false`                                    |
| APK          | `CustoMIUIzer-A14-r14.15.3.apk`            |
| APK SHA-256  | `F7AB34722B0193DD8C97DF0146C968E5A6064655AD497061E902CD1545375E7E` |
| 签名证书 SHA-256 | `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70` |

上一个公开版本为 `r14.13.8`。

面向 LSPosed 用户的下载页面位于：

`Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14`

> Releases 页面仅保留当前正式版。旧版本的变更记录已合并到当前 Release 和 CHANGELOG；旧版 APK 不再提供下载，历史源码 tag 继续保留。

## r14.15.3 更新重点

* 恢复此前误删的 `system` 作用域，修复 `system_server` Hook 未加载、相关系统级功能静默失效的问题；
* 加固 Global Actions 在 `system_server` 中的 BroadcastReceiver 异常边界、信任检查和有序广播结果；
* 完善 Receiver / Observer 的 owner 绑定、替换、清理和并发注册处理，避免重复注册及已注册但未跟踪的 Receiver；
* 改进 Hook 加载诊断、兼容信息和运行时错误记录；
* 状态栏网速粗体保留 SystemUI 当前字体家族，并增加无有效粗体字形时的兜底；
* 新增双排网速行距 `70%–130%` 调整及相关本地化提示；
* 修复设置控件文本样式继承和 About 页面署名、版本文字换行；
* 合并 `r14.13.8` 之后的运行时安全、作用域、网速显示和 UI 修复。

完整变化见 [CHANGELOG.md](CHANGELOG.md)。

## 兼容范围

| 项目            | 值                                                 |
| ------------- | ------------------------------------------------- |
| 系统            | HyperOS 1 / Android 14                            |
| Android SDK   | 34                                                |
| ABI           | `arm64-v8a`                                       |
| 框架            | 实现 libxposed API 101 或 API 102 的 LSPosed / Vector |
| Android 15/16 | 不支持                                               |

具体功能是否可用取决于设备 ROM 和系统应用版本。厂商更新可能改变 Hook 类、方法或字段。

不要与上游版或其他 CustoMIUIzer 派生模块同时启用。

## 主要功能

* 状态栏图标、电池、信号、网速、日期和温度；
* 控制中心、音量面板、亮度和通知行为；
* 锁屏、充电信息、媒体界面和快捷操作；
* Launcher、最近任务、文件夹、图标和桌面手势；
* 导航栏、按键、自定义动作、电源菜单和系统动画；
* 应用、权限、安装、分享、隐私应用和应用锁行为。

## 重要升级说明

`r14.13.5` 及之后的新签名版本可直接覆盖安装。

`r14.12.0` 及更早公开版本使用的旧签名私钥已经遗失，不能直接覆盖安装。升级前必须：

1. 备份模块设置；
2. 记录 LSPosed / Vector 作用域；
3. 卸载旧版；
4. 安装新版本；
5. 重新启用作用域；
6. 恢复设置；
7. 完整重启设备。

不要在完成备份前卸载旧版本。

## 安装

1. 从 LSPosed 发布仓库下载正式 APK；
2. 核对 APK SHA-256；
3. 安装 APK；
4. 在 LSPosed / Vector 中启用模块；
5. 确认推荐作用域包含 `system`；
6. 打开一次模块设置并完整重启设备。

## 构建

需要 JDK 17 和对应 Android SDK。

```bash
./gradlew :app:assembleRelease -PofficialRelease=true
```

正式签名配置位于仓库之外。不得提交 keystore、密码、令牌、真实 `keystore.properties`、APK、签名备份、私人日志、缓存与构建目录。

## 验证说明

`r14.15.3` 已完成正式 Release APK 构建及以下基础检查：

* APK v2 签名；
* zipalign；
* applicationId、versionCode、versionName；
* libxposed module.prop、scope.list 和 java_init.list；
* `system` 与 `android` 作用域；
* APK SHA-256 与正式签名证书。

本次发布未执行完整单元测试、Lint、工程 Audit、ADB regression 或全功能实机回归。APK 构建和元数据检查不能证明所有 Hook 在全部 HyperOS 1 ROM 上均可用。

## 反馈

提交问题时请提供：

* 模块版本和 APK 来源；
* 设备、ROM 与系统应用版本；
* 框架名称和实际 libxposed API；
* 实际启用作用域；
* 完整重启后的 `system_server`、SystemUI 或 Launcher 日志；
* 可重复的功能开关和操作步骤。

## 许可证与致谢

项目派生自 Mikanoshi/CustoMIUIzer，并参考 MonwF/customiuizer 的 Android 14 工作，依据 GPL-3.0 分发。
