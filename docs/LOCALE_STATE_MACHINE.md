# Locale 状态机

> 本文档记录 CustoMIUIzer A14 的 Locale 状态所有者、转换规则和修复后的状态机。

## 1. 问题描述

在 `r14.13.5` 及更早版本中，用户在 **About → 界面语言** 执行以下切换时可能出现状态异常：

```text
跟随系统 → 简体中文 → English → 跟随系统
```

表现包括：

- 语言选项右侧当前值文字消失；
- 语言列表部分消失或显示不完整；
- 选中语言未真正生效；
- UI 部分中文、部分英文；
- 需要退出页面或重启应用后才生效；
- Preference 显示值、SharedPreferences 值和当前应用语言不一致。

本轮修复目标：状态绝对正确；重启应用后必定生效；不出现列表消失或显示值不一致；不引入 Activity / Fragment 重建循环。

## 2. 修复前的状态源

| 状态 | 写入方 | 读取方 | 生命周期 | 是否持久化 | 是否为真实状态源 |
| --- | --- | --- | --- | --- | --- |
| `pref_key_miuizer_locale`（SharedPreferences） | `AppLocaleController.setUserLocale`（立即 apply） / `ListPreference` 自动持久化 | `AppLocaleController.setupLocalePreference`、`getUserLocale` | 应用进程 | 是 | **是**（应作为唯一来源） |
| `AppCompatDelegate.getApplicationLocales()` | `AppLocaleController.applyLocale` | AppCompat Activities | 应用 | AppCompat 内部持久化 | 否，是派生状态 |
| `Locale.getDefault()` | `AppLocaleController.applyLocale` / `getLocaleContext` | `String.format`、Dialog、通知 | 进程 | 否 | 否，是派生状态 |
| `Activity.resources.configuration`（手动 `createConfigurationContext`） | `MainActivity.attachBaseContext` 中 `AppHelper.getLocaleContext` | Activity / Fragment / View | Activity | 否 | 否，与 AppCompat 重复 |
| `ListPreference.value` / `entries` / `entryValues` | `AppLocaleController.setupLocalePreference`（运行时动态构建） | `ListPreferenceEx` 渲染 | Fragment | 否 | 否，UI 派生 |

问题根因：

1. **选择后立即应用**：`onPreferenceChangeListener` 返回 `true`，`ListPreference` 自动持久化并触发 `setUserLocale`，`applyLocale` 又触发 AppCompat Activity 重建；重建过程中 `ListPreference` 的 `entries`/`entryValues` 可能还没设置好，导致 `entry == null`。
2. **apply() 异步落盘风险**：切换后立即重建，新值可能尚未写入磁盘；应用被杀死后下次启动读到旧值。
3. **手动 Context 与 AppCompat 混用**：`MainActivity.attachBaseContext` 手动调用 `createConfigurationContext`，与 `AppCompatDelegate.setApplicationLocales` 并存。
4. **双写竞争**：`ListPreference` 自动持久化与 `onPreferenceChangeListener` 中手动写入同时发生。
5. **临时状态未清理**：切换后未退出应用，旧 Activity / Fragment 状态与新 Locale 交错，summary 和实际语言不同步。

## 3. 修复后状态机

### 3.1 单一状态源

唯一持久化状态：`pref_key_miuizer_locale` 中保存的用户选择字符串。

合法值：

```text
auto, en, zh-CN, zh-TW, ru-RU, ja-JP, vi-VN, cs-CZ, pt-BR, tr-TR, es-ES
```

- `auto` = 跟随系统。
- 任何非法、空白、`null`、旧值 `"1"` 都被规范化为 `auto`。

辅助标记：`pref_key_miuizer_locale_reconcile_pending` 表示用户已确认新选择，下次启动需要执行一次 Locale 对账。

### 3.2 派生状态计算

