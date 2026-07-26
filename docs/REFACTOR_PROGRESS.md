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

## 待办

- [x] `BitmapCachedLoader` threadCount 优先级缺陷修复 + 回归测试
- [x] `Helpers.performVibration` 可空参数语义恢复
- [x] `Helpers` 四个应用列表构建函数去重
- [x] 阶段 1 收尾全量构建 + APK 对照
- [x] 阶段 2：`subs/`、`prefs/` UI 层逐文件梳理与去重 + 收尾构建
- [ ] 阶段 3：mods 冷路径死代码证明与清理（后续会话）
- [ ] 阶段 4：Java 边界评估（需用户确认）

## 未实机验证清单（累积）

- 阶段 1 全部变更（设置应用冷路径）：需要实机确认应用列表选择页
  （普通/分享/打开方式）加载正常、图标加载无崩溃、振动反馈正常。
- 阶段 2 全部变更（preference/subs UI 显示、BT/WiFi 列表页、颜色/应用选择器、
  状态标记/标题高亮）：需要实机确认设置页滚动/搜索高亮、各选择器打开与返回、
  BT/WiFi 列表刷新与点击选择正常。
