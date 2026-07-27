# AGENTS.md

## 0. 任务连续性与中断恢复

### 三层状态来源

任务状态按以下顺序恢复：

1. 用户当前消息和当前会话中最后一个明确任务；
2. `.devin/ACTIVE_TASK.md`；
3. 当前工作区的 `git status`、`git diff`、已修改文件、构建日志和进程状态；
4. `docs/DEVIN_A14_CHECKPOINT.md`；
5. Git 历史和其他工程文档。

`AGENTS.md` 记录长期规则；checkpoint 记录分支级阶段状态；`.devin/ACTIVE_TASK.md` 记录当前会话的实时执行状态。不得使用旧 checkpoint 覆盖用户刚刚给出的新任务。

### 每轮恢复协议

发生以下任一情况时，必须执行恢复协议：

- 用户说“继续”“继续刚才的任务”“接着做”；
- 用户问“卡了吗”“还在运行吗”；
- 命令被取消、超时、终端断开或工具调用失败；
- 会话上下文被压缩；
- Devin 无法确定上一条命令执行到哪里；
- 长构建、日志分析或批量修改被打断。

恢复时必须：

1. 读取 `.devin/ACTIVE_TASK.md`；
2. 检查当前分支、HEAD、tracking 和 `git status --short`；
3. 检查 `git diff --stat` 和完整相关 diff；
4. 检查上一条命令的退出状态、日志文件和相关进程是否仍在运行；
5. 核对已完成项是否真实存在于工作区或提交中；
6. 从第一个未完成项继续，不重复已完成修改；
7. 不重新询问用户已经明确给出的路径、分支、目标和限制；
8. 只有遇到真正的破坏性操作、凭据缺失或无法由证据决定的产品行为时才询问。

### 命令取消语义

- 命令被用户取消，只代表该条命令终止，不代表整个任务被取消。
- 不得把“Canceled terminal command”解释为用户要求停止项目任务。
- 用户随后说“继续”时，应检查残留进程和部分产物，然后重新执行该未完成步骤。
- 不得在普通构建、测试、读取、搜索、日志审计、当前分支 commit 或已授权 push 前再次请求确认。
- 不得因为一次命令取消而清空任务列表、重新生成计划或丢失已经完成的修改。

### “卡了吗”响应规则

用户问“卡了吗”时：

1. 先检查真实命令或进程状态；
2. 简短报告当前步骤、已完成项和是否仍有进程运行；
3. 若没有硬阻塞，立即继续下一步；
4. 不以“是否继续？”结束；
5. 不承诺后台稍后交付；
6. 不把等待命令审批误报为代码卡死。

### 实时任务文件

每个持续超过一个步骤的任务必须维护：

`.devin/ACTIVE_TASK.md`

该文件是本地临时状态，不提交 Git。

开始任务、完成一个步骤、修改路线、命令失败、命令取消、开始长构建和结束长构建时都要更新。

至少包含：

- 用户当前目标；
- 仓库和分支；
- 起始 HEAD；
- 当前工作区状态；
- 已完成项；
- 正在执行项；
- 未完成项；
- 最后一条命令及退出状态；
- 已修改文件；
- 已产生的验证证据；
- 当前阻塞；
- 下一条精确动作；
- 禁止事项。

任务完成后，将长期有效结果同步到 checkpoint，再删除或清空 `ACTIVE_TASK.md`。

### 长命令保护

运行 Gradle、Lint、R8、日志全量分析等长命令前：

1. 更新 `ACTIVE_TASK.md`；
2. 确认命令、工作目录和日志输出路径；
3. 避免命令行尾部引号、转义和管道错误；
4. 将完整输出写入 `.devin/`；
5. 命令结束后记录退出码和结果；
6. 失败时先分析日志，不盲目重复执行；
7. 不因构建持续数分钟而自行判定卡死。

### 事实优先

任务列表中的“完成”必须有工作区 diff、commit、构建产物、日志或实机结果支持。

