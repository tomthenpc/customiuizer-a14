# r14.13 重构进度

分支：`devin/r14.13-kotlin-refactor`。计划见 `docs/REFACTOR_PLAN_r14.13.md`。

## 环境备忘（本地 Windows）

- JDK 17：`C:\Program Files\Java\jdk-17`（`.tools\jdk-17` 为指向它的 junction，
  修复了失效的 `JAVA_HOME`）。
- Android SDK：`c:\Users\tv\Downloads\Peengeek\.tools\android-sdk`
  （`platforms;android-37.0`、`build-tools;37.0.0`、platform-tools，本轮重新安装）。
- 构建命令需带 `$env:JAVA_HOME='C:\Program Files\Java\jdk-17'`。
- 本地无签名 keystore，Release 使用 debug 签名，仅作构建对照。

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
  （本地 debug 签名，仅对照用）。

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
