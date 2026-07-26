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
