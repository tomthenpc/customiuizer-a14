# r14.13 重构进度

分支：`devin/r14.13-kotlin-refactor`。计划见 `docs/REFACTOR_PLAN_r14.13.md`。

## 环境备忘（本地 Windows）

- JDK 17：`C:\Program Files\Java\jdk-17`（`.tools\jdk-17` 为指向它的 junction，
  修复了失效的 `JAVA_HOME`）。
- Android SDK：`c:\Users\tv\Downloads\Peengeek\.tools\android-sdk`
  （`platforms;android-37.0`、`build-tools;37.0.0`、platform-tools，本轮重新安装）。
- 构建命令需带 `$env:JAVA_HOME='C:\Program Files\Java\jdk-17'`。
- 签名：`app/build.gradle.kts` 从仓库外部 `../keystore.properties` 读取正式签名配置；缺失时构建阶段抛出 `GradleException`；`develop` 和 `release` 均固定使用正式 `v2` signingConfig，不再回退 Debug 证书；每个候选 APK 必须校验实际签名证书 SHA-256。
- 当前 HEAD：`b63ec5f3360e09519f894f81b42d91ad9f336603`（`docs: sync REFACTOR_PROGRESS, REFACTOR_PLAN and DEVIN_A14_CHECKPOINT to r14.13.3 HEAD`，checkpoint 同步），相对 `main`：ahead 35 / behind 0。工作区另有未提交修改（UI、About、DexKitBridge 守护、AGENTS/.devin、文档同步），将在当前会话提交。

## Phase 0 基线（已完成）

- 基线 commit `8e596881` = `r14.12.0` 源码（差异仅文档）。
- `clean test lint assembleDebug assembleRelease lintVitalRelease` 通过，33 个单元测试。
- Release APK：3,020,249 bytes，
  SHA-256 `864C4EFCBF870DEFA4C1D647FA44F247B439FB8364C39F1C5348B460312004A5`。

## 提交记录

| # | commit | 内容 | 验证 |
| --- | --- | --- | --- |
| 1 | `dffa1c46` | 计划与进度文档 | `git diff --check`，UTF-8 |
| 2 | `c3405daf` | `BitmapCachedLoader` threadCount 优先级缺陷修复 + 3 个回归测试 | `test assembleDebug` 通过 |
| 3 | `8c7ca517` | `Helpers` 振动函数恢复 `Context?` 宽容语义（死判空来自迁移） | `test assembleDebug` 通过 |
| 4 | `d8b49af4` | `Helpers` 四个应用列表构建函数去重（-39 行，行为不变） | `test assembleDebug` 通过 |
| 5 | `c718e125` | 提取 preference 状态标记后缀为共享 `Helpers.appendStatusMarker` | `test assembleDebug` 通过 |
| 6 | `fa3a0285` | `prefs`/`subs` 中 `TextUtils.isEmpty` → `isNullOrEmpty()`，移除冗余判空 | `test assembleDebug` 通过 |
| 7 | `7f675a99` | `BTList` 显式泛型 parcelable 与配对设备 `any()` 查找 | `test assembleDebug` 通过 |
| 8 | `12137bd4` | `WiFiList` 网络状态 `when` 分发与 `convertView ?: inflate` | `test assembleDebug` 通过 |
| 9 | `08f932fe` | `subs` 中冗余 `java.util.ArrayList/HashMap/LinkedHashSet` 导入清理 | `test assembleDebug` 通过 |
| 10 | `14861202` | 删除未使用 `ModuleHelper.printCallStack`（全仓库无调用） | `test assembleDebug` 通过 |
| 11 | `8fa0f237` | 删除未使用 `ModuleHelper.stringifyBundle`（全仓库无调用） | `test assembleDebug` 通过 |
| 12 | `a507c008` | 删除 `SystemClockHooks` 冗余 `java.util.ArrayList` 导入 | `test assembleDebug` 通过 |

## 阶段 1 收尾验证（已完成）

- `clean test lint assembleDebug assembleRelease lintVitalRelease` 全部通过；
  单元测试 36 个（基线 33 + 新增 3）。
- Release APK：3,020,249 bytes（与基线字节数相同），
  SHA-256 `7CCF0AEA7FCD5F69816D4AF10A9F804ED23AF89A09ABE4E425D3BA6BD702767E`
  （本地 debug 签名，仅对照用）。

