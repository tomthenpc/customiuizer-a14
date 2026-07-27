# LSPosed 大型日志快速分析

本项目使用 `tools/analyze_lsposed_log.py` 统一处理 LSPosed `full.log`，避免直接人工通读十万行日志。

## 使用方式

```powershell
python tools/analyze_lsposed_log.py `
  "C:\path\full.log" `
  --profile a14 `
  --repo-root "." `
  --output "build\log-analysis\r14-test"
```

## 输出产物

| 文件 | 说明 |
|------|------|
| `summary.md` | 日志摘要、P0/P1/P2 数量、模块加载情况、最终结论 |
| `candidates.tsv` | 归并后的候选问题，含优先级、风险分、次数、进程、tag、异常、指纹 |
| `contexts.log` | P0/P1 及必要 P2 的上下文片段 |
| `noise-stats.tsv` | 高频噪声签名统计 |
| `signatures.json` | 标准化签名数据库，用于后续增量比较 |
| `parser-stats.json` | 解析统计（大小、行数、可解析行数、时间范围等） |

## 分析原则

- 最多顺序扫描原日志两遍，不一次性加载进内存。
- 按指纹聚合重复异常，不把大量重复输出交给人工。
- 优先保留包含模块源码、Hook 失败、崩溃、ANR、system_server/SystemUI/Launcher 异常的上下文。
- 仅凭 `E`/`W` 等级不能判定为本模块问题。
- 归因需要：模块堆栈、Hook 目标与异常的因果关系、模块日志紧邻异常、或修复后复测消失。

## 增量比较

```powershell
python tools/analyze_lsposed_log.py `
  "C:\path\new\full.log" `
  --profile a14 `
  --baseline "build\log-analysis\last\signatures.json" `
  --output "build\log-analysis\current"
```

## 配置文件

- `tools/analyze_lsposed_log.py` 内置 A14/A13 profile、异常锚点、噪声规则。
- 可选 `tools/lsposed_log_profiles.json` 和 `tools/lsposed_noise_rules.json` 进行覆盖扩展。

## 输出目录

中间产物输出到 `build/log-analysis/<日志名>/`，该目录由 `.gitignore` 的 `build/` 规则自动忽略，不得提交。