不得仅根据之前的 thought、计划文本或任务计数声称完成。

## 1. 适用范围与优先级

本文件适用于整个仓库。更深目录存在 `AGENTS.md` 时，只补充对应子树规则。

指令优先级：

1. 用户本轮明确要求
2. 本文件
3. 当前任务文档与 `docs/DEVIN_A14_CHECKPOINT.md`
4. 仓库其他工程文档
5. Git 历史与上游参考

修改前按任务范围读取：

- `docs/DEVIN_A14_CHECKPOINT.md`
- `docs/AI_MAINTENANCE_GUIDE.md`
- `docs/PROJECT_LINEAGE.md`
- `docs/LIBXPOSED_API_101_102_COMPATIBILITY.md`
- `docs/VERIFICATION.md`
- `docs/ENGINEERING_METHOD.md`
- 活跃重构分支存在时读取 `docs/REFACTOR_PLAN_r14.13.md`、`docs/REFACTOR_PROGRESS.md`
- 与任务直接相关的入口、调用链、测试、R8 规则和近期提交

当前源码、当前分支、HEAD、构建配置、APK、日志和实机结果高于可能滞后的说明文档。

## 2. 固定项目边界

- 仓库：`tomthenpc/customiuizer-a14`
- 项目：CustoMIUIzer A14
- 平台：HyperOS 1 / Android 14 / SDK 34
- `applicationId=tv.withaibuild.customiuizer.r14`
- `minSdk=34`
- `targetSdk=34`
- ABI：`arm64-v8a`
- libxposed：`minApiVersion=101`、`targetApiVersion=102`
- `staticScope=false`
- Hot Reload 关闭
- 禁止 Legacy `de.robv.android.xposed` 运行 API
- 上游功能语义基线：`MonwF/customiuizer v24.10.12`

`compileSdk`、Build Tools、Gradle、AGP、Kotlin、依赖版本、versionName、versionCode 和签名配置必须从当前分支实时读取，不得照抄历史文档。

## 3. 直接代码基线

本轮用户明确指定分支时，该分支是唯一直接代码基线。

要求：

- 先确认本地分支、HEAD、上游 tracking 和 `git status`
- 不因为 `main` 是默认分支就切回 `main`
- 不重新创建平行分支
- 不用 `main`、旧 Release、旧 APK 或上游覆盖当前分支
- 当前分支有新提交时，以最新远端分支和受保护的本地工作树共同核对
- checkpoint 中记录分支相对 `main` 的 ahead/behind 状态

只有用户明确要求结束该开发线时，才准备 PR、合并、tag、Release 或分支清理。

## 4. 上游参考边界

判断来源顺序：

1. 用户当前要求
2. 当前 A14 分支源码、Git、构建、APK、日志和实机结果
3. 当前仓库历史
4. Android、Kotlin、Gradle 和 libxposed 官方资料
5. 上游 `v24.10.12`

上游只用于核对功能原意、Hook 目标和历史行为。禁止：

- 用上游 Java 覆盖当前 Kotlin/API 101/102 实现
- reset、rebase 或 merge 当前仓库到上游 tag
- 恢复旧包名、authority、版本线或构建配置
- 用上游测试代替当前 Release/R8 和实机验证

LSPosed 展示仓库 `Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14` 只维护用户展示、scope、source URL 和发布说明，不存放业务源码。

## 5. 固定优先级

1. 实际可构建、可安装、可运行
2. 功能和 Hook 行为正确
3. `system_server`、SystemUI、Launcher 稳定
4. HyperOS 1 / Android 14 兼容
5. JVM、反射、ClassLoader、进程、R8 和 libxposed 兼容
6. 生命周期与资源安全
7. 性能、内存和功耗
8. 可维护性
9. Kotlin 覆盖率和代码简短

不得用低优先级目标交换高优先级目标。

## 6. 当前工程方向

项目已建立：

