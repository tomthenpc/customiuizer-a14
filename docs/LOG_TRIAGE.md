# LSPosed 日志分诊规程（给 agent 的执行手册）

> 本文件是**操作规程**，不是背景介绍。拿到一份 `full.log` 时按这里做，不要自创流程。
> 工具说明见 `docs/LSPOSED_LOG_ANALYSIS.md`，规则背后的缺陷见 `docs/RUNTIME_INVARIANTS.md`。

---

## 0. 三条硬规则

**规则 1：绝对不要把原始日志读进上下文。**

一份 LSPosed `full.log` 通常 10 万～100 万行、几十 MB。把它读进来会：

- 烧掉整个上下文窗口，后面没有余量做真正的分析；
- 让你只能看到日志的**一小段**，却以为自己看完了；
- 得出比脚本更差的结论 —— 脚本会跨全文做指纹归并，人眼滚动不会。

**用 `Read` 打开 `full.log` 就是操作错误。** 用脚本。

**规则 2：先跑脚本，再决定要不要看任何一行原文。**

```bash
python tools/analyze_lsposed_log.py "<full.log 路径>" --profile a14 --repo-root . --output "build/log-analysis/<标签>"
```

1614 行样本耗时 0.8 秒；十万行量级也是秒级，因为它是**流式**的，不会把日志装进内存。

**规则 3：包名出现 ≠ 是我们的问题。**

ROM 每天都在自己的日志里写我们的 applicationId —— 启动 Activity、SmartPower、包管理、最近任务。
**代码被点名才是证据，包名被提到只是上下文。** 详见 §3。

---

## 1. 标准流程（正常情况 4 步，5 分钟内）

| 步 | 动作 | 读什么 |
| --- | --- | --- |
| 1 | 跑脚本 | 终端输出的 `Candidates: N` |
| 2 | `Read summary.md` | 约 25 行 |
| 3 | 若 P0/P1 全为 0 → **到此为止**，报告"未发现模块级异常" | — |
| 4 | 有 P0/P1 → `Read candidates.tsv`，再按需 `Read contexts.log` | contexts 只含 P0/P1/P2 |

`contexts.log` 现在**只写 P0/P1/P2**。干净日志下它只有一行 `No P0/P1/P2 candidates`（59 字节）；
有真实发现时也只有几十 KB。**如果它很大，说明真的有很多待查项，不是噪声。**

### 先确认这份日志来自哪个构建

模块在每个进程加载时会打一行：

```
CustoMIUIzer r14.13.5 (183) loaded in com.android.systemui
```

**没有这一行 = 模块没加载进那个进程**，先查作用域和 LSPosed 启用状态，不要往下分析业务问题。
版本号对不上你正在改的代码，说明用户装的不是这个构建，**先说清楚再分析**。

---

## 2. summary.md 怎么读

只看这几项，其余略过：

| 字段 | 判读 |
| --- | --- |
| `模块加载` | `失败` → 直接查 `java_init.list` / `module.prop` / 作用域，其余都是次要问题 |
| `P0` / `P1` | 都是 0 → 结束。非 0 → 进入 §3 归因 |
| `system_server 崩溃` | `是` → **最高优先级**，这是重启设备级事故 |
| `SystemUI / Launcher 崩溃` | `是` → 高优先级 |
| `Hook 失败` | `是` → 通常是 ROM 版本差异，看是哪个 hook |
| `RemotePreferences 异常` | `是` → 配置链断了，模块会跑在空配置上 |
| `时间范围` | 和用户描述的操作时间对得上吗？对不上说明抓错了时段 |

---

## 3. 归因：这是不是我们的 bug

按证据强度从高到低。**没有到"代码级"就不要下结论说是模块的问题。**

| 强度 | 形态 | 判定 |
| --- | --- | --- |
| **代码级** | 栈帧里有 `at tv.withaibuild.customiuizer.…` | 我们的代码在栈上 → 是我们的 |
| **日志级** | tag 是 `LSPosed-Bridge` 且消息含模块类名 | 我们自己打的日志 → 大概率是我们的 |
| **进程级** | 我们 hook 的进程崩溃，但栈里没有我们 | **可疑但未证实**，需要"停用模块后复测消失"才能定性 |
| **提及级** | 只是消息里出现了 applicationId | **不是证据**。ROM 日常行为 |

