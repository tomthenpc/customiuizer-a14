# r14.13 Kotlin 现代化与瘦身重构计划

版本线：`r14.12.0` → `r14.13.0`（本分支：`devin/r14.13-kotlin-refactor`）。

本计划遵循 `AGENTS.md`、`docs/ENGINEERING_METHOD.md` 与
`docs/LIBXPOSED_API_101_102_COMPATIBILITY.md` 的固定优先级：可运行性 > 行为正确 >
API 101 基线稳定 > 兼容 > 性能 > 可维护性 > 形式。100% Kotlin 不是验收条件。

## 基线

- 基线 commit：`8e596881`（`main`，与 `r14.12.0` 源码零差异，仅文档变化）。
- Phase 0 基线构建（本地，JDK 17 / SDK 37.0 / Build-Tools 37.0.0）：
  `clean test lint assembleDebug assembleRelease lintVitalRelease` 全部通过，14 个单元测试。
- 基线 Release APK：3,020,249 bytes，
  SHA-256 `864C4EFCBF870DEFA4C1D647FA44F247B439FB8364C39F1C5348B460312004A5`
  （本地 debug 签名，仅作为重构对照，不是发布产物）。

## 现状盘点

- 主源码：88 个 Kotlin 文件 + 3 个 Java 文件。
- 保留的 Java 边界（有意保留，属阶段 4 评估范围）：
  - `MainModule.java`（Xposed 稳定入口，R8/`META-INF/xposed` 关联）；
  - `mods/utils/XposedHelpers.java`（Hook 兼容层，含大量 Java 调用方语义）;
  - `org/apache/commons/lang3/reflect/MemberUtilsX.java`（第三方派生类）。
- Java 侧对 Kotlin 的依赖：`MainModule.java` 使用 `PrefMap`、`ModuleHelper`、
  `HookerClassHelper`、`ResourceHooks` 及 `mods` 各 hook 类的静态入口——这些公共
  签名（`@JvmStatic`/`@JvmField`/`@JvmOverloads`）不得改变。
- R8 边界：`proguard-rules.pro` keep 了 `mods.**` 公共静态方法/字段与若干入口类名；
  `utils`/`prefs`/`subs` 包不在 keep 范围内，重构自由度较高，但仍需检查字符串类名。

## 分阶段范围

### 阶段 1（本轮执行）：低风险纯重构 / 正确性修复

仅动设置应用冷路径与已证实的缺陷，不动任何 Hook 语义：

1. `utils/BitmapCachedLoader.kt`：`threadCount` 运算符优先级缺陷修复
   （现算式等价于 `cores / 2`，未夹取到 [2,4]；单核设备为 0 会使
   `ThreadPoolExecutor` 构造抛 `IllegalArgumentException`，≥10 核设备会超出上限）。
   抽出可测试的纯函数并新增回归单元测试。
2. `utils/Helpers.kt`：`performVibration` 恢复可空参数语义（现有 `context == null`
   检查在非空参数下是死代码，来自 Java→Kotlin 迁移；改为 `Context?` 保留宽容行为）。
3. `utils/Helpers.kt`：`getInstalledApps` / `getLaunchableApps` / `getShareApps` /
   `getOpenWithApps` 四个近重复函数合并公共构建逻辑（设置应用冷路径，无 Hook 影响）。

每步独立提交，每步后运行 `test assembleDebug`；阶段末补 Release + Lint 全量验证。

### 阶段 2（后续会话）：设置应用 UI 层现代化

- `subs/`、`prefs/` 中残留的 Java 风格代码（getter/setter、手写 ViewHolder 判空链）
  按证据逐文件小步梳理；不引入 Flow/DataBinding/Compose 等新框架。
- `SubFragment.kt` / `PreferenceFragmentBase.kt` 生命周期路径复查。

### 阶段 3（后续会话）：mods 冷路径梳理

- `mods/System.kt`、`SystemUI.kt`、`Launcher.kt` 超大文件只做"分区注释 + 明确
  死代码删除"，不改 Hook 注册顺序与 before/after 语义；任何删除必须证明无
  字符串/反射/DexKit 引用。

### 阶段 4（需单独评估 + 用户确认）：Java 边界

- `MainModule.java`、`XposedHelpers.java` 是否迁移 Kotlin 需要独立风险评估与
  实机验证窗口；`MemberUtilsX.java` 为第三方派生代码，倾向永久保留 Java。
- 本轮不改变 libxposed API 版本、Hot Reload 状态与构建工具链。

## 明确不做

- 不重命名/移动 `mods.**` 公共静态入口，不缩小/扩大 R8 keep 规则；
- 不改 `META-INF/xposed`、Manifest、applicationId、版本线与签名；
- 不做无证据的热路径"优化"与集合链式化改写；
- 不为覆盖率翻译 `XposedHelpers.java`；
- 不合并 `main`、不打 tag、不发 Release（需用户确认）。

## 验证矩阵

每个代码提交：`test assembleDebug`。
每个阶段收尾：`clean test lint assembleDebug assembleRelease lintVitalRelease` +
Release APK 大小/SHA-256 对照。实机验证本环境不可用，统一标注"未实机验证"，
清单见 `docs/REFACTOR_PROGRESS.md`。

