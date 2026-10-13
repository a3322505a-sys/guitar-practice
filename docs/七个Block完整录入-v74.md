# 七个Block完整录入 v74

2026-10-13；目标工程 `guitar-practice`；v74 / 3.9.0-local26。

## 入口和覆盖

和弦 → 短句应用 → 练习设置 → 七个Block。7个独立完整入口沿用原播放、指板、六线谱和每短句设置；没有新增播放器或运行时曲库。

来源为用户指定桌面GP文件 `七个Block-吉他技巧练习-校对版-v1.gp`；SHA-256 `b9f5e0ae40d99035978764cf6c192b44e392877803d5b4e1958b14a1fe438081`。从GPIF读取单轨吉他、标准调弦、Capo 0。逐事件对照保留的校对转录 `七个Block练习/data.json`：38小节、803事件、988音头、3休止、201个死音音头、114个三连音内事件，弦品/音高/时值/起音/上下拨/反复边界全数匹配。

| 入口 | 原谱小节 | 书面小节数 | 展开演奏小节数 |
| --- | --- | ---: | ---: |
| SB-1 交替拨弦热身 | 1–3 | 3 | 12 |
| SB-2 换弦练习 | 5–14 | 10 | 32 |
| SB-3 手指组合练习 | 16–22 | 7 | 20 |
| SB-4 节奏型练习 | 24–27 | 4 | 16 |
| SB-5 跳弦练习 | 29–33 | 5 | 20 |
| SB-6 时值切换练习 | 35–38 | 4 | 20 |
| SB-7 音阶练习 | 40–44 | 5 | 20 |
| 合计 | | 38 | 140 |

## 音乐与使用规则

- 每条完整Block按GP反复展开到同一事件时间轴，已知组4遍、原38小节8遍。原11–14、16–17、18–19三组原次数待核，暂沿用交付GP各2遍；练习说明保留这项事实。
- TAB主号为原谱号，括号为该Block的展开序号；选区与播放状态用“原号 · 展开序号”区分同一个原谱小节的多次经过。示范完整播放展开谱一次，跟练次数表示完整展开谱的轮数；仍支持选任意小节范围。
- 每拍细分使用每Sequence可选24 tick/四分音符，直三十二分=3 tick、三十二分三连音=2 tick。旧内容仍默认12 tick，旧数据与生成结果未缩放。制谱时用实际段长，播放帧换算、Cursor、节拍声、预备拍和选区统一使用该Sequence的拍长。
- 支持3/4、4/4、5/4、6/4与原位置的拍号变化；三十二分显示三条符杠。保留实际弦品、上下拨和X死音，不臆测和弦名称。
- 60 BPM只是可调整的练习参考，起步建议40 BPM；原谱没有固定速度。App沿用自身音源，未复现GP RSE/效果链/拾音器自动化。
- 原4、15、23、28、34、39不在该GP的完整内容范围内，未补写。交付覆盖是该GP全部38个书面小节，不声称补全缺失原工程。

## 来源生产和检查

专属候选工具 `scripts/import-seven-blocks.py`复用共享GPIF引用图及Fraction节奏，固定原谱SHA，不修改现有GP门禁白名单。可用一个新的空目录重新生成；`SevenBlocks.kt`、`source-practice.json`、`report.json`保留书面事实与展开演奏关系。注册版本与来源摘要见 [来源报告](七个Block来源报告-v74.json)。

独立夹具 `app/src/test/resources/seven-blocks-source.csv`从保留的校对转录制作，非该GP导入器输出。`SevenBlocksTest`核对全部展开小节的每一个事件/音高/方向/三连音、全部7条反复路线、源缺口、制谱时长、预备/节拍/选区帧时长和旧12 tick契约；`SevenBlocksRenderTest`启动Activity、选择全部7入口并检查38个书面小节的实际Compose绘制。

现有v73尚未提交的顶部背景修复继续保留，工作前副本在本地 `work/seven-blocks-v74-baseline`。本轮没有请求GitHub同步，未推送。

## 验证与交付结果

- 完整 `testDebugUnitTest`：111项、0失败、0错误、0跳过；`lintDebug/lintRelease`通过，仅原有DataExtractionRules警告；`assembleRelease`成功。
- 完成截图隔离方式调整后，`--tests '*SevenBlocks*'`复核5项全过；实际查看38个书面小节的完整单小节渲染及三十二分、三连音、混合拍号和休止细节。此前完整展开路线截图受前一Block滚动/裁切影响，改成逐书面小节展示用于审核，没有修改生产音乐数据。
- 现有歌曲资产工具5项测试通过；另一个空候选目录重复生成后，Kotlin SHA-256 `84077fab93cdabfff9e3e7a3a36075100745b069bf684be0ced6d64116a08132`与App源码一致；原GP哈希保持不变。
- 正式包：[guitar-practice-3.9.0-local26-v74.apk](../output/guitar-practice-3.9.0-local26-v74.apk)。包名`com.a3322505a.guitarlearning`、versionCode 74、minSdk26/targetSdk35已由aapt回读。
- APK SHA-256：`71e3c6a1899a90566829102aa76a6476a8511256d9a7823c6f4fdbffb310014b`。沿用长期签名，证书SHA-256 `43b7af607adb264fb2faa79437d91dbc7e1bea7d7ce92804965fe0f1c8daf320`；v2/v3签名及zipalign检查通过。
- ADB前后均未检测到连接设备；本轮未实机安装、启动或操作。自动Compose测试启动Activity并选择7入口到同一Player，不等同于该APK在真机启动。真人试听/实弹/严格音画同步仍未验证。

本地渲染证据：`.local/seven-blocks-v74/SB-*-source-*.png`及`contact-reviewed.png`；本地检查摘要：`work/seven-blocks-v74-validation.json`。原GP、原校对稿和原音源均保留。