- Kotlin DSL 与 version catalog
- libxposed API 101/102 单 APK 兼容
- 核心 Java → Kotlin 保守迁移
- 主要生命周期与重复注册治理
- 主要热路径治理
- 独立包名、签名和 `r14.*` 版本线

默认后续方向：

- 延续当前活跃分支的明确目标
- 修复可复现的功能/UI/兼容回归
- 根据日志和调用链修复稳定性问题
- 完成当前候选版本缺少的构建、签名和实机验证
- 同步代码、checkpoint、进度文档和 changelog

以下不是默认目标：

- 再次执行全项目 Java → Kotlin 迁移
- 强制达到 100% Kotlin
- 迁移剩余稳定 Java 边界
- 重做 Kotlin DSL 或 version catalog
- 启用 Hot Reload
- Android 15/16 适配
- 无证据全仓重构或微优化

## 7. Devin 执行协议

每轮开始：

- 检查仓库根目录、分支、HEAD、tracking、`git status`、最近提交和 remote
- 比较当前分支与 `main`
- 读取 checkpoint 和当前阶段文档，但用实时事实复核
- 保护用户已有或来源不明的未提交修改
- 建立复现、证据或明确成本后再修改
- 追踪入口、调用方、进程、生命周期、动态引用、测试和近期历史
- 使用最小但完整、可解释、可验证的变更
- 先跑最快相关验证，再按风险扩大
- 完成前审查完整 diff、HEAD、工作区和远端同步状态

普通读取、搜索、编辑、测试、构建、commit 和已授权当前开发分支 push 不逐项询问。

仅在以下情况询问：

- 破坏性操作
- 凭据或签名材料未知
- 清除应用/设备数据
- 修改、合并或强推 `main`
- 创建 PR、tag、Release 或公开 APK
- 产品行为无法从证据判断

## 8. 轻度 Claude 风格

Devin 仍以自主执行和实际落地为主，只采用以下分析纪律：

- 先陈述证据，再给判断
- 计划简短，并随新证据调整
- 可并行处理互不依赖的只读调查
- 主动寻找反证
- 不把日志级别、理论风险、上游差异或代码形式直接判定为缺陷
- 长任务只在完成闭环、发现重要风险、改变路线或遇到硬阻塞时简短汇报
- 不输出冗长命令流水账
- 最终区分：已验证、代码层面确认、待实机验证、无法确认
- 不因一次构建、一个 commit、一次 push 或一个 Phase 完成而提前停止

不得因此降低 Devin 自主执行力度，也不得变成只分析不修改。

## 9. Git 与敏感信息

禁止未经授权执行：

- `git reset --hard`
- `git restore .`
- `git checkout -- .`
- `git clean -fd`
- force push
- 用远程旧状态覆盖未知本地工作

不得提交或公开：

- keystore、密码、token、真实 `keystore.properties`
- APK、签名备份、私人日志、缓存和构建目录
- 私有设备数据或机器专属敏感信息

签名规则：

- 正式签名配置位于仓库外部
- 缺少正式签名配置时，不得伪造 Release 成功
- 每个候选 APK 必须检查实际签名证书 SHA-256
- 新签名线与旧签名线不可直接覆盖安装时，必须在用户文档中明确备份、卸载和重装要求
- 不得用 Debug 证书 APK 冒充正式候选或 Release

## 10. Hook、JVM 与动态兼容契约

Java → Kotlin 或重构时必须保持：

- FQCN、构造器、重载、JVM descriptor、可见性和 primitive/boxed 类型
- static 字段/方法、初始化顺序、同步、volatile/atomic 语义
- `@JvmStatic`、`@JvmField`、`@JvmName` 和 Java 互操作
- 反射类名/成员名、DexKit、字符串入口、JNI/native 边界
- Manifest、authority、XML、preference key 和资源名
- `META-INF/xposed/java_init.list`、`module.prop`、scope 和 libxposed metadata
- process gate、ClassLoader 和初始化时机
- Hook target、priority、注册条件/顺序、before/after、参数修改、early return、result、throwable、`Chain.proceed()` 和回调次数
- Release/R8 可达性和 resource shrink 行为

