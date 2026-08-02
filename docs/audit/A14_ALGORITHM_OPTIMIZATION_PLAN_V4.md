# A14 Algorithm Optimization Plan v4

```text
DocumentKind: PLAN
Product: A14
Repository: tomthenpc/customiuizer-a14
Branch: devin/a14-rom-intelligence-audit
EvidenceCommit: 59a93b9c36aed293908d87a8a4a09a33e1d06ae7
EvidenceState: STATIC
DeviceEvidence: NOT_EXERCISED
GeneratedBy: v4 audit snapshot
SourceOfTruth: A13_A14_Full_Review_Optimization_FINAL_v4/A14/A14_ALGORITHM_OPTIMIZATION_PLAN_V4.md
```

## A. 最终状态事务

修 SMART/checker，更新 state 后重跑 Full，验证 staged snapshot。

## B. APK 证据

冻结：

```text
A14_APK_SIZE_BASELINE_P0_DEBUG.json
A14_APK_SIZE_BASELINE_P0_DEVELOP.json
```

生成：

```text
A14_APK_SIZE_CURRENT_DEBUG.json
A14_APK_SIZE_CURRENT_DEVELOP.json
A14_APK_SIZE_DELTA_P0_TO_CURRENT.json
A14_APK_SIZE_DELTA_P0_TO_CURRENT.md
```

## C. SystemUI coordinator

状态：

```text
UNINITIALIZED
HOOK_INSTALLED
CONTEXT_READY
BASE_READY
PREFERENCE_READY
COMPLETE
FAILED_TRANSIENT
```

Coordinator 拥有 initializer/context/receiver/statusbar/watch/restart policy/retry。

MainModule 只 resolve + dispatch。

## D. Generic app selection

```text
GenericAppSelection(
  launcher,
  statusBarColor,
  noOverscroll,
  mediaControl
)
```

Pure resolver 读 prefs；installer 接受 selection。

## E. Gesture

1. trace MotionEvent adapter；
2. 定义 pointer contract；
3. adapter tests；
4. Gate 无中间 list；
5. Arbiter stale cleanup + bound；
6. owner lifecycle inventory；
7. resolve unused observe computation。

## F. Fatal boundary

机械检查所有 `catch(Throwable)`：

- rethrow fatal；
- 使用 guarded helper；
- 或带理由 allowlist。

## G. 文档与 CI

document checker、current architecture、performance delta、Fast CI、scheduled/manual Full。