## 阶段 2 收尾验证（已完成）

- `clean test lint assembleDebug assembleRelease lintVitalRelease` 全部通过；
  单元测试 36 个。
- Release APK：3,020,249 bytes（与基线字节数相同），
  SHA-256 `BD89870B3A2B9F338E8E939ED346782B65C54D0095402F8F6654FC833EB02BD7`
  （本地 debug 签名，仅对照用）。

## 阶段 3 收尾验证（已完成）

- `clean test lint assembleDebug assembleRelease lintVitalRelease` 全部通过；
  单元测试 36 个。
- Release APK：3,020,249 bytes（与基线字节数相同），
  SHA-256 `6FAD6CE6CBC7EE8D17461B98BFE32722AD7358E3B634AF384ACB5C98D310EF93`
  （本地 debug 签名，仅对照用）。
- 本次删除项均经全仓库引用搜索确认无调用，且未在 ProGuard/R8、Manifest、
  `META-INF/xposed` 或 DexKit/反射字符串中出现；未触碰 Hook target 或兼容 fallback。

## 阶段 4 Java 边界评估（已完成）

- 评估文档：`docs/JAVA_BOUNDARY_ASSESSMENT_r14.13.md`。
- 结论：剩余 3 个 Java 源文件（`MainModule.java`、`XposedHelpers.java`、
  `MemberUtilsX.java`）均位于 libxposed 入口、反射/Hook 或第三方兼容边界，
  风险不可控，明确保留 Java。
- `PreferenceFragmentBase.java` 与 `Credentials.java` 已分别由上游迁移为
  `PreferenceFragmentBase.kt` 与 `Credentials.kt`，无需处理。
- 未执行任何 Java → Kotlin 迁移，未触碰 Hook target、API 边界、R8 keep 规则。
- `clean test lint lintRelease lintVitalRelease assembleDebug assembleRelease` 通过。

## 阶段 5 热路径审计与收口（已完成）

- 审计文档：`docs/HOT_PATH_AUDIT_r14.13.md`。
- 修复 1：`SystemUI.kt` 网络速度 `humanReadableByteCount` 使用 `Locale.ROOT`，
  避免默认 Locale 产生逗号小数点；减少 `String.format` 调用次数。
- 修复 2：`SystemUI.kt` `StatusBarGesturesHook` 在 `ACTION_DOWN` 缓存
  `mDisplayManager` 和 `mDisplayId`，避免 `ACTION_MOVE`/`ACTION_UP` 每次反射取字段。
- 其余热路径（电池/温度/充电/通知/音量/时钟/触摸等）经审计未发现同时满足
  "明确收益" 与 "低风险" 的修改点，未强行改代码。
- `clean test lint lintRelease lintVitalRelease assembleDebug assembleRelease` 通过。
- 单元测试 36 个；Java 源文件 3 个；Kotlin 源文件 88 个。
- Release APK：3,020,249 bytes，SHA-256
  `82265AAEB106BECC0B90DB1F1DBA36D3C1E0BE436264645EE169F3C78AE3AE6F`
  （该构建为历史本地 debug 签名对照；当前分支已改为仅正式签名，见环境备忘）。

## Phase 5+ 后续修复与 r14.13.3 推进

在 Phase 5 基线之上，针对 UI/Locale/Root 重启/资源清理进行了后续会话修复，最终推进到 `r14.13.3`（versionCode 181）。

- 当前 HEAD：`b63ec5f3360e09519f894f81b42d91ad9f336603`（`docs: sync REFACTOR_PROGRESS, REFACTOR_PLAN and DEVIN_A14_CHECKPOINT to r14.13.3 HEAD`，checkpoint 同步），相对 `main`：ahead 35 / behind 0。当前会话新增未提交修改。

### RC1 日志审计

- 审计文档：`docs/RC1_LOG_AUDIT_r14.13.md`。
- 日志文件：17,707,825 bytes / 120,759 行，SHA-256 `5889427B742A95FEFD69E33570D7DC6E5F8964073AD7C836D2F27D9C3CE03646`。
- 未发现可归因于模块的 P0/P1/P2 异常、Hook 失败、RemotePreferences 异常或模块崩溃。
- 限制：日志仅明确观察到模块在 `com.android.settings` 加载；SystemUI 与 Launcher 在日志期间未重新加载。
- 该日志只证明 rc1 对应范围，不能证明当前 `r14.13.3` HEAD。