`MainModule.java`、`XposedHelpers.java`、`MemberUtilsX.java` 等已评估的稳定边界继续保留 Java，除非用户另行启动独立迁移和实机验证阶段。

ROM 目标不存在时只记录一次，仅停用当前单项功能，不得高频重试或拖垮关键进程。

## 11. libxposed API 101/102

固定边界：

- 使用 API 102 编译，API 101 为最低运行基线
- 公共加载和 Hook 路径只依赖 API 101 已有能力
- API 102 专属类型不得进入 API 101 必经类的字段、签名或静态初始化
- API 版本判断只放在入口或冷路径
- 不反射调用 libxposed API
- 不混用 Legacy Xposed API
- Hot Reload、hook ID 和原子 replacement 保持关闭

非 API 迁移任务不得顺带改变上述配置。API 101 结果不得冒充 API 102 实机证据。

## 12. Kotlin-first 与代码风格

目标是 Kotlin-first，不强制 100% Kotlin。

优先使用 Kotlin 改善：

- Null 安全
- 显式状态建模
- 不可变性
- 资源释放
- 分支完整性
- 测试能力

避免：

- `!!`
- 深层 scope function
- 复杂 DSL 和隐藏副作用
- 热路径长集合链和无必要 `Sequence`
- 用 Flow/coroutine 替代简单回调却增加调度和生命周期成本
- 为减少代码行数破坏 JVM、反射或 Hook 语义

项目风格：

- 低抽象
- 强边界
- 状态显式
- 控制流直接
- 热路径可预测
- 资源所有权清楚
- 兼容代码集中

不得曲解为超长函数、全局可变状态、复制逻辑或吞异常。

## 13. 性能与生命周期

额外成本模型：

`触发频率 × 单次成本 × 进程数量 × 存活时间`

要求：

- 功能关闭时接近零运行成本
- 无关进程不初始化无关功能
- 事件和生命周期回调优先于轮询
- Hook、Receiver、Observer、Listener、Callback、Runnable、Coroutine 和 Executor 注册幂等
- 释放和注销可重复调用
- 长期资源有明确创建者、所有者、停止路径和防重复状态
- 缓存有容量、范围、生命周期或失效规则
- 禁止静态持有 Activity、Fragment、View、临时 Context 或临时 ClassLoader

绘制、动画、触摸、通知绑定、状态栏、控制中心、网速、音频和高频 SystemUI/Launcher Hook 中避免：

- 反射、DexKit、磁盘 I/O、同步远程 Binder
- 重复 SharedPreferences、API/ROM 判断
- 临时集合/数组、Pair/Triple、装箱、捕获 lambda、重复格式化
- 大锁、正常运行日志和重复兼容探测

反射、解析、资源查找和兼容探测放到冷路径；热路径只读取准备好的不可变或原子状态。

## 14. 设置 UI、Locale 与资源变更

设置应用变更必须验证：

- 日间/夜间主题
- 状态栏和导航栏图标明暗
- Toolbar、Preference title/summary、Switch、弹窗和 About 页面
- 主页面、子页面、搜索、旋转、返回栈和 Fragment 重建
- 应用内语言切换、跟随系统、配置变化和恢复
- 普通/分享/打开方式选择器
- BT/WiFi 列表
- 资源收缩和多语言 fallback

不得因为 Lint 或“未使用资源”报告直接批量删除资源。删除前必须搜索：

- XML 引用
- 代码 `R.*`
- `getIdentifier`
- 反射和字符串名称
- ROM/Xposed 动态访问
- R8/resource shrink 输出

行尾、格式化和资源清理应独立提交，避免掩盖真实行为差异。

## 15. Root 命令与进程重启

涉及 Root shell、Launcher/SystemUI/Security Center 重启时：

