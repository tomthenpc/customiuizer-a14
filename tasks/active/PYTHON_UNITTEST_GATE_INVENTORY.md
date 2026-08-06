# A14 Python `unittest discover -s tools/tests` 唯一根因清单

- 生成时间：R5.1
- 命令：`python -m unittest discover -s tools/tests`
- 结果：`FAILED (failures=9, errors=53)`，合计 62 条失败/错误
- 唯一根因数：7
- 失败实例数：62

本文件只作分类与证据清单，不修复、不 skip、不创建占位文件。

## 分类汇总

| 分类 | 唯一根因数 | 失败实例数 | 说明 |
|------|------------|------------|------|
| MISSING_REQUIRED_ARTIFACT | 6 | 42 | 历史曾存在、被 `fb205a0d` 替换/删除后未恢复的作者文档 |
| GENERATED_FILE_DEPENDENCY | 1 | 20 | 测试加载的生成报告/状态文件，当前未生成或未保留 |
| STALE_TEST_EXPECTATION | 0 | 0 | 已修复：v2 任务状态读取与证据降级逻辑已对齐 |
| PORTABILITY_CHECKER_FALSE_POSITIVE | 0 | 0 | 已修复：canonical 跨平台 wrapper 选择已统一放行 |
| ENVIRONMENT_ONLY | 0 | 0 | — |
| UNKNOWN | 0 | 0 | — |
| **合计** | **7** | **62** | |

## 唯一根因明细

### 1. MISSING_REQUIRED_ARTIFACT — A14_GESTURE_EVENT_CONTRACT.md

- 失败测试：`test_gesture_event_contract.*`（18 条）
- 期待文件：`docs/A14_GESTURE_EVENT_CONTRACT.md`
- 历史存在：是，`git log` 可追溯，被 `fb205a0d` 替换
- 删除依据：`fb205a0d docs: replace legacy A14 document architecture with v2`
- 建议：恢复或重定向文档；若 v2 使用新文件名，应同步更新 `test_gesture_event_contract.py` 路径
- 阻塞正式版：否（流程/文档门禁）
- 最小任务范围：文档路径对齐或恢复 artifact

### 2. MISSING_REQUIRED_ARTIFACT — A14_CURRENT_ARCHITECTURE.md

- 失败测试：`test_current_architecture.*`（12 条）
- 期待文件：`docs/A14_CURRENT_ARCHITECTURE.md`
- 历史存在：是，被 `fb205a0d` 替换
- 删除依据：`fb205a0d docs: replace legacy A14 document architecture with v2`
- 建议：恢复/重命名并更新测试引用
- 阻塞正式版：否
- 最小任务范围：文档路径对齐

### 3. MISSING_REQUIRED_ARTIFACT — A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md

- 失败测试：`test_gesture_lifecycle_inventory.*`（8 条）
- 期待文件：`docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md`
- 历史存在：是，被 `fb205a0d` 替换
- 删除依据：`fb205a0d docs: replace legacy A14 document architecture with v2`
- 建议：恢复文档或更新 `test_gesture_lifecycle_inventory.py` 使用 v2 路径
- 阻塞正式版：否
- 最小任务范围：文档路径对齐

### 4. MISSING_REQUIRED_ARTIFACT — A14_PROCESS_EXCEPTIONS.md

- 失败测试：`test_audit_deliverables.ProcessExceptionsTest.*`（2 条）
- 期待文件：`docs/rom-intelligence/A14_PROCESS_EXCEPTIONS.md`
- 历史存在：是，被 `fb205a0d` 替换
- 删除依据：`fb205a0d`
- 建议：恢复/重定向
- 阻塞正式版：否
- 最小任务范围：文档路径对齐

### 5. MISSING_REQUIRED_ARTIFACT — A14_FEATURE_RETIREMENT.md

- 失败测试：`test_audit_deliverables.FeatureRetirementConsistencyTest.*`（1 条）
- 期待文件：`docs/audit/A14_FEATURE_RETIREMENT.md`
- 历史存在：是，被 `fb205a0d` 替换
- 删除依据：`fb205a0d`
- 建议：恢复/重定向
- 阻塞正式版：否
- 最小任务范围：文档路径对齐

### 6. MISSING_REQUIRED_ARTIFACT — A14_HOOK_OWNERSHIP_INVENTORY.md

- 失败测试：`test_audit_hook_ownership.AuditHookOwnershipTest.test_script_runs_successfully`（1 条）
- 期待文件：`docs/audit/A14_HOOK_OWNERSHIP_INVENTORY.md`
- 历史存在：是，`audit_hook_ownership --check` 曾提交该清单
- 删除依据：`fb205a0d` 或后续清理
- 建议：恢复清单或让 `audit_hook_ownership` 工具接受无历史清单的 drift 模式
- 阻塞正式版：否
- 最小任务范围：恢复审计清单或调整审计脚本错误阈值

### 7. GENERATED_FILE_DEPENDENCY — A14_APK_SIZE_DELTA.json

- 失败测试：`test_apk_size_delta.*`（20 条）
- 期待文件：`docs/performance/A14_APK_SIZE_DELTA.json`
- 历史存在：是，由 `46c7e2a4 docs(perf): add P12.4 APK delta report and mechanical tests` 引入
- 删除依据：`fb205a0d` 替换文档架构
- 建议：恢复生成流程或从 CI 中排除该测试直到有实际 delta 报告
- 阻塞正式版：否（非运行时）
- 最小任务范围：恢复 JSON 或移除对未生成产物的测试依赖

## 已修复（R5.1 不再失败）

- `tools/tests/test_check_ci_portability.py`：`test_self_passes` 已通过；canonical 跨平台 wrapper 选择统一放行。
- `tools/tests/test_progress_snapshot.py`：v2 `tasks/{active,backlog,blocked,completed}/` + `ROADMAP.md` 读取已对齐。
- `tools/tests/test_check_staged_snapshot.py`：v2 状态路径与 staged-only 检查已对齐。

## 处理原则

- 不创建空 JSON、空 Markdown 或伪造基线。
- 不删除失败测试。
- 不给所有失败统一加 `skip`。
- 不在本任务（R5.1）中开始批量修复。
