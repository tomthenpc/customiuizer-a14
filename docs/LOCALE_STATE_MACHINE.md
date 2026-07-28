# Locale 状态机

> 本文档记录 CustoMIUIzer A14 的 Locale 状态所有者、转换规则和修复后的状态机。

## 1. 问题描述

在 `r14.13.5` 及更早版本中，用户在 **About → 界面语言** 执行以下切换时可能出现状态异常：

```text
跟随系统 → 简体中文 → English → 跟随系统
```

表现包括：

- 语言选项右侧当前语言文字消失；
- 语言列表部分消失或显示不完整；
- 选中语言未真正生效；
- UI 部分中文、部分英文；
- 需要退出页面或重启应用后才生效；
- Preference 显示值、SharedPreferences 值和当前应用语言不一致。

## 2. 修复前的状态源

| 状态 | 写入方 | 读取方 | 生命周期 | 是否持久化 | 是否为真实状态源 |
| --- | --- | --- | --- | --- | --- |
| `pref_key_miuizer_locale`（SharedPreferences） | `AppHelper.applyLocaleChange`（listener + ListPreference） | `AppHelper.getLocaleContext`、`setupLocalePreference` | 应用进程 | 是 | **是**（应作为唯一来源） |
| `AppCompatDelegate.getApplicationLocales()` | `AppHelper.applyLocaleChange` | AppCompat Activities | Activity | AppCompat 内部持久化 | 否，是派生状态 |
| `Locale.getDefault()` | `AppHelper.applyLocaleChange` / `getLocaleContext` | `String.format`、Dialog、通知 | 进程 | 否 | 否，是派生状态 |
| `Activity.resources.configuration`（手动 `createConfigurationContext`） | `MainActivity.attachBaseContext` 中 `AppHelper.getLocaleContext` | Activity / Fragment / View | Activity | 否 | 否，与 AppCompat 重复 |
| `ListPreference.value` / `entries` / `entryValues` | `AppHelper.setupLocalePreference`（运行时动态构建） | `ListPreferenceEx` 渲染 | Fragment | 否 | 否，UI 派生 |

问题根因：

1. **双写竞争**：`ListPreference` 与 `onPreferenceChangeListener` 同时向 `pref_key_miuizer_locale` 写入，且 `apply()` 异步。
2. **Activity 重建时读取旧值**：`apply()` 完成后未确认，配置变化已经触发 Activity/Fragment 重建。
3. **手动 Context 与 AppCompat 混用**：`MainActivity.attachBaseContext` 手动调用 `createConfigurationContext`，同时 `AppCompatDelegate.setApplicationLocales` 也管理语言，形成两套机制。
4. **动态 entries 与 value 不同步**：`setupLocalePreference` 在 `onViewCreated` 中重建 `entries`/`entryValues`，而 `ListPreference` 的 `value` 可能来自旧 `SharedPreferences` 快照，不在当前 `entryValues` 中，导致 `entry == null`、summary 为空。

## 3. 修复后状态机

### 3.1 单一状态源

唯一持久化状态：`pref_key_miuizer_locale` 中保存的用户选择字符串。

合法值：

```text
auto, en, zh-CN, zh-TW, ru-RU, ja-JP, vi-VN, cs-CZ, pt-BR, tr-TR, es-ES
```

- `auto` = 跟随系统。
- 任何非法、空白、`null`、旧值 `"1"` 都被规范化为 `auto`。

### 3.2 派生状态计算

```text
┌──────────────────────┐
│ pref_key_miuizer_    │
│ locale (user choice) │
└──────────┬───────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│ AppLocaleController                     │
│ 1. normalizeLocaleTag()                 │
│ 2. getEffectiveLocale()                 │
│    auto  -> current system locale        │
│    else  -> Locale.forLanguageTag(tag)   │
│ 3. Locale.setDefault(effective)          │
│ 4. LocaleListCompat -> AppCompatDelegate │
└─────────────────────────────────────────┘
```

### 3.3 写入路径

用户切换语言时：

