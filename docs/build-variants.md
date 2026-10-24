# V114 构建与签名

当前开发分支 `codex/v114-count-in-cleanup`，版本 `1.4.4 / 114`。AGP 8.10.1、Kotlin 2.2.20、JDK 17、compileSdk 36 / targetSdk 35 / minSdk 26；minSdk 是当前工程配置，不是旧设备兼容承诺。

| 变体 | applicationId | 用途 |
| --- | --- | --- |
| release | com.a3322505a.guitarlearning | 正式实现、既有长期签名 |
| debug | com.a3322505a.guitarlearning | 同一原生实现，默认调试签名；额外仅有现代 Compose 测试 Activity |
| compare | com.a3322505a.guitarlearning.compare | 长期并装对照，正式实现、长期证书、独立数据 |
| standalone | com.a3322505a.guitarlearning.standalone | 当前交付，沿用 v111–v113 standalone 专用证书、独立数据 |

历史 P0 build type、探针、旧 Debug 播放器和 PCM 已退役；原始 SoundFont 制作素材仍保存在 `assets/soundfont-source/`，所有变体使用 main 的 `sonivox.sf2`。

## 验证

配置 JDK 17、Android SDK 36 / Build Tools 35.0.0，使用 `local.properties` 或 `ANDROID_HOME` 指定 SDK。新环境先解析依赖；已缓存环境可加 `--offline`。

```sh
python -m pip install -r scripts/song_package/requirements.txt
python scripts/test_song_assets.py
python scripts/test_song_packages.py
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleStandalone :app:assembleRelease :app:testCompareUnitTest :app:lintCompare :app:assembleCompare :app:lintStandalone
python scripts/audit-native-teaching.py
python scripts/audit-runtime-isolation.py --apk app/build/outputs/apk/release/app-release-unsigned.apk --debug-apk app/build/outputs/apk/debug/app-debug.apk
python scripts/audit-runtime-isolation.py --apk app/build/outputs/apk/standalone/app-standalone-unsigned.apk
python scripts/audit-runtime-isolation.py --apk app/build/outputs/apk/compare/app-compare-unsigned.apk
```

CI 执行相同检查，保留现代 Debug 测试和 compare 检查，上传 release / compare / standalone 未签名 APK 与验证报告。隔离审计使用冻结的退役类清单，检查实际 DEX 和 assets；删除源文件不会使拒绝清单失效。

## 签名与交付

Windows 可继续复用 `scripts/build-local.ps1` 与 `scripts/sign-local.ps1`；共享工具链和正式私钥不属于清理范围。正式/compare 继续用既有长期证书；standalone 使用已交付 standalone 证书，签名前核对上一版证书指纹，签后检查 `apksigner verify`、zipalign、包名和版本。CI 不持有私钥。

同包名更新须使用相同证书和递增的 versionCode；不要为安装擅自卸载或清数据。compare/standalone 不读取或复制正式版数据，不创建新对照槽位。历史 `.compare95` 保留其当时证据。

交付报告记录 Git commit、Actions、APK SHA256、签名和真机结果。主机 PCM 与 CI 能证明引擎行为和构建结果，不能替代真实 Android 设备听到预备拍及正式首拍。当前验收边界见 [V114 记录](V114-实施与交付.md)。