### 应用内语言

- 语言选项移到主设置页。
- 支持英文、简中、繁中、俄语、日语、越南语、捷克语、葡萄牙语、土耳其语、西班牙语和跟随系统。
- 使用 `AppCompatDelegate.setApplicationLocales`。
- `MainActivity` 处理 `locale|layoutDirection|uiMode` configChanges；`PreferenceFragmentBase` 在 Locale 变化时重新加载 Preference。
- Locale key 不同步到 Xposed RemotePreferences。

### 搜索与返回状态

- 搜索结果进入功能后返回主页面，而不是重新展开搜索。
- 保存并恢复搜索状态，避免 Fragment 重建状态错乱。

### Root 重启

- Launcher、SystemUI、Security Center 重启改为后台 Root shell 命令。
- 增加无 Root、目标未运行和失败反馈。
- Root 重启功能尚需当前 HEAD 实机覆盖成功与失败路径。

### UI、主题与资源

- 恢复日间/夜间状态栏和导航栏图标明暗（`MainActivity.applySystemBarsAppearance()`、`WindowInsetsControllerCompat`、`styles.xml` 的 `windowLightStatusBar`/`windowLightNavigationBar`）。
- 调整 Toolbar、Preference、About 页面、弹窗、颜色、间距和圆角。
- Preference title 最大行数调整为 2（`pref_item.xml`）。
- 清理 70+ 已判定未使用的字符串及数组资源；`lint` `UnusedResources` 降为 0。
- 修改过的 XML 行尾统一为 LF（7 个资源文件）。

### 版本推进

- `r14.13.0-rc2` / code 176
- `r14.13.0-rc3` / code 177
- `r14.13.0` / code 178
- `r14.13.3` / code 181

### 构建与产物

- 完整矩阵 `clean test lint lintRelease lintVitalRelease assembleDebug assembleRelease` 通过。
- 退出码：`0`（`BUILD SUCCESSFUL in 2m 37s`）。
- 单元测试 36 个。
- Release APK：`3,039,311` bytes，SHA-256 `FCF048906551ED6BFEC903B1FFCD44796A2A5B777D0327CC5EC16FE520381927`。
- 签名证书 SHA-256：`C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`（V2 Signer，使用仓库外部 `../keystore.properties` 指定的正式签名配置）。
- `apksigner verify -v` 确认 V2 签名与 1 个签名者；`aapt2 dump badging` 确认 applicationId、versionCode、`minSdk`/`targetSdk` 正确。
- 该 APK 为当前 `r14.13.3` 候选构建产物；缺少 `keystore.properties` 时构建会抛出 `GradleException`，不再回退 Debug 签名。
- 本轮构建曾因 `prefs_about.xml` 缺少 `xmlns:miuizer` 命名空间导致 `mergeReleaseResources` 失败；已修复。

### 当前会话修复与验证（`b63ec5f` 工作区）

- 清理设置首页重复语言入口，保留到 About 页面并启用 `valueAsSummary`；同步修改 `prefs_main.xml`、`prefs_about.xml`、`MainFragment.kt`、`AboutFragment.kt`、所有 `values*/strings.xml` 和 `fragment_about_head.xml`。
- About 页面拆分为 `about_maintainer`、`about_based_on`、`about_version` 三行。
- `MainActivity` `configChanges` 移除 `uiMode`，使系统正常重建以刷新日间/夜间主题。
- `XposedHelpers.createBridge` 增加 `bridge != null` 非空守护，避免 DexKitBridge 重复创建；`closeBridge` 同步加空指针保护。
- `AGENTS.md` 新增“0. 任务连续性与中断恢复”章节；`.gitignore` 增加 `/.devin/`。
- 建立 `.devin/ACTIVE_TASK.md` 实时任务文件，用于会话中断恢复。
- 审计 r14.13.3 重启 LSPosed 日志：未在 `full.log`、`modules_*.log` 和 `tombstones` 中发现可归因于模块的崩溃、ANR、Hook 失败或 RemotePreferences 异常；模块在 system_server / Settings / Launcher 等 scope 成功加载。`class cp` 为 R8 `-repackageclasses` 后的预期入口类名。
- 同步更新 `CHANGELOG.md`、`VERIFICATION.md`、`DEVIN_A14_CHECKPOINT.md`、`REFACTOR_PROGRESS.md`。
- 实机 UI/Locale/About/Hook 回归与 API 102 独立验证尚未完成。

