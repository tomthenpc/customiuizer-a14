# DEVIN A14 CHECKPOINT

> 本文件记录当前开发分支的真实状态，不是长期规则。
> 开始任务时必须用本地 Git 和远端分支重新核对。
> 每完成一个代码、构建、Git 或实机闭环，立即替换更新；不要追加命令流水账。

## 当前唯一主目标

完成 `devin/r14.13-kotlin-refactor` 当前 `r14.13.3` 代码线的文档同步、完整正式签名构建和针对最新 UI/Locale/资源变更的实机回归。在获得当前 HEAD 的完整证据前，不合并 `main`、不打 tag、不创建 Release。

## 当前 Git 基线

- Repository: `tomthenpc/customiuizer-a14`
- Active branch: `devin/r14.13-kotlin-refactor`
- Remote branch HEAD: `b63ec5f3360e09519f894f81b42d91ad9f336603`
- Local HEAD: `b63ec5f3360e09519f894f81b42d91ad9f336603`
- HEAD subject: `docs: sync REFACTOR_PROGRESS, REFACTOR_PLAN and DEVIN_A14_CHECKPOINT to r14.13.3 HEAD`
- Base branch: `main`
- Merge base / main HEAD: `8e596881419938d0edb96a8e466dc8e1e970894a`
- Compared with main: ahead 35 / behind 0
- Branch status: active development branch; do not switch to main or create another branch

## 当前构建身份

- versionName: `r14.13.3`
- versionCode: `181`
- applicationId: `tv.withaibuild.customiuizer.r14`
- namespace: `tv.withaibuild.customiuizer`
- Platform: HyperOS 1 / Android 14 / SDK 34
- minSdk / targetSdk: 34 / 34
- compileSdk / Build Tools: 37 / 37.0.0
- ABI: `arm64-v8a`
- libxposed: min 101 / target 102
- Hot Reload: disabled
- Legacy Xposed runtime API: forbidden

## 签名状态

当前 `app/build.gradle.kts`：

- 从仓库外部 `../keystore.properties` 读取正式签名配置
- 文件缺失时配置阶段直接抛出 `GradleException`
- `develop` 和 `release` 都固定使用正式 `v2` signingConfig
- 已取消缺少正式配置时回退 Debug 证书的行为

要求：

