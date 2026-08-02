# A14 Full Review v4

```text
DocumentKind: SNAPSHOT
Product: A14
Repository: tomthenpc/customiuizer-a14
Branch: devin/a14-rom-intelligence-audit
EvidenceCommit: 00f159b96911f38abe477d15c320d229d99ec7a7
EvidenceState: REMOTE_STATIC_PLUS_RECORDED_BUILD
DeviceEvidence: NOT_EXERCISED
AuditTime: 2026-08-02T11:47:00+08:00
AheadOfMain: 57
GeneratedBy: v4 audit snapshot
Supersedes: docs/audit/A14_FRAMEWORK_AUDIT.md (Round 1 view)
SourceOfTruth: A13_A14_Full_Review_Optimization_FINAL_v4/A14/A14_FULL_REVIEW_V4.md
```

## 完成项

| Phase | 台账 | 复核 |
|---|---|---|
| P0/P1/P2 | COMPLETE | 有实现和机械证据 |
| P3 | IN_PROGRESS | 正确；GenericApp 错误方案被测试拒绝并回退 |
| P4 | COMPLETE | API101/102 静态分类已完成 |
| P5 | COMPLETE | 状态机和压力测试存在；pointer contract 仍需明确 |
| P6-P14 | TODO | 尚未完成 |
| P15 | BLOCKED_EXTERNAL | 正确 |
| P16 | TODO | 正确 |

## 控制状态不足

当前 SMART state：

- duplicate `LastFailureClass`；
- 旧 Mode；
- 缺 `LastQualifyingCheckpoint` / `CurrentObjective`；
- Standard/Deep/Full pending；
- CI pending；
- ResumeTask 未解析。

A14 checker存在，但还不检查：

- Mode；
- checkpoint count 与 TASK 表；
- state-only commit；
- Standard 与 Full 绑定；
- ResumeTask；
- staged final snapshot。

最新 HEAD 更新了 APK JSON，因此需要在最终 state/doc 集合上重新跑 Full。

## 已正确暴露的架构问题

把 GenericApp preference 读取移进 installer 后，被 `RemainingFeaturesWiringTest` 拒绝并回退。

正确方向：

```text
MainModule
→ GenericAppEligibilityResolver
→ immutable GenericAppSelection
→ GenericAppInstaller
```

Resolver 读 prefs，Installer 只执行。

## 算法缺口

### A14-ALG-001：SystemUI bootstrap 仍在 MainModule

仍包含：

- initializer hook；
- context；
- fast-reboot receiver；
- status-bar setup；
- preference watch；
- 10 秒 restart guard。

创建 `SystemUiBootstrapCoordinator` 和显式状态。

### A14-ALG-002：fatal 仍可能被吞

部分 MainModule 代码只单独 rethrow OOM，再 catch Throwable，可能吞掉 ThreadDeath、其他 VirtualMachineError 或包装 fatal。

创建最小共享 helper：

```text
FatalErrors.rethrowIfFatal
FatalErrors.unwrapAndRethrowIfFatal
```

### A14-ALG-003：pointerCount contract 未证明

StateMachine 的 POINTER_UP 保存 `event.pointerCount`；测试人为传 1，生产 adapter 直接传 MotionEvent.pointerCount。

先定义：

```text
raw count
或
post-action active count
```

在 adapter 中唯一归一化并补测试，不能依赖隐含测试约定。

### A14-ALG-004：Gate 热路径中间列表

`commands.filter(::isBusinessEffect)` 仅用于判断是否存在业务命令。

改为显式无中间列表扫描，并要求 `maxFingerprints > 0`。无测量时只声称减少结构性分配。

### A14-ALG-005：Arbiter map 无硬上限

漏掉 UP/CANCEL/detach 时 token 可残留。

增加：

- main-thread contract；
- stale cleanup；
- 最大 active token；
- 满载后拒绝新 claim，不驱逐 live token；
- missing CANCEL 测试。

### A14-ALG-006：owner map 需要生命周期完整证明

GestureMachine 的 snapshot/dependencies/configs 依赖每个 detach 调用 clear。

生成 production attach/detach inventory 和测试。

### A14-ALG-007：observe 计算结果未使用

Status bar intercept 调用 `observe()`，调用方忽略返回值；observe 又执行 config/state-machine，而 gate 对 intercept 不允许 effect。

证明是否需要 consume decision：

- 需要：返回并使用明确 observation decision；
- 不需要：改为廉价 eligibility/no-op；
- 不保留无效热路径计算。

## 文档不足

1. `A14_SYSTEMUI_LAUNCHER_SMOOTHNESS.md` 仍说 gesture 无物理事件去重，与当前 arbiter/gate 冲突。
2. `A14_FRAMEWORK_AUDIT.md` 是 Round 1/P0 语言，缺 EvidenceCommit/superseded。
3. 最新构建覆盖了名为 BASELINE 的 JSON，而 baseline Markdown 仍保存旧 hash。
4. dead-code 文档只有 static evidence，不能直接自动删除。
5. 缺唯一 CURRENT architecture、gesture event contract、lifecycle owner inventory。
6. CI 未配置。

## 推荐顺序

```text
状态/最终 Full
→ baseline/current/delta
→ fatal boundary
→ SystemUI coordinator
→ GenericApp resolver
→ pointer contract
→ observe/gate/arbiter
→ current docs
→ CI
→ P6
```
