# Claude 启动合同：A14 现有代码质量、稳定性与性能推进

## 1. 开始

在仓库根目录执行：

```powershell
git branch --show-current
git status --short
git rev-parse HEAD
git fetch origin
```

目标分支应为 `codex/a14-claude-audit-r14.20.0`。先完整阅读 `AGENTS.md`、
`PROJECT.md`、`ARCHITECTURE.md` 和 `WORKFLOW.md`，然后以当前源码、测试、构建结果和日志
为真实状态开始工作。

## 2. 总目标

自由检查和改进当前 A14 已有实现，把代码继续推向：

- 更高稳定性，尤其避免 `SystemUI`、`Launcher` 和 `system_server` 崩溃、ANR、泄漏与状态错乱；
- 更高性能，减少高频 Hook/回调中的分配、反射、锁竞争、阻塞、重复计算和偏好读取；
- 更低内存与更低后台占用，让关闭的功能尽可能不初始化、不注册、不扫描、不持有对象；
- 更直接、清晰、可维护的代码，在必要处消除重复状态、脆弱时序和过度复杂实现；
- 保持现有用户行为、数据兼容、HyperOS 1 / Android 14 边界和 libxposed API 101 基线。

不新增用户功能。除此之外，不预设固定模块、固定检查顺序或固定改动规模；由 Claude 根据真实
风险和收益自行选择切入点，并完成“发现问题 → 修改 → 测试 → 修正 → 完整验证”的闭环。

## 3. 自主权限

在上述目标内，可以自行：

- 修改生产代码、测试、工具和长期文档；
- 修复确定或高置信度的缺陷、竞态、生命周期、兼容性和资源问题；
- 优化已有功能的实现，包括热路径、启动路径、缓存、对象所有权和状态发布；
- 做有充分理由的局部重构、去重、简化、拆分或合并；
- 添加能证明问题和防止回归的测试、基准或静态门禁；
- 删除已经确认无入口、无反射/R8/资源依赖且没有兼容责任的内部死代码；
- 更新过时或与当前实现矛盾的文档，但不要恢复已经删除的旧执行流程；
- 在同一分支提交多个小而完整、可验证的 commit；普通技术决定不必逐项询问用户。

优先做实际价值高、证据清楚、能验证的改动。若一个更大但合理的重构能明显降低崩溃、内存或
持续运行成本，也可以实施，但要用测试和调用链证明行为保持，而不是只凭代码风格判断。

## 4. 不可突破的边界

自由发挥不等于改变产品范围。必须保持：

- 只支持 HyperOS 1 / Android 14 / SDK 34 / `arm64-v8a`；
- applicationId 为 `tv.withaibuild.customiuizer.r14`；
- libxposed API 101 是最低生产路径，API 102 专属能力保持隔离和安全降级；
- 不增加设置项、页面、Hook 目标、用户可见能力或 Android/ROM 支持范围；
- 不静默改变现有功能语义、持久化数据格式兼容、Hook 时序、参数改写或原方法调用次数；
- callback 最外层沿用项目 fatal 边界，`OutOfMemoryError` 等 fatal 不得被吞掉；
- 不用宽泛 fallback、删除测试、降低断言或掩盖异常来制造通过；
- 不把静态检查、编译、lint 或 JVM 单元测试当作 HyperOS 1 实机证明；
- 不提交 APK、keystore、密码、本地签名配置，不自动创建 Release/Tag 或公开上传产物；
- 不创建额外审计/实现分支，所有工作留在当前唯一开发分支。

如果现有行为本身就是已证实缺陷，可以修正；报告中说明旧行为、问题证据和新行为。

## 5. 合并基线

- `main`：`3f9d6ba6a73018e05a7085962f7eaf5197259c2b`
- 合并后的生产代码基线：`a83ccd687ad564718b2b6eaa78bbcece267f133f`
- 工作分支：`codex/a14-claude-audit-r14.20.0`

旧开发分支的 tip 已逐一证明是上述生产代码基线的祖先，相对该基线遗漏提交数均为 `0`：

