# Android 构建与签名

长期规则见 [AGENTS.md](../AGENTS.md)。JDK 17、compileSdk 36、targetSdk 35、minSdk 26；版本值只读取 `app/build.gradle.kts`，历史 v115/v116/v120 不作为当前发布基线。

| 变体 | 包名 | 用途 |
| --- | --- | --- |
| release | `com.a3322505a.guitarlearning` | 唯一正式交付，显示名“吉他练习” |
| debug | `com.a3322505a.guitarlearning.debug` | 本机调试，显示名“吉他练习·调试” |
| compare / standalone | 独立后缀 | 临时对照，无正式交付入口 |

正式版本 `X.Y.Z`，内部升级整数严格递增；修复默认升 PATCH。Gradle 自动生成 `GuitarPractice-X.Y.Z.apk`，未配置签名时自动带 `-unsigned`，只用于编译检查。已去除临时 tabletReview 包名开关。

## 本地与 CI 共用签名入口

Gradle 读取环境变量 `ANDROID_KEYSTORE_PATH`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。四项齐全时签署 release，缺失时只生成未签名检查包。私钥/密码不进 Git、不进日志。

已恢复既有正式密钥，继续使用 `001-release-signing-backup.zip` 中的 `001-release.p12`，alias 为 `guitar-release`。证书 SHA-256：

`43b7af607adb264fb2faa79437d91dbc7e1bea7d7ce92804965fe0f1c8daf320`

备份原件保留在用户文件中；操作时把密钥与密码文件分别保管。禁止为每次交付另建证书。旧 standalone 密钥仅供历史安装包追溯，正式发布固定使用上述证书。

Windows 本地已安装 JDK/SDK 时，使用已有 JAVA_HOME、ANDROID_HOME（或 ANDROID_SDK_ROOT），无需修改脚本路径：

```powershell
./scripts/build-local.ps1 ':app:compileDebugKotlin'
./scripts/sign-local.ps1 -Keystore /private/001-release.p12 -PasswordFile /private/001-release.password
```

`sign-local.ps1` 只是同一 Gradle 签名配置的便捷入口，产物仍在 `app/build/outputs/apk/release/`；不再手动重命名或另外执行旧签名流程。

## GitHub Actions 首次设置（只做一次）

仓库 Settings → Secrets and variables → Actions 配置以下 Repository secrets：

| Secret | 来源 |
| --- | --- |
| ANDROID_KEYSTORE_BASE64 | 既有 001-release.p12 的 Base64 |
| ANDROID_KEYSTORE_PASSWORD | 既有密码文件的内容 |
| ANDROID_KEY_ALIAS | guitar-release |
| ANDROID_KEY_PASSWORD | 同一既有密码 |

本地已登录 GitHub CLI（需要本仓库 Secrets 管理权限）、装有 JDK 的情况下，可以直接运行一次：

```powershell
./scripts/setup-release-signing.ps1 -BackupZip /private/001-release-signing-backup.zip
```

脚本核对上述证书指纹，再通过 stdin 设置四项 Secrets，不输出密码或 Base64；结束清理临时文件。未登录时先执行 `gh auth login`。完成后在 Actions 的 Android core 选择 main → Run workflow。

PR / 普通任务分支只执行无密钥核心检查和未签名 release 编译。main 的核心检查通过后，release 作业恢复同一私钥、校验证书、签署并验证 APK，上传 `official-release-apk`，最后清理私钥。Secrets 缺失时作业明确报告“待首次配置”，只留下 `unsigned-release-check`，不得称正式 APK 已交付。

核心检查以 `.github/workflows/android.yml` 为准，含现有曲包/导入/练习 UI 主流程、实际 alphaTab 速度验证、调速保存和 Android lint/编译。默认不新增单元测试。

## 发布与下载

代码合并最新 main 后从该次 Android core 的 `official-release-apk` 获取唯一正式包，检查包名、版本、证书与 SHA-256；由有网盘访问权限的 Agent 上传 Google Drive【APK】，不搭额外发布平台。CI artifact 是可信原件，本地发布也必须使用同一 Gradle 签名配置和证书。

首次从“吉他练习·新版” standalone 迁移时卸载旧 standalone，安装正式 release；之后只覆盖安装正式包。内置曲包只取 `app/src/main/assets/song-packages/`，不在构建时抓取网盘目录。
