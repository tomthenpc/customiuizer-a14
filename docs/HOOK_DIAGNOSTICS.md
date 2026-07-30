# Hook 安装诊断

## 日志示例

模块在每个进程安装结束时打印一次汇总，不会为每个成功 hook 单独刷日志：

```text
CustoMIUIzer HookSummary process=com.android.systemui installed=42 classMissing=2 memberMissing=1 failed=0 silentSkipped=3 dexkitFailed=0 prefsUnavailable=0
```

入口标记（每个进程只出现一次）：

```text
CustoMIUIzer <version> loaded in <process>
CustoMIUIzer HookSummary process=...
```

## 状态含义

| 状态 | 含义 |
| --- | --- |
| `INSTALLED` | 类/成员找到，底层 Hook 安装返回成功。 |
| `TARGET_CLASS_MISSING` | 目标 class 在 ROM 中不存在；可能是 ROM 版本差异或包名/类名变动。 |
| `TARGET_MEMBER_MISSING` | class 存在，但给定签名的方法/构造器不存在；可能是重载签名变更。 |
| `INSTALL_FAILED` | 安装期抛异常，不是 class/member 缺失；可能为权限、API 变化或底层框架失败。 |
| `SILENTLY_SKIPPED` | `*Silently` 包装器失败或被跳过，不视为致命错误（常用于可选或兼容性 hook）。 |
| `DEXKIT_FAILED` | DexKit bridge/加载/查询失败。 |
| `PREFERENCES_UNAVAILABLE` | RemotePreferences 不可读或加载异常。 |

## 排障顺序

1. 先看 `CustoMIUIzer HookSummary process=<进程名>` 中 `classMissing` / `memberMissing` / `failed` 是否非零。
2. 在同一进程内搜索对应的 `classMissing` / `memberMissing` 目标类名。
3. 如果是 `TARGET_CLASS_MISSING`，检查 ROM 是否裁剪了该类或包名。
4. 如果是 `TARGET_MEMBER_MISSING`，用 `javap -p` 或 baksmali 检查该类实际签名，尤其是重载和参数类型。
5. 如果是 `INSTALL_FAILED`，查看详细 exceptionType 并检查 ROM/libxposed 版本。
6. `PREFERENCES_UNAVAILABLE` 通常意味着 `RemotePreferences` 未初始化或 binder 不可达，不会导致宿主崩溃，但会让所有偏好默认关闭。

## 数据约束

- 状态仅保存在宿主进程内存中。
- 单进程记录上限 256 条，超出时丢弃最早条目。
- 记录中只包含字符串和枚举，不包含 `Context`、`ClassLoader`、`MethodHook`、`Throwable`、用户偏好值或 token。
- Hook callback 热路径不调用诊断记录器。

## 测试

`app/src/test/java/tv/withaibuild/customiuizer/mods/utils/HookDiagnosticsTest.kt` 覆盖：

- 安装/缺失/失败分类；
- 同目标去重；
- 同目标不同状态保留；
- 记录有界；
- `printSummaryOnce` 幂等；
- 异常分类识别；
- snapshot 不含敏感对象。
