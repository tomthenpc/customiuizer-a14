# RC1 LSPosed 实机日志审计报告

## 日志基本信息

- **日志文件**: `C:\Users\tv\Downloads\Peengeek\LSPosed_log\r14\r14.13.0-rc1\LSPosed_2026-07-26T22_41_18.824470\full.log`
- **SHA-256**: `5889427B742A95FEFD69E33570D7DC6E5F8964073AD7C836D2F27D9C3CE03646`
- **文件大小**: 17,707,825 bytes
- **总行数**: 120,759
- **可解析行数**: 120,740
- **时间范围**: 08:02:25.220 - 22:41:21.942
- **分析工具**: `tools/analyze_lsposed_log.py`（按 `LSPosed 大型日志快速分析规范.txt` 实现）

## 模块加载情况

- `VectorModuleManager` 成功加载 `tv.withaibuild.customiuizer.r14`。
- 加载发生在 `com.android.settings` 进程（pid 21654）。
- 日志期间 `com.android.systemui` 与 `com.miui.home` 已有运行实例，未触发重新加载，因此未观察到这两个目标进程的模块加载记录。

## 候选问题统计

| 优先级 | 数量 | 是否含模块证据 | 处理结论 |
|--------|------|----------------|----------|
| P0     | 6    | 否             | 系统/ROM/其他应用噪声，无需修复 |
| P1     | 1    | 否             | 系统服务启动时序问题，无需修复 |
| P2     | 24   | 部分无         | 未展开，未发现模块堆栈 |
| P3/P4  | 60   | 无             | 归入噪声统计 |

## P0/P1 候选详情

1. **keystore2 watchdog 线程退出**（P0，7 次）
   - 进程：`keystore2`
   - 根因：`keystore2` 内部 watchdog 正常终止，属系统正常日志。
   - 分类：**系统/ROM 噪声**

2. **Launcher.StatusBarController touch intercept**（P0，14 次）
   - 进程：`com.miui.home`
   - 根因：MIUI Launcher 自身触摸拦截调试日志。
   - 分类：**系统/ROM 噪声**

3. **MDC `IllegalArgumentException` 调用 ContentResolver.call**（P0，6 次）
   - 进程：`com.miui.home` 相关 MDC 进程
   - 根因：MIUI 内部组件调用，堆栈无模块源码。
   - 分类：**系统/ROM 噪声**

4. **GestureDispatcher `ControlCenterWindowViewImpl` 手势拦截**（P0，7 次）
   - 进程：`com.android.systemui` 手势分发
   - 根因：MIUI 控制中心手势拦截调试日志。
   - 分类：**系统/ROM 噪声**

5. **NotificationListeners mask intercept**（P0，5 次）
   - 进程：`system_server`
   - 根因：系统通知监听器对 `com.android.mms` 等应用通知的过滤逻辑。
   - 分类：**系统/ROM 噪声**

6. **TaskView `OnGlobalListenerError` / RecentsContainer.setVisibility**（P0，5 次）
   - 进程：`com.miui.home` 最近任务视图
   - 根因：MIUI Launcher 最近任务布局监听异常。
   - 分类：**系统/ROM 噪声**

7. **FingerprintServiceInjectorStubImpl `IllegalStateException` SharedPreferences**（P1，3 次）
   - 进程：`system_server`（指纹服务）
   - 根因：用户未解锁前，`FingerprintServiceInjectorStubImpl` 尝试读取 CE 加密的 SharedPreferences。
   - 分类：**系统/ROM 噪声**

## 模块相关异常

- 在 P0/P1/P2 候选的上下文中 **未出现任何 `tv.withaibuild.customiuizer` 包名、模块类名或模块方法堆栈**。
- 未观察到 `MainModule` 加载失败、`Hook failed`、`Failed to hook`、`RemotePreferences` 异常或模块崩溃。
- `com.android.systemui`、`com.miui.home`、`system_server` 均无因模块引发的崩溃或异常链。

## 分类结论

所有高优先级候选均属于系统服务、MIUI 框架或其他应用的运行噪声，**没有可归因于 `r14.13.0-rc1` 重构的模块问题**。

## 修复与版本决定

- 未对任何业务代码进行修改。
- 不生成 `r14.13.0-rc2`，保留当前 `r14.13.0-rc1`。
- 本次审计提交 `tools/analyze_lsposed_log.py` 和 `docs/LSPOSED_LOG_ANALYSIS.md` 作为分析工具沉淀。

## Git 状态

- 分支：`devin/r14.13-kotlin-refactor`
- HEAD：`2a389d15 chore: prepare r14.13.0-rc1 signing baseline`
- 工作区：除分析脚本与文档外，无业务代码改动。
