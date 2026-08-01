# AGENTS.md — CustoMIUIzer A14

本文件是本仓库的执行规则。

优先级：**用户本轮明确要求 > 本文件 > `docs/` 工程文档 > Git 历史 > 上游 `MonwF/customiuizer v24.10.12`**。

---

## 1. 项目边界

- 平台：HyperOS 1 / Android 14 / SDK 34
- 分支：仅 `devin/a14-runtime-hardening`
- `applicationId`：`tv.withaibuild.customiuizer.r14`
- `minSdk` / `targetSdk`：34 / 34
- ABI：`arm64-v8a`
- libxposed：用 API 102 编译，API 101 是最低运行基线；API 102 专属类型不得出现在 API 101 必经路径
- 保留 Java 三个文件：`MainModule.java`、`XposedHelpers.java`、`MemberUtilsX.java`

---

## 2. 验证方式

只进行：

- `python tools/verify.py full`（`check-invariants` + 编译 + 单元测试 + `lintDebug` 的统一离线入口）
- `python tools/check-invariants.py`
- 针对性单元测试：`./gradlew test`
- Debug Kotlin/Java 编译：`./gradlew compileDebugKotlin compileDebugJavaWithJavac`
- 必要的 Debug Lint：`./gradlew lintDebug`
- LSPosed 详细日志离线分析：`python tools/analyze_lsposed_log.py`

不进行 ADB、logcat、dumpsys、Tasker、自动安装/重启、UI 自动化、PSS 采集、实机脚本。用户不会与 Devin 同步进行实机测试，不得等待设备。

禁止构建任务名包含：`assemble`、`package`、`bundle`、`install`、`sign`、`publish`、`officialRelease`、`lintVitalRelease`。不得运行 R8、resource shrink、APK 签名。缺少正式签名配置时，不得用 Debug 证书冒充 Release。

---

## 3. 编码原则

- 关闭功能接近零运行成本，无关进程不初始化无关功能。
- Hook 时序、参数改写、`chain.proceed()` 调用次数保持不变。
- 回调（`onReceive`、`onChange`、`Runnable`、listener lambda 等）最外层必须用 `ModuleHelper.guarded` 保护。
- 反射、DexKit、磁盘 I/O、同步 Binder 只在冷路径；热路径只读已准备好的不可变/原子状态。
- 注册必须绑定所有者：进程单例用 `registerModuleReceiver`，多实例用 `registerOwnedReceiver`，非 Receiver 用 `replaceModuleRegistration`。
- Receiver/ContentObserver 注册必须有 active/stale 闭环。
- Feature 同一进程只安装一次，Preference 变化不得把已安装 Hook 重置为未安装。
- 缓存必须有界；`Map<Int, *>` / `Map<Long, *>` 在热路径换 `SparseArray` / `LongSparseArray`。
- 不持有 Activity/View 强引用。
- `system` scope 不得删除。

---

## 4. 改动纪律

- 改前确认：具体代码与调用链、所属进程/生命周期、功能开关、Git 历史、可复现场景/日志、是否影响 R8/反射/ClassLoader/动态入口。
- 只做用户要求的事；不顺手重构、不升级依赖、不改配置。
- 行尾/格式化/资源清理单独提交。
- 资源不得批量删除；删前搜索 XML 引用、`R.*`、`getIdentifier`、反射字符串、ROM 动态访问。

---

## 5. Git 与发布

- 普通读取/搜索/编辑/测试/当前开发分支的 commit 与已授权 push，不逐项询问。
- 禁止：force push、`git reset --hard`、`git restore .`、`git checkout -- .`、`git clean -fd`、修改/合并 `main`、创建平行分支、创建 PR/tag/Release、公开上传 APK、删除分支或旧 Release。
- 禁止提交：keystore、密码、token、真实 `keystore.properties`、APK、签名备份、私人日志、缓存/构建目录。

---

## 6. 汇报

只报有价值的事实：分支/HEAD/工作区状态、证据与根因、文件与行为变化、对 hook/JVM/API/R8/生命周期的影响、实际跑过的验证、commit/push 状态，以及 **已验证 / 待实机 / 无法确认** 分档。

## 7. 文档阅读策略

- 运行时任务：默认只读取目标 `installer`、目标 `FeatureDefinition`、目标 `Hook/Controller`、对应测试和本文件。
- 需要当前架构、进程路由或组件状态时，读取 `docs/A14_RUNTIME_HARDENING.md`。
- 需要理解静态门禁规则或真实缺陷案例时，读取 `docs/RUNTIME_INVARIANTS.md`。
- 发布/对外说明任务才读取 `README.md`、`README_EN.md`、`CHANGELOG.md` / `CHANGELOG_EN.md`。
- 验证/签名/APK 相关任务才读取 `docs/VERIFICATION.md`。
- 日志任务才读取 `docs/LSPOSED_LOG_ANALYSIS.md`。
- 工程文档优先级保持为：用户明确要求 > `AGENTS.md` > `docs/A14_RUNTIME_HARDENING.md` > `docs/RUNTIME_INVARIANTS.md` > Git 历史 > 上游。
