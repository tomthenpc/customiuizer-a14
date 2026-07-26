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
- `clean test lint assembleDebug assembleRelease lintVitalRelease` 通过，14 tests。
- Release APK：3,020,249 bytes，
  SHA-256 `864C4EFCBF870DEFA4C1D647FA44F247B439FB8364C39F1C5348B460312004A5`。

## 提交记录

| # | commit | 内容 | 验证 |
| --- | --- | --- | --- |
| 1 | (本提交) | 计划与进度文档 | `git diff --check`，UTF-8 |

## 阶段 1 待办

- [ ] `BitmapCachedLoader` threadCount 优先级缺陷修复 + 回归测试
- [ ] `Helpers.performVibration` 可空参数语义恢复
- [ ] `Helpers` 四个应用列表构建函数去重
- [ ] 阶段收尾全量构建 + APK 对照

## 未实机验证清单（累积）

- 阶段 1 全部变更（设置应用冷路径）：需要实机确认应用列表选择页
  （普通/分享/打开方式）加载正常、图标加载无崩溃、振动反馈正常。
