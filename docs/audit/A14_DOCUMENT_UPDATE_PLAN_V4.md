# A14 Document Update Plan v4

```text
DocumentKind: PLAN
Product: A14
Repository: tomthenpc/customiuizer-a14
Branch: devin/a14-rom-intelligence-audit
EvidenceCommit: 59a93b9c36aed293908d87a8a4a09a33e1d06ae7
EvidenceState: STATIC
DeviceEvidence: NOT_EXERCISED
GeneratedBy: v4 audit snapshot
SourceOfTruth: A13_A14_Full_Review_Optimization_FINAL_v4/A14/A14_DOCUMENT_UPDATE_PLAN_V4.md
```

## Snapshot/supersede

- `A14_FRAMEWORK_AUDIT.md`：加 SNAPSHOT/EvidenceCommit，指向 current architecture。
- `A14_SYSTEMUI_LAUNCHER_SMOOTHNESS.md`：冻结旧 commit 或重新生成，纳入 arbiter/gate。
- APK baseline JSON：恢复 P0 hash，停止覆盖 baseline 路径。
- `A14_DEAD_CODE_CANDIDATES.md`：static-only 项降为 `CANDIDATE_STATIC`。

## 新建

```text
docs/DOCUMENT_INDEX.md
docs/architecture/A14_RUNTIME_ARCHITECTURE_CURRENT.md
docs/audit/A14_PROJECT_HEALTH_CURRENT.md
docs/audit/A14_PROJECT_HEALTH_CURRENT.json
docs/audit/A14_GESTURE_EVENT_CONTRACT.md
docs/audit/A14_LIFECYCLE_OWNER_INVENTORY.md
docs/performance/A14_APK_SIZE_DELTA_P0_TO_CURRENT.md
docs/performance/A14_APK_SIZE_DELTA_P0_TO_CURRENT.json
```

## TASK_STATE

- P3 保持 IN_PROGRESS；
- P5 的 pointer contract 作为补充验收；
- 新增 fatal/SystemUI bootstrap/artifact drift finding；
- 回退 commit 记录为学习证据，不计 forward checkpoint；
- CurrentObjective 指向 fatal + SystemUI coordinator。

## SMART state

- unique keys；
- professional mode；
- qualifying count；
- real LastFull；
- CI NOT_CONFIGURED；
- no duplicate failure；
- no state-only checkpoint。
