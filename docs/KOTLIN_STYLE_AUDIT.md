# Kotlin 与 JVM 互操作审计

## 范围

本审计基于 `codex/r14.12-modern-api102-kotlin`，仅检查上一轮阶段 A～D 完成后的剩余 Kotlin/JVM 风险，不重新执行已经推送的生命周期、Hook 热路径或设置 UI 优化。

当前正式源码为 88 个 Kotlin 文件和 3 个 Java 文件。工作树中的 7 个 `*.java.bak` 仅作为未跟踪备份保留，未读取为实现基线、未修改、未暂存。

## 已实施

`subs/System.kt` 的 `openSystemSubFragment` 没有高阶函数参数，也不是需要具体化类型参数的泛型函数。Kotlin 编译器明确警告该 `inline` 的预期性能收益不显著。现已改为普通私有函数：

- 不改变参数、返回值或调用位置；
- 不改变 Fragment 导航行为；
- 避免在每个调用点复制相同函数体；
- 消除无依据的 `inline`。

验证：

```powershell
.\gradlew.bat --no-daemon test assembleDebug
```

结果：通过。

## 保留的稳定边界

- `MainModule.java`：libxposed 模块入口和 API 101 兼容边界，保留 Java 形式以降低类验证、R8 和框架回调签名风险。
- `mods/utils/XposedHelpers.java`：现代 libxposed 兼容层，保留既有 Hook 参数、异常和 unhook 语义。
- `org/apache/commons/lang3/reflect/MemberUtilsX.java`：局部 JVM 反射兼容实现，不为追求 Kotlin 覆盖率改写。
- `@JvmStatic`、`@JvmField`、`@JvmOverloads`：只要仍服务于 Java 调用、Android XML 构造器、反射或既有 JVM 签名，就不做风格性删除。
- 显式 `CoroutineScope`：上一轮已完成所有权和取消路径审查；本轮未发现 `GlobalScope`、`runBlocking` 或新增无所有者协程。

## 未引入

- Flow、Sequence、DSL 或新架构层；
- 新线程、轮询、定时器或永久后台任务；
- 无必要协程或 scope function 链；
- 为形式统一而进行的 Java 到 Kotlin 迁移；
- Hook 入口、回调、反射或 R8 可见 JVM 签名变化。

## 结论

阶段 K 没有发现需要继续扩大改动范围的高收益问题。除上述单一 `inline` 修正外，其余候选要么属于上一轮已验证范围，要么会改变稳定 JVM/Hook 边界，风险高于可证明收益，因此有意保留。
