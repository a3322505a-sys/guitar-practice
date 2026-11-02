# 本地三文件曲包

准备同一目录下的 `manifest.json`、`score.json`、`practices.json`，然后运行：

```sh
python -m pip install -r scripts/song_package/requirements.txt
python scripts/song-package.py build --input my-song --output my-song.gpractice
```

`manifest.json` 使用以下外壳，乐谱仍沿用 scoreSchemaVersion 1：

```json
{
  "format": "guitar-practice-song",
  "formatVersion": {"major": 2, "minor": 0},
  "scoreSchemaVersion": {"major": 1, "minor": 0},
  "packageId": "local.my-song",
  "revision": 1,
  "display": {"title": "我的练习", "author": "自制", "summary": ""}
}
```

- 不需要 review、provenance、files 或 requires；旧 v1 五文件包继续导入，旧工具命令保留。
- 作者输入可省略 MIDI、可精确推导的常规/附点 written、空的可选集合、速度和练习坐标 offset。无速度时用 120 BPM，end.offset 缺省为目标小节末尾。
- 已写 MIDI/时值/技法仍按音乐事实检查。编辑作者目录时可以一直省略 MIDI，由 build 重新计算。
- 确定的空拍补为休止；不猜三连音等连音组的写法。build 输出三文件 ZIP，并打印默认速度和补休止位置。
- 改谱后直接再次 build，同一输出文件会更新。导入同 packageId、同 revision 或较低 revision 都会激活新内容。
- 内容历史按整包哈希分开保存，可用现有回退按钮恢复上次内容；已开始的播放会话继续使用原数据。

验证只覆盖制包、导入、编译与历史恢复主流程，不新增单元测试。播放内核、AlphaTab、节拍器和设置存储保持现有实现。