脚本已经按这个层级打分（`module_evidence()`）。你在 `contexts.log` 里做的事只是**复核**，不是重新判断。

### 常见的"看着像、其实不是"

这些每份日志都有，**不要报告**：

- `SmartPower.DisplayPolicy: …tv.withaibuild.customiuizer.r14…` —— 电源策略在记录前台应用
- `PackageConfigPersister: App-specific configuration not found for packageName: …` —— 正常
- `ActivityManagerWrapper: getRecentTasks: …` —— 最近任务在列包名
- `ApkAssets: Deleting an ApkAssets object … with 1 weak references` —— 资源正常回收
- `avc: denied` —— SELinux 审计，除非紧邻我们的崩溃
- 任何只有 `W`/`E` 等级、但没有异常类型也没有我们代码的行

---

## 4. 优先级的语义

`priority` 是**严重度**，不是频次。

- 一条无害日志重复 200 次，仍然是 P4；
- 一次 `system_server` 崩溃只出现 1 次，仍然是 P0。

`count` 单独表示出现次数。**不要因为 count 大就提高关注度** —— 高频往往正是噪声的特征。

| 优先级 | 含义 | 该做什么 |
| --- | --- | --- |
| P0 | 代码级证据 + 崩溃/严重信号 | 必须查清并给出根因 |
| P1 | 代码级或日志级证据 | 查清 |
| P2 | 有异常但证据弱 | 看一眼，多数可排除 |
| P3/P4 | 噪声 | 不看。需要时查 `noise-stats.tsv` |

---

## 5. 增量比较（回归验证用）

用户复测新构建后，用上一轮的签名库做差分，只看**新增**的：

```bash
python tools/analyze_lsposed_log.py "<新 full.log>" --profile a14 --repo-root . \
  --output "build/log-analysis/after" --baseline "build/log-analysis/before/signatures.json"
```

这是验证"某个修复是否真的生效"的正确方式 —— 比对签名，而不是比对两份日志的文本。

---

## 6. 报告格式

只报这些，不要贴大段日志：

1. **构建**：日志里的 `CustoMIUIzer <版本> (<versionCode>)`，与预期是否一致
2. **模块加载**：进入了哪些进程，有没有缺失
3. **结论分档**：P0/P1 各几条，分别是什么
4. **每条 P0/P1**：指纹、进程、首末时间、`count`、根因、以及**证据强度属于哪一级**
5. **明确排除的**：哪些看着像但已确认是 ROM 噪声
6. **无法从日志判定的**：需要哪些额外信息或复现步骤

**不要**输出脚本已经归并掉的重复行；**不要**把 `contexts.log` 整段贴出来。

---

## 7. 这套规程为什么长这样

第一版脚本对整条消息做包名子串匹配，命中就 +100 分，并且**分数按出现次数累加**。
在一份真实日志上的结果是：

| | 修复前 | 修复后 |
| --- | --- | --- |
| P0 数量 | 16（全部是 ROM 噪声） | 1（正是注入的模块崩溃） |
| 最高分项 | 无害日志重复 197 次，9875 分 | `NoSuchFieldError at …SystemUIStatusBarHooks`，255 分 |
| `contexts.log` | 721 KB | 干净日志 59 字节 / 有发现时 59 KB |

agent 拿到 16 个假 P0，就会去翻 721 KB 的上下文，然后往往退回去读原始日志 —— 慢就慢在这里，
而且结论还不可靠。**修的是打分算法，不是让 agent"再仔细一点"。**

对照实验保存在 `tools/analyze_lsposed_log.py` 的行为里：同一份日志，只往末尾追加一段
带 `at tv.withaibuild.customiuizer.…` 栈帧的崩溃，就应该恰好多出 1 个 P0。
改动打分逻辑后**必须重跑这个对照**，确认真实缺陷仍然是 P0、噪声仍然是 P4。
