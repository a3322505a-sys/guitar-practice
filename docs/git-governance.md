# Git 分支与交付约定

> 本文只约束仓库管理，不改变 Android App 功能、包名、签名、用户数据或既有提交历史。

## 唯一主线与任务边界

- `main` 是唯一发布基线；每个任务从最新 `main` 建一条短期分支。
- 分支名用 `codex/<task>`、`feat/<task>`、`fix/<task>`、`chore/<task>`、`docs/<task>` 或 `refactor/<task>`；同一任务不反复开平行分支。
- 一个可独立验收的变更对应一个 PR。PR 写清楚**需求—规则—验收**、影响范围、实际执行的测试与遗留风险。
- 不直接向 `main` 推功能代码、不强制推送、不重写已共享提交；发现问题用修复 PR，不以“整理历史”为由重置主线。
- 旧版本作为 commit/PR/归档记录查找，不依靠长期保留的已合并工作分支。`docs/archive/` 只存确实过期的材料；不要为归档而批量移动截图或有效证据。

## 合并与交付

1. PR 的代码/文档范围清晰、没有未解决的关键风险。
2. 等待 `Android core / verify` 核心 CI 检查成功；仅凭 CI 不声称音色、真机表现、APK 覆盖安装已通过。
3. 原应用 ID 和长期签名不变；正式 APK 交付时递增版本，验证可以覆盖安装。个人凭据、签名密钥不进仓。
4. 合并至 `main` 后，以主线 CI 与交付记录为准。旧分支完成后删除，保留 Git 历史与 PR 追溯。

## 自动分支卫生

`.github/workflows/branch-hygiene.yml` 使用最小限度的 `contents: write`：

- 合并 PR 时，只对**本仓库** `codex/feat/fix/chore/docs/refactor` 短期分支操作；
- 必须同时满足“分支 SHA 未变”和“分支没有未合并提交（ahead=0）”才删除；不删除 `main`。
- 2026-10-17 记录的 18 条历史 `codex/*` 分支也按**固定名称 + 固定 SHA + 再次比较**处理；改名、推进或出现未合并提交的分支不会被误删。
- 事件：主线 push（重试旧分支清理）、PR merged（清理新分支），另支持手动重跑。
- 如果 GitHub Actions 的 token 写权限被策略限制，任务会失败并留下日志；这不等于已经删除分支。请以 GitHub Branches 列表复核结果。

## GitHub 仓库设置（需管理员在网页完成）

当前连接只支持读取仓库设置，无法替你改动以下两项。进入仓库 Settings：

1. **General → Pull Requests**：勾选 `Automatically delete head branches`。
2. **Rules → Rulesets → New branch ruleset**：目标为默认分支 `main`，开启禁止强推、禁止删除、要求通过 PR 更新、要求状态检查 `Android core / verify`。个人项目无需强制人工审批人数，以免自己被阻塞。启用前核对当前 CI 的实际检查名称。

启用原生“合并后自动删除分支”后，可考虑移除本仓库的 PR-closed 自动删除作业，仅保留历史清理记录；避免维持两套相同职责。

## 本次清理的界限

2026-10-17 审计时 `main` 为 `66730d4bc87f2fdba814983b0cce81e8dd5440da`：18 个 `codex/*` 分支均为 `ahead=0`，没有开启的 PR。自动清理脚本内保留 18 条分支的审计 SHA。删除的只是 Git ref，不回滚、Squash 或重写已有代码和历史。

仓库当前在 GitHub 是 **Public**（虽然名字包含 `private`）。本次不修改可见性；如需私有请单独核对 Settings → General → Danger Zone 的可见性选项。
