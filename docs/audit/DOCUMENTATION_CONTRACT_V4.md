# Documentation Contract v4

```text
DocumentKind: EXTERNAL_CHECKLIST
Product: A14
Repository: tomthenpc/customiuizer-a14
Branch: devin/a14-rom-intelligence-audit
EvidenceCommit: 59a93b9c36aed293908d87a8a4a09a33e1d06ae7
EvidenceState: STATIC
DeviceEvidence: NOT_EXERCISED
GeneratedBy: v4 audit snapshot
SourceOfTruth: A13_A14_Full_Review_Optimization_FINAL_v4/DOCUMENTATION_CONTRACT_V4.md
```

## 1. 文档类型

每份架构、审计、性能、ROM、验证和维护文档必须明确属于：

```text
CURRENT
SNAPSHOT
GENERATED
EXTERNAL_CHECKLIST
PLAN
```

禁止把历史快照、当前事实和未来计划混在同一份文档。

## 2. 必需元数据

标题后加入：

```text
DocumentKind:
Product:
Repository:
Branch:
EvidenceCommit:
EvidenceState: STATIC | BUILD | CI | DEVICE | MIXED
GeneratedBy:
SourceOfTruth:
Supersedes:
DeviceEvidence:
```

规则：

- `CURRENT` 只描述当前 HEAD 可复现事实。
- `SNAPSHOT` 不允许被后续构建覆盖，文件名应包含里程碑或 commit。
- `GENERATED` 中的机械数字不得手工维护。
- `EXTERNAL_CHECKLIST` 不得声称已执行。
- `PLAN` 不得被自动化当作已完成事实。

## 3. Baseline/current/delta 分离

性能、APK、inventory、ROM matrix 必须使用：

```text
*_BASELINE_<MILESTONE>_<VARIANT>.json
*_CURRENT_<VARIANT>.json
*_DELTA_<BASELINE>_TO_CURRENT.json
*_DELTA_<BASELINE>_TO_CURRENT.md
```

Baseline 记录后不可覆盖。

Delta 必须包括：

```text
baselineCommit
currentCommit
baselineArtifactSha256
currentArtifactSha256
absoluteDelta
percentageDelta
topContributors
explained
```

## 4. 唯一 CURRENT 架构文档

```text
docs/architecture/A13_RUNTIME_ARCHITECTURE_CURRENT.md
docs/architecture/A14_RUNTIME_ARCHITECTURE_CURRENT.md
```

必须覆盖：

- module entry；
- process routing；
- installer/coordinator；
- feature lifecycle；
- diagnostics；
- fatal boundary；
- lifecycle owner；
- ClassLoader；
- ROM/API；
- verification。

旧审计改为 SNAPSHOT 并指向 CURRENT。

## 5. 文档索引

创建 `docs/DOCUMENT_INDEX.md`：

```text
Path
Kind
Owner
EvidenceCommit
Generated
Current
SupersededBy
DeviceState
```

同一主题只能有一个 `Current=true`。

## 6. 生成文档

头部包含：

```text
GeneratedFromCommit
Generator
GeneratorVersion
InputPaths
InputDigest
GeneratedAt
```

生成器必须支持：

```text
--check
--write
```

`--check` 发现 drift 时非零退出，不能修改文件。

## 7. 文档门禁

创建：

```text
tools/check_document_contracts.py
tools/tests/test_check_document_contracts.py
```

至少检查：

- metadata；
- branch；
- EvidenceCommit 存在且是 HEAD 祖先；
- CURRENT 唯一；
- SNAPSHOT 不被覆盖；
- GENERATED 通过 `--check`；
- stale branch/status；
- baseline/current hash；
- COMPLETE 阶段所需文档；
- 引用路径；
- 手工重复维护的机械数字。

接入 Fast verification。

## 8. CURRENT 文档禁用表述

```text
等待当前 Agent 重新验证
尚无 checkpoint（已有 qualifying commit 时）
Registry 仍与 legacy dispatcher 并存（收口后）
旧授权分支
完成后等待 owner
用 current build 覆盖 baseline
```

历史事实只能留在带 EvidenceCommit 的 SNAPSHOT。
