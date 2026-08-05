# A14 构建与验证

## 快速门禁

```powershell
python tools/verify.py fast --changed
```

## 针对性测试

```powershell
python tools/verify.py fast --tests <TestClassName>
```

## 完整门禁

```powershell
python tools/verify.py full
git diff --check
```

工具目录变更：

```powershell
python -m compileall tools
python -m unittest discover -s tools/tests -p "test_*.py"
```

## Debug APK

任务明确要求时：

```powershell
.\gradlew.bat :app:assembleDebug
```

记录 APK 路径、大小、签名类型、SHA-256 和 Final SHA。

## Release

仅用户明确要求且仓库外 A14 专用签名配置有效时执行。禁止 Debug 冒充正式版，
禁止提交 APK、密钥、密码、令牌或本地签名配置。

## 证据等级

- `STATIC_VERIFIED`
- `BUILD_VERIFIED`
- `LOG_VERIFIED`
- `DEVICE_VERIFIED`
- `UNVERIFIED`

APK 构建、签名和静态测试不能替代目标 HyperOS ROM 实机验证。
