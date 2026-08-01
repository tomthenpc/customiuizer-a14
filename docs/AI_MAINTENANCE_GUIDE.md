# AI 维护入口

本文给 Codex、Devin 和其他本地 Agent 提供最短的项目接管路径。它只描述当前状态和
工作顺序；具体工程原则、兼容边界与证据分别由链接文档负责。

## 开始前读取顺序

1. 根目录 `AGENTS.md` 和本轮用户任务；
2. [项目谱系](PROJECT_LINEAGE.md)；
3. [libxposed API 101/102 双兼容说明](LIBXPOSED_API_101_102_COMPATIBILITY.md)；
4. [A14 运行时加固](A14_RUNTIME_HARDENING.md)；
5. [验证记录](VERIFICATION.md)；
6. [工程方法](ENGINEERING_METHOD.md)；
7. 与任务直接相关的入口、调用链、测试和 R8 规则。

不要从历史会话、旧分支名、旧 Release 或上游 README 推断当前代码状态。

## 当前维护快照

| 项目 | 当前值 |
| --- | --- |
| 源码仓库 | `tomthenpc/customiuizer-a14` |
| 当前分支 | `devin/a14-runtime-hardening` |
| 当前 HEAD | `71ff6e9f` |
| 候选版本 | `r14.15.3` / versionCode 191 |
| applicationId | `tv.withaibuild.customiuizer.r14` |
| 运行平台 | HyperOS 1 / Android 14 / SDK 34 / `arm64-v8a` |
| libxposed | `minApiVersion=101` / `targetApiVersion=102` |
| Hot Reload | 关闭 |
| 构建 | JDK 17、Kotlin DSL、version catalog、R8、资源压缩 |
| 实机证据 | API 101 已验证；API 102 实机仍待独立验证 |

该表是接管提示，不替代实时检查。开始任务必须执行：

```powershell
git rev-parse --show-toplevel
git status --short
git branch --show-current
git log -5 --oneline
git remote -v
```

当前本地工作树、当前分支和当前 HEAD 是唯一直接代码基线。不得先 pull、reset、restore、
clean、rebase 或重新 clone 覆盖本地内容。

## 已完成、不要重复

- 独立包名、签名、版本线和发布流程；
- libxposed API 101/102 单 APK 双兼容；
- 核心 Java → Kotlin 保守迁移；
- Kotlin DSL 与 version catalog 迁移；
- 生命周期、重复注册和主要热路径治理；
- README、CHANGELOG、Release 与仓库分支清理。

100% Kotlin、Hot Reload、Android 15/16 适配和无证据的全仓重构都不是默认后续目标。

## 关键边界文件

| 边界 | 文件 |
| --- | --- |
| Xposed 入口 | `app/src/main/java/tv/withaibuild/customiuizer/MainModule.java` |
| Hook 兼容层 | `mods/utils/HookerClassHelper.kt`、`ModuleHelper.kt`、`XposedHelpers.java` |
| 资源 Hook | `mods/utils/ResourceHooks.kt` |
| Xposed 元数据 | `app/src/main/resources/META-INF/xposed/` |
| R8 | `app/proguard-rules.pro` |
| Android 构建 | `app/build.gradle.kts` |
| 依赖版本 | `gradle/libs.versions.toml` |

路径表中的 `mods/utils` 位于
`app/src/main/java/tv/withaibuild/customiuizer/` 下。删除或重命名动态入口前，还要搜索
Manifest、XML、字符串类名、DexKit、JNI 和 ProGuard/R8 引用。

## 修改与验证

1. 先搜索符号和调用链，只读取相关范围；
2. 区分模块问题、ROM/框架问题和其他应用日志；
3. 使用最小、完整、可回滚的修改；
4. 源码提交前至少运行相关测试和 `test assembleDebug`；
5. 涉及 Hooker、反射、入口、R8、Manifest、资源或动态调用时增加 Release 验证；
6. 阶段或发布候选执行完整 Lint、Release、R8、资源压缩、签名和元数据检查；
7. 没有对应设备时明确写“未实机验证”，不得推断成功。

纯文档修改只需验证 UTF-8、文档链接、`git diff --check` 和最终状态；不要因此重签或替换
已经安装验证的 Release APK。

## 仓库和发布

- 源码与完整工程说明只写入 `tomthenpc/customiuizer-a14`；
- LSPosed 展示仓库只维护用户说明、CHANGELOG、scope、source URL 和对应发布信息；
- 使用短期分支和 PR 时，合并后删除本地与远端分支，恢复仅 `main`；
- 不 force push，不提交 APK、keystore、密码、日志、缓存或备份；
- 不恢复已清理的上游旧 tag；`v24.10.12` 仅作为功能参考；
- 未经用户明确确认，不创建 tag、Release 或替换正式 APK。

发布结论必须分别列出：静态/构建已验证、实机已验证、尚未验证。
