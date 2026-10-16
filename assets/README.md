# SoundFont 生产原料

`soundfont-source/guitar-nylon/` 保留 v95 使用的全部 22 份 CC0 PCM、原许可、来源说明和逐文件 SHA-256 清单。它是正式 `sonivox.sf2` 的生产输入；不能按废弃播放器素材删除。

生产与核对命令保持不变，使用固定的 alphaTab 1.8.4 AAR 和 Python 标准库：

```text
python scripts/build-practice-soundfont.py path/to/alphaTab-1.8.4.aar
python scripts/build-practice-soundfont.py path/to/alphaTab-1.8.4.aar --check
```

脚本生成 `app/src/main/assets/sonivox.sf2` 及 `third-party/nylon/` 的许可、README、manifest 副本。`--check` 同时检查 SoundFont 和这三份分发记录。固定上游来源的准备脚本 `scripts/prepare-nylon.py` 也输出到本目录；重新取得、转换原始录音才需要其已有 numpy/scipy/soundfile 环境。

debug 的额外 assets 源目录直接复用此处输入，仍以 `guitar-nylon/*.pcm` 提供旧播放器对照。release/p0 不包含这些原始 PCM，只使用生成的 SoundFont。旧电吉他 PCM 单独保留在 `app/src/debug/assets/guitar/`。

v95 SoundFont SHA-256：`02ab9f358d1d79906e07ba8eb1d08993addfa3f23a9aaebea51e4589379a2bf8`。本轮不改变采样、校准、音色映射、节拍短音或混音参数。
