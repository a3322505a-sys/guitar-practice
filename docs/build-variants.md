# 构建与长期 compare 对照包

## 变体职责

项目使用 Android Gradle Plugin 8.7.3、JDK 17、compileSdk 36 / targetSdk 35 / minSdk 26。单 `:app`，不新增 flavor 维度，原任务名保持可用。

| build type | applicationId | 用途与源码范围 |
| --- | --- | --- |
| `release` | `com.a3322505a.guitarlearning` | 正式发行；main 正式实现，保持原标签、绿色图标与长期签名 |
| `compare` | `com.a3322505a.guitarlearning.compare` | 长期并装对照；同一提交的正式实现，标签“吉他练习·对照”，紫色 A/B 图标 |
| `debug` | `com.a3322505a.guitarlearning` | 开发与旧实现回归；额外包含 debug 源码、旧采样和测试 Activity，默认调试签名 |
| `p0` | `com.a3322505a.guitarlearning` | 历史原生引擎探针；额外包含 p0 入口，可调试，版本继承当前开发版本 |

debug/p0 沿用正式应用 ID，不作为真机并装对照包。compare 不可调试，和 release 一样默认生成未签名 APK，再用本机长期证书签名。

compare 用 `initWith(release)` 继承构建设置，`matchingFallbacks = release` 选择依赖变体。两者共享 `src/main`；`src/compare` 只覆盖应用名与同名图标资源，不加入业务 Kotlin、旧播放器、原始 PCM 或探针。`initWith` 不会继承 `src/release` 源码目录：新增正式共用实现应放 main；若将来新增 release 专用目录/依赖，必须同时核对 compare。配置依据：[Android 构建变体](https://developer.android.com/build/build-variants)。

## 构建和签名

本机复用现有工具链，脚本会设置 JDK 和 Android SDK；历史工具链仍放在 `001/work/toolchain`，源码和产物来自当前工作区。

```powershell
# 正式实现的并装对照包
.\scripts\build-local.ps1 --offline --no-daemon --max-workers=2 assembleCompare
.\scripts\sign-local.ps1 -BuildType compare

# 正式版保持原命令
.\scripts\build-local.ps1 --offline --no-daemon --max-workers=2 assembleRelease
.\scripts\sign-local.ps1 -BuildType release
```

其他机器配置 JDK 17、SDK 36 与 `local.properties` 后可用 `./gradlew assembleCompare`；正式证书只在已有安全环境中读取，CI 不持有私钥。

- 未签名 compare APK：`app/build/outputs/apk/compare/app-compare-unsigned.apk`。
- 签名脚本按该变体的 `output-metadata.json` 读取版本、校验 applicationId，校验既有证书，并检查签名及 zipalign。
- 签名 compare APK：`output/guitar-practice-<versionName>-compare-v<versionCode>.apk`，与正式产物分开；已存在的同名文件不会被覆盖。
- `versionCode` 继承 defaultConfig，`versionName` 自动添加 `-compare`。增加构建变体本身不递增正式版本。
- CI 在既有验证中构建、lint compare，并单独上传 `unsigned-compare-apk`；正式 `unsigned-apk` 保持原路径。

按任务选择必要检查，例如：

```powershell
.\scripts\build-local.ps1 --offline --no-daemon --max-workers=2 testCompareUnitTest lintCompare assembleCompare assembleRelease
python scripts/audit-runtime-isolation.py --apk app/build/outputs/apk/compare/app-compare-unsigned.apk
```

## 并装、升级与 A/B 约定

- 固定 `.compare` 应用 ID 是一个长期对照槽位，和正式版并装、数据目录独立，不自动读取或复制正式曲库和偏好。后续对照构建覆盖这个槽位；不是每个版本都生成新包名。
- 同包名升级仍受 Android 版本与签名约束。要保留 compare 数据需使用同一长期证书和不低于已安装包的 versionCode；不要为安装旧版擅自卸载、清数据或改正式包名。
- 历史 v95 包 `.compare95` 是一次性交付，保留其历史证据；新 `.compare` 不覆盖或迁移它。需要多个对照槽位时单独明确需求。
- compare 构建的是所在提交/工作区的实现，不自动冻结到 v95，也不承担 v97 回退。对照前记录源码 SHA、未提交补丁、版本、签名和 APK SHA-256；选择基线后，在相同曲目、速度、选段、音量及声音开关下比较。
- 核对 APK 身份、有效曲包/SoundFont/原生库一致性及旧代码隔离，再做安装与数据隔离检查。手机音量、漏音、跨行跟随、长时播放和实际听感由真机验收；构建、模拟器和 QQ 回读只证明各自范围。

当前 V107 词典与每日练习见 [实施记录](V3.1-v107-实施记录.md)；V106 的已交付事实保留在 [历史记录](原生教学数据-v106.md)。
