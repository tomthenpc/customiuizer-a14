# A14 Python `unittest discover -s tools/tests` 唯一根因清单

- 生成时间：R5.2
- 命令：`python -m unittest discover -s tools/tests`
- 结果：`FAILED (errors=20)`，合计 20 条失败/错误
- 唯一根因数：1
- 失败实例数：20

本文件只作分类与证据清单，不修复、不 skip、不创建占位文件。

## 分类汇总

| 分类 | 唯一根因数 | 失败实例数 | 说明 |
|------|------------|------------|------|
| GENERATED_FILE_DEPENDENCY — A14_APK_SIZE_DELTA | 1 | 20 | `docs/performance/A14_APK_SIZE_DELTA.json` 未生成或未保留 |
| MISSING_REQUIRED_ARTIFACT | 0 | 0 | 已迁移为源码机械不变量 |
| STALE_TEST_EXPECTATION | 0 | 0 | 已修复 |
| PORTABILITY_CHECKER_FALSE_POSITIVE | 0 | 0 | 已修复 |
| ENVIRONMENT_ONLY | 0 | 0 | — |
| UNKNOWN | 0 | 0 | — |
| **合计** | **1** | **20** | |

## 唯一根因明细

### 1. GENERATED_FILE_DEPENDENCY — A14_APK_SIZE_DELTA.json

- 失败测试：`test_apk_size_delta.*`（20 条）
- 期待文件：`docs/performance/A14_APK_SIZE_DELTA.json`
- 历史存在：是，由 `46c7e2a4 docs(perf): add P12.4 APK delta report and mechanical tests` 引入
- 删除依据：`fb205a0d` 替换文档架构
- 建议：恢复生成流程或从 CI 中排除该测试直到有实际 delta 报告
- 阻塞正式版：否（非运行时）
- 最小任务范围：恢复 JSON 或移除对未生成产物的测试依赖
- 处理限制：R5.2 明确不处理 APK size generator，交给 R5.3

## 已修复（R5.2 迁移为源码机械不变量）

- `docs/A14_GESTURE_EVENT_CONTRACT.md` → `tools/tests/test_gesture_event_contract.py` 直接解析 `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/gesture/` 源码与 JVM 测试。
- `docs/A14_CURRENT_ARCHITECTURE.md` → `tools/tests/test_current_architecture.py` 直接解析源码拓扑与既有不变量扫描器。
- `docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md` → `tools/tests/test_gesture_lifecycle_inventory.py` 直接构建内存 owner/release 清单。
- `docs/rom-intelligence/A14_PROCESS_EXCEPTIONS.md` / `docs/audit/A14_FEATURE_RETIREMENT.md` / `docs/audit/A14_FEATURE_RETIREMENT.csv` → `tools/tests/test_audit_deliverables.py` 直接验证 process routing 与 feature reachability 不变量。
- `docs/audit/A14_HOOK_OWNERSHIP_INVENTORY.md` → `tools/audit_hook_ownership.py` 默认在内存生成并校验分类，不再依赖已提交 Markdown。
- `TASK_STATE.md` / `SMART_OPERATION_STATE.md` 等旧控制面文件 → `tools/check_staged_snapshot.py` 明确拒绝 staged，并更新 `tools/tests/test_check_staged_snapshot.py` 覆盖拒绝逻辑。

## 处理原则

- 不恢复六个旧 Markdown。
- 不创建空 JSON、空 Markdown 或伪造基线。
- 不删除失败测试。
- 不给失败测试统一加 `skip`。
- 不修改 `app/src/main/**` 产品代码。
- 本任务（R5.2）不处理 A14_APK_SIZE_DELTA；剩余唯一根因交给 R5.3。