## 提交记录（Phase 5 后）

| # | commit | 类型 | 内容 | 验证 |
| --- | --- | --- | --- | --- |
| 17 | `186eb386` | build | bump version to `r14.13.3` (181) | `test assembleDebug` |
| 18 | `7c5ef782` | fix(ui) | 修复浅色/深色状态栏图标与语言切换不 recreate | `test assembleDebug` |
| 19 | `cfbbbe3f` | fix(prefs) | Preference title maxLines 调整为 2 | `test assembleDebug` |
| 20 | `eb590544` | chore(cleanup) | 删除未使用字符串与数组资源 | `test assembleDebug` |
| 21 | `9caa563f` | style | 将 7 个 XML 资源文件 CRLF 规范化为 LF | `assembleDebug` + `git diff --check` |
| 22 | `b63ec5f*` | docs/infra | 增加任务连续性与中断恢复协议，建立 `.devin/ACTIVE_TASK.md` | `git diff --check` |
| 23 | `?` | fix(ui) | 清理首页重复语言入口、About 三行、移除 `uiMode` | 完整构建矩阵 |
| 24 | `?` | fix(hook) | `XposedHelpers.createBridge` 非空守护，避免 DexKitBridge 重复创建 | 完整构建矩阵 |
| 25 | `?` | docs | 同步 CHANGELOG/VERIFICATION/DEVIN_A14_CHECKPOINT/REFACTOR_PROGRESS | 文档审阅 |

> 注：第 17–19 项涉及 `MainActivity`、`MainFragment`、`PreferenceFragmentBase`、`AppHelper` 及资源调整，未改变 Hook target、R8 keep 规则或 libxposed API 边界；
> 第 20–21 项仅影响资源/行尾，行为不变。

## 提交记录（Phase 1–5）

| # | commit | 类型 | 内容 | 验证 |
| --- | --- | --- | --- | --- |
| 1 | `dffa1c46` | 文档 | 计划与进度文档 | `git diff --check` |
| 2 | `c3405daf` | 正确性修复 | `BitmapCachedLoader` threadCount 优先级缺陷修复 + 3 个回归测试 | `test assembleDebug` |
| 3 | `8c7ca517` | 正确性修复 | `Helpers` 振动函数恢复 `Context?` 宽容语义 | `test assembleDebug` |
| 4 | `d8b49af4` | 维护性重构 | `Helpers` 四个应用列表构建函数去重 | `test assembleDebug` |
| 5 | `c718e125` | 维护性重构 | 提取 preference 状态标记后缀为 `Helpers.appendStatusMarker` | `test assembleDebug` |
| 6 | `fa3a0285` | 维护性重构 | `prefs`/`subs` 中 `TextUtils.isEmpty` → `isNullOrEmpty()` | `test assembleDebug` |
| 7 | `7f675a99` | 维护性重构 | `BTList` 显式泛型 parcelable 与配对设备 `any()` 查找 | `test assembleDebug` |
| 8 | `12137bd4` | 维护性重构 | `WiFiList` 网络状态 `when` 分发与 `convertView ?: inflate` | `test assembleDebug` |
| 9 | `08f932fe` | 维护性重构 | `subs` 中冗余 `java.util.ArrayList/HashMap/LinkedHashSet` 导入清理 | `test assembleDebug` |
| 10 | `14861202` | 维护性重构 | 删除未使用 `ModuleHelper.printCallStack` | `test assembleDebug` |
| 11 | `8fa0f237` | 维护性重构 | 删除未使用 `ModuleHelper.stringifyBundle` | `test assembleDebug` |
| 12 | `a507c008` | 维护性重构 | 删除 `SystemClockHooks` 冗余 `java.util.ArrayList` 导入 | `test assembleDebug` |
| 13 | `3afbd1f3` | 文档 | Java 边界评估 | `clean test lint ... assembleRelease` |
| 14 | `dc09f318` | 文档 | 修正 `MainModule.java` 边界结论表述 | `clean test lint ... assembleRelease` |
| 15 | `773e57dc` | 正确性修复 | 网络速度 `String.format` 使用 `Locale.ROOT` | `test assembleDebug` |
| 16 | `453a8bc2` | 理论性能改进 | 缓存 `StatusBarGesturesHook` 中 `mDisplayManager`/`mDisplayId` | `test assembleDebug` |

