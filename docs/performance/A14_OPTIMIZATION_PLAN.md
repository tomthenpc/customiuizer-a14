# A14 性能优化执行计划

## 基线与目标

- 分支：`codex/a14-performance-optimization`
- Base SHA：`fdc7b8f5d6354ed372b4020be595b909dfec9351`
- 平台：HyperOS 1 / Android 14 / SDK 34 / arm64-v8a
- 兼容边界：libxposed API 101 为最低运行基线，API 102 能力继续隔离

本计划以“缩短热路径、关闭功能低成本、长生命周期对象所有权明确”为目标。
Kotlin + 少量 Java 的现状保持不变；不以迁移到 Java、Rust、Compose 或增加 Gradle
模块作为性能优化手段。

## 已完成工作，不重复实施

当前 `main` 已经完成以下工程优化：

- SystemClock 每秒更新路径的 preference 快照与重复更新消除；
- 普通网速、详细网速、状态栏图标可见性热回调的 preference 快照；
- B1/B2/B3 关闭时不创建对应运行态对象；
- Feature 关闭时不创建 `FeatureDefinition`、业务 installer、Hook、Receiver 或
  Observer，仅保留冷路径的 `LazyFeatureSpec` 元数据与启用条件判断；
- SystemUI 所有者与注册表的第一轮静态审计。

上述工作已有测试和文档证据。新任务不得为了“统一架构”重写这些已验证路径。

## 当前剩余证据

| 方向 | 当前静态证据 | 判断 |
|---|---|---|
| View 生命周期 | `AudioVisualizer` 与 `BatteryIndicator` 的 preference observer 回调会访问外部 View；正常 detach 会解绑，但漏掉 detach 时存在 value 反向持有 owner 的风险 | 优先、可小步修复 |
| 调用栈扫描 | Launcher、锁屏充电信息、SecurityCenter Dock 建议三处仍调用 `Thread.currentThread().stackTrace` | 分成三个独立任务 |
| 设置 UI | `prefs_system.xml` 有 210 个 XML 元素；仓库已有 15 元素的 `prefs_system_cat.xml` 分类壳和大量子页面 | 复用现有分类能力，不整体重写 UI |
| 搜索 | `Helpers.parsePrefXml()` 在运行时解析 system、launcher、controls、various 四份 XML | 先冻结等价性，再生成索引 |
| Feature 安装 | 关闭功能已经没有业务对象成本，但 SystemUI 启动仍遍历约 96 个 Spec 并检查 preference | 当前是可接受冷路径，需实机证据后再改 |
| 安装状态 | `FeatureInstallState` 使用 `HashMap<Int, FeatureState>` | 可替换，但尚无收益证据 |
| R8 | Hooker 与 `mods.**` 公共成员 keep 边界较宽 | 先做 why-kept 证据，禁止直接删规则 |
| 实机性能 | 现有 P2 checkpoint 因无设备未采集 PSS、CPU、启动和帧数据 | 大改前必须补齐 |

## 执行顺序

每个编号都是独立的 `OPTIMIZE` 任务合同、独立 diff 和独立验证闭环。不得把多个
编号合成一次大提交。

### M0：补齐实机基线

复用 [P2 实机检查点](P2_CHECKPOINT_1_DEVICE_RUNTIME_EVIDENCE.md) 的方法，在同一台 HyperOS 1 / A14
设备上记录关闭基线、用户配置和压力配置，每组稳定后重复采样并取中位数：

- SystemUI、Launcher、`system_server` 的 PSS / USS / Private Dirty；
- 目标进程启动和 Hook 安装耗时；
- SystemUI CPU、线程数、FD 数；
- 设置系统页首次打开耗时和帧时间；
- 重建状态栏、锁屏和控制中心后的 retained owner / heap 证据。

没有设备时明确记录 `DEVICE_CHECKPOINT_BLOCKED_NO_DEVICE`。M0 不阻塞 M1、M2 的
小范围行为保持修复，但 M3 及之后的 UI 或启动架构改造必须等待基线。

### M1：关闭 SystemUI View 的反向持有链

#### M1.1 AudioVisualizer observer（由 owner 暂缓）

Owner 于 2026-08-08 明确暂缓此项，因为该功能很少使用。本分支不修改
`AudioVisualizer`；以下约束留作未来重新启用任务时使用：

- 将 observer 与 `AudioVisualizer` 的关系改为弱 owner；
- 保留现有 `dispose()`、`onDetachedFromWindow()`、协程取消和精确解绑顺序；
- observer 找不到 owner 时直接返回，不创建替代全局所有者；
- 增加正常 detach、重复 dispose、漏掉 detach 后可回收、preference 更新行为等测试。

#### M1.2 BatteryIndicator observer（工程已完成）

