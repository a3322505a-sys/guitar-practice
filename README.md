# 吉他练习

独立的个人 Android 工程，Kotlin / Compose，一个 `:app` 模块。按 [执行方案](docs/执行.md) 实现离线音阶和开放和弦练习。

首版内容：C 大调五型、A 自然小调视角、六类音阶练法、弦范围／一八度选择、四分／八分音符；C、Am、G、Em、D、Dm、E、A 的具体开放指法，以及逐弦检查、两和弦转换、规则下拨、简单分解。内容直接开放，可点音、听示范、跟练和撤提示自行对照。

默认 60 BPM、4/4、跟练预备 1 小节、4 次循环；示范播放 1 遍。暂停／后台停止推进和发声，手动继续从所在小节起点恢复。仅保存音名、提示和声音开关等使用设置，不读写 001 的学习档案。

代码保持直接：音乐事实与数据、有限事件编排、PCM 混音与播放、图示、页面各自负责一件事。没有旧课程、判题、数据库、通用课程引擎或未实现功能的空接口。设备输出错误会显示原因；取消和释放、音位边界、签名身份检查仍是实际功能所需。

## 本地入口

- 签名 APK：[guitar-practice-3.0.0-local01-v49.apk](output/guitar-practice-3.0.0-local01-v49.apk)
- [验收记录和未验证项](docs/验收记录.md)
- [来源与许可](docs/来源与许可.md)
- [本地组件渲染图](docs/screenshots/phone-scale.png)，属于 Robolectric 原生渲染，不是真机截图。

应用 ID `com.a3322505a.guitarlearning`，版本 `3.0.0-local01` / `versionCode 49`，沿用 001 长期签名。已核实源码基线是 alpha31 / 48；这不等于核实用户手机当前安装版本。

**安装运行与真机音画同步尚未验证，当前不能标为 P4 全部验收完成。** 详见验收记录。项目仅保存在本机，没有添加远端或推送 GitHub。

## 构建

本机复用现有 JDK 17 / Android SDK 35，脚本只设置其进程环境：

```powershell
.\scripts\build-local.ps1 testDebugUnitTest lintDebug assembleRelease
.\scripts\sign-local.ps1
```

其他机器设置 JDK 17 和 Android SDK，配置 `local.properties` 后使用 `./gradlew testDebugUnitTest lintDebug assembleRelease`。Gradle 8.9、AGP 8.7.3、Kotlin 2.0.21 和 Compose BOM 2024.12.01 沿用已构建的工具版本；升级依赖留到后续有需要时处理。

签名脚本使用已有私钥及密码文件，校验证书后执行 zipalign / apksigner。仓库只保存路径示例和公有证书指纹，私钥、密码及本地工具不提交。

## 修改入口

| 文件 | 职责 |
| --- | --- |
| `music/Music.kt` | 标准调弦、音名、调性、五型与具体和弦数据 |
| `practice/Sequence.kt` | 音阶路线、和弦弦序、时值、休止、循环与恢复小节 |
| `audio/Pcm.kt` | CC0 采样重采样、整和弦／扫弦混音、绝对 tick 到采样帧 |
| `audio/Player.kt` | 唯一播放入口，读取 AudioTrack 实际播放位置、取消、释放 |
| `ui/Diagrams.kt` | 横向指板、竖向和弦图与同一几何的点击命中 |
| `ui/PracticeApp.kt` | 两入口、选择、自检、播放控制与使用设置 |

以上路径均在 `app/src/main/java/com/a3322505a/guitarlearning/`。现有 001、002 工程保留为来源，没有改动其源码。
