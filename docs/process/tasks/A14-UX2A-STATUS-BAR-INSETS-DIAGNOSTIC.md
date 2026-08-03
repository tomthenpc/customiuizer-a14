# A14-UX2A Status Bar / WindowInsets 一致性诊断

## 任务元数据

| 字段 | 值 |
|---|---|
| TaskId | A14-UX2A |
| Priority | P2 |
| Risk Tier | R1 |
| State | VERIFIED_STATIC |
| ReviewerDecision | PENDING |
| DeviceEvidence | PENDING |
| ProductionBehaviorChanged | NO |
| P11.4 | 保持现状 |
| P14 | 未开始 |
| UX2B | BLOCKED_BY_DIAGNOSTIC_EVIDENCE |

---

## 目标

确认当前“状态栏高度”功能是否只改变 framework/resource 视角的高度，而没有同步 `WindowInsets`/`InsetsSource`/`displayCutout` 等运行时状态。

本阶段仅做审计、诊断工具、测试和设计文档；不实施真正的布局修复。

---

## 当前实现诊断

| 属性 | 值 | 来源 |
|---|---|---|
| preference key | `pref_key_system_statusbarheight` | `app/src/main/res/xml/prefs_system.xml:358` |
| 默认 sentinel | `11` | `prefs_system.xml:360` |
| sentinel 映射 | `11 -> 27 dp` | `System.kt:186` |
| 修改资源 | `status_bar_height_default`、`status_bar_height`、`status_bar_height_portrait`、`status_bar_height_landscape` | `System.kt:188-191` |
| FeatureTarget | `ANY` | `CommonPackageFeatures.kt:26` |
| InstallPhase | `PACKAGE_READY` | `CommonPackageFeatures.kt:27` |
| FeatureId | 146，name `status_bar_height` | `FeatureIds.kt:601-604` |
| 实现机制 | 资源替换 (`ResourceHooks.setThemeValueReplacement`) | `ModuleHelper.kt:862-867`，`ResourceHooks.kt:442-466` |
| InsetsSource 修改 | 无 | 审计未发现 |

---

## 诊断工具

- `tools/a14_status_bar_insets_probe.py`：只读 ADB 诊断工具。
- `tools/tests/test_a14_status_bar_insets_probe.py`：基于 fixture 的单元测试，不连接真实设备。

### 用法

```text
python tools/a14_status_bar_insets_probe.py \
  --serial <optional> \
  --package <target.package> \
  --configured-height-dp <number> \
  --output <json path> \
  [--timeout <seconds>] \
  [--verbose] \
  [--fixture <fixture file>] \
  [--keep-raw]
```

### 输出字段

- `device`: serial, product, sdk, displayId, rotation, densityDpi, density, physicalSize, overrideSize
- `target`: packageName, activity, windowToken, windowingMode, edgeToEdgeEvidence
- `configured`: heightDp, heightPx
- `insets`: statusBarsFrame, statusBarsHeightPx, statusBarsVisible, statusBarsIgnoringVisibilityTopPx, stableTopPx, contentTopPx, displayCutoutSafeTopPx
- `analysis`: classification, mismatchPx, mismatchDp, confidence, notes
- `commands`: 每个 ADB 命令的 argv, exitCode, supported, source

### 分类

- `CONSISTENT`：配置高度与 InsetsSource 高度差异 ≤ 1px
- `RESOURCE_GREATER_THAN_INSET`：配置高度明显高于 InsetsSource
- `INSET_GREATER_THAN_RESOURCE`：InsetsSource 明显高于配置高度
- `INSUFFICIENT_EVIDENCE`：无法取得 density 或 InsetsSource frame

---

## 本地验证

| 命令 | 结果 |
|---|---|
| `python -m unittest tools.tests.test_a14_status_bar_insets_probe` | PASS (19/19) |
| `python tools/a14_status_bar_insets_probe.py --help` | PASS |
| `python tools/check_document_contracts.py` | 待运行 |
| `python tools/check-invariants.py` | 待运行 |
| `python tools/check_automation_state.py` | 待运行 |
| `python tools/progress_snapshot.py --check` | 待运行 |
| `python tools/verify.py fast` | 待运行 |
| `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Mode Fast` | 待运行 |

---

## ROM Hook 点结论

候选 Hook 矩阵结论：**C. NEEDS_DEVICE_DEX_EVIDENCE**

AOSP framework 的 `DisplayPolicy` / `InsetsSourceProvider` 是最有可能的切入口，但 HyperOS 重写概率高，真实方法签名无法仅凭源码确定。当前资源替换路径不能同步 `WindowInsets`，在 edge-to-edge / cutout / multi-window 场景下可能不一致。

### 明确排除的危险方案

- 全局 Hook `WindowInsets.getInsets()` / `WindowInsetsCompat.getInsints()`
- Hook `ViewRootImpl.dispatchApplyInsets()` / `DecorView.onApplyWindowInsets()`
- 给所有 Activity root View 强制加 `paddingTop`
- 把新高度和 `displayCutout.safeInsetTop` 直接相加
- 对所有 App 强制 `decorFitsSystemWindows=true`
- 修改所有 App 的 `layoutInDisplayCutoutMode`
- 状态栏隐藏时仍强制保留顶部 inset
- 使用固定 px 或固定 27dp

---

## 设备证据计划

| # | 配置 | 目标 | 记录 |
|---|---|---|---|
| 1 | 默认 27dp + 传统 View App | 一个传统 View App | package, activity, orientation, configured dp/px, statusBars source height, stable/content top, cutout safe top, mismatch, 截图 |
| 2 | 增高状态栏 + 传统 View App | 同一应用 | 同上，确认 UI 是否被遮挡 |
| 3 | 默认 + edge-to-edge App | 声明 `SHORT_EDGES` 的应用 | 同上，加 `decorFitsSystemWindows`、`requestedVisibleTypes` |
| 4 | 增高 + edge-to-edge App | 同一应用 | 确认 Insets 是否仍跟随旧高度 |
| 5 | 默认 + Compose App | Compose App | `WindowInsets.statusBars` 返回值 |
| 6 | 默认 + WebView App | WebView App | 网页内容遮挡情况 |
| 7 | 横屏 | 任意全屏 App | `rotation=1/3`，source frame 宽高交换 |
| 8 | 分屏/小窗 | 支持 multi-window 的应用 | `windowingMode`、`mFrame` 偏移 |

---

## 修改文件

- `tools/a14_status_bar_insets_probe.py`
- `tools/tests/test_a14_status_bar_insets_probe.py`
- `docs/audit/A14_STATUS_BAR_INSETS_ANALYSIS.md`
- `docs/process/tasks/A14-UX2A-STATUS-BAR-INSETS-DIAGNOSTIC.md`
- `TASK_STATE.md`

---

## 禁止事项确认

- 未实施真正的布局修复
- 未添加 `system_server` Hook
- 未 Hook `WindowInsets`、`ViewRootImpl`、`DecorView` 或应用 root View
- 未修改应用 padding、margin、translation 或 `fitsSystemWindows`
- 未开始 UX2B
- 未修改 P11.4 文件
- 未修改 UX1 文件
- 未修改 `FeatureIds.kt`、installer registry、process matrix、CI、R8、Gradle

---

## 风险

- 静态审计无法确认 HyperOS 真实方法签名。
- 设备证据缺失，不能从理论推导实机 Insets 行为。
- 若后续进入 UX2B 并尝试 system_server Hook，风险等级将上升为 R2/R3，需要 `a14-independent-review`。