- 使用同样的弱 owner 模式处理 preference observer；
- 不改已使用 owner 参数的 `ReceiverRegistry` 回调；
- 保留 `viewScope`、测试动画、截图状态和更新时序；
- 增加 owner 已回收、重复 attach/detach、observer/receiver 精确释放测试。

M1 不修改 `XposedHelpers.java`、全局注册表或其他 View。验收重点是切断
`process map -> observer -> View`，同时保留显式解绑作为正常路径。

### M2：消除三处调用栈数组分配

先定位每个调用点的真实上层方法；若 ROM 上存在稳定的直接目标，优先 Hook 直接目标。
只有缺少稳定目标时才使用带 `try/finally` 的有界 `ThreadLocal<Int>` 重入标记。

#### M2.1 Launcher `force_fsg_nav_bar`

工程已于 2026-08-09 完成：

- 目标设备为 `fuxi`，HyperOS `V816.0.7.0.UMCTWXM`，Android 14 / SDK 34；
- 设备 Launcher 为 `RELEASE-4.39.30.8604-08262248`（versionCode `439308604`），
  `/product/priv-app/MiuiHome/MiuiHome.apk` 的 SHA-256 为
  `A6546D51D9220039ED7AC143DE249E9014202CFAFEAFD715237EB49F8F5A3F7B`；
- 设备 APK 字节码中的 `BaseRecentsImpl` 有 109 个声明方法，只有
  `updateFsgWindowState()`、`lambda$showBackStubWindow$...` 和
  `lambda$updateFsgWindowVisibilityState$...` 三种精确签名直接读取该键；
- 安装冷路径按方法前缀、返回类型和参数签名要求三类目标各且仅有一个，避免写死
  synthetic lambda 编号；热路径改为带 `finally` 清理的 `ThreadLocal<Int>` 重入标记；
- 本分支 APK 已在同一设备执行 Launcher 重启、最近任务、横向滑动和 Home 连续回归；
  HookSummary 零失败、PID 未变化，状态更新为 `DEVICE_RUNTIME_PASS`。完整证据见
  [M0_M2_DEVICE_RUNTIME_EVIDENCE_2026-08-09.md](M0_M2_DEVICE_RUNTIME_EVIDENCE_2026-08-09.md)。

- 仅替换 `MiuiSettingsUtils.getGlobalBoolean(..., "force_fsg_nav_bar")` 对
  `BaseRecentsImpl` 调用来源的识别；
- 保留原始值缓存、强制返回 `true` 的条件、异常传播和 `chain.proceed()` 次数；
- 验证 BaseRecentsImpl 内外两种调用、嵌套调用和异常退出后的标记清理。

#### M2.2 锁屏充电信息

- 仅替换 `ChargeUtils.getChargingHintText()` 中 Keyguard 与 MiuiCharge 调用来源的识别；
- 保持 Keyguard 优先、MiuiCharge 排除、未知来源不替换的现有语义；
- 不改变电池属性读取、文本组合和已存在的 View observer 生命周期。
- 实机 AC 充电锁屏已显示模块附加的电流、电压、功率和温度；SystemUI PID 保持，
  无崩溃，状态为 `DEVICE_RUNTIME_PASS`。

#### M2.3 SecurityCenter Dock 建议

- 仅替换 `MiuiMultiWindowUtils.getFreeformSuggestionList()` 对
  `DockAppEditActivity` / `BubblesSettings` 的来源识别；
- 两个白名单调用方继续拿到 ROM 原结果，其他调用方继续拿到清空结果；
- 单元素 `ArrayList` 的数据结构调整不并入本任务；只有另一个有收益证据的任务证明
  返回类型与可变性合同后才实施。
- 实机 HookSummary 零失败且全局侧边栏可正常展开、滚动；由于固定应用已满，ROM
  禁用了内部编辑入口，外层为 `DEVICE_RUNTIME_PASS`，`DockAppEditActivity` worker
  为 `DEVICE_RUNTIME_PARTIAL`。

M2 完成后，这三个功能路径不得再出现 `Thread.currentThread().stackTrace`；不以猜测的
ROM 类或广泛 fallback 换取静态扫描通过。

### M3：设置 UI 按需创建

#### M3.1 分类入口

- 复用现有 `prefs_system_cat.xml` 与子页面路由，把系统设置默认入口变为小型分类壳；
- 打开一个分类时只创建该分类及其直接子页面所需的 Preference；
- 保持所有 preference key、默认值、依赖关系、导入导出和搜索跳转兼容；
- 先迁移一个代表性分类并测量，再逐分类迁移，禁止一次重写全部 210 个元素；
- 不引入 Compose，也不先造一套自定义 Cell 框架。

目标不是删除 XML，而是让进入系统设置页时不再一次实例化完整 Preference 树。

