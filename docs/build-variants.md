# Android 构建与签名（v115）

当前参考 `main` 的 `app/build.gradle.kts`：JDK 17、compileSdk 36、targetSdk 35、minSdk 26，`versionCode=116`、`versionName=1.4.6`。以实时 Gradle 文件为最终准则。

| 变体 | 包名 | 说明 |
| --- | --- | --- |
| release | `com.a3322505a.guitarlearning` | 正式包名，长期签名不变 |
| debug | `com.a3322505a.guitarlearning` | Android 调试变体 |
| compare | `com.a3322505a.guitarlearning.compare` | 并装对照，独立数据 |
| standalone | `com.a3322505a.guitarlearning.standalone` | 独立试用安装包，沿用此前 standalone 证书 |

工程同时有 `tabletReview` 调试属性，默认 false；不能误把该临时 applicationId 当正式应用包名。

## 最短构建检查

```sh
python -m pip install -r scripts/song_package/requirements.txt
PYTHONPATH=scripts python -m unittest test_song_packages.PackageTests.test_all_original_shared_fixtures test_song_packages.PackageTests.test_bundled_content_matches_independent_source_facts
./gradlew :app:testDebugUnitTest :app:lintStandalone :app:assembleStandalone :app:assembleRelease
```

以上为可执行的核心流程建议；实际 CI 检查见 `.github/workflows/android.yml`，本次整理不宣称已执行或通过构建。没有本地 Android SDK/JDK 时以 CI 为准。

## 交付约束

保留正式 appId 和签名，递增版本号才能覆盖安装；standalone/compare 不与正式安装数据混用。私钥和密码不进仓库。签署前核对证书指纹，签署后验证 APK 包名、versionCode、签名、SHA-256。CI 的未签名 APK 不等于可覆盖安装的最终包。历史 v114 验证记录可从 Git 提交历史查阅。
