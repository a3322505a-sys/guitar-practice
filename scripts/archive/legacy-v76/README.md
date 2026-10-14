# v76 及以前的歌曲生产工具归档

此目录保存旧歌曲 Kotlin 生成器、保护清单和一次性迁移捕获器，供追溯历史来源。它们不参与 App 编译、运行时歌曲注册、当前歌曲制作或 CI 生成。按原路径复现旧发行版本时，使用 Git 中的历史提交；不要把归档脚本的旧输出复制回当前源码。

基线 `7a6e68c` 保存用户已验收的 v76 实现；之前的原谱事件、教学整理及指法集合均可从 Git 查看。`ExportLegacySongs.kt` 是在该基线执行的迁移捕获器，捕获输出仅存在本地 `work/v77/legacy-snapshot.json`，没有第二套 App 运行时注册。新迁移产物在 `app/src/main/assets/song-packages/`，独立来源CSV、已审核PDF转录和原件继续保留。

`scripts/migrate-legacy-songs.py` 是本轮一次性转换工具，消费捕获文件并走现有 protocol 的校验/打包；它不生成 Kotlin。所有后续新歌或修订使用 `scripts/song-package.py` 的通用生产流程。

《七个Block》按用户要求仍为内置技巧练习，其来源导入工具保留在 scripts 根目录。
