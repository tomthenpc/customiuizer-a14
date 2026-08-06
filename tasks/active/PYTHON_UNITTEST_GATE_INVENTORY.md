# A14 Python `unittest discover -s tools/tests` 唯一根因清单

- 生成时间：R4.2
- 命令：`python -m unittest discover -s tools/tests`
- 结果：`FAILED (failures=12, errors=66)`，合计 78 条失败/错误
- 唯一根因数：12

本文件只作分类与证据清单，不修复、不 skip、不创建占位文件。

## 分类汇总

| 分类 | 计数 | 说明 |
|------|------|------|
| MISSING_REQUIRED_ARTIFACT | 43 | 历史曾存在、被 `fb205a0d` 替换/删除后未恢复的作者文档 |
| GENERATED_FILE_DEPENDENCY | 34 | 测试加载的生成报告/状态文件，当前未生成或未保留 |
| STALE_TEST_EXPECTATION | 2 | 测试仍要求已不存在的文档达到 `verified` 级别 |
| PLATFORM_ISOLATION_BUG | 1 | `tools/build_debug_apk.py` 在 Windows 平台分支上硬编码 `gradlew.bat` / `os.name` |
| ENVIRONMENT_ONLY | 0 | — |
| UNKNOWN | 0 | — |

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

- 失败测试：`test_audit_deliverables.*`（2 条）
- 期待文件：`docs/rom-intelligence/A14_PROCESS_EXCEPTIONS.md`
- 历史存在：是，被 `fb205a0d` 替换
- 删除依据：`fb205a0d`
- 建议：恢复/重定向
- 阻塞正式版：否
- 最小任务范围：文档路径对齐

### 5. MISSING_REQUIRED_ARTIFACT — A14_FEATURE_RETIREMENT.md

- 失败测试：`test_audit_deliverables.*`（1 条）
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

### 8. GENERATED_FILE_DEPENDENCY — TASK_STATE.md

- 失败测试：`test_progress_snapshot.*`（9 条）、`test_check_staged_snapshot.*`（1 条）
- 期待文件：`TASK_STATE.md`
- 历史存在：是，曾在仓库根目录生成
- 删除依据：`fb205a0d` 替换状态文件架构
- 建议：更新 `progress_snapshot.py` / 测试以读取 v2 状态文件；或恢复 `TASK_STATE.md` 生成
- 阻塞正式版：否
- 最小任务范围：生成脚本输出路径对齐

### 9. GENERATED_FILE_DEPENDENCY — SMART_OPERATION_STATE.md

- 失败测试：`test_progress_snapshot.*`（3 条）
- 期待文件：`SMART_OPERATION_STATE.md`
- 历史存在：是
- 删除依据：`fb205a0d`
- 建议：同 `TASK_STATE.md`
- 阻塞正式版：否
- 最小任务范围：生成脚本输出路径对齐

### 10. STALE_TEST_EXPECTATION — A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md 作为 verified evidence

- 失败测试：`test_progress_snapshot.EvidenceProvenanceTest.test_real_repo_path_accepted`（1 条）
- 期待行为：`docs/A14_GESTURE_LIFECYCLE_OWNER_INVENTORY.md` 出现在 `evidence_paths` 中
- 实际行为：仅 `tools/tests/test_gesture_lifecycle_inventory.py` 存在；目标文档已缺失
- 删除依据：`fb205a0d`
- 建议：更新 `progress_snapshot` 证据表，移除对已删除文档的硬编码期望
- 阻塞正式版：否
- 最小任务范围：调整测试/生成器证据路径

### 11. STALE_TEST_EXPECTATION — evidence_level 为 verified

- 失败测试：`test_progress_snapshot.EvidenceProvenanceTest.test_referenced_doc_evidence_commit_used_if_path_valid`（1 条）
- 期待行为：`evidence_level == "verified"`
- 实际行为：`evidence_level == "pending"`
- 根因：引用的文档不存在，生成器降级为 pending
- 删除依据：`fb205a0d`
- 建议：修复引用文档或接受 pending 直到文档恢复
- 阻塞正式版：否
- 最小任务范围：证据配置对齐

### 12. PLATFORM_ISOLATION_BUG — tools/build_debug_apk.py

- 失败测试：`test_check_ci_portability.CIPortabilityCheckerTest.test_self_passes`（1 条）
- 期待行为：CI 可移植性扫描无 Windows-only 分支
- 实际行为：`tools/build_debug_apk.py:28` 使用 `gradlew.bat` 和 `os.name` 分支
- 删除依据：无明确提交，是历史脚本的平台分支
- 建议：移除 `gradlew.bat` 硬编码；改为通过 `gradlew` 脚本跨平台执行或使用 `sys.platform`/`shutil.which` 判断 gradle 可执行文件
- 阻塞正式版：否（仅 Windows 本地 CI 可移植性）
- 最小任务范围：单行平台分支改写

## 处理原则

- 不创建空 JSON、空 Markdown 或伪造基线。
- 不删除失败测试。
- 不给所有失败统一加 `skip`。
- 不在本任务（R4.2）中开始批量修复。