首个原子切片于 2026-08-09 进入验证：

- “状态栏”普通分类点击和搜索结果直达统一通过
  `SystemPreferenceResourceResolver` 选择独立的 `prefs_system_statusbar.xml`；
- 其他系统分类仍使用原 `prefs_system.xml`，不在本切片顺带迁移；
- 原总表继续作为搜索索引的规范来源，独立资源与其中的状态栏分类由合同测试逐节点核对
  标签、属性、顺序、依赖和 preference key；
- 静态元素数从完整资源的 `210` 降为状态栏资源的 `33`。这只证明少创建对象的结构边界，
  不冒充设备页面耗时或帧收益；
- JVM 针对性测试、全部工具测试、`fast --changed` 和完整门禁已通过，设备覆盖安装与
  页面往返回归待 ADB 恢复后补齐。

#### M3.2 构建期搜索索引

- 先用测试冻结当前四份 XML 的可搜索 key、标题、分类、子页面和显示顺序；
- 构建阶段生成紧凑索引，运行时只解析生成结果和当前语言资源；
- 搜索结果、禁用项过滤、直接跳转和高亮行为必须与现状等价；
- 生成器改动必须补 Python/Gradle 工具测试，生成结果必须可重复。

只有 M0 表明设置页仍有明显帧或启动问题时，才评估 RecyclerView 自定义 Cell。

### M4：有证据才实施的启动与体积优化

#### M4.1 Feature 安装表

触发条件：实机证据显示 Feature 遍历或 Spec/lambda 分配是目标进程启动的显著成本。

- 稳定 `FeatureId` 和现有 target / phase / enabled / failure 语义不变；
- 候选实现为按进程生成直接分发代码或紧凑静态表；
- 禁用功能仍不得创建业务 installer、Hook 或注册对象；
- 安装失败分类、每进程只安装一次、preference 变化不重装等合同必须保留；
- API 102 专属类型不得进入 API 101 的生成表和必经路径。

如果 M0 未证明启动成本，保持现有 `LazyFeatureSpec`，不为理论上的少量 Map 查询增加
代码生成复杂度。

#### M4.2 FeatureInstallState 数据结构

只有 profile 证明装箱 `HashMap` 有可见成本时，才评估 `ByteArray` 或 JVM/Android
共同可测的紧凑容器。必须处理 ID 上界、未知 ID、并发安装、reset 和失败重试，且不改变
调用方 API。

#### M4.3 R8 keep 收窄

- 先生成 seeds、usage、mapping，并对主要 Hooker 与 `mods.**` 跑 why-kept 分析；
- 建立由 Manifest、`META-INF/xposed`、类名字符串、反射和框架入口组成的 allowlist；
- 每次只收窄一类入口，验证 settings 进程与目标进程类加载边界；
- 未获正式 Release 授权时，不创建、不签名、不发布 Release APK。

#### M4.4 巨型 Hook 单例

只在 `<clinit>`、retained heap 或 disabled baseline 证明确有成本时，按进程、生命周期和
启用条件引入 Lazy holder。不得仅为了文件长度拆类，也不得改变 Hook 时序和安装顺序。

## 每个实现任务的统一验收

- 用户行为和 preference 持久化兼容；
- Hook 参数改写、异常语义与 `chain.proceed()` 次数保持；
- owner 的 replace、stale、release 路径有测试；
- 回调热路径无磁盘 I/O、DexKit、同步 Binder、调用栈扫描或无界缓存；
- `OutOfMemoryError` 不被吞掉；
- API 101/102 隔离门禁通过；
- 针对性测试通过；
- `python tools/verify.py fast --changed` 通过；
- `python tools/verify.py full` 通过；
- `git diff --check` 通过；
- 工具目录改动时补 `python -m compileall tools` 和 Python 单元测试；
- 静态、构建、lint 与实机证据分级报告，不把前者写成 ROM 实测结论。

## 当前状态与下一步

M1.1 继续由 owner 暂缓；M1.2 与 M2.1-M2.3 已完成工程修复、完整门禁和设备回归。
M3.1 已进入首个“状态栏”原子切片，当前只完成实现与静态验证，不自动迁移其他分类。
M0 A/B 结果见
[M0_M2_DEVICE_RUNTIME_EVIDENCE_2026-08-09.md](M0_M2_DEVICE_RUNTIME_EVIDENCE_2026-08-09.md)：
设置页冷启动和帧数据没有证明 M3 的复杂重构有收益，PSS 也缺乏可重复归因性。

因此 M3.1 只按用户明确优先级和单分类 A/B 证据逐项推进，不扩大为整体 UI 重构；M4
仍保持证据门槛，也不实施用户已明确跳过的 AudioVisualizer 优化。
