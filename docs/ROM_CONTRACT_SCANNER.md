# 离线 ROM 合约扫描工具可行性

## 目标

在 ROM 升级后，离线检查 `services.jar`、SystemUI、Launcher 等目标中模块依赖的类、方法和字段是否仍然存在，避免升级后才发现 hook 点消失。

## 可行性结论

可行，但需分阶段实现。推荐优先支持类/方法/字段存在性，DexKit 字符串稳定性可作为二期目标。

## 基础扫描方案

### 1. 反编译 ROM 目标

- `services.jar` 使用 `baksmali disassemble classes.dex` → 得到 `smali`。
- SystemUI (`com.android.systemui`) / Launcher (`com.miui.home`) APK → `apktool d` 或 `baksmali`。
- 提取 `classes*.dex`。

### 2. 合约清单来源

从源码生成 `contract.json`：

```json
{
  "com.android.server.policy.BaseMiuiPhoneWindowManager": {
    "methods": ["initInternal", "closeApp"],
    "fields": ["mContext", "mHandler"]
  },
  "com.android.systemui.statusbar.phone.CentralSurfacesImpl": {
    "methods": ["start"],
    "fields": ["mContext"]
  }
}
```

### 3. 扫描实现

- 对 `smali` 文件夹做字符串/类名查找即可判断类/成员是否存在。
- 方法签名匹配：需要解析 `smali` 中 `L...;` 类型描述符，转换源码类型到 smali 类型。
- 字段存在：直接搜索 `.field`。

### 4. 与项目集成

- 输出 JSON：对每个目标给出 `missing` / `present` / `ambiguous`。
- CI 中作为可选步骤：上传 ROM 文件后运行，失败时不阻塞构建，仅生成报告。

## 工具草稿

见 `tools/rom-contract-scan.py`：

- 输入：ROM 目录或 `services.jar`/APK 路径。
- 输出：`build/rom-contract-report.json`。
- 只依赖 `zipfile` 和 `pathlib`，不引入第三方反编译库；要求用户先准备 `baksmali`。

## 限制

- 不能验证 hook 语义（before/after 参数含义、调用顺序、返回值）。
- 不能检测 ROM 内部私有 API 重命名导致的语义变化。
- `DexKit` 字符串匹配无法通过反编译 `smali` 直接得到，需要额外在 `classes.dex` 中搜索常量池。

## 下一步

1. 在分支 `feature/rom-contract-scan` 中，先生成第一批 `contract.json`（约 20 个核心入口类）。
2. 对当前已验证的 HyperOS 1 ROM 跑一次基线，确认清单完整。
3. 集成到 CI 作为 `workflow_dispatch` 手动任务。
