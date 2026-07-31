# LSPosed 日志离线分析

`tools/analyze_lsposed_log.py` 用于离线分析 CustoMIUIzer A14 的 LSPosed 日志。

本工具**不使用 ADB、网络或 APK 相关功能**，所有输入均来自本地文件。

## 支持输入

- `.txt`、`.log` 文本日志
- `.zip` 压缩包（自动读取其中 `.txt`/`.log`/无扩展名的文件）
- 目录（递归读取其中 `.txt`/`.log`/无扩展名的文件）
- 多文件/多目录同时输入

## 输出格式

在 `--output` 目录中生成：

| 文件 | 说明 |
|------|------|
| `summary.md` | 分析摘要（Markdown） |
| `analysis.json` | 结构化分析数据（JSON） |

## 使用方式

```powershell
# 单文件
python tools/analyze_lsposed_log.py "C:\path\full.log" --output "build\log-analysis\r14-test"

# 多输入
python tools/analyze_lsposed_log.py "log1.txt" "log2.log" "logs/" "archive.zip" --output "build\log-analysis\r14-test"

# 仅输出 JSON
python tools/analyze_lsposed_log.py "full.log" --output "build\log-analysis\r14-test" --format json

# 指定仓库根目录以获取源码类建议（默认当前仓库根目录）
python tools/analyze_lsposed_log.py "full.log" --output "build\log-analysis\r14-test" --repo-root "."
```

## 分析内容

- **A14 marker**：模块 `CustoMIUIzer r14` 在各进程的加载标记
- **process**：各进程日志行数、PID、事件分布
- **system_server / SystemUI / Launcher / Settings / SecurityCenter**：关键进程的事件统计
- **HookDiagnostics**：阶段汇总（installed、missing、failed、DexKit、preferences 等）
- **Preference 状态**：`UNAVAILABLE`、`LOADED`、`VALID_EMPTY`、`EMPTY_PENDING` 等状态
- **missed / deferred / restart required**：偏好未就绪、延迟初始化、需要重启等事件
- **Receiver active / stale**：注册、替换、stale 队列、unregister 失败
- **Class / Method / Field missing**：缺失的类、方法、字段
- **DexKit**：DexKit 查询失败/无匹配
- **crash / ANR**：崩溃、ANR、Watchdog
- **重复指纹**：按标准化指纹聚合重复事件
- **源码类建议**：对模块栈或缺失目标，给出可能对应的源码文件

## 设计约束

- **流式读取**：逐行扫描，不一次性将多 GB 日志加载到内存。
- **容量有界**：事件列表、指纹表、上下文缓冲区均设置上限，超出后计入 `overflow`。
- **纯离线**：仅读取本地文件与仓库源码，不涉及设备、网络或 APK 构建。