```text
┌─────────────────────────────┐
│ pref_key_miuizer_locale     │
│ (user choice)               │
└──────────────┬──────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ AppLocaleController                      │
│ 1. normalizeLocaleTag()                  │
│ 2. toLocaleListCompat()                  │
│    auto  -> LocaleListCompat.empty       │
│    else  -> LocaleListCompat.forLanguageTags│
│ 3. getEffectiveLocale()                  │
│    auto  -> current system locale        │
│    else  -> Locale.forLanguageTag(tag)   │
│ 4. on startup:                           │
│    if pending or current != target:      │
│       Locale.setDefault(effective)       │
│       AppCompatDelegate.setApplicationLocales(target)
│       clear reconcile pending            │
└──────────────────────────────────────────┘
```

### 3.3 用户切换语言路径

```text
ListPreference dialog selection
  -> onPreferenceChangeListener (AboutFragment)
       if newValue == currentValue: return true  (无变化)
       else:
           show confirmation dialog
             -> 取消:  return false  (不保存、不退出)
             -> 确认并退出:
                   1. setUserLocale(prefs, tag)
                        a. normalizeLocaleTag(tag)
                        b. SharedPreferences.commit()  (同步)
                        c. put LOCALE_RECONCILE_PENDING = true
                   2. if commit failed: Toast, do NOT exit
                   3. if commit succeeded:
                        activity.finishAffinity()
                        Handler.post { Process.killProcess(myPid()) }
                        (用户下次打开应用后生效)
```

关键点：

- `ListPreference.isPersistent = false`，禁用其自动持久化，避免双写。
- `onPreferenceChangeListener` 对任何不同值都返回 `false`，阻止 `ListPreference` 在确认前自动写入。
- 只有用户点击“确认并退出”后，才由 `AppLocaleController.setUserLocale` 同步 `commit()`。
- `commit()` 成功后，才结束设置应用自身进程；不重启 SystemUI、Launcher、system_server 或设备。
- 切换后**不**立即重建当前 Activity / Fragment，语言在用户下次打开应用时生效。

### 3.4 应用启动路径

```text
MainApplication.onCreate()
  -> AppHelper.appPrefs?.let { AppLocaleController.reconcileAndApply(it) }
       1. getUserLocale(prefs)                 // 规范化
       2. isReconcilePending(prefs)             // 是否需要强制对账
       3. current = AppCompatDelegate.getApplicationLocales()
       4. target  = toLocaleListCompat(userTag)
       5. effective = getEffectiveLocale(userTag)
       6. defaultChanged = (Locale.getDefault() != effective)
       7. appLocaleChanged = pending || (current != target)
       8. if defaultChanged: Locale.setDefault(effective)
       9. if appLocaleChanged: AppCompatDelegate.setApplicationLocales(target)
      10. clear reconcile pending
```

关键点：

- 只在启动时执行一次，不在每次打开 `AboutFragment` 时重复应用。
- 仅在 `pending` 或目标与当前 AppCompat 语言不一致时才调用 `setApplicationLocales`，避免循环。
- `auto` 模式对应 `LocaleListCompat.getEmptyLocaleList()`，让 AppCompat 跟随系统。
- `Locale.getDefault()` 只在需要时设置，保证 `String.format` 等使用正确语言。

### 3.5 Preference UI 路径

`AboutFragment.onViewCreated`：

1. 调用 `AppLocaleController.setupLocalePreference(locale, appPrefs)`；
2. `setupLocalePreference` 先设置完整 `entries` / `entryValues`；
3. 从唯一状态源读取当前值并规范化；
4. 设置 `ListPreference.value`；
5. 安装 `onPreferenceChangeListener` 处理确认逻辑。

`ListPreferenceEx.onBindViewHolder`：

- 检查 `entries` 与 `entryValues` 长度是否一致。
- 检查当前 `value` 是否存在于 `entryValues`；不在则回退到首个值并记录错误。
- `valueAsSummary` 逻辑保证即使 `entry` 为空也不显示空字符串。

### 3.6 清理的状态源

- 移除 `MainActivity.attachBaseContext` 中的手动 `createConfigurationContext`。
- 移除 `AppLocaleController.getLocaleContext()`。
- 移除 `AppHelper.getLocaleContext()` 与 `AppHelper.applyLocaleChange()`。
- 设置应用的 Activity、Fragment、Dialog、Preference 统一由 AppCompat `setApplicationLocales` 处理。

