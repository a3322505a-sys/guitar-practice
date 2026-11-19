# Android 构建与签名

工具链与目标版本：JDK 17、`compileSdk 36`、`targetSdk 35`、`minSdk 26`。版本号只以 `app/build.gradle.kts` 为准（`versionName = X.Y.Z`，`versionCode` 严格递增）。

| 变体 | 包名 | 用途 |
| --- | --- | --- |
| `release` | `com.a3322505a.guitarlearning` | 正式安装包，显示名“吉他练习” |
| `debug` | `com.a3322505a.guitarlearning.debug` | 本机调试，显示名“吉他练习·调试” |
| `compare` / `standalone` | 独立后缀 | 临时对照变体，非正式交付 |

Gradle 自动输出 `app/build/outputs/apk/release/GuitarPractice-<versionName>.apk`；未提供签名环境变量时自动带 `-unsigned` 后缀（仅用于编译检查）。

## 正式签名配置（本地与 CI 共用）

Gradle 读取 4 个环境变量：`ANDROID_KEYSTORE_PATH`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。私钥与密码不得入库或打印到日志。

- 既有正式密钥文件：`001-release.p12`（alias：`guitar-release`）
- 证书 SHA-256 指纹：`43b7af607adb264fb2faa79437d91dbc7e1bea7d7ce92804965fe0f1c8daf320`
- 禁止另建新证书，确保手机可直接覆盖升级。

Windows 本地已配置 `JAVA_HOME`、`ANDROID_HOME`（或 `local.properties`）时：

```powershell
./scripts/build-local.ps1 ':app:compileDebugKotlin'
./scripts/sign-local.ps1 -Keystore /private/001-release.p12 -PasswordFile /private/001-release.password
```

## GitHub Actions 自动签发正式包

仓库 Settings → Secrets and variables → Actions 需配置以下 4 项 Repository secrets（一次性配置，可用 `./scripts/setup-release-signing.ps1 -BackupZip <backup.zip>` 写入）：

| Secret | 说明 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `001-release.p12` 的 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | 密钥库密码 |
| `ANDROID_KEY_ALIAS` | `guitar-release` |
| `ANDROID_KEY_PASSWORD` | 同一密钥密码 |

推送到 `main` 后，`.github/workflows/android.yml` 会先执行 Python 曲包校验、全量单元测试、Lint 与未签名构建检查；通过后 `release` 作业自动恢复密钥、校验证书指纹、签署并上传 `official-release-apk` 产物（若 Secrets 未配置则仅产出未签名检查包）。
