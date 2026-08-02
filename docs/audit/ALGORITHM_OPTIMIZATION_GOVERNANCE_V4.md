# Algorithm Optimization Governance v4

```text
DocumentKind: EXTERNAL_CHECKLIST
Product: A14
Repository: tomthenpc/customiuizer-a14
Branch: devin/a14-rom-intelligence-audit
EvidenceCommit: 59a93b9c36aed293908d87a8a4a09a33e1d06ae7
EvidenceState: STATIC
DeviceEvidence: NOT_EXERCISED
GeneratedBy: v4 audit snapshot
SourceOfTruth: A13_A14_Full_Review_Optimization_FINAL_v4/ALGORITHM_OPTIMIZATION_GOVERNANCE_V4.md
```

## 优化优先级

```text
correctness
→ fatal/runtime safety
→ lifecycle ownership
→ algorithmic complexity
→ measured hot-path allocation
→ blocking/I/O/reflection
→ memory bounds
→ APK/R8
→ cosmetic simplification
```

## 证据

优化前至少有：

- O(n) 或重复扫描；
- 热路径分配；
- 帧敏感路径反射/I/O/锁；
- 无界容器；
- 重复调用计数；
- APK/R8 delta；
- failing regression；
- 静态不变量。

优化后证明：

- 行为等价；
- disabled path 不更重；
- wrong process/phase 不做业务工作；
- fatal 不被吞；
- 容器有界；
- owner 可释放；
- focused tests；
- Fast/必要时 Full。

## 俄罗斯系统代码纪律

- 显式分支和状态；
- 一个功能一个生产入口；
- owner/process/phase/ClassLoader 明确；
- 资源上限明确；
- 冷路径允许复杂，热路径必须直接；
- 不建立多层 facade/service locator；
- 不为一行复用创建框架；
- 不把用户功能当 dead code；
- Java/JVM/反射边界可保留；
- Kotlin 迁移必须改善行为清晰度或资源安全；
- 一个 patch 解决一个可证明问题。

## 复杂度记录

```text
BeforeComplexity
AfterComplexity
BeforeAllocations
AfterAllocations
ContainerBound
ColdPath
HotPath
FailureMode
RegressionTests
```

无数据时不得声称性能提升。