```text
ListPreference dialog selection
  -> onPreferenceChangeListener (AboutFragment)
       -> setUserLocale(prefs, tag)
            1. normalizeLocaleTag(tag)
            2. SharedPreferences.commit()  (同步，确认完成)
            3. applyLocale(tag)
                 - Locale.setDefault(effective)
                 - AppCompatDelegate.setApplicationLocales(localeList)
       -> pref.value = tag (UI 立即刷新)
```

关键点：

- `ListPreference.isPersistent = false`，禁用其自动持久化，避免双写。
- `commit()` 保证 Activity 重建前 `SharedPreferences` 已写入。
- `applyLocale()` 只读不写，供 `MainApplication.onCreate` 启动时使用。

### 3.4 重建路径

- `MainApplication.onCreate`：`applyLocale(getUserLocale(prefs))`，在首个 Activity 创建前设置 AppCompat 全局 locale。
- `MainActivity`：移除 `attachBaseContext` 手动包装，完全由 AppCompat 处理 Activity 上下文。
- `AboutFragment.onViewCreated`：调用 `AppLocaleController.setupLocalePreference()`，从同一 `SharedPreferences` 重建 `entries`/`entryValues` 并设置 `value`。
- `PreferenceFragmentBase.onConfigurationChanged`：检测到 locale 变化时调用 `reloadPreferences()`，重新创建当前 Fragment 的 PreferenceScreen。
- `ListPreferenceEx`：增加 `entry == null` 和 `entries/entryValues` size 不匹配的防御性回退。

## 4. 修复文件

- 新增：
  - `app/src/main/java/tv/withaibuild/customiuizer/utils/AppLocaleController.kt`
  - `app/src/test/java/tv/withaibuild/customiuizer/utils/AppLocaleControllerTest.kt`
  - `app/src/test/java/tv/withaibuild/customiuizer/utils/FakeSharedPreferences.kt`
  - `docs/LOCALE_STATE_MACHINE.md`
- 修改：
  - `app/build.gradle.kts`：`testOptions.unitTests.isReturnDefaultValues = true`
  - `app/src/main/java/tv/withaibuild/customiuizer/utils/AppHelper.kt`（委托给 `AppLocaleController`）
  - `app/src/main/java/tv/withaibuild/customiuizer/MainApplication.kt`
  - `app/src/main/java/tv/withaibuild/customiuizer/MainActivity.kt`（移除 `attachBaseContext`）
  - `app/src/main/java/tv/withaibuild/customiuizer/prefs/ListPreferenceEx.kt`

## 5. 验收矩阵

| 用户选择 | 系统语言 | Preference 显示 | 实际应用语言 |
| --- | --- | --- | --- |
| auto | zh-CN | 跟随系统 | zh-CN |
| auto | en | Follow system | en |
| zh-CN | en | 简体中文 | zh-CN |
| en | zh-CN | English | en |

单元测试覆盖：

- `auto → zh-CN`
- `zh-CN → en`
- `en → auto`
- `auto → en`
- `en → en`（幂等）
- 非法值 → `auto`
- 空字符串 / `null` / 旧值 `"1"` → `auto`
- `entries` 与 `entryValues` 长度一致
- `commit()` 完成后立即可读取

## 6. 尚未实机验证的边界

以下需要在真机/模拟器上手动确认：

1. `auto → 中文 → English → auto` 全程不丢失语言列表；
2. 切回 `auto` 后，更改系统语言时应用跟随；
3. 强制停止并重启应用后语言选择正确；
4. 旋转屏幕、切换日间/夜间模式后 About 页面状态正确；
5. `MainApplication` 通知通道名称在 `auto` 模式下使用系统语言（当前 Application Context 未被 AppCompat 包装，可能使用系统默认）。

## 7. 是否保留手动 Context 包装

- `AppLocaleController.getLocaleContext()` 仍保留，仅用于非 AppCompat 上下文：`getProtectedContext`（device-protected storage 的 `SharedPreferences` 读取）。
- `MainActivity.attachBaseContext` 已移除，Activities 统一由 `AppCompatDelegate.setApplicationLocales` 处理。