- 命令不得在主线程执行
- 先确认 Root
- 处理 `pidof` 无结果、多个 PID、非零退出码和 stderr
- 输出和日志必须限长，不暴露敏感内容
- Fragment/Activity 销毁后不得回调失效 UI
- 实机验证成功、失败、无 Root 和目标未运行路径
- 不用 Root 命令掩盖原有 Hook 或广播逻辑回归

## 16. 问题证据门槛

修改前至少确认：

1. 具体代码和调用链
2. 所属包、进程和生命周期
3. 当前功能开关和触发条件
4. 相关 Git 历史
5. 测试、日志、稳定版本或可复现场景
6. 是否属于 ROM、框架或其他模块
7. 是否影响 R8、反射、ClassLoader 或动态入口
8. 结论是否只适用于旧候选 APK

不得仅因为 Java 文件仍存在、代码不够函数式、日志出现错误级别、A14 上游不同或理论上可能更慢就修改稳定代码。

## 17. 验证

使用仓库实际存在的任务，不伪造结果。

按风险覆盖：

- targeted/unit test
- `test`
- `lint`、`lintRelease`、`lintVitalRelease`
- `assembleDebug`、`assembleRelease`
- R8、resource shrink
- applicationId、version、SDK、ABI
- Xposed metadata、scope 和动态入口
- zipalign、APK SHA-256、实际签名证书
- Legacy Xposed API 扫描
- API 101/102 边界

涉及 Hook、入口、反射、R8、Manifest、资源、Locale、主题、Fragment 生命周期或 libxposed 时必须增加 Release 和实机验证。

纯文档修改只需检查 UTF-8、相对链接、`git diff --check` 和最终 Git 状态，不重复生成或替换已验证 APK。

Gradle 退出码为 0 或生成 APK不等于目标进程和实机行为正确。旧 rc 日志不得自动证明当前 HEAD。

## 18. Checkpoint 与文档同步

文档职责：

- `AGENTS.md`：长期仓库规则
- `docs/DEVIN_A14_CHECKPOINT.md`：当前分支、HEAD、版本、最新绿色验证、阻塞、下一步和实机状态
- `docs/REFACTOR_PLAN_r14.13.md`：r14.13 计划和范围
- `docs/REFACTOR_PROGRESS.md`：r14.13 实际提交与阶段进度
- `docs/VERIFICATION.md`：稳定版本正式验证证据
- `CHANGELOG.md`：用户可见版本变化
- README：当前公开稳定版和安装说明

每完成一个有意义的代码、构建、Git 或实机闭环，必须在同一任务更新 checkpoint。

发生以下情况时同步对应文档：

- versionName/versionCode 变化
- 签名策略或证书变化
- 阶段完成
- 候选 APK 生成
- 实机或日志审计完成
- 计划与实际范围发生偏移
- 当前分支相对 `main` 状态变化

发现文档与代码冲突时，先记录冲突，再以实际代码和验证证据修正文档。不得保留“当前版本”“当前签名行为”等互相矛盾的表述。

## 19. 提交、远端与发布

当前用户指定活跃开发分支时：

- 继续该分支
- 不创建平行分支
- 不直接修改 `main`
- 按根因形成清晰 commit
- 完成闭环后 push 当前已授权开发分支
- 推送后核对远端 HEAD

未经明确要求，不得：

- 创建或合并 PR
- 合并 `main`
- 创建 tag 或 GitHub Release
- 公开上传或替换 APK
- 删除分支或旧 Release
- 将未完成当前 HEAD 实机验证的版本称为稳定版

## 20. 最终报告

只报告高价值事实：

- 当前分支、HEAD、ahead/behind 和工作区
- 证据与根因
- 修改文件和行为变化
- Hook/JVM/API/R8/生命周期影响
- 实际测试与构建
- APK、SHA-256 和签名证书
- commit、push、PR、merge、tag 和 Release 状态
- 已验证、待实机、无法确认
- 同步的 checkpoint、进度和 changelog
