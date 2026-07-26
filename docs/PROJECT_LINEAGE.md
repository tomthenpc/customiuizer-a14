# 项目谱系与参考边界

## 最上游

仓库：

`https://github.com/MonwF/customiuizer`

功能基线：

`v24.10.12`

Release：

`https://github.com/MonwF/customiuizer/releases/tag/v24.10.12`

该版本是本项目 HyperOS 1 / Android 14 功能和原始 Hook 行为的最上游参考。

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
- 现代 libxposed API 101 适配
- Java → Kotlin 重构
- API 101/102 单 APK 双兼容
- 生命周期、热路径与构建工程治理

当前稳定版本为 `r14.12.0`。当前仓库和本地工作树是直接维护基线；上游版本、旧
Release 与历史分支均不能替代当前实现。

## 仓库职责

| 仓库 | 职责 |
| --- | --- |
| `tomthenpc/customiuizer-a14` | 唯一源码、工程文档、构建脚本、tag 和正式 Release 来源 |
| `Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14` | LSPosed 用户展示、scope、source URL 和镜像发布说明 |
| `MonwF/customiuizer` | 只读功能语义与历史 Hook 行为参考 |

源码仓库和 LSPosed 展示仓库正常状态都只保留 `main`。短期工作分支只用于审查，合并后
删除。上游旧 tag 已从当前远端清理，仅保留 `v24.10.12` 作为功能参考；独立项目版本
使用 `r14.*`。

## 使用原则

遇到功能回归时，上游可用于核对：

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
4. 上游 v24.10.12 功能语义与历史实现
5. 可能滞后的说明文档

开始任何任务时仍需实时检查分支、HEAD、工作树、构建配置与目标文件，不能把本文记录的
版本状态当成永远不变的事实。
