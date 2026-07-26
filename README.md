# CustoMIUIzer A14

Independent maintenance line for HyperOS 1 / Android 14.

本项目是 CustoMIUIzer 的独立维护版本。Android 14 功能语义以
[MonwF/customiuizer v24.10.12](https://github.com/MonwF/customiuizer/releases/tag/v24.10.12)
为参考，但本项目不是 MonwF 的官方发布，并使用独立包名、版本线、构建、签名和发布流程。

目标平台仅为 HyperOS 1 / Android 14。项目不承诺 Android 15、Android 16 或其他
MIUI/HyperOS 版本的兼容性。

## 当前稳定状态

| 项目 | 当前状态 |
| --- | --- |
| 稳定版本 | `r14.12.0` |
| Android | Android 14 / SDK 34 |
| ROM | HyperOS 1 |
| ABI | `arm64-v8a` |
| 包名 | `tv.withaibuild.customiuizer.r14` |
| Xposed API | min 101 / target 102 |
| Hot Reload | 关闭 |
| 构建 | Kotlin DSL / version catalog |
| Release | [Releases/latest](https://github.com/tomthenpc/customiuizer-a14/releases/latest) |

`r14.12.0` 是当前现代化、Kotlin 迁移、生命周期治理和 API 101/102 单 APK
兼容工作的稳定基线。完整长期日志审计不属于本版本的发布门槛；后续确认的问题进入
`r14.12.x` 补丁版本。

## 与上游 v24.10.12 的差异

| 维度 | 本项目 | 上游 v24.10.12 |
| --- | --- | --- |
| 定位 | 独立 Android 14 维护线 | Android 14 功能参考基线 |
| 包名和版本 | 独立包名与 `r14.x` 版本线 | 上游原始包名与版本线 |
| Xposed API | libxposed API 101/102 单 APK 兼容 | libxposed API 100 |
| Kotlin | Kotlin-first，保留必要 Java/JVM 边界 | 以 Java 为主 |
| 构建 | Kotlin DSL、version catalog、Gradle 9 | Groovy DSL |
| 生命周期 | 明确 Receiver、Observer、Listener 和 Coroutine owner | 保留上游实现 |
| Hook | 防重复注册，并区分冷路径与热路径成本 | 保留上游实现 |
| Release | R8、资源压缩、签名和 APK 元数据检查 | 上游独立发布流程 |
| 验证 | 单测、Lint、Debug/Release、API 回编译和实机门禁 | 上游独立验证流程 |

主要功能来自上游 Android 14 功能基线。本项目的工作重点是独立维护、现代 libxposed
兼容、已确认缺陷修复、生命周期约束、构建治理和可复现发布，不把上游功能重新描述为
本项目原创。

## 支持范围

- 仅支持 HyperOS 1 / Android 14（SDK 34）。
- 仅提供 `arm64-v8a` 构建。
- 框架必须实现 libxposed API 101 或 API 102。
- 推荐使用支持现代 libxposed API 的 LSPosed/Vector 构建。
- 不支持 Android 15、Android 16，也不保证其他 MIUI/HyperOS 版本。
- 不得与上游版或其他 CustoMIUIzer 派生模块同时启用。
- 不同签名的 APK 可能无法覆盖安装；卸载前应先备份配置。
- 系统应用版本和厂商 ROM 差异仍可能影响个别 Hook。

模块声明 `targetApiVersion=102`。在 API 101 框架中，管理器可能提示模块“为较新的
Xposed 版本设计”；这是目标 API 的版本比较结果，不等同于模块加载失败。应以框架日志、
目标进程状态和实际功能行为为准。

## 功能范围

项目保留上游 Android 14 基线中的主要定制能力，按运行区域可概括为：

- SystemUI、状态栏和图标；
- 控制中心、音量面板和通知；
- 锁屏、充电信息和媒体界面；
- Launcher、最近任务和桌面行为；
- 全局手势、按键动作和电源菜单；
- 应用、权限、安装和分享行为；
- 其他 HyperOS 系统界面定制。

具体功能是否可用取决于 ROM 和系统应用版本。出现问题时应同时记录模块版本、框架 API、
设备完整重启后的日志和目标应用版本。

## 工程差异

- 大部分业务和 Hook 实现已迁移到 Kotlin；稳定的入口、反射和 JVM 兼容边界保留 Java。
- 使用 API 102 编译，同时以 API 101 为最低运行基线。
- API 102 专属 Hot Reload、hook ID 和 replacement 当前未进入运行路径。
- Receiver、Observer、Listener、Handler、动画和 CoroutineScope 绑定明确生命周期。
- SystemUI 重建路径避免重复 Hook、重复注册和静态 View/Context 长期持有。
- 对反射、DexKit、资源查找和稳定输入进行冷路径缓存。
- 高频 Hook 回调减少临时数组、集合、格式化和重复状态计算。
- Release 启用 R8、资源压缩、zipalign 和 APK Signature Scheme v2。
- Gradle Wrapper 和直接依赖版本均固定；依赖集中在 version catalog。

详细兼容边界见
[libxposed API 101/102 双兼容说明](docs/LIBXPOSED_API_101_102_COMPATIBILITY.md)。

## 安装和升级

1. 从 [Latest Release](https://github.com/tomthenpc/customiuizer-a14/releases/latest) 下载 APK。
2. 安装 APK；如签名不同导致无法覆盖，先备份设置再处理旧安装。
3. 在 LSPosed/Vector 中启用模块并检查作用域。
4. 打开模块设置一次。
5. 完整重启设备，不以单独重启应用代替。
6. 检查模块、`system_server`、SystemUI 和 Launcher 日志及常用功能。

旧包名版本不会自动迁移为当前独立包名。升级前不要同时启用两个同源模块。

## Release 验证

稳定 Release 的静态和构建门禁包括：

- unit tests；
- Debug 和 Release 构建；
- Lint、`lintRelease` 和 `lintVitalRelease`；
- R8 和 resource shrink；
- signing 与 zipalign；
- APK 内 Xposed 入口、scope 和 `module.prop`；
- Legacy `de.robv.android.xposed` API 扫描；
- API 101 依赖回编译和 API 102 正式构建；
- 设备完整重启和基础 LSPosed/Vector 日志检查。

构建通过只能证明产物和静态兼容边界，不代替 API 101、API 102 各自的实机功能验证。

## 构建

需要 JDK 17 和 Android SDK：

```powershell
.\gradlew.bat --no-daemon test assembleDebug
.\gradlew.bat --no-daemon clean test lint lintRelease lintVitalRelease assembleRelease
```

正式签名配置位于仓库外部的 `../keystore.properties`，不得提交 keystore、密码或本地
构建状态。

## 版本策略

- `r14.x` 是 Android 14 独立维护版本线。
- `r14.12.0` 是当前 minor 的初始稳定基线。
- `r14.12.x` 只承载日志归因明确的兼容性、崩溃、生命周期和小范围行为修复。
- 新功能或重大架构变化进入新的 minor 版本。
- 纯文档变更不单独发布补丁版本，也不为每个 commit 创建 Release。
- GitHub Releases 只保留 4 个关键版本；其余版本保留在 CHANGELOG 和 Git 历史中。
- 发布新补丁时保留最新补丁、上一补丁、当前 minor 初始基线和一个长期回退基线。
- 删除任何旧 Release 前必须先完整归档其说明、资产和 SHA-256。

## 上游、许可证和致谢

项目派生自 Mikanoshi/CustoMIUIzer，并参考
[MonwF/customiuizer](https://github.com/MonwF/customiuizer) 的 Android 14 工作。
感谢 LSPosed/libxposed、DexKit 及相关开源项目的维护者。

本项目依据 [GPL-3.0](LICENSE) 分发。二进制发布必须提供对应源代码，保留版权和许可证
声明，并明确标识修改。来源和独立维护关系见 [NOTICE.md](NOTICE.md) 与
[项目谱系](docs/PROJECT_LINEAGE.md)。