## 状态说明

- 当前分支：`devin/r14.13-kotlin-refactor`
- 当前 HEAD：`41b336ed2329fb224be79441a471be9830829e81`
- 相对 `main`：ahead 34 / behind 0
- 当前目标版本：`r14.13.3` / versionCode `181`
- 当前构建签名：仅正式签名（`../keystore.properties` + `v2` signingConfig），缺少 keystore 时构建直接失败，不回退 Debug 签名
- 当前完成阶段：Phase 0–5 + Phase 5+（UI/Locale/Root/资源/CRLF 修复）已合并推进到 `r14.13.3`
- 当前未完成：当前 HEAD 的实机验证、完整正式签名构建矩阵复验、后续 Phase E（受控 Java → Kotlin）尚未启动

## 实际执行结果与计划偏移

本文件上半部分为原始计划，应保留为基线记录。下列内容说明实际执行中超出原计划的范围与原因。

### Manifest

- **原计划**："不改 Manifest"。
- **实际偏移**：修改了 `app/src/main/AndroidManifest.xml`。
- **原因**：为了让 `MainActivity` 在 Locale、layoutDirection 和 uiMode 变化时不被系统 recreate，必须在 `MainActivity` 上显式声明 `android:configChanges="locale|layoutDirection|uiMode"`。不修改 Manifest 就无法实现应用内语言切换和日夜间主题切换无闪黑。

### 版本线

- **原计划**：版本目标为 `r14.13.0`。
- **实际偏移**：推进到 `r14.13.3` / versionCode `181`。
- **原因**：在 rc 验证过程中逐步迭代了 `r14.13.0-rc2`（code 176）、`r14.13.0-rc3`（code 177）、`r14.13.0`（code 178），并继续修复浅色状态栏、语言切换、搜索状态、Root 重启、Preference 两行标题和资源清理后，Version 提升到 `r14.13.3`（code 181），以区分中间 rc 状态。

### 签名

- **原计划**："不改 ... 签名"。
- **实际偏移**：`app/build.gradle.kts` 取消缺少正式 keystore 时回退 Debug 证书的行为。
- **原因**：避免把 Debug 签名 APK 误作 Release 候选；确保每个 `release`/`develop` 构建都使用仓库外部 `../keystore.properties` 指定的正式 `v2` signingConfig。该签名配置本身位于仓库外部，不提交密钥材料到仓库。

### Locale 与应用内语言

- **原计划**：阶段 2 仅做 "`subs/`、`prefs/` 中 Java 风格代码清理"。
- **实际偏移**：新增并实现了完整的应用内语言切换（11 种语言 + 跟随系统）。
- **原因**：浅色状态栏修复和主题切换需要 `MainActivity`/`MainFragment`/`PreferenceFragmentBase` 协同处理 `locale|uiMode` configChanges；在验证过程中发现语言选项分散、每次切换 recreate/闪黑，因此把 Locale 选项移到主设置页，并使用 `AppCompatDelegate.setApplicationLocales` 实现无 recreate 切换。这超出了原 UI 层小步梳理的范围，但属于同一工作线的合理延伸。

### Root 重启

- **原计划**：未包含 Root 重启改动。
- **实际偏移**：`AppHelper` 中 Launcher、SystemUI、Security Center 重启改为后台 Root shell 执行。
- **原因**：设置页右上角菜单的 "重启" 操作原来可能阻塞 UI 或在无 Root 时崩溃。为了让状态栏/语言切换后的验证流程可靠，需要把 Root 命令放到后台、处理 `pidof` 无结果/多 PID/非零退出码/无 Root 等情况，并给出用户反馈。

### UI、主题与资源

- **原计划**：阶段 2 不做 "新框架"，只做 UI 层 Kotlin 风格清理。
- **实际偏移**：实际调整了 `MainActivity`、多个 layout/XML 资源、Toolbar、Preference、About 页面、颜色、间距、圆角、Preference title 最大行数，并清理了 70+ 个未使用字符串/数组资源。
- **原因**：`REFACTOR_PLAN`  작성 시点是 `r14.13.0-rc1` 之后，但 rc1/rc2 实机和日志反馈暴露出浅色状态栏图标反色、语言切换闪黑、搜索返回状态错乱、About/Preference 标题截断等问题。修复这些问题需要修改 Activity 生命周期、资源 XML 和 Theme/Style，因此 UI 工作超出了原计划 "Java 风格代码清理" 的范围，但方向一致且未引入 Flow/DataBinding/Compose 等新框架。

### 结论

上述偏移均来自 `r14.13.0-rc` 系列的实机/日志反馈，不是为迁移而迁移。所有改动仍遵守：
- 不修改 `mods.**` 公共静态入口与 R8 keep 规则；
- 不修改 `META-INF/xposed`、applicationId、libxposed API 版本；
- 不合并 `main`、不打 tag、不发 Release（需用户确认）。

下一步必须在当前 HEAD 完成正式签名构建矩阵复验与实机回归，再决定 Phase E / PR / 合并 / tag / Release。
