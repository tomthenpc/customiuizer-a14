# AGENTS.md

## 适用范围

本文件适用于整个仓库。修改任何源码前，先阅读：

- `docs/AI_MAINTENANCE_GUIDE.md`
- `docs/PROJECT_LINEAGE.md`
- `docs/LIBXPOSED_API_101_102_COMPATIBILITY.md`
- `docs/VERIFICATION.md`
- `docs/ENGINEERING_METHOD.md`
- 本轮用户任务

更深目录如存在 `AGENTS.md`，其规则只覆盖对应目录；本轮用户直接指令优先。

## 项目谱系与定位

本项目最上游功能基线为：

`MonwF/customiuizer@v24.10.12`

随后当前项目完成独立包名、applicationId、版本线、签名和构建流程，并完成现代
libxposed API 101/102 单 APK 兼容、性能与生命周期治理、Java → Kotlin 保守迁移和
构建工程化。

当前直接维护仓库是：

`tomthenpc/customiuizer-a14`

当前稳定版本为 `r14.12.0`，正常维护分支为 `main`。本地当前工作树、当前分支和当前
HEAD 是后续修改的唯一直接代码基线；GitHub `origin` 用于同步和历史核对，
`MonwF/customiuizer` 只读参考。

上游 tag 仅作为功能语义、原始 Hook 行为和历史实现参考。不得用上游代码覆盖、reset、merge 或 rebase 当前独立项目，也不得恢复旧包名、旧 authority、旧版本线或旧构建配置。

本项目面向 HyperOS 1 / Android 14，使用现代 libxposed API，主要运行于：

- `system_server`
- `com.android.systemui`
- `com.miui.home`
- Android/MIUI 系统应用
- 模块设置应用

关键进程中的错误可能造成 SystemUI、Launcher 或 system_server 崩溃，因此稳定性高于代码形式。

## 上游参考边界

遇到功能回归时，可以对照 `MonwF/customiuizer@v24.10.12`，但：

- 当前独立仓库是实现和修改基线；
- 上游只用于确认功能原意和迁移前行为；
- 不机械复制上游 Java 覆盖当前 Kotlin；
- 先判断差异是否来自独立包名、API 101/102、性能优化或有意重构；
- API 102 设计以 libxposed 官方资料和当前架构为准；
- 上游行为不能替代当前 Release/R8 和实机验证。

## 固定优先级

1. 实际可运行
2. 功能行为正确
3. API 101 基线稳定
4. API、ROM、ClassLoader 和 R8 兼容
5. 性能、内存和功耗
6. 可维护性
7. Kotlin 覆盖率和形式简洁

不得用低优先级目标交换高优先级目标。

## 核心原则

> 功能关闭时接近零额外成本；功能开启时只响应真实事件；高频路径避免不必要分配、重复反射、阻塞和日志；兼容代码限制在明确边界内。

额外成本主要取决于：

`触发频率 × 单次成本 × 进程数量 × 存活时间`

代码变短不等于性能提高。

## 修改前

必须：

- 检查当前分支、HEAD 和 `git status`
- 阅读相关入口、调用链、测试和 R8 规则
- 保护所有未提交工作
- 先复现或确认问题，再修改
- 使用最小、完整、可解释的变更

未经用户授权，不得执行：

- `git reset --hard`
- `git restore .`
- `git checkout -- .`
- `git clean -fd`
- force push
- 用远程旧代码覆盖当前工作树

不得提交 keystore、密码、日志、缓存、构建目录或本地 APK。

## Hook 与进程

- 未启用功能尽量不注册 Hook。
- 无关进程不初始化对应功能、DexKit、资源、缓存、线程或监听器。
- 注册 Hook、Receiver、Observer、Listener、Callback、Runnable、Coroutine、Executor 必须防重复。
- ROM 目标不存在时只记录一次，安全禁用当前单项功能，不得高频重试。
- 入口层尽早按 Android 版本、包名和进程退出无关路径。

## 热路径

绘制、动画、状态栏、控制中心、网络速度、触摸、通知绑定、音频回调及高频 SystemUI/Launcher Hook 中避免：

