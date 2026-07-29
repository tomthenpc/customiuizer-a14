# 状态回归审计 r14.13

> 本文件记录 `r14.12.0` 至今用户可见的状态型功能，包括所有者、转换事件、销毁方式、重建测试覆盖和已确认问题。

## 审计方法

1. 收集 `r14.12.0` 之后修改过的、与用户状态相关的文件和提交。
2. 对每个状态项建立：
   - 状态所有者；
   - 初始状态来源；
   - 触发转换的事件；
   - 重建/恢复方式；
   - 销毁方式；
   - 是否存在测试；
   - 是否经过进程重启测试。
3. 重点搜索危险模式：
   - `static`/companion object 可变状态；
   - `lateinit`；
   - 全局可空单例；
   - `onCreate`/`onViewCreated` 重复初始化；
   - 监听器注册后未注销；
   - 异步任务持有已销毁的 Fragment/View；
   - Preference 值与 UI 状态分别保存。

## 状态审计表

| 功能 | 状态所有者 | 初始状态 | 转换事件 | 恢复方式 | 销毁方式 | 重建测试 | 进程重启测试 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Locale | `AppLocaleController.LOCALE_PREF_KEY`（SharedPreferences） | `auto` | 用户在 About 语言列表选择 | `setupLocalePreference` 从 SharedPreferences 重建 `entries`/`value`；`applyLocale` 设置 `Locale.getDefault` 与 `AppCompatDelegate` | 无状态需销毁，全局 `Locale` 随进程 | `AppLocaleControllerTest` 覆盖 | 否 |
| Search navigation | `SearchNavigation` / `MainFragment.searchState` | `0` | SearchView open/query/result click | `MainFragment.onViewCreated` 恢复视图；`SearchNavigation` 处理路由 | Fragment `onDestroyView` 清理 SearchView 引用 | `SearchRouteResolverTest`, `SearchStateMachineTest` | 否 |
| Fragment back stack | `FragmentManager` / `parentFragmentManager` | Activity/Fragment 恢复 | `openSubFragment`, `popBackStack` | 系统恢复 Fragment 实例；`MainActivity.onCreate` 尝试 `getFragment(savedInstanceState)` | `finish()` 调用 `popBackStackImmediate` | 无专门测试 | 否 |
| Preference highlight | `SubFragment.highlightKey` | `arguments.mod` | 搜索/外部跳转 | `onCreatePreferences` 设置 `applyHighlight()`；`onStart` 平滑滚动 | Fragment 销毁后自然释放 | `SearchNavigation` 间接覆盖 | 否 |
| Theme / day-night | `SharedPreferences` + `AppCompatDelegate.setDefaultNightMode` | 系统/用户选择 | 用户切换或系统变化 | `MainActivity.applySystemBarsAppearance`；系统资源自动刷新 | 无显式状态 | 无 | 否 |
| Root restart | `lifecycleScope` + `AppHelper.executeRootCommand` | 按钮点击 | 菜单选择 | 显示 Toast；不持久化 | `Coroutine` 随 `lifecycleScope` 取消 | 无（IO 操作） | 否 |
| RemotePreferences | `AppHelper.remotePrefs` | `null` | Xposed 服务绑定/死亡 | `MainActivity` 注册 `XposedServiceHelper` 监听并在 `onServiceBind` 重新设置 | `onServiceDied` 置 `null` | `LibXposedMetadataTest` | 否 |
| installedAppsList | `AppHelper.installedAppsList` | `null` | 首次加载应用列表 | 重新调用加载逻辑 | 未显式清理，长期持有 | `PrefMapTest` 等未覆盖 | 否 |
| SystemUI View 注册表 | `StatusBarTextIcon` 内部 Map/WeakReference | 空 | Hook 触发 | 随 SystemUI 重建创建新实例；旧引用被 WeakReference GC | SystemUI 死亡 | 无 | 否 |
| Receiver/Observer | 各 Mod 内部字段 | 未注册 | 功能开启 | 功能关闭时注销 | `onPause`/`onDestroy` | 无 | 否 |
| Coroutine/Handler | `lifecycleScope` / 各 Mod 内部 `Handler` | 无 | 功能启用/定时任务 | 重新创建 | `lifecycleScope` 随生命周期取消；`Handler` 需手动移除 | 无 | 否 |

## 已确认问题与修复

### 1. Locale 状态（已修复）

- **问题**：存在多个状态源（SharedPreferences、AppCompat Locale、手动 Context、`Locale.getDefault`）；`ListPreference` 与 listener 双写；`apply()` 异步导致 Activity 重建读取旧值；`entries/entryValues` 与 `value` 重建时可能不同步。
- **修复**：
  - 新增 `AppLocaleController` 作为唯一状态所有者。
  - `setUserLocale` 使用同步 `commit()`。
  - `ListPreference` 禁用自动持久化，由 controller 统一写入。
  - 移除 `MainActivity.attachBaseContext` 手动 Context 包装，Activities 由 AppCompat 处理。
  - `ListPreferenceEx` 增加 `entry == null` 与 size mismatch 防御。
- **验证**：`AppLocaleControllerTest` 23 个测试通过；`assembleDebug` 与 `lintDebug` 通过。

### 2. 搜索导航（r14.13.5 已修复）

- **问题**：`Various` 搜索结果及子分类项点击后立即返回首页。
- **修复**（已在 r14.13.5）：
  - `ModData.sub` 改为可空；
  - `openModCat()` 返回 `true`；
  - 新增 `SearchRouteResolver` 与 `SearchStateMachine`。
- **验证**：`SearchRouteResolverTest`、`SearchStateMachineTest` 通过。

### 3. 潜在风险（需进一步实机验证）

- **Activity 配置变化时的 Fragment 恢复**：`MainActivity.onCreate` 仅在 `savedInstanceState != null` 时恢复 `mainFrag`，未显式处理 SubFragment 已在回退栈的场景。当 `locale` 触发 Activity recreate 时，FragmentManager 可能自动恢复回退栈，随后 `MainActivity` 执行 `replace` 可能覆盖。
- **installedAppsList 长期持有**：`AppHelper.installedAppsList` 首次加载后不再释放，如果包含大量应用，可能占用内存。
- **Receiver/Observer 注销不完整**：需要逐项审计每个 Mod 的 `onResume`/`onPause` 或 `SystemUI` 重建时的注销。
- **MainApplication 通知通道**：Application Context 未由 AppCompat 包装，`auto` 模式下通知通道名称使用系统默认，不影响功能但可能语言不一致。

## 未覆盖边界

以下需要实机或后续专项测试：

1. 系统语言变化时 `auto` 是否跟随；
2. 旋转屏幕、切换日间/夜间模式后 Fragment 状态；
3. 完整重启设备后 Locale 恢复；
4. SystemUI/Launcher 重复 Hook 与 Receiver 注册；
5. 内存长期增长（见 `MEMORY_AUDIT_r14.13.md`）。

