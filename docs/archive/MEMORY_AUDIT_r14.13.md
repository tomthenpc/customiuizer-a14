# 内存占用审计 r14.13

> 本文档记录 CustoMIUIzer A14 模块在不同场景下的内存采样方法、对照实验设计、脚本使用说明以及结果模板。

## 1. 审计目标

用户反馈“刚开机不久时，感觉当前版本内存占用比之前高”。需要通过同条件测量区分以下可能：

- 开机后正常缓存预热；
- 设置应用自身内存高；
- SystemUI、Launcher 或 `system_server` 中模块额外占用；
- 一次性高水位；
- 随时间持续增长（泄漏）；
- Activity/Fragment 重建泄漏；
- Receiver/Observer/Coroutine 重复注册；
- Kotlin 迁移后对象分配增加；
- R8 / 资源 / ClassLoader 行为变化；
- 系统自身波动。

## 2. 采样对象

| 目标进程 | 说明 |
| --- | --- |
| `tv.withaibuild.customiuizer.r14` | 模块设置应用 |
| `system_server` | 模块作用域所在的系统进程 |
| `com.android.systemui` | 状态栏、通知、控制中心 |
| `com.miui.home` | 桌面、最近任务、手势 |

如果用户启用了作用于其他进程的功能（如 `com.miui.securitycenter`），按需增加。

## 3. 采样时序

每个实验至少采样三次：

| 时间点 | 条件 |
| --- | --- |
| T0 | 开机完成后约 1 分钟 |
| T1 | 开机完成后约 5 分钟 |
| T2 | 开机完成后约 15 分钟 |

随后执行固定操作并再次采样：

1. 打开设置应用；
2. 进入 About；
3. 连续执行 10 轮语言切换和 Activity 重建；
4. 返回首页；
5. 强制停止设置应用；
6. 重启 SystemUI；
7. 重启 Launcher；
8. 展开和收起通知栏、控制中心；
9. 使用用户已启用的状态栏功能；
10. 熄屏和亮屏；
11. 再采样；
12. 静置后再次采样。

每个场景至少重复三次。

## 4. 工具

### 4.1 采样脚本

```powershell
.\tools\capture-memory-baseline.ps1 -Scenario "T0_boot_1min" -Samples 3 -DelaySeconds 5
```

参数：

- `-Scenario`：场景名，用于输出目录和 JSON 文件名。
- `-Samples`：每个目标连续采样次数。
- `-DelaySeconds`：两次采样间隔。
- `-Targets`：目标进程/包名数组。
- `-OutDir`：输出目录，默认 `.devin/memory-audit`。

输出：

- `raw/<Scenario>/<target>/sample_<n>/` 下的原始 ADB 输出；
- `summary_<Scenario>.json` 中的解析后数值。

### 4.2 比较脚本

```powershell
python tools/compare-memory-baseline.py `
    --baseline .devin/memory-audit/summary_baseline_disabled.json `
    --current .devin/memory-audit/summary_current_user_config.json `
    --output .devin/memory-audit/comparison.md
```

输出 Markdown 表格，包含中位数 PSS、RSS、Java heap、native heap、private dirty、FD 数差异，并高亮超过阈值的差值。

## 5. 对照实验设计

| 实验 | 含义 |
| --- | --- |
| A. 模块禁用 | LSPosed 中禁用模块，作为基线 |
| B. 当前版本，用户实际配置 | 待测版本，使用用户当前配置 |
| C. 较早稳定基线 | 优先 `r14.12.0` |
| D. 当前版本，全部可选功能关闭 | 排查是否是特定功能导致 |

历史版本使用独立 Git worktree 构建，同一份本地测试签名，避免污染当前工作区。

### 5.1 版本切换流程

1. 自动备份 SharedPreferences；
2. 记录 LSPosed/Vector 作用域；
3. 卸载测试版本；
4. 安装对照版本；
5. 恢复相同设置；
6. 完整重启；
7. 完成采样后恢复当前版本。

原始配置和备份不提交到仓库。

## 6. 判定标准

### 6.1 可接受

- 开机后 PSS 上升，随后稳定；
- 首次打开页面产生缓存，退出后保留有限稳定缓存；
- 当前版与基线差异处于系统噪声范围；
- 多次重建后对象和内存不持续线性增长。

### 6.2 不可接受

- 每次语言切换后 Activity/Fragment/View 数持续增加；
- 每次 SystemUI 重建后 View、Receiver、Observer 或 Runnable 增加；
- 10 轮操作后 PSS/Private Dirty 持续上升且无法回落；
- 线程数或 FD 数持续增长；
- 静置后周期任务仍不断产生对象；
- 功能关闭后对应 Hook、Listener 或任务仍存在；
- 相同设置下当前版本稳定占用显著高于基线，且能定位到模块改动。

## 7. 泄漏重点检查

### 设置应用

- 语言切换后旧 Activity 是否可回收；
- `AboutFragment`、`PreferenceScreen` 是否残留；
- `PreferenceChangeListener` 是否持有旧 Fragment；
- `ListPreferenceEx` 是否持有旧 Context；
- AppCompat Locale 切换是否触发重建循环；
- `SharedPreferences` Listener 是否正确注销；
- Xposed service listener 是否重复注册；
- `installedAppsList` 是否无限期保留大型应用列表。

### SystemUI

- 状态栏文字 View 弱引用注册表是否真正清理；
- 主题、密度、方向和 SystemUI 重建后旧 View 是否残留；
- Receiver、Observer、Runnable 是否重复；
- 2 秒监控任务是否只有一个；
- 旧 Handler 消息是否仍在队列；
- 熄屏时是否继续无效工作；
- Drawable、Bitmap、Shader、Matrix 是否无界缓存。

### Launcher

- 小窗、最近任务、图标缩放和手势 Hook；
- Launcher 重启后 Listener 是否重复；
- View、Activity、Task 对象是否进入静态字段；
- Bitmap cache 是否有容量；
- Coroutine、Executor 是否重复创建。

## 8. 结果模板

### 8.1 设备和 ROM

- 设备：
- ROM / 版本：
- Android 版本：
- 模块版本：
- 用户启用功能：见 `ENABLED_FEATURE_MATRIX.md`

### 8.2 数据表

| 进程 | T0 PSS | T1 PSS | T2 PSS | 10 轮后 PSS | 静置后 PSS | Java Heap | Native Heap | FD | 线程 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `tv.withaibuild.customiuizer.r14` | | | | | | | | | |
| `system_server` | | | | | | | | | |
| `com.android.systemui` | | | | | | | | | |
| `com.miui.home` | | | | | | | | | |

### 8.3 当前版 vs 基线

使用 `tools/compare-memory-baseline.py` 生成。

### 8.4 结论

- 是否存在持续增长：
- 是否找到泄漏持有链：
- 修复前后数据：
- 不能确定的部分：