| 来源 | 已纳入 tip |
| --- | --- |
| `codex/a14-device-runtime-polish-r14.20.0` | `ae65ec264fa523952cd48eb0910b46f5493760fc` |
| `codex/a14-device-runtime-statusbar-geometry-r14.20.0` | `086cdbd4df427f1c9a3e1a5ea729f0bea1ae3500` |
| `codex/recovered-charging-info-style-r14.20.0` | `0ef0b68aa8098b035a0a91907ab8f43b4783b7c6` |
| `devin/a14-architecture-c-r14.20.0` | `e926ce9591b4de42867f0f37c1c4d2a4999c2a7a` |
| `devin/a14-optimization-r14.18.8` | `aff0449b49e183475ec48a9c160238db344416be` |
| `devin/a14-production-quality-r14.18.8` | `2c4efeafc8655855b824b72ecbf6106641b04a8e` |
| `devin/a14-runtime-primitives-r14.20.0` | `85e33243929a9853a0b5a787865c108f35d80959` |
| `devin/a14-settings-maintenance-r14.20.0` | `6f99dd7688882a7ccd2e95a145f46b709329eb72` |
| `devin/a14-strong-toast-geometry-r14.20.0` | `b0c28b0dbcd70a24b877fbc274f454c531ef606b` |

旧分支名只记录来源。不要切回旧分支或以旧审计文档覆盖当前源码事实。

## 6. 高价值方向

以下是起点而不是限制，可根据证据调整顺序：

1. 系统关键进程的崩溃、fatal 传播、Hook 目标误判、重复安装和错误进程初始化；
2. Receiver、Observer、listener、controller、View/Activity 的所有权、替换、释放和 stale callback；
3. 并发发布、初始化顺序、线程可见性、竞态、锁范围和跨 ClassLoader 状态；
4. 状态栏高度、StrongToast、时钟、电池、通知展开、音频可视化、Launcher 手势/最近任务、
   模糊、动态岛、设置备份/恢复等近期合并实现；
5. 高频回调中的临时对象、装箱、集合/lambda、反射、字符串格式化、日志、同步 Binder、
   磁盘 I/O、重复 preference 查询和可避免的主线程工作；
6. 无界或跨 ClassLoader 污染的缓存，静态持有短生命周期对象，已关闭功能仍持续运行；
7. legacy 输入、备份数据、反射、DexKit、R8、Manifest、资源和动态入口的边界与失败语义；
8. 重复实现、分散状态和过度抽象导致的正确性或持续成本问题。

不要为了 findings 数量制造问题。性能改动应说明路径频率或成本依据；没有设备测量时可以依据
明确的热路径和操作成本实施低风险优化，但只能报告静态/测试证据，不能声称已测得提速、省电
或 PSS 降低。

## 7. 工作方式

1. 先确认干净基线并运行一次完整门禁，区分原有失败与新改动。
2. 用 `git diff main...HEAD`、真实 Feature/installer/Hook 调用链、测试和日志定位候选。
3. 自主选择最高价值的一小批，先写或补能暴露问题的测试，再做最小充分修改。
4. 运行针对性测试和 `python tools/verify.py fast --changed`，失败就在同一批次修复。
5. 检查最终 diff 是否包含行为意外变化、热路径新增成本、生命周期缺口和假测试。
6. 一批完成后可提交，再继续下一批；避免长期只写预审文档而不改善代码。
7. 收口时运行完整门禁并汇总仍需实机或 ROM 证据的项目。

常用命令：

```powershell
git diff --stat main...HEAD
git log --first-parent --oneline main..HEAD
python tools/verify.py fast --changed
python tools/verify.py full
git diff --check
```

若工具目录有改动：

```powershell
python -m compileall tools
python -m unittest discover -s tools/tests -p "test_*.py"
```

可以使用额外的静态分析、基准、测试脚本或设备日志工具，只要结果可复现且不会伪装证据等级。

## 8. 每批完成标准

- 问题有真实入口、触发条件、影响和证据；
- 改动直接解决根因，没有顺手增加功能或无关重排；
- 现有用户行为和持久化数据兼容得到说明与测试保护；
- 关键 Hook 的时序、参数、返回值和原方法调用次数得到核对；
- 生命周期、并发、ClassLoader、反射/R8 和 API 101/102 边界按相关性检查；
- 针对性测试能防止回归，完整门禁通过；
- 静态、测试、日志、设备证据清楚分级；
- 工作区没有未解释修改。

## 9. 汇报

自由选择最有用的文档形式；可以维护一个简洁工作日志，也可以直接更新相关长期文档和任务记录。
最终回复至少包括：

- Base SHA、Final SHA 和 commits；
- 找到的根因，以及具体修改了什么；
- 对稳定性、性能、内存或后台占用的预期影响；
- 实际运行的测试和门禁；
- 哪些结论只有静态/测试证据，哪些有日志或设备证据；
- 尚未解决但值得下一批继续的问题，按实际价值排序；
- 最终 `git status --short`。

如果一轮检查没有值得改的高置信问题，可以如实停止并说明覆盖范围；如果仍有明确高价值问题，
可以在当前分支继续下一批，不需要创建额外 Review、Audit 或 Implement 任务。