- 不得继续引用“无 keystore 时 Release 使用 Debug 签名”的旧结论
- 每个候选 APK 必须检查实际证书 SHA-256
- 当前新签名证书 SHA-256 记录为：
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`
- `r14.12.0` 及更早版本旧私钥已遗失；新签名不能覆盖旧签名安装
- 升级文档必须要求备份设置、卸载旧版、安装新版

## 已完成：Phase 0–5

### Phase 0
- `r14.12.0` 基线建立
- 全量测试、Lint、Debug/Release 和 `lintVitalRelease` 通过

### Phase 1–3
- 修复 BitmapCachedLoader threadCount 边界并新增 3 个回归测试
- 恢复振动函数 `Context?` 宽容语义
- 合并四个应用列表构建路径
- 清理设置层重复 Java 风格代码和已证明无引用的死代码
- 未改变 Hook target、注册顺序或兼容 fallback

### Phase 4
- 评估并保留 3 个 Java 边界：
  - `MainModule.java`
  - `XposedHelpers.java`
  - `MemberUtilsX.java`
- 不再以 100% Kotlin 为目标

### Phase 5
- 网络速度格式化使用 `Locale.ROOT`
- StatusBarGesturesHook 缓存 DisplayManager/displayId，减少移动事件重复反射
- 其他热路径没有明确低风险收益时不强行修改
- 阶段收尾记录：36 个单元测试、88 个 Kotlin、3 个 Java
- 阶段 5 全量测试、Lint、Debug/Release 通过
- 上述性能结论主要是静态路径判断，未做同条件 systrace/功耗量化

## Phase 5 后新增工作

### RC1 与日志审计
- 生成并审计 `r14.13.0-rc1`
- 日志文件：17,707,825 bytes / 120,759 行
- 日志 SHA-256：
  `5889427B742A95FEFD69E33570D7DC6E5F8964073AD7C836D2F27D9C3CE03646`
- 未发现可归因于模块的 P0/P1/P2 异常、Hook 失败、RemotePreferences 异常或模块崩溃
- 限制：日志仅明确观察到模块在 `com.android.settings` 加载；SystemUI 与 Launcher 没有在日志期间重新加载
- 该日志只证明 rc1 对应范围，不能证明当前 `r14.13.3` HEAD

### 应用内语言
- 语言选项移到主设置页
- 支持英文、简中、繁中、俄语、日语、越南语、捷克语、葡萄牙语、土耳其语、西班牙语和跟随系统
- 使用 `AppCompatDelegate.setApplicationLocales`
- Locale/configuration 变化时重新加载 Preference
- `MainActivity` 处理 `locale|layoutDirection|uiMode`
- Locale key 不同步到 Xposed RemotePreferences

### 设置应用行为修复
- 搜索结果进入功能后，返回主页面而不是重新展开搜索
- 保存并恢复搜索状态，避免 Fragment 重建状态错乱
- Launcher、SystemUI、Security Center 重启改为后台 Root shell 命令
- 增加无 Root、目标未运行和失败反馈
- Root 命令尚需当前 HEAD 实机覆盖成功与失败路径

### UI、主题与资源
- 恢复日间/夜间状态栏和导航栏图标明暗
- 调整 Toolbar、Preference、About 页面、弹窗、颜色、间距和圆角
- Preference title 最大行数调整为 2
- 清理 70+ 已判定未使用的字符串及数组资源
- 修改过的 XML 行尾统一为 LF

### 版本推进
- `r14.13.0-rc2` / code 176
- `r14.13.0-rc3` / code 177
- `r14.13.0` / code 178
- 当前 `r14.13.3` / code 181

## 当前文档冲突

### `CHANGELOG.md`
- 顶部仍写 `r14.13.0-rc1 - Unreleased`
- 未覆盖 rc2、rc3、r14.13.0、r14.13.3 以及后续 UI/Locale/Root 重启/资源清理
- 当前不能作为 `r14.13.3` 完整变更说明

### `docs/REFACTOR_PROGRESS.md`
- 本轮已同步：保留 Phase 0–5 历史，新增 Phase 5+（RC1 日志、应用内语言、搜索状态、Root 重启、UI/主题、Preference 两行标题、资源清理、XML LF、r14.13.3 推进），修正签名表述，更新 HEAD、构建产物与待验证清单。
- 状态：已更新，待 commit。

### `docs/REFACTOR_PLAN_r14.13.md`
- 本轮已同步：保留原始计划，新增“状态说明”与“实际执行结果与计划偏移”，说明 Manifest、版本线、签名、Locale、Root 重启和 UI 工作超出最初计划的原因。
- 状态：已更新，待 commit。

### `docs/VERIFICATION.md`
- 正式验证主体仍对应 `r14.12.0`
- 不得把其 API 101 实机结果直接套用到当前分支

## 最新绿色验证

### 当前 HEAD 证据（`b63ec5f`）

- 构建命令：`$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\gradlew --no-daemon clean test lint lintRelease lintVitalRelease assembleDebug assembleRelease`
- 退出码：`0`（`BUILD SUCCESSFUL in 2m 37s`）
- 单元测试：36
- Lint / `lintRelease` / `lintVitalRelease`：通过
- Release R8、资源压缩、zipalign：通过
- `apksigner verify -v`：V2 签名，1 个签名者
- 签名证书 SHA-256：`C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`
- Release APK：`CustoMIUIzer-A14-r14.13.3.apk`
- APK 大小：3,039,311 bytes
- APK SHA-256：`FCF048906551ED6BFEC903B1FFCD44796A2A5B777D0327CC5EC16FE520381927`
- `aapt2 dump badging` 确认 `applicationId='tv.withaibuild.customiuizer.r14'`，`versionCode=181`，`versionName='r14.13.3'`，`minSdk=34`，`targetSdk=34`
- `module.prop` 与 `META-INF/xposed/java_init.list` 元数据正确（R8 `-adaptresourcefilecontents` 会更新入口类名）

### LSPosed 日志审计（r14.13.3 重启日志）

- 日志路径：`C:\Users\tv\Downloads\Peengeek\LSPosed_log\r14\r14.13.3\LSPosed_2026-07-27T20_48_50.619383`
- 未在 `full.log`、`modules_*.log` 及 `tombstones` 中发现模块包名与崩溃/ANR/Hook 失败/RemotePreferences 异常的直接关联
- 模块在 system_server、com.android.settings、com.miui.home 等目标作用域成功加载
- R8 混淆后入口类在 LSPosed 日志中显示为 `class cp`，属 `-repackageclasses` 与 `-adaptresourcefilecontents` 的预期行为
- 大量 `SmartPower.DisplayPolicy`、`PackageConfigPersister` 等系统侧日志含模块包名，但均为 ROM 正常组件信息/配置查询，非模块异常

### 仍需补充

- 设置 UI/Locale/About 主题 实机回归；
- Root 重启功能 实机回归；
- SystemUI/Launcher Hook 实机日志；
- API 102 独立环境验证。

## 当前待验证清单

### 构建与产物
- 正式签名环境下运行完整测试、Lint、Debug、Release、R8、resource shrink
- 核对 versionName/versionCode、Xposed metadata、scope、ABI、zipalign
- 记录 APK 文件名、大小、SHA-256 和签名证书 SHA-256
- 确认资源删除没有导致 Release 资源缺失

### 设置应用
- 日间/夜间主题和系统栏图标
- 主页面、子页面、搜索、返回栈、旋转和重建
- Locale 切换与跟随系统，不出现空白、重复、旧语言残留或循环重建
- Preference title 两行、summary、Switch、弹窗和 About 页面
- 普通/分享/打开方式选择器
- BT/WiFi 列表刷新和点击
- 备份/恢复和重置设置

### Root 重启
- 有 Root：Launcher、SystemUI、Security Center
- 无 Root
- 目标进程未运行
- 多 PID
- 命令失败和错误输出
- Fragment/Activity 退出时不回调失效 UI

### Hook 与日志
- 完整重启后采集模块、system_server、SystemUI、Launcher 日志
- 网络速度在非英语 Locale 下不使用逗号小数点
- 状态栏滑动调节亮度/音量、长按、双击
- 无重复 Hook、Receiver、Observer、Coroutine 或初始化
- 无模块崩溃、ANR、链接错误和 RemotePreferences 异常

### API 102
- 独立 API 102 框架环境验证
- 不以 API 101 结果替代

## 当前阻塞

- 完整 `r14.13.3` 候选构建矩阵已在本机通过；缺少实机安装、整机重启和 LSPosed/Vector 完整日志审计
- 当前 `r14.13.3` 缺少完整设置 UI/Locale/Root 重启实机回归
- API 102 实机环境仍未确认
- 由于缺少实机闭环，暂不 bump 到 `r14.13.4/182`

## 下一步

1. 安装 `app/build/outputs/apk/release/CustoMIUIzer-A14-r14.13.3.apk`（SHA-256 `FCF048906551ED6BFEC903B1FFCD44796A2A5B777D0327CC5EC16FE520381927`）完成实机回归；
2. 整机重启后采集 LSPosed/Vector `full.log` 和 `modules_*.log`，确认 SystemUI / Launcher / Settings / system_server 中模块加载与 Hook 行为正常；
3. 完成 API 102 框架环境独立验证；
4. 实机验证全部通过后，再决定 bump 到 `r14.13.4/182` 并重新构建；
5. 全部验证通过后再考虑 PR / 合并 `main` / tag / Release。

任何一步失败都先修根因，不得继续版本 bump 或准备发布。

## 发布状态

- 当前分支已合并 main：否
- 当前分支相对 main：ahead 35 / behind 0
- `r14.13.3` tag：未创建（仅工作区候选构建）
- `r14.13.3` GitHub Release：未创建
- 当前 HEAD 正式 APK：已构建，SHA-256 `FCF048906551ED6BFEC903B1FFCD44796A2A5B777D0327CC5EC16FE520381927`，V2 签名正确
- 可以称为公开稳定版：否（缺少实机回归与 API 102 验证）
- 当前公开稳定基线仍应以 `r14.12.0` 及其已发布证据为准
