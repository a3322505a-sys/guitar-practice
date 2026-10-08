# 吉他练习

独立的离线 Android 工程，Kotlin / Compose，单个 `:app` 模块。本轮按 [施工方案](docs/施工方案-20261008.md) P0—P5 实施，参考图仅用于视觉方向。

## 本轮更新

- 奶油白、柔和糖果色、大圆角卡片与绿色主操作；设置收进面板，练习图示成为主区域。导航、播放控制与系统安全区各自留出空间。
- 指板／六线谱手动切换。宽窗默认六线谱，窄窗默认指板；手动偏好保存。六线谱与指板共用原练习事件及 AudioTrack 实际消耗帧，切换不重播。
- 六线谱保留真实弦品、时值、多音和弦、扫弦及分解；含四分、八分、符梁、长音、休止与延音连线，按可读宽度分成多小节、多行，跟随当前谱行。
- 统一撤提示；当前／下一目标、拍点与循环进度；系统配置重建保留参数和播放器。改变内容、速度或循环会结束旧序列，下次从头开始。
- 新默认音源为 FreePats CC0 尼龙弦实录，22 个键中心覆盖本轮音域。新旧单音、开放和弦、分解经同一 App 渲染，并匹配响度供试听。

原有六类音阶练法、八个开放和弦、四类和弦练习、点音探索、整和弦／逐弦试听、声音开关、循环与后台暂停均保留。不新增成绩、课程、账号、导入导出或内容库；没有修改 001/002。

## 交付入口

- 版本：`3.1.0-local02` / `versionCode 50`，包名 `com.a3322505a.guitarlearning`，沿用原长期签名。
- APK：[guitar-practice-3.1.0-local02-v50.apk](output/guitar-practice-3.1.0-local02-v50.apk)
- [本轮验收与未验证项](docs/验收记录.md)、[阶段记录](docs/本轮施工记录.md)、[来源与许可](docs/来源与许可.md)
- [手机和弦指板](docs/screenshots/v50/phone-chord.png)、[平板多行六线谱](docs/screenshots/v50/tablet-multiline-tab.png)
- [新旧音色试听](output/audio-comparison/preview-old-then-new.wav)；单独片段、响度与哈希见 [音色对照说明](output/audio-comparison/README.md)。

截图是 Robolectric 原生组件渲染，不是真机或模拟器安装截图。真机覆盖安装、音画同步与主观听感尚待实际设备验证；代码检查与输出一致性不替代这些结论。本轮仅本地交付，没有远端发布。

## 本地构建

本机脚本复用 JDK 17、SDK 35，不修改系统 Java、网络或签名资料：

```powershell
.\scripts\build-local.ps1 --offline --no-daemon --max-workers=2 testDebugUnitTest lintDebug assembleRelease
.\scripts\sign-local.ps1
```

其他机器配置 JDK 17、Android SDK 35 和 `local.properties` 后运行 `./gradlew testDebugUnitTest lintDebug assembleRelease`。依赖版本沿用已验证基线，详见 Gradle 文件。签名脚本读取已有密钥路径，检查公有证书、版本元数据和签名；私钥及密码不入仓库。已签名同名产物不覆盖。

## 代码入口

| 文件 | 职责 |
| --- | --- |
| `music/Music.kt` | 调弦、五型与开放和弦事实 |
| `practice/Sequence.kt` | 原练习编排、事件、多音分组、循环与继续位置 |
| `practice/TabNotation.kt` | 原事件的制谱分段、连线、谱行与统一 Cursor |
| `audio/SoundBank.kt`、`audio/Pcm.kt` | 素材配置、音长、重采样、扫弦展开与混音 |
| `audio/Player.kt`、`audio/PlaybackSink.kt` | 唯一会话与设备输出，按实际消耗帧推进 |
| `ui/Diagrams.kt`、`ui/Tablature.kt` | 图示、原弦品、点击区域与谱行跟随 |
| `ui/PracticeApp.kt`、`ui/Style.kt` | 页面参数、响应式布局、设置及视觉语义 |

以上 Kotlin 文件位于 `app/src/main/java/com/a3322505a/guitarlearning/`。首版证据保留在 [v49 历史记录](docs/首版验收记录-v49.md)，原执行方案继续作为首版背景。