- 反射和 DexKit 搜索
- 磁盘访问
- 同步远程 Binder
- 重复 SharedPreferences 读取
- 临时数组、集合、Sequence、Pair/Triple、捕获 lambda
- 重复字符串格式化
- 大范围锁
- 正常运行日志
- 重复 API/ROM 判断

反射、解析和兼容探测放到冷路径，热路径只读取已准备状态。

## Kotlin/JVM 兼容

不要机械翻译 Java。必须保持：

- Hook target、priority、注册条件和顺序
- before/after、参数修改、提前返回和异常语义
- ClassLoader 与进程边界
- Java/Kotlin 静态互操作
- `@JvmStatic`、`@JvmField`、必要 JVM 签名
- 初始化时机和同步语义
- 反射、DexKit、字符串类名和动态方法查找
- Release/R8 行为

稳定 Java 边界可以保留。100% Kotlin 不是验收条件。

## libxposed API 101/102

非 API 迁移任务不得顺带改变 API 版本。

当前固定兼容边界：

- API 101 为最低运行基线
- 使用 API 102 编译
- `minApiVersion=101`
- `targetApiVersion=102`
- 公共路径只依赖 API 101 能力
- API 102 专属逻辑集中在冷边界
- 不反射调用 libxposed API
- 不混用 `de.robv.android.xposed`
- Hot Reload 保持关闭；只有用户另行启动独立生命周期改造阶段时才评估

## 生命周期与内存

每个长期资源必须有创建者、所有者、停止/注销路径和防重复状态。

不得静态持有 Activity、Fragment、View、临时 Context 或 ClassLoader。缓存必须有上限或明确生命周期。UI 异步任务必须随生命周期取消。

## 错误处理

- 修根因，不用大范围 `try/catch` 隐藏问题。
- 不用空实现、假返回、吞异常或禁用功能伪造成功。
- 不无限重试。
- Release 日志必须限流。
- 单项功能可以安全失败，但不能拖垮 SystemUI、Launcher 或 system_server。

## R8 与动态入口

删除、重命名、私有化或移动代码前检查：

- `META-INF/xposed`
- Manifest
- 反射和字符串类名
- DexKit
- XML 和动态资源
- JNI/native
- ProGuard/R8
- Java/Kotlin 静态入口

不得为了通过构建无边界扩大 keep 规则。

## 变更纪律

- Kotlin DSL、version catalog 和核心 Kotlin 迁移已经完成，不重复迁移或回退。
- 不混入无关依赖升级、工具链升级或大型架构替换。
- 没有实际收益证据，不修改稳定代码。
- 删除死代码必须证明不被动态引用。
- 完成前审查完整 `git diff` 和 `git status`。

## 仓库与分支

- 源码仓库：`tomthenpc/customiuizer-a14`。
- LSPosed 展示仓库：`Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14`，只放
  README、CHANGELOG、scope、source URL 和发布资产说明，不放业务源码。
- 两个仓库正常状态都只保留 `main`。
- 需要审查时使用短期分支和 PR；合并后删除本地与远端短期分支。
- 不恢复已清理的上游旧分支、旧 tag、备份、日志或阶段性报告。
- `v24.10.12` 是保留的上游功能参考 tag；独立版本使用 `r14.*`。

## 验证

使用项目实际可用任务，至少覆盖：

- 单元测试
- Debug 构建
- Release 构建
- R8/资源压缩
- Lint、`lintRelease`、`lintVitalRelease`
- APK 元数据、签名、大小、SHA-256

编译通过不等于目标进程可用。不能完成实机验证时，必须明确标注。

纯文档变更至少执行 UTF-8、相对链接和 `git diff --check`；没有修改源码、资源、构建
配置、Manifest 或 Xposed 元数据时，不为形式完整重复生成已实机验证的 APK。

## 发布

代码应提交到短期独立分支。未经用户明确确认：

- 不合并 `main`
- 不 force push
- 不创建 tag
- 不创建 GitHub Release
- 不上传正式 APK

用户明确要求更新远端、发布或清理分支时，应完成提交、PR/合并、推送和最终远端复核，
不要停在本地提交。最终报告必须区分已验证、未验证和需要实机测试的内容，不得声称
未经测量的性能或续航提升。
