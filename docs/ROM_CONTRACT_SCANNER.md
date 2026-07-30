# ROM 合约扫描器

`tools/rom-contract-scan.py` 用于在**离线环境**验证目标 ROM 是否包含模块 hook 所需的类、方法和字段。不联网、不修改 ROM、不依赖真实设备。

## 准备 ROM 输入

1. 从手机提取目标 APK/JAR：
   - `services.jar`（system_server）
   - `SystemUI.apk`（或 `com.android.systemui`）
   - `Launcher.apk`（`com.miui.home`）
2. 使用 `baksmali`（`smali` 项目）反编译：

```bash
java -jar baksmali.jar disassemble services.jar -o rom-smali/system_server
java -jar baksmali.jar disassemble SystemUI.apk -o rom-smali/systemui
java -jar baksmali.jar disassemble Launcher.apk -o rom-smali/launcher
```

多 dex APK 会生成 `smali`、`smali_classes2` 等目录；将每个目录都作为 `--target` 传入。

## 扫描

```bash
python tools/rom-contract-scan.py \
  --contract rom-contracts/hyperos1-a14-core.json \
  --schema rom-contracts/schema.json \
  --target systemui=/path/to/systemui \
  --target systemui=/path/to/systemui_classes2 \
  --target system_server=/path/to/system_server \
  --target launcher=/path/to/launcher \
  --output-json build/rom-contract-report.json \
  --output-markdown build/rom-contract-report.md
```

## 退出码

- `0`：所有已提供 target 的 `required` 合约满足
- `1`：至少一个 `required` 合约缺失
- `2`：输入、schema 或扫描错误

## 合约格式

见 `rom-contracts/schema.json`。关键字段：

- `class`：完整 smali class descriptor，如 `Lcom/android/systemui/SystemUIInitializer;`
- `anyOf`：备选 class descriptor
- `methods[].descriptor`：完整 smali 方法 descriptor，如 `(Z)V`、`(Landroid/view/KeyEvent;Z)V`
- `fields[].type`：字段完整 smali 类型
- `required` / `optional`：区分必须与可选
- `sourceFile` / `sourceHookFunction`：对应源码中的 hook 调用点

## 覆盖率

当前 `rom-contracts/hyperos1-a14-core.json` 仅包含 6 条已引用的示例合约，并标注 `coverage.status: partial`。完整合约需要对照目标 ROM 的 `javap`/baksmali 输出提取方法全签名后逐步补全。