## 4. 修复文件

- 新增：
  - `app/src/main/java/tv/withaibuild/customiuizer/utils/AppLocaleController.kt`
  - `app/src/main/java/tv/withaibuild/customiuizer/utils/RestartRequirement.kt`
  - `app/src/test/java/tv/withaibuild/customiuizer/utils/AppLocaleNormalizationTest.kt`
  - `app/src/test/java/tv/withaibuild/customiuizer/utils/AppLocaleEntryTest.kt`
  - `app/src/test/java/tv/withaibuild/customiuizer/utils/AppLocaleReconcileTest.kt`
  - `app/src/test/java/tv/withaibuild/customiuizer/utils/RestartRequirementTest.kt`
- 修改：
  - `app/src/main/java/tv/withaibuild/customiuizer/utils/AppHelper.kt`
  - `app/src/main/java/tv/withaibuild/customiuizer/MainApplication.kt`
  - `app/src/main/java/tv/withaibuild/customiuizer/MainActivity.kt`（`attachBaseContext` 移除手动包装）
  - `app/src/main/java/tv/withaibuild/customiuizer/AboutFragment.kt`
  - `app/src/main/java/tv/withaibuild/customiuizer/prefs/ListPreferenceEx.kt`
  - `app/src/main/res/values/strings.xml`
  - `docs/LOCALE_STATE_MACHINE.md`
  - `docs/VERIFICATION.md`
  - `docs/DEVIN_A14_CHECKPOINT.md`

## 5. 验收矩阵

| 操作流程 | 预期行为 |
| --- | --- |
| `auto` → 选择 `zh-CN` → 取消 | 不退出；显示值保持 `auto`；Preference 未写入 |
| `auto` → 选择 `zh-CN` → 确认并退出 → 重新打开 | 应用中文；显示 `简体中文`；列表完整 |
| `zh-CN` → 选择 `en` → 确认并退出 → 重新打开 | 应用英文；显示 `English`；About/Toolbar/Dialog 全部英文 |
| `en` → 选择 `auto` → 确认并退出 → 重新打开 | 跟随系统中文；显示 `跟随系统`；列表完整；不残留 English |
| 选择 `auto` 后系统中文→英文 → 重新打开 | 应用英文 |
| 选择 `zh-CN` 后系统中文→英文 → 重新打开 | 仍保持中文 |
| 连续切换 20 轮 | 无列表消失、无空 summary、无 Activity 重建循环 |
| 强制停止 / 清理任务 / 设备重启 / 日夜切换 / 横竖屏 / 返回栈 | 重新打开后语言状态正确 |

单元测试覆盖：

- `null` / `""` / `"1"` / 非法值 → `auto`
- `auto` → `LocaleListCompat.getEmptyLocaleList()`
- `en` / `zh-CN` → 非空目标
- 相同值不需要保存和退出
- 取消确认不改变状态
- 保存失败不退出
- 保存成功标记 pending
- 启动对账成功后清除 pending
- 当前 AppCompat Locale 已正确时不重复应用
- `entries` 与 `entryValues` 长度一致
- `auto` 位于首项

## 6. 尚未实机验证的边界

1. `auto → 简体中文 → English → 跟随系统` 全程不丢失语言列表；
2. 切回 `auto` 后更改系统语言，应用跟随变化；
3. 明确选择 `zh-CN` 后更改系统语言，应用保持中文；
4. 强制停止、清理任务、设备重启后语言选择正确；
5. 旋转屏幕、切换日间/夜间模式后 About 页面状态正确；
6. 确认取消后 summary 与 `ListPreference` 选择同步回到原值；
7. 20 轮连续切换无崩溃、无重复退出提示、无 Locale 异常日志。

## 7. 手动 Context 包装

- `AppLocaleController.getLocaleContext()`、`AppHelper.getLocaleContext()` 已删除。
- `MainActivity.attachBaseContext` 已移除手动 `createConfigurationContext`。
- 设置应用 UI 统一由 `AppCompatDelegate.setApplicationLocales` 处理。
