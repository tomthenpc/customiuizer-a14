---

`	ext
DocumentKind: CURRENT
Product: customiuizer-a14
Repository: tomthenpc/customiuizer-a14
Branch: devin/a14-rom-intelligence-audit
EvidenceCommit: pending
EvidenceState: STATIC
GeneratedBy: A14_OWNER_V5_MERGE
SourceOfTruth: A13_A14_GOAL_EXTREME_V5/COMMON_LONG_HORIZON_CONSTITUTION_V5.md
`

---

# Long-Horizon Constitution

## 0. 性质

本文件是 A13/A14 的长期工程宪章。产品专属 GOAL 高于本文件；本文件补充长期治理，不扩大产品 Android/ROM 边界。

时间范围不是固定年份，而是：

```text
只要仓库仍被使用、构建或维护，本宪章持续有效
```

## 1. 产品家族角色

```text
A13: LTS_STABILITY_LINE
A14: ACTIVE_STABLE_LINE
Future Android/HyperOS: NEW_REPOSITORY_REQUIRED
```

A13/A14 不直接扩展到下一 Android 大版本。

它们必须输出未来迁移资产：

- architecture contract；
- feature semantics；
- preference schema；
- process/target matrix；
- ROM sample schema；
- regression corpus；
- release metadata schema；
- bootstrap checklist。

## 2. 生命周期状态

```text
ACTIVE_HARDENING
RELEASE_CANDIDATE
STABLE
LTS
SECURITY_ONLY
EXTERNAL_VALIDATION_REQUIRED
ARCHIVE_READY
ARCHIVED
```

### ACTIVE_HARDENING

允许架构修复、性能优化、兼容补强和安全迁移。

### RELEASE_CANDIDATE

机器门禁通过，等待真实设备、签名和最终回归。

### STABLE

有正式设备证据、RC 和已知限制。

### LTS

默认 feature freeze，以稳定、兼容、构建恢复和高优先级修复为主。

### SECURITY_ONLY

只处理：

- P0/P1；
- 构建链失效；
- 严重兼容；
- 安全与供应链；
- 设备 bootloop/fatal。

### ARCHIVE_READY

满足：

- 最终源码可构建；
- 依赖和工具链已归档说明；
- artifacts 和 hashes 可追溯；
- known limitations 完整；
- 不存在未说明 secret；
- 迁移资产已导出。

### ARCHIVED

只读历史状态。不得伪装为继续支持。

## 3. 永久不变量

无论阶段：

```text
no silent fatal swallowing
no wrong-process business install
no duplicate business owner
no false device evidence
no mutable baseline
no secret in repository
no unbounded runtime container
no user feature deletion without owner approval
no current document contradicting current code
no unsupported version marketing
```

## 4. 稳定性契约

建立版本化 schema：

```text
FeatureIdentitySchema
PreferenceSchema
DiagnosticsSchema
ArtifactMetadataSchema
RomSampleSchema
CompatibilityContractSchema
```

要求：

- stable Feature ID 不复用；
- preference key 不静默改变含义；
- 默认值变更必须作为产品行为变更审批；
- 删除 preference 前提供迁移/废弃证据；
- diagnostics 字段向后兼容；
- schema 变更有版本和迁移测试；
- release artifact 能追溯 schema 版本。

## 5. 可靠性与错误预算

### 零容忍

```text
新增 bootloop
新增 system_server fatal
新增 SystemUI restart loop
新增 Launcher restart loop
fatal 被吞
错误进程安装
重复永久 owner
```

### 默认相对性能预算

在固定设备、固定 ROM、固定场景和相同构建类型下：

```text
startup p50/p95: 不得出现无法解释的明显回退
steady memory: 不得出现持续增长或无法释放 owner
jank/frame: 不得出现统计显著恶化
periodic work: 无功能需求时为 0
APK/R8: 超过预算必须归因
```

首次建立基线后，将预算写入产品专属 performance contract。

推荐默认告警线，不作为无基线时的虚假精确承诺：

```text
startup p95 > baseline + max(5 ms, 5%)
steady memory > baseline + max(3 MiB, 8%)
jank rate > baseline + max(1 percentage point, 10%)
develop APK > baseline + 100 KiB
DEX > baseline + 50 KiB
```

任何阈值可由所有者基于设备噪声校准，但必须记录原因。

## 6. 兼容支持等级

每个 ROM build / package version / feature target 标记：

```text
DEVICE_VERIFIED
LOG_VERIFIED
STATIC_CONTRACT_VERIFIED
GUARDED_UNEXERCISED
KNOWN_INCOMPATIBLE
UNKNOWN
```

不得把低等级宣传成高等级。

兼容资产：

```text
RomFingerprint
CompatibilityPack
TargetResolutionReplay
KnownFailureSignature
DeviceScenarioResult
```

ROM 更新后自动：

```text
fingerprint diff
→ contract replay
→ matrix delta
→ risk classification
→ TASK_STATE issue
```

