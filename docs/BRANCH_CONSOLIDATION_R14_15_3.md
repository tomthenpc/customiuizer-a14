# A14 分支整合台账（r14.15.3）

主体基线：`origin/integration/a14-r14.15.1`（`9dd52ec1`）。

## 远程分支枚举

```
origin/main                                           de082cc6
origin/hardening/a14-lts-foundation                   4ca0fccc
origin/integration/a14-r14.15.1                       9dd52ec1
origin/devin/r14-netspeed-font-spacing-i18n           49351caf
origin/fix/a14-ui-text-inheritance-and-about-wrap     03b938ab
```

## 拓扑关系

| 分支 | tip SHA | 与主体 merge-base | 相对主体 ahead/behind | 是否被主体包含 | 最终处理 |
|---|---|---|---|---|---|
| `origin/main` | `de082cc6` | `de082cc6` | 65 / 0 | 是 | `contained`，不合并 |
| `origin/hardening/a14-lts-foundation` | `4ca0fccc` | `4ca0fccc` | 8 / 0 | 是 | `contained`，不合并 |
| `origin/integration/a14-r14.15.1` | `9dd52ec1` | - | - | 主体基线 | 作为 `release/r14.15.3` 起点 |
| `origin/devin/r14-netspeed-font-spacing-i18n` | `49351caf` | `a1d6c7f5` | 6 / 3 | 否 | 合并并解决冲突 |
| `origin/fix/a14-ui-text-inheritance-and-about-wrap` | `03b938ab` | `4ca0fccc` | 8 / 2 | 否 | 合并并解决冲突 |

* ahead/behind 计数使用 `git rev-list --left-right --count`。
* `--cherry-pick` 计数：`devin` 6/3（未去重）→ 5/2（去重）；`fix` 去重前后均为 8/2。

## 各分支详情

### 1. `origin/main`

- **merge-base:** `de082cc6`（自身 tip）
- **相对主体:** 主体领先 65 个提交，main 无独立提交
- **独立提交:** 无
- **独立文件:** 无
- **patch-equivalent:** 主体即包含 main 全部历史
- **结论:** `contained`
- **处理:** 不合并；保留为长期默认分支，不用于 r14.15.3 构建

### 2. `origin/hardening/a14-lts-foundation`

- **merge-base:** `4ca0fccc`（自身 tip）
- **相对主体:** 主体领先 8 个提交，hardening 无独立提交
- **独立提交:** 无
- **独立文件:** 无
- **结论:** `contained`
- **处理:** 不合并；主体已继承其全部安全加固

### 3. `origin/integration/a14-r14.15.1`（主体基线）

- **tip:** `9dd52ec1 release: prepare A14 r14.15.1`
- **与主体关系:** 自身即基线
- **包含的关键提交:**
  - `9dd52ec1` release: prepare A14 r14.15.1
  - `66d4aed4` test: cover network speed spacing and resources
  - `7d35fa42` feat(i18n): add dual-row network speed spacing preference
  - `e31d830c` fix(systemui): preserve network speed typeface and add line spacing helper
  - `5b4edaac` fix(system_server): keep receiver early exits inside guard
  - `a11a2669` fix(system_server): guard global action receiver callbacks
  - `a1d6c7f5` release: bump A14 to r14.15.0

### 4. `origin/devin/r14-netspeed-font-spacing-i18n`

- **merge-base:** `a1d6c7f5`
- **相对主体 ahead/behind:** 6 / 3
- **独立提交（`git log --cherry-pick` 右侧）:**
  - `49351caf chore: bump to r14.15.1 and add network speed tests`
  - `d014f420 feat(i18n): complete network speed translations and add row spacing preference`
  - ~~`b13b22f4 fix(systemui): preserve network speed typeface and add line spacing helper`~~ — 与主体 `e31d830c` patch-equivalent，不重复引入
- **patch-equivalent:** `b13b22f4` 与 `e31d830c` 为同一 patch
- **独立文件（`a1d6c7f5...49351caf`）:**
  - `app/build.gradle.kts`
  - `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt`
  - `app/src/main/res/values*/strings.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/xml/prefs_system_detailednetspeed.xml`
  - `app/src/test/java/tv/withaibuild/customiuizer/mods/NetSpeedLineSpacingTest.kt`
  - `feature-semantics/a14.json`
  - `tools/tests/test_netspeed_resources.py`
- **预计冲突/处理要点:**
  - `app/build.gradle.kts`：devin 把版本号改为 r14.15.1，需以 r14.15.3 / 191 为准覆盖。
  - `prefs_system_detailednetspeed.xml` 与 `strings.xml`：devin 对同一功能做了更完整的重写/翻译，主体为最小增量实现；合并时需逐段比对，保留 devin 中尚未被主体采纳的字符串措辞与控件声明，同时避免覆盖主体的安全/结构改动。
  - `feature-semantics/a14.json`：devin 与主体均增加 `system_netspeed_rowspacing` 元数据，需合并语义。
  - `NetSpeedLineSpacingTest.kt` / `test_netspeed_resources.py`：如内容相同，取主体版本；如 devin 有额外用例，补充合并。

### 5. `origin/fix/a14-ui-text-inheritance-and-about-wrap`

- **merge-base:** `4ca0fccc`
- **相对主体 ahead/behind:** 8 / 2
- **独立提交（`git log --cherry-pick` 右侧）:**
  - `03b938ab test(ui): harden text inheritance and about layout invariants`
  - `31384ca1 fix(ui): preserve system text styling and wrap about attribution`
- **patch-equivalent:** 无
- **独立文件（`4ca0fccc...03b938ab`）:**
  - `app/src/main/java/tv/withaibuild/customiuizer/prefs/SeekBarPreference.kt`
  - `app/src/main/res/layout/fragment_about_head.xml`
  - `tools/tests/test_ui_text_invariants.py`
- **预计冲突/处理要点:**
  - `SeekBarPreference.kt`：2 行改动，保留修复。
  - `fragment_about_head.xml`：移除 `ellipsize` 与 `maxLines`，允许 About 页面署名/版本文字自动换行。
  - `test_ui_text_invariants.py`：新增 UI 文本继承与布局测试，直接纳入。

## 冲突处理原则

1. 主体分支 `integration/a14-r14.15.1` 的安全加固、测试体系、发布配置优先。
2. `devin` 与 `fix` 的独立修复需保留；若同一文件被主体与分支同时修改，按语义合并，不整文件 `ours` 或 `theirs`。
3. 版本号最终统一为 `versionName = r14.15.3`、`versionCode = 191`（若 191 已被使用则取历史最大值 +1）。
4. patch-equivalent 提交（`b13b22f4` / `e31d830c`）不重复引入；合并历史以最终实际文件 diff 为准。
5. 合并后统一提交：`chore(integration): consolidate A14 development branches`。

## 验证结果

- `git fsck --full`：通过
- `git bundle create ..\customiuizer-a14-pre-r14.15.3.bundle --all`：通过，bundle 包含所有 refs
- 待后续补充：`check-invariants.py`、Gradle 测试/Lint/R8、签名 APK 验证