> 注：第 16 项为静态分析得出的热路径优化，未经过实机 systrace 测量；
> 其余性能/维护性重构以构建和单元测试验证，未实机测量。

## 待办

- [x] `BitmapCachedLoader` threadCount 优先级缺陷修复 + 回归测试
- [x] `Helpers.performVibration` 可空参数语义恢复
- [x] `Helpers` 四个应用列表构建函数去重
- [x] 阶段 1 收尾全量构建 + APK 对照
- [x] 阶段 2：`subs/`、`prefs/` UI 层逐文件梳理与去重 + 收尾构建
- [x] 阶段 3：mods 冷路径死代码证明与清理 + 收尾构建
- [x] 阶段 4：Java 边界评估与全量构建
- [x] 阶段 5：热路径审计与最终收口 + 全量构建
- [x] 阶段 5+：`r14.13.3` 版本推进、状态栏/语言切换/搜索/Root 重启修复、资源清理、XML 行尾规范化
- [x] 同步 `docs/REFACTOR_PROGRESS.md`、`docs/REFACTOR_PLAN_r14.13.md`、`docs/DEVIN_A14_CHECKPOINT.md` 与当前 HEAD
- [x] 当前 HEAD（`b63ec5f`）完整正式签名构建矩阵（`clean test lint lintRelease lintVitalRelease assembleDebug assembleRelease`）与 APK SHA-256 确认
- [x] LSPosed 日志审计 r14.13.3：未归因模块 P0/P1 崩溃/ANR/Hook 失败
- [ ] 当前 HEAD 实机验证：状态栏图标、语言切换、About 页面、搜索返回、Root 重启、设置页 UI
- [ ] API 102 独立框架环境验证
- [ ] 根据实机结果决定是否 bump `r14.13.4/182` 并重新构建
- [ ] 全部验证通过后决定 Phase E / PR / 合并 `main` / tag / Release

## 未实机验证清单（累积）

- 阶段 1 全部变更（设置应用冷路径）：需要实机确认应用列表选择页
  （普通/分享/打开方式）加载正常、图标加载无崩溃、振动反馈正常。
- 阶段 2 全部变更（preference/subs UI 显示、BT/WiFi 列表页、颜色/应用选择器、
  状态标记/标题高亮）：需要实机确认设置页滚动/搜索高亮、各选择器打开与返回、
  BT/WiFi 列表刷新与点击选择正常。
- 阶段 3 删除 `ModuleHelper.printCallStack`/`stringifyBundle` 和 `SystemClockHooks`
  冗余导入：可构建验证，行为不变；无需单独实机测试（删除的是从未执行的调试/包装代码）。
- 阶段 4 保留 Java 边界：无代码变更，无需单独实机测试。
- 阶段 5 网络速度 `Locale.ROOT`：需要在非英语 Locale 下确认状态栏网络速度
  显示正常（不出现逗号小数点）。
- 阶段 5 `StatusBarGesturesHook` 缓存 `mDisplayManager`/`mDisplayId`：
  需要实机确认状态栏左右滑动调节亮度/音量、长按/双击状态栏动作正常。
- 阶段 5+ 浅色/深色状态栏与导航栏图标：需要实机确认日间/夜间切换、子页面返回、搜索/分类/About 页面状态栏一致。
- 阶段 5+ 应用内语言切换：需要实机确认中文 ↔ English ↔ 跟随系统切换不闪黑、不 recreate、无旧语言残留。
- 阶段 5+ 搜索返回状态：需要实机确认搜索进入子功能后返回主页面状态正确，Fragment 重建不丢失搜索结果。
- 阶段 5+ Root 重启功能：需要实机确认有 Root/无 Root、目标运行/未运行、命令失败等路径反馈正确。
- 阶段 5+ Preference 两行标题与 About/Tail 页面：需要实机确认长标题显示两行、About 页面布局正常。
- 阶段 5+ 资源清理：需要实机确认设置页、弹窗、BT/WiFi 列表等无资源缺失或崩溃。
- 阶段 5+ XML 行尾规范化：已通过 `git diff --check`；行为不变，无需单独实机测试。
