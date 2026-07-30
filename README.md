# CustoMIUIzer A14

简体中文 | [English](README_EN.md)

面向 **HyperOS 1 / Android 14** 的 CustoMIUIzer 独立维护版。

本项目以 [MonwF/customiuizer v24.10.12](https://github.com/MonwF/customiuizer/releases/tag/v24.10.12)
作为 Android 14 功能语义参考，使用独立包名、版本线、签名与现代 libxposed API。
它不是上游官方版本，也不支持 Android 15、Android 16 或其他 MIUI / HyperOS 大版本。

## 当前版本

**r14.13.8 是当前唯一公开版本。**

- [源码仓库 Release](https://github.com/tomthenpc/customiuizer-a14/releases/tag/r14.13.8)

当前正式 APK：

| 项目 | 值 |
| --- | --- |
| APK | `CustoMIUIzer-A14-r14.13.8.apk` |
| versionCode / versionName | `186 / r14.13.8` |
| applicationId | `tv.withaibuild.customiuizer.r14` |
| 系统 | HyperOS 1 / Android 14（SDK 34） |
| ABI | `arm64-v8a` |
| libxposed | 最低 API 101 / 目标 API 102 |
| Hot Reload | 关闭 |
| SHA-256 | `B0E7D4A3CB50E39748531D5B0FD3CB95F81C1F777DDAC9E346B8C8D67B8CBE62` |
| 签名证书 SHA-256 | `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70` |

请只从上述官方入口下载。LSPosed 模块仓库镜像如未同步，以源码仓库 Release 为准。

## r14.13.8 解决了什么

- 优化 Hook 进程与设置应用工具代码的边界，减少无关类加载。
- 清理 GlobalActions 遗留的 6 个转发桩，调用点直接使用实际实现。
- 修复未配置任何自定义动作时，应用内“重启系统”无法执行的问题。
- 区分快速重启广播无人接收与接收端执行失败，不再把后者误报为“未连接 LSPosed 服务”。
- 已在 Android 14 / HyperOS 1 与 LSPosed 2.1.1（7790）上完成实机验收。

已知问题：系统 Toast 屏蔽仍可能无效，本版本未改动相关逻辑。

完整变更与历史记录见 [CHANGELOG](CHANGELOG.md)。

## 安装前必须知道

`r14.12.0` 及更早公开版本使用的旧签名私钥已经遗失，无法直接覆盖安装 r14.13.8。
如果正在使用这些旧版本：

1. 在旧版模块中备份设置；
2. 记录当前 LSPosed / Vector 作用域；
3. 卸载旧版；
4. 安装 `CustoMIUIzer-A14-r14.13.8.apk`；
5. 恢复作用域与设置；
6. 完整重启设备。

不要在备份前卸载旧版。其他来源的同名 APK 可能使用不同签名，也可能无法覆盖安装。

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

r14.13.8 已通过仓库静态门禁、单元测试、三档 lint，以及 Debug / Release 构建。
APK 已核对 R8、资源压缩、zipalign、v2 签名、包信息、SHA-256 与实际签名证书。

快速重启修复已在 Android 14 / HyperOS 1、LSPosed 2.1.1（7790）上完成实机验收：
模块在 SystemUI 与 Launcher 正常加载，两次快速重启后系统完成启动，未发现 P0/P1、
目标进程崩溃、Hook 异常或 Receiver 重复注册。其他 ROM 与系统应用版本仍需分别验证。
详细证据与边界见 [验证记录](docs/VERIFICATION.md)。

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
