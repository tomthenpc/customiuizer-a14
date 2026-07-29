# CustoMIUIzer A14

简体中文 | [English](README_EN.md)

面向 **HyperOS 1 / Android 14** 的 CustoMIUIzer 独立维护版。

本项目以 [MonwF/customiuizer v24.10.12](https://github.com/MonwF/customiuizer/releases/tag/v24.10.12)
作为 Android 14 功能语义参考，使用独立包名、版本线、签名与现代 libxposed API。
它不是上游官方版本，也不支持 Android 15、Android 16 或其他 MIUI / HyperOS 大版本。

## 当前版本

**r14.13.7 是当前唯一公开版本。**

- [源码仓库 Release](https://github.com/tomthenpc/customiuizer-a14/releases/tag/r14.13.7)
- [LSPosed 模块仓库 Release](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases/tag/185-r14.13.7)

两个入口提供同一份 APK：

| 项目 | 值 |
| --- | --- |
| APK | `CustoMIUIzer-A14-r14.13.7.apk` |
| versionCode / versionName | `185 / r14.13.7` |
| applicationId | `tv.withaibuild.customiuizer.r14` |
| 系统 | HyperOS 1 / Android 14（SDK 34） |
| ABI | `arm64-v8a` |
| libxposed | 最低 API 101 / 目标 API 102 |
| Hot Reload | 关闭 |
| SHA-256 | `11D01A737BED25C3C4D31153DE22CB918A651D0DD043D0374E2C0E41D32492CC` |
| 签名证书 SHA-256 | `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70` |

请只从上面两个官方入口下载。

## r14.13.7 解决了什么

- LSPosed 服务暂时不可用时，设置改动不再被静默丢弃；重新连接后会进行一次完整对账。
- 快速重启改为直接向 SystemUI 发送有序广播，不再依赖设置应用自身的 binder 状态。
- 损坏或类型变化的列表偏好会回退到默认值，不再把异常带入 SystemUI 或 `system_server`。
- 状态栏电池与温度的格式、单位变更可以即时生效；必须重启才能生效的选项已在界面标明。
- 锁屏专辑封面处理改为代次校验、字节限额缓存和正确的缓存键，避免并发生成与无效缓存。
- 图标加载队列饱和时会正确释放在途状态，不再让图标永久空白。

完整变更与历史记录见 [CHANGELOG](CHANGELOG.md)。

## 安装前必须知道

`r14.12.0` 及更早公开版本使用的旧签名私钥已经遗失，无法直接覆盖安装 r14.13.7。
如果正在使用这些旧版本：

1. 在旧版模块中备份设置；
2. 记录当前 LSPosed / Vector 作用域；
3. 卸载旧版；
4. 安装 `CustoMIUIzer-A14-r14.13.7.apk`；
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

r14.13.7 已通过仓库静态门禁、171 项单元测试、三档 lint，以及 Debug / Release 构建。
APK 已核对 R8、资源压缩、zipalign、v2 签名、包信息、SHA-256 与实际签名证书。

**r14.13.7 尚未完成本轮完整实机验收，因此这里只称为当前公开版本，不称为已实机验证的稳定版。**
涉及 SystemUI、Launcher、`system_server`、ROM 反射目标或 API 102 框架的行为，仍以对应设备上的
加载日志和实际功能为准。详细证据与未验证边界见 [验证记录](docs/VERIFICATION.md)。

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
