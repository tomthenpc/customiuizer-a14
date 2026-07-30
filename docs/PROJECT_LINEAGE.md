# 项目谱系与参考边界

## 原始最上游

项目：

`Mikanoshi/CustoMIUIzer`

原始仓库：

`https://code.highspec.ru/Mikanoshi/CustoMIUIzer`

Mikanoshi/CustoMIUIzer 是本项目的原始代码与产品谱系来源。原始作者及贡献者的版权、
许可证和 Git 作者信息继续保留。

## Android 14 功能上游

仓库：

`https://github.com/MonwF/customiuizer`

功能基线：

`v24.10.12`

Release：

`https://github.com/MonwF/customiuizer/releases/tag/v24.10.12`

该版本是本项目 HyperOS 1 / Android 14 功能集合、Hook 目标、偏好键和 ROM 兼容分支的
直接功能参考。它继承自原始项目，但不取代 Mikanoshi/CustoMIUIizer 的最上游地位。

## 当前独立项目

仓库：

`https://github.com/tomthenpc/customiuizer-a14`

独立化内容包括：

- 包名和 namespace
- applicationId
- provider/组件标识
- 版本号和 Release 线
- 签名与构建流程
- 性能和生命周期治理
- Java → Kotlin 重构
- 现代 libxposed API 101/102 单 APK 双兼容
- 生命周期、热路径与构建工程治理

当前稳定版本为 `r14.13.8`。当前仓库和本地工作树是直接维护基线；上游版本、旧
Release 与历史分支均不能替代当前实现。

## 仓库职责

| 仓库 | 职责 |
| --- | --- |
| `tomthenpc/customiuizer-a14` | 唯一源码、工程文档、构建脚本、tag 和正式 Release 来源 |
| `Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14` | LSPosed 用户展示、scope、source URL 和镜像发布说明 |
| `Mikanoshi/CustoMIUIzer` | 原始最上游；代码、产品设计与许可证谱系来源 |
| `MonwF/customiuizer` | Android 14 直接功能上游；只读功能语义与历史 Hook 行为参考 |

源码仓库和 LSPosed 展示仓库正常状态都只保留 `main`。短期工作分支只用于审查，合并后
删除。独立项目使用 `r14.*` 版本线；外部上游 `v24.10.12` 仅作为固定的 Android 14
功能参考，不是当前仓库的发布基线。

## 使用原则

遇到功能回归时，MonwF 的 Android 14 功能实现可用于核对：

- 功能原意
- Hook 类、方法和参数
- before/after 语义
- 用户可见行为
- ROM 兼容分支的历史来源

但不得：

- 用上游文件覆盖当前 Kotlin 实现
- 恢复旧包名或 authority
- 将当前仓库 reset/rebase/merge 到上游 tag
- 把上游旧构建配置带回
- 用上游测试结果替代当前 R8 和实机验证

技术判断优先级：

1. 当前用户要求
2. 当前独立仓库实际代码与实机结果
3. libxposed 官方资料
4. MonwF/customiuizer v24.10.12 功能语义与历史实现
5. 可能滞后的说明文档

开始任何任务时仍需实时检查分支、HEAD、工作树、构建配置与目标文件，不能把本文记录的
版本状态当成永远不变的事实。
