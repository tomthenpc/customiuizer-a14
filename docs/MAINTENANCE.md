# 维护与发布记录

本文件记录当前开发线的发布状态、已验证证据、延期任务和发布阻塞项。它服务于发布收口，不替代源码、构建产物或实机日志。

---

## 当前版本

- `versionName`：`r14.15.0`
- `versionCode`：`188`
- `applicationId`：`tv.withaibuild.customiuizer.r14`
- 运行时代码基线：与已验证的 `r14.13.9` 保持一致

---

## 已验证证据

- `r14.13.9 (187)` 在真机日志中成功加载 `system`（即 `system_server`）、`com.android.systemui`、`com.miui.home`。
- `system_server` 的 `onSystemServerStarting` 已执行，`installed=40`，所有错误计数为 0。
- `SystemUI` 最终 `post-init` `installed=44`，所有错误计数为 0。
- `Launcher` 最终 `post-attach` `installed=12`，所有错误计数为 0。
- `Toast` 禁用功能在真机确认有效，对应 `system_server` 中 `NotificationManagerService.tryShowToast` 的 Hook 路径。
- 当前启动日志中未发现本模块导致的崩溃、ANR、Watchdog 或进程循环重启。

---

## 延期任务：人工冒烟测试

状态：

- 不作为当前 `r14.15.0` 发布的阻塞项；
- 不连接 ADB；
- 不操作设备；
- 不要求用户现在执行；
- 仅当用户以后明确提出“开始人工冒烟测试”时才执行。

待测试功能组（按优先级排序）：

1. 电源键、音量键、导航键动作；
2. AppLock、锁屏和强认证；
3. 自由窗口、旋转和窗口管理；
4. 音频、震动和来电处理；
5. 安全策略、安装策略、壁纸和 Global Actions。

注意：`installed=40` 仅代表当前配置下的 Hook 安装诊断记录，不能表述为 40 个独立功能均已人工验证。不得把离线测试或模拟测试称为真机冒烟通过。

---

## 发布代码冻结

`r14.15.0` 必须保持与已验证的 `r14.13.9` 相同的运行时代码。

允许修改：

- `versionName` / `versionCode`；
- CI 产物名称；
- `CHANGELOG`；
- 本维护文档；
- 发布摘要。

禁止修改：

- `MainModule`；
- Hook 实现；
- 偏好读取逻辑；
- `HookDiagnostics`；
- 性能优化；
- R8 规则；
- `AndroidManifest.xml`；
- Xposed 作用域（除非只是验证 `system` 已存在）。

若发现必须修改运行时代码，立即停止发布并报告，因为现有真机证据将不再对应最终版本。

---

## 离线发布门禁

发布前必须运行：

```bash
python tools/check-invariants.py
python -m unittest discover -s tools/tests -p "test_*.py"
.\gradlew.bat clean test lintDebug lintRelease lintVitalRelease assembleDebug assembleRelease --no-daemon
```

验证项：

- `versionName = r14.15.0`
- `versionCode = 188`
- `applicationId = tv.withaibuild.customiuizer.r14`
- `minSdk / targetSdk = 34 / 34`
- `debuggable = false`
- ABI = `arm64-v8a`
- `scope.list` 包含 `system`、`android`、`com.android.systemui`、`com.miui.home`
- 普通 `assembleRelease` 仍为 `unsigned-ci`
- 正式构建使用 `officialRelease=true`
- Release v2 签名和 zipalign 通过
- Release `java_init.list` 经 `mapping.txt` 映射到 `MainModule`

---

## 发布状态表述

允许写：

- `r14.13.9` 相同运行时代码已完成 Android 14 / HyperOS 1 真机核心验证；
- `system_server`、`SystemUI`、`Launcher` 加载和 Hook 安装无错误；
- `Toast` 禁用功能已确认恢复；
- 离线发布门禁通过。

必须同时写：

- 未完成全部功能组合的人工冒烟测试；
- 延期人工冒烟不阻塞当前发布；
- `r14.15.0` 不能声称所有功能均已逐项验证。
