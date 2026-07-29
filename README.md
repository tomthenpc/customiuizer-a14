# CustoMIUIzer A14

简体中文 | [English](README_EN.md)

面向 HyperOS 1 / Android 14 的 CustoMIUIzer 独立维护版。

本项目以
[MonwF/customiuizer v24.10.12](https://github.com/MonwF/customiuizer/releases/tag/v24.10.12)
作为 Android 14 功能语义参考，但使用独立包名、版本线、签名、现代 libxposed API 和
发布流程，不是上游官方版本。

## 当前版本

| 项目 | 状态 |
| --- | --- |
| 稳定版本 | `r14.13.6` |
| 支持系统 | HyperOS 1 / Android 14（SDK 34） |
| ABI | `arm64-v8a` |
| applicationId | `tv.withaibuild.customiuizer.r14` |
| libxposed API | min 101 / target 102 |
| Hot Reload | 关闭 |
| 构建 | Kotlin DSL / version catalog / R8 |
| 下载 | [源码 Release](https://github.com/tomthenpc/customiuizer-a14/releases/tag/r14.13.6) · [LSPosed 模块页](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases/tag/184-r14.13.6) |

## r14.13.6 亮点

- **界面语言切换终于生效**：`AppCompatDelegate.setApplicationLocales()` 在应用启动阶段是
  静默空操作（API 33+ 需要一个存活的 Activity 才能解析 `LocaleManager`），语言选择被保存后
  从未被应用。改为直接调用框架的 `LocaleManager`。
- **关于页语言项不再让设置界面报错**：绑定期间不再写入 Preference 值，也不会用 XML 占位值
  覆盖掉已保存的语言。
- **不再误报「模块未被激活」**：区分「等待超时」与「确认未连接」，超时后再等一轮才下结论。
- **搜索跳转后开关状态立即刷新**：搜索高亮恢复为一次性，且不再永久替换行背景。
- **系统进程健壮性**：模块从 hook 注册出去的回调全部加了异常隔离，其中两处运行在
  `system_server` 内；协程 scope 统一挂上失败处理器。
- **注册与内存**：receiver / 观察者的清理改为绑定所有者，修掉数个从未生效的清理路径；
  实例级附加字段改为按身份存储。
- **性能**：hook 参数不再逐次复制与重新编排；反射缓存命中零分配；主界面搜索改为单次
  零分配扫描。
- **结构**：三个超大 hook 文件按功能域拆为 18 个文件；搬移经逐字节比对，
  hook 注册顺序与 R8 保留方法集不变。

## r14.13.5 亮点

- **修复首页搜索导航回归**：`Various` 搜索结果与子分类项点击后不再立即返回首页，目标
  Preference 正确高亮并滚动。
- **恢复搜索状态机**：`0/1/2` 三态控制，返回首页后自动收起 SearchView、清空 query、
  隐藏结果列表，原搜索结果不再重新展开。
- **统一 `sub` 空/空白语义**：`ModData.sub` 改为可空，`SubFragment` 不再把空字符串误判为
  有效子分类。
- **修正 `openModCat()` 返回值**：System / Launcher / Controls / Various 成功导航后统一
  返回 `true`，未知分类返回 `false`。
- **新增单元测试**：`SearchRouteResolver` 与 `SearchStateMachine` 覆盖路由解析与状态转换。
- **API 101/102 单 APK 兼容**：保持 Android 14 / HyperOS 1、R8、资源压缩、zipalign 和
  APK Signature Scheme v2。

## 关于本次发布的验证状态

`r14.13.6` 通过了 `check-invariants`（113 文件 / 8 规则 / 0 违规）、122 项单元测试、
lint / lintRelease / lintVitalRelease 0 errors 以及 Debug/Release 构建，
但**尚未完成实机验收**。签名证书与 `r14.13.5` 相同，可直接覆盖安装，
如遇问题可回退到 `r14.13.5`（源码 tag 仍在）。

## 下载与校验

- APK：`CustoMIUIzer-A14-r14.13.6.apk`
- 大小：3,082,129 bytes
- SHA-256：`35AEE1FEA1D7B38D967267210B7C272340B56B580ED49BEF4945AA9FC6F2ED96`
- 签名证书 SHA-256：`C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`

仅从本项目 Release 或对应的 LSPosed 模块仓库下载。不同签名可能无法覆盖安装，处理旧
安装前请先备份设置。

## 重要：旧版本需要备份后重装

`r14.12.0` 及更早公开版本使用的旧签名私钥已经遗失。`r14.13.6` 使用新的正式签名，
无法直接覆盖安装那些旧版本，但可直接覆盖安装 `r14.13.5`。

升级步骤：

1. 在旧版本模块中备份设置；
2. 记录当前 LSPosed/Vector 作用域；
3. 卸载旧版本；
4. 安装 `CustoMIUIzer-A14-r14.13.6.apk`；
5. 重新启用模块和原有作用域；
6. 恢复设置；
7. 完整重启设备。

不要在未备份设置的情况下直接卸载旧版本。

## 功能范围

- 状态栏、图标、电池、信号、网速、日期和温度；
- 控制中心、音量面板、亮度与通知行为；
- 锁屏、充电信息、媒体界面和快捷操作；
- Launcher、最近任务、文件夹、图标和桌面手势；
- 导航栏、按键、自定义动作、电源菜单和系统动画；
- 应用、权限、安装、分享、隐私应用和应用锁行为。

具体功能是否可用仍取决于 ROM 与系统应用版本。厂商更新可能改变 Hook 目标。

## 理论性能与省电评估

模块运行在 SystemUI、Launcher 和 `system_server` 等长驻进程中，额外成本主要取决于：

> 触发频率 × 单次成本 × 进程数量 × 存活时间

下表是相对于上游或早期 r14 实现的**代码路径理论评估**，不是实验室功耗跑分：

| 场景 | 早期实现风险 | 当前处理 | 理论影响 |
| --- | --- | --- | --- |
| 功能关闭 | Hook 或监听仍可能进入回调 | 按开关和进程决定是否注册 | 降低无效 Hook 分发与常驻开销 |
| SystemUI 重建 | Receiver/Observer/任务可能重复 | 注册幂等，资源随 owner detach | 降低重复回调、泄漏和后台工作 |
| 绘制与动画 | 重复资源查找、格式化、临时对象 | 冷路径缓存，热路径复用状态 | 降低 CPU、分配和 GC 压力 |
| 音频与周期事件 | 任务所有权不清或重复调度 | 生命周期取消，优先响应系统事件 | 降低空唤醒与残留任务概率 |
| 设置列表 | 重复遍历、过滤和去重 | 使用稳定缓存和常数时间去重 | 降低页面加载时的主线程工作 |
| 兼容失败 | 重复反射探测或日志刷屏 | 冷路径探测，单项安全停用 | 降低异常重试和重启风暴风险 |

理论上，收益最明显的场景是：关闭大量功能、SystemUI 多次重建、长时间待机，以及频繁
触发状态栏/控制中心绘制。省电潜力主要来自减少无效回调、线程调度、轮询、重复注册和
异常重试，而不是代码行数变少。

项目没有宣称固定的续航、CPU 或内存提升百分比。实际收益会受启用功能、ROM、框架、
使用习惯和系统应用版本影响，需要在同设备、同设置下用 Perfetto、Batterystats 和
内存工具对照测量。

## 兼容范围

- 仅支持 HyperOS 1 / Android 14（SDK 34）和 `arm64-v8a`。
- 框架必须实现现代 libxposed API 101 或 API 102。
- 不支持 Android 15、Android 16，也不保证其他 MIUI/HyperOS 版本。
- 不要与上游版或其他 CustoMIUIzer 派生模块同时启用。
- `targetApiVersion=102` 在 API 101 管理器中可能显示“面向较新 API”的提示；应以实际
  加载日志和功能行为判断，不把提示本身当作错误。
- API 102 Hot Reload、hook ID 和原子 replacement 当前未启用。

完整边界见
[libxposed API 101/102 双兼容说明](docs/LIBXPOSED_API_101_102_COMPATIBILITY.md)。

## 安装

1. 下载并安装 APK。
2. 在 LSPosed/Vector 中启用模块并确认推荐作用域。
3. 打开模块设置一次。
4. 完整重启设备。
5. 检查 `system_server`、SystemUI、Launcher 和常用功能。

旧包名版本不会自动迁移到当前独立包名。卸载旧模块前请先备份配置。

## 验证状态

- 单元测试、Debug/Release、Lint、`lintRelease`、`lintVitalRelease` 通过；
- R8、资源压缩、zipalign、v2 签名与 APK 元数据通过；
- API 101 依赖回编译与 API 102 正式构建通过；
- API 101 实机完成安装、整机重启和完整 `full.log` 审计；
- 未发现可归因于模块的崩溃、ANR、入口、Hook 或 API 链接错误；
- API 102 实机运行仍需在对应框架环境独立验证。

构建成功只证明静态和产物边界，不替代不同 ROM、框架与功能组合的实机测试。
完整证据、APK 摘要和未验证边界见[验证记录](docs/VERIFICATION.md)。

## 开发与构建

需要 JDK 17 和 Android SDK：

```powershell
.\gradlew.bat --no-daemon test assembleDebug
.\gradlew.bat --no-daemon clean test lint lintRelease lintVitalRelease assembleRelease
```

签名配置位于仓库外部的 `../keystore.properties`。不得提交 keystore、密码、日志、缓存
或本地构建状态。

工程方法和来源说明：

- [CHANGELOG](CHANGELOG.md)
- [项目谱系](docs/PROJECT_LINEAGE.md)
- [libxposed API 101/102 双兼容说明](docs/LIBXPOSED_API_101_102_COMPATIBILITY.md)
- [验证记录](docs/VERIFICATION.md)
- [工程方法](docs/ENGINEERING_METHOD.md)
- [历史 Release 归档](docs/RELEASE_ARCHIVE.md)

## 与上游的区别

| 维度 | 本项目 | 上游参考 |
| --- | --- | --- |
| 定位 | HyperOS 1 / Android 14 独立维护线 | Android 14 功能语义参考 |
| 包名 | `tv.withaibuild.customiuizer.r14` | 上游包名 |
| Xposed API | 现代 API 101/102 单 APK | v24.10.12 使用 API 100 |
| 实现 | Kotlin-first，保留稳定 JVM 边界 | 以 Java 为主 |
| 生命周期 | 显式 owner、注销和防重复 | 保留上游实现 |
| 构建 | Kotlin DSL、version catalog、R8 | 上游独立流程 |

上游只用于核对功能原意和历史 Hook 行为，不会用旧代码覆盖当前 Kotlin/API 101/102
实现。

## Release 策略

公开 Release 保留以下关键版本：

| 版本 | 定位 |
| --- | --- |
| `r14.13.6` | 当前稳定版 |
| `r14.13.5` | 上一版本；源码 tag 保留，Release 已移除 |
| `r14.12.0` | 源码 tag 保留，Release 已移除 |
| `r14.8.0` | 旧签名回退点；升级时须备份并重装 |
| `r14.7.4` | r14.7.x Kotlin/Coroutine 合并版 |
| `r14.5.0` | 独立包名、签名与发布路径基线 |

Release 标题只使用版本号。其余版本保留于 CHANGELOG、历史归档和 Git tag 中。

## 许可证与致谢

项目派生自 Mikanoshi/CustoMIUIzer，并参考
[MonwF/customiuizer](https://github.com/MonwF/customiuizer) 的 Android 14 工作。
感谢 LSPosed/libxposed、DexKit 及相关开源项目维护者。

本项目依据 [GPL-3.0](LICENSE) 分发。来源和独立维护关系见 [NOTICE.md](NOTICE.md)。
