# 安装并启动 A14 最终自治控制层

## 1. 建议源目录

解压到：

```text
C:\Users\tv\Downloads\A14_Autonomous_Control_Plane_FINAL_v2
```

包含：

```text
GOAL.md
AGENTS.md
TASK_STATE.md
DEVIN_START_PROMPT.md
INSTALL_A14_CONTROL_PLANE.md
scripts\verify.ps1
scripts\bootstrap-and-start.ps1
```

## 2. 目标锁

仓库：

```text
tomthenpc/customiuizer-a14
```

唯一分支：

```text
devin/a14-rom-intelligence-audit
```

使用精确锁，不支持 wildcard。

## 3. 一次启动

向当前 A14 Agent 发送：

```text
在当前 A14 仓库执行：

powershell -NoProfile -ExecutionPolicy Bypass -File "C:\Users\tv\Downloads\A14_Autonomous_Control_Plane_FINAL_v2\scripts\bootstrap-and-start.ps1" -SourceRoot "C:\Users\tv\Downloads\A14_Autonomous_Control_Plane_FINAL_v2"

成功后完整读取仓库内 DEVIN_START_PROMPT.md，并立即执行 P0.1。之后自行分析、修改、运行、测试、发现问题、修复、提交、push 和检查 CI，除外部设备/ROM/签名/权限/产品决策外，不等待我的常规确认。

只允许 devin/a14-rom-intelligence-audit。禁止新建分支、合并 main、force-push、tag 和 Release。
```

## 4. 安装脚本行为

它会：

- 验证当前 Git 仓库；
- 规范化验证 origin；
- 精确验证分支；
- 检查 upstream 和 unfinished operation；
- 检查七个源文件；
- 只复制控制文件；
- 校验 SHA-256；
- 运行 Audit；
- 只暂存控制文件；
- 创建独立治理提交；
- 只 push 授权分支；
- 再次运行 Audit。

它不会：

- reset；
- clean；
- 删除业务代码；
- 合并 main；
- 新建分支；
- force-push；
- tag/release。

## 5. 未来更换分支

不得改为模糊匹配。

未来更换分支必须由仓库所有者创建治理变更，同时更新：

```text
GOAL.md
AGENTS.md
DEVIN_START_PROMPT.md
INSTALL_A14_CONTROL_PLANE.md
scripts/verify.ps1
scripts/bootstrap-and-start.ps1
```
