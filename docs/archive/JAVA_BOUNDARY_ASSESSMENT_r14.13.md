# r14.13 Java 边界评估

分支：`devin/r14.13-kotlin-refactor`

评估日期：2026-07-26

## 剩余 Java 源文件清单

| 文件 | 路径 | 说明 |
| --- | --- | --- |
| `MainModule.java` | `app/src/main/java/tv/withaibuild/customiuizer/MainModule.java` | libxposed 模块入口 |
| `XposedHelpers.java` | `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java` | LSPosed 反射/Hook 工具类 |
| `MemberUtilsX.java` | `app/src/main/java/org/apache/commons/lang3/reflect/MemberUtilsX.java` | Apache Commons Lang 3 兼容桥 |

> `PreferenceFragmentBase.java` 与 `Credentials.java` 在当前仓库已分别迁移为
> `PreferenceFragmentBase.kt` 与 `Credentials.kt`，不再作为 Java 边界处理。

## 分类表

| 文件 | 风险等级 | 关键依赖 | 是否建议迁移 | 理由 |
| --- | --- | --- | --- | --- |
| `MainModule.java` | **高** | `META-INF/xposed/java_init.list` 声明入口；继承 `io.github.libxposed.api.XposedModule`；`mPrefs`/`resHooks` 被跨进程/跨语言静态引用；Remote Preferences、API 101/102 生命周期、R8 keep | **否** | Kotlin 可以通过保持相同 FQCN、`@JvmField`、`@JvmStatic` 和明确初始化顺序维持 JVM 边界，但当前迁移收益有限，libxposed 入口、API 101 类加载、跨语言静态访问、R8 和生命周期回归风险高于收益，因此本轮保留 Java。 |
| `XposedHelpers.java` | **高** | 反射调用链、`Class`/`Member`/`Method` 缓存、vararg、`HookerClassHelper` 回调、被大量 Kotlin 代码以 Java 静态方式调用 | **否** | 这是第三方/LSPosed 风格反射工具类；Kotlin 化会引入 platform type、vararg、泛型、异常传播和反射语义风险；无明确收益。 |
| `MemberUtilsX.java` | **中** | Apache Commons Lang 3 内部桥，被 `XposedHelpers` 调用；保留 ASF 协议头 | **否** | 属于 vendored 兼容代码，保持原样可避免协议/签名变更和上游合并冲突。 |

## 评估结论

本轮 Phase 4 未发现可控低风险的 Java 源文件可迁移至 Kotlin：

- 用户列出的普通设置应用类（`PreferenceFragmentBase`、`Credentials`）已 Kotlin 化；
- 剩余 3 个 Java 文件均处于 libxposed、反射/Hook 或第三方兼容边界，根据项目
  `LIBXPOSED_API_101_102_COMPATIBILITY.md`、`AI_MAINTENANCE_GUIDE.md` 和
  `PROJECT_LINEAGE.md`，应明确保留为 Java 稳定边界。

因此**不执行 Java → Kotlin 迁移**，仅输出本评估表并更新进度文档。

## 验证命令

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:ANDROID_HOME='c:\Users\tv\Downloads\Peengeek\.tools\android-sdk'
.\gradlew --no-daemon clean test lint lintRelease lintVitalRelease assembleDebug assembleRelease
```

执行结果：全部通过，36 个单元测试。

## 备注

- 不修改 Hook target、priority、before/after、参数、返回值、异常传播和 unhook 语义；
- 不修改 API 101/102 边界；
- 不引入 Legacy Xposed API；
- 不扩大 R8 keep 规则；
- 不合并 main，不创建 tag 或 Release。
