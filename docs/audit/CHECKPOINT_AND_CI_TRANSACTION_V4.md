# Checkpoint and CI Transaction v4

```text
DocumentKind: PLAN
Product: A14
Repository: tomthenpc/customiuizer-a14
Branch: devin/a14-rom-intelligence-audit
EvidenceCommit: 59a93b9c36aed293908d87a8a4a09a33e1d06ae7
EvidenceState: STATIC
DeviceEvidence: NOT_EXERCISED
GeneratedBy: v4 audit snapshot
SourceOfTruth: A13_A14_Full_Review_Optimization_FINAL_v4/CHECKPOINT_AND_CI_TRANSACTION_V4.md
```

## 1. 已暴露的错误模式

```text
验证通过
→ 修改 TASK_STATE / SMART_OPERATION_STATE / generated docs
→ commit/push
→ 远程 HEAD 与已验证文件集合不同
```

## 2. Qualifying checkpoint transaction

严格顺序：

```text
1. 代码、测试、工具、文档完成
2. 更新 TASK_STATE
3. 更新 SMART_OPERATION_STATE
4. 重新生成受影响文档
5. automation-state checker
6. document-contract checker
7. targeted verification
8. Fast；达到阈值时 Full
9. git diff --check
10. 精确 stage
11. staged snapshot checker
12. 在 staged snapshot 上重新运行只读门禁
13. commit
14. push exact branch
15. 确认 remote HEAD 等于本地 commit
16. CI 已配置时等待并自动修复
17. 开始下一目标
```

第 8 步后再修改状态或生成文档，必须重跑门禁。

## 3. 新工具

```text
tools/check_staged_snapshot.py
tools/tests/test_check_staged_snapshot.py
scripts/checkpoint.ps1
```

检查：

- secrets/APK/签名/本机配置；
- state/docs/source 是否同一事务；
- state-only commit 不得标 qualifying；
- generated output 与 staged source；
- commit trailer；
- baseline 文件不可被普通 current build 修改；
- 失败状态不得记录为通过。

`checkpoint.ps1` 只编排，不自动选择业务改动，不执行 destructive Git。

## 4. Commit trailers

```text
Task: P3.2
Objective: SYSTEMUI_BOOTSTRAP_OWNER
Automation-Checkpoint: qualifying
Verification: targeted,fast
Full-Verification: pass | not-required
Docs: current
CI: NOT_CONFIGURED | PENDING | PASS
Device-Evidence: NOT_EXERCISED
```

## 5. CI

### Fast

精确授权分支 push：

- Python syntax/tests；
- automation checker；
- document checker；
- generated drift；
- Fast verifier；
- compile/unit/lint。

### Full

`workflow_dispatch`、schedule、重大阶段：

- Full verifier；
- assemble；
- A14 develop/R8；
- APK current/delta；
- artifact metadata；
- logs；
- 不签名、不发布、不读取 secrets。

### Concurrency

```text
group: <product>-<branch>
cancel-in-progress: true
```

## 6. CI 状态

只允许：

```text
NOT_CONFIGURED
PENDING
PASS
FAIL
UNAVAILABLE
```

没有 workflow 时必须是 `NOT_CONFIGURED`。