## 7. 运行期架构

永久目标：

```text
entry
→ process router
→ eligibility/enablement
→ phase
→ contract/variant
→ atomic install transaction
→ typed result
→ diagnostics
→ bounded owner
→ deterministic cleanup
```

禁止架构漂移回：

- MainModule 业务堆积；
- installer 重新读散乱 preference；
- 多套 Feature 状态；
- 业务类自行处理 ROM 变体；
- 热路径动态反射；
- 静态持有 Activity/View/Context；
- 未绑定生命周期的线程、协程、Handler 或 ticker。

## 8. 可观测性

不建立联网遥测。

必须提供本地、可脱敏的 diagnostic bundle：

```text
module version
commit
build variant
ROM fingerprint
package/process
Feature result summary
compatibility failures
fatal signatures
owner/lifecycle summary
performance counters
redacted logs
```

要求：

- 默认不采集个人数据；
- 不上传；
- 导出由用户主动触发；
- 路径、账号、通知内容、应用私密数据脱敏；
- diagnostics 不得成为热路径负担；
- schema version 固定。

## 9. 测试极限

除普通单元测试外，逐步建立：

```text
property-like tests
model-based state tests
fault injection
concurrency/reentry tests
long-sequence stress
contract replay
ROM sample replay
legacy-vs-new differential tests
artifact reproducibility test
generated-doc drift test
staged-snapshot test
device scenario matrix
```

对系统路径重点注入：

- class/member missing；
- wrong ClassLoader；
- null/stale Context；
- duplicate callback；
- delayed callback after detach；
- process recreation；
- OOM/ThreadDeath/VirtualMachineError；
- partial installer failure；
- preference unavailable；
- ROM variant ambiguity。

## 10. 供应链与构建

必须：

- pin wrapper/plugin/dependency；
- dependency verification/checksum；
- SBOM；
- license inventory；
- secret scan；
- forbidden binary scan；
- reproducible build investigation；
- immutable build metadata；
- source commit → artifact hash；
- CI logs 和 tool versions；
- no dynamic executable download at runtime。

正式签名永远仓库外。

## 11. 发布与回退

发布阶段：

```text
MACHINE_CANDIDATE
DEVICE_CANDIDATE
SIGNED_RC
OWNER_APPROVED_RELEASE
```

Agent 只能达到前三项，不能公开 Release。

每个 RC 记录：

- commit；
- schema versions；
- supported devices/ROM；
- known limitations；
- artifact hash；
- signing identity summary；
- rollback instructions；
- preference migration；
- device evidence。

紧急 hotfix 必须小、可回退，并补回归测试。

## 12. 文档即代码

文档分：

```text
CURRENT
SNAPSHOT
GENERATED
PLAN
EXTERNAL_CHECKLIST
```

机械数字由生成器维护。

每次 qualifying commit 必须保持：

```text
code
tests
state
generated docs
current architecture
verification evidence
```

属于同一事务。

## 13. 自治维护

Agent 可以持续发现和修复，但禁止 artificial churn。

没有高价值变更时：

```text
验证可复现性
→ 检查依赖和 CI
→ replay contracts
→ review device evidence gaps
→ update current generated facts
→ 不制造代码改动
```

自动任务必须有：

```text
Evidence
Impact
Risk
Acceptance
Rollback
Verification
```

## 14. 跨版本传承

共享但不机械复制：

- Feature semantics；
- diagnostics schema；
- task/evidence schema；
- ROM inventory schema；
- verifier contract；
- performance methodology；
- owner/lifecycle patterns；
- fatal boundary patterns。

必须记录差异：

```text
Android API
ROM package/process
ClassLoader
libxposed capability
preference behavior
feature availability
user-visible semantics
```

跨版本 bug 修复流程：

```text
identify shared semantic bug
→ inspect both implementations
→ patch independently
→ cross-version regression
→ update parity matrix
```

## 15. 新版本仓库启动包

未来 A15/HyperOS2 等必须新建仓库并使用：

```text
PRODUCT_BOUNDARY.md
FEATURE_SEMANTICS_SCHEMA
PREFERENCE_SCHEMA
PROCESS_MATRIX_GENERATOR
ROM_SAMPLE_SCHEMA
CONTRACT_REPLAY_CORPUS
DEVICE_SCENARIO_TEMPLATE
VERIFY_PIPELINE
RELEASE_METADATA_SCHEMA
MIGRATION_DECISION_LOG
```

A13/A14 不承担新版本运行支持，只输出可复用证据与方法。

## 16. 退役

退役不是失败。

进入 ARCHIVE_READY 前：

- 最后一次可复现 Full；
- 工具链锁定；
- dependency mirror/来源说明；
- final SBOM；
- final RC/hash；
- known device matrix；
- unresolved issues；
- successor repository；
- migration notes；
- security limitations。

ARCHIVED 后不得继续宣称 ACTIVE/LTS。
