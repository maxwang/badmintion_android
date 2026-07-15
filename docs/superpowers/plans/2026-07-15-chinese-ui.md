# Chinese UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** All user-facing copy switches to Chinese, byte-identical to the WeChat mini program (`E:\Code\ai\wechat\badminton`) wherever a counterpart string exists; Android-only strings get consistent translations. No behavior changes.

**Architecture:** Pure string substitution driven by the mapping tables below. Domain error reasons and display builders adopt the WeChat originals verbatim (this IMPROVES parity — reason strings become byte-identical to `data.js`). Domain tests are updated in the same commits (copy tests define copy). App screens re-labeled per the wxml sources. Docs updated to flip the language convention.

**Tech Stack:** No dependency changes. Kotlin sources are UTF-8 (Chinese fixture names already prove the toolchain handles CJK).

## Global Constraints

- Language conversion ONLY: no layout, logic, navigation, schema, or v2-feature changes. The Android Settings screen keeps its v1 shape (defaultRate field) — its label is a translation, not a port of WeChat's newer rate-history section.
- Where a WeChat counterpart exists, the Chinese string is copied VERBATIM from the source (full-width punctuation included: `，` `（）` `：` `、` `？`). Money stays `$X.XX`.
- Mapping tables below are the single source of truth. Every table row must land; nothing else changes.
- Tests updated alongside (same commit): a copy change is red→green via the updated assertions.
- Gates green at every commit. Branch `feat/chinese-ui` off `main`.

## String Mapping Tables

### T-A: domain edit reasons (from `utils/data.js`, verbatim)
| Current (English) | New (Chinese, data.js original) |
|---|---|
| This member has records and cannot be deleted | 该成员已有记录，不能删除 |
| Paid and credit amounts must be positive | 实付与到账额度需为正数 |
| Contribution amounts must be positive | 出资金额需为正数 |
| Contributions must add up to the paid amount | 出资合计需等于实付金额 |
| Please select a member | 请选择成员 |
| Amount must be positive | 金额需为正数 |
| Hours must be a positive number | 小时数需为正数 |
| Rate must be a positive number | 单价需为正数 |
| Factor must be a positive number | 折扣系数需为正数 |
| Select at least one player | 至少选择一名上场成员 |
| This week already has a record — edit the existing one | 该周已有记录，请编辑原记录 |
| Another record already exists in the target week | 目标周已有另一条记录 |
| Record not found | 记录不存在 |
| Nothing owing for the selected member (Settle.kt) | 该成员当前无欠款 *(no WeChat counterpart — translation)* |

### T-B: domain backup reasons (from `utils/data.js` validateImport, verbatim)
| Current | New |
|---|---|
| Not a valid backup file | 备份文件格式不正确 |
| Unsupported backup version | 备份文件版本不兼容 |
| Backup is missing member data | 备份文件缺少成员数据 |
| Backup is missing config data | 备份文件缺少配置数据 |
| Backup is missing refill data | 备份文件缺少充值数据 |
| Backup is missing payment data | 备份文件缺少收款数据 |
| Backup is missing session data | 备份文件缺少周记录数据 |
| Member data is incomplete | 成员数据不完整 |
| Config data is incomplete | 配置数据不完整 |
| Refill data is incomplete | 充值数据不完整 |
| Payment data is incomplete | 收款数据不完整 |
| Session data is incomplete | 周记录数据不完整 |
| Backup references a missing member | 备份数据引用了不存在的成员 |

### T-C: domain report/display builders (from `pages/report/report.js`, `pages/history/history.js`, verbatim)
| Where | Current | New |
|---|---|---|
| Report.kt memberName | Unknown | 未知 |
| Poster.kt weekly title | 🏸 Badminton Weekly Settlement | 🏸 羽毛球周结算 |
| Poster.kt face line | `${h}h × $${r} = $${f}` | `${h}小时 × $${r} = $${f}` |
| Poster.kt real line | `× factor ${x} = paid $${y}` | `× 折扣 ${x} = 实付 $${y}` |
| Poster.kt players header | Played this week (N) | 本周上场（N人） |
| Poster.kt owes/left prefixes | `owes $` / `left $` / `$` | `欠 $` / `剩 $` / `$` (same three-way split as report.js: player+balance rows use 欠/剩; monthly balance col uses 欠/plain) |
| Poster.kt balances header | Balances (didn't play) | 未上场成员余额 |
| Poster.kt pool line | Venue pool remaining: $X | 球馆额度剩余：$X |
| Poster.kt monthly title | 🏸 Badminton Monthly Report | 🏸 羽毛球月度报告 |
| Poster.kt monthly subtitle | `ym (N session(s), total paid $t)` | `ym（N次活动，合计实付 $t）` *(no plural logic — delete the conditional)* |
| Poster.kt monthly headers | Member/Played/Share/Balance | 成员/出场/应摊/当前余额 |
| History.kt session desc | `${h}h × $${r}, N player(s)` | `${h}小时 × $${r}，N人` *(full-width comma; no plural logic — delete the conditional)* |
| History.kt names joiner | ", " | 、 |
| History.kt refill desc | Paid $X → credit $Y | 实付 $X → 到账 $Y |
| History.kt payment desc | ${name} paid $X | ${name} 交来 $X |
| ReportOptions.kt week label | `${date} (paid $X)` | `${date}（实付 $X）` |

### T-D: app screens (from the wxml/json sources; Android-only strings marked *)
| Screen | Current | New |
|---|---|---|
| Manifest label + Home title | Badminton Ledger | 羽毛球记账 |
| Home pool card | Venue pool | 球馆额度剩余 |
| Home warn | Low balance — consider a refill | 额度不足，需要充值 |
| Home empty | No members yet — add members in Settings | 还没有成员，请先到「设置」添加成员 |
| Home/Payment balance rows | `owes $X` / `$X` | `欠 $X` / `剩 $X` (MemberBalanceRow gains the 剩 prefix for non-owing, matching home.wxml) |
| Home guest suffix | " (guest)" | （补位） *(full-width parens, no leading space — session.wxml)* |
| Home buttons | Record week/Refill/Payment/Report/History/Settings icon | 记录本周/充值/收款/报告/历史/设置 (icon contentDescription 设置) |
| Session title | Record This Week | 记录本周 |
| Session fields | Date/Hours/Rate ($/h)/Factor | 日期/小时数/球馆单价（$/小时）/折扣系数（实付/到账，默认取最近充值） |
| Session players header | Players | 本周谁上场？ |
| Session guest input/button | Guest name / Add guest | 临时补位姓名 / 添加补位 |
| Session preview card | Cost preview + lines | 费用预览 / `面额 $X × 折扣 = 实付 $Y` / `N 人上场 → 每人约 $Z` / 取整余数由名单最后一人吸收，合计精确 |
| Session save button | Save this week / Save changes | 保存本周记录 / 保存修改 |
| Session editing notice* | This week already has a record — editing it | 该周已有记录，正在编辑 |
| Refill title/fields | Refill / Date / Paid ($) / Credit ($) / Factor (paid ÷ credit): | 充值 / 充值日期 / 实付（$） / 到账额度（$） / 折扣系数（实付/到账）： |
| Refill contributions | Contributions (must total the paid amount) / Amount ($) / `Total  $X` | 各人出资（可不均等，如 600/600/800） / 出资金额（$）* / `出资合计（需等于实付）  $X` |
| Refill save | Save refill | 保存充值 |
| Payment title/fields | Receive Payment / Date | 收款 / 收款日期 |
| Payment prompt | Who paid? Checking a name settles their full debt. | 谁交钱了？（勾选即全额结清） |
| Payment chip | `${name} · owes $X` | `${name} 欠 $X` |
| Payment empty | No one owes right now 🎉 | 当前无人欠款 🎉 |
| Payment reference card / save | Current balances (reference) / Record payments | 当前余额（参考） / 保存收款 |
| Settings title/sections | Settings / Members / Defaults / Data | 设置 / 成员管理 / 默认参数 / 数据备份 |
| Settings member row | Rename / Guest / Delete cd | 重命名 / 补位 / 删除 |
| Settings add | New member name / Add | 新成员姓名 / 添加 |
| Settings defaults fields | Hourly rate ($) / Typical refill paid ($) / Typical refill credit ($) | 默认单价（$/小时）* / 充值实付（$） / 充值到账额度（$） |
| Settings save + toasts | Save defaults / Saved / Enter valid positive numbers* | 保存默认参数 / 已保存 / 请输入有效的正数* |
| Settings rename dialog | Rename member / Save / Cancel | 重命名成员* / 保存 / 取消 |
| Settings delete dialog | Delete member / Delete X? / Delete / Cancel | 删除成员* / 删除 X？ / 删除 / 取消 |
| Settings import/export | Import backup / Export backup | 导入数据 / 导出数据 |
| Import dialog* | This backup contains N members, M weekly records and K refills. Importing will replace ALL current data. Continue? | 备份包含 N 位成员、M 条周记录、K 条充值。导入将覆盖全部现有数据，继续？ |
| Import results* | Import successful / Could not read the file | 导入成功 / 无法读取文件 |
| VM loading* | Data is still loading | 数据加载中，请稍后再试 |
| Report title/chips | Report / Weekly / Monthly | 报告 / 周结算 / 月度报告 |
| Report pickers | Week / Month / No weeks recorded / No months recorded | 选择周次 / 选择月份 / 暂无记录 / 暂无月份 |
| Report buttons/toast | Generate poster / Share / Nothing to generate yet | 生成海报 / 分享 / 暂无可生成的记录 |
| Report preview cd* | Poster preview | 海报预览 |
| History title/sections | History / Weekly records (last 12 months) / Refills / Payments | 历史 / 周记录（近12个月，点击可编辑/删除） / 充值记录（点击可删除） / 收款记录（点击可删除） |
| History session dialog* | Weekly record X / Edit / Delete | 周记录 X / 编辑 / 删除 |
| History delete dialog | Delete {weekly,refill,payment} record / body / Delete / Cancel | 删除{周,充值,收款}记录 / 删除后所有余额自动重算，确定？ / 删除 / 取消 (labels become 周/充值/收款; title `删除${label}记录` — history.js verbatim) |
| Share chooser titles* | Share poster / Share backup | 分享海报 / 分享备份 |
| Back contentDescription* | Back | 返回 |

---

### Task 1: domain strings (tables T-A, T-B, T-C) + all affected domain tests

**Files:** `domain/src/main/kotlin/com/badmintonledger/domain/edit/{MemberEdits,LedgerEdits,Settle}.kt`, `backup/BackupCodec.kt`, `report/{Report,Poster,History,ReportOptions}.kt` and their test files (`LedgerEditTest`, `SettleTest`, `BackupCodecTest`, `PosterTest`, `HistoryTest`, `ReportOptionsTest`, `ReportTest` if it asserts "Unknown").

- [ ] Apply every T-A/T-B/T-C row; delete the two plural conditionals (Poster monthly subtitle, History players count) in favor of the WeChat single forms.
- [ ] Update every test assertion that names an old string to the new Chinese string (grep for each English literal to find them all; also update expected line counts if none change — they should not).
- [ ] `gradlew.bat :domain:test ktlintCheck detekt` → green.
- [ ] Commit: `feat(domain): Chinese copy verbatim from the WeChat source`

### Task 2: app screens (table T-D)

**Files:** `app/src/main/AndroidManifest.xml`, `LedgerViewModel.kt`, `ui/{HomeScreen,SessionScreen,RefillScreen,PaymentScreen,SettingsScreen,ReportScreen,HistoryScreen,AppNav}.kt`, `ui/components/LedgerParts.kt` (MemberBalanceRow 剩 prefix + （补位） suffix), `poster/PosterShare.kt`, `backup/BackupExport.kt` (chooser titles).

- [ ] Apply every T-D row. Grep each English literal to catch every occurrence (contentDescriptions included). The MemberBalanceRow change (non-owing rows gain the `剩 ` prefix) applies wherever the component renders (Home + Payment reference — one change).
- [ ] `gradlew.bat assembleDebug test ktlintCheck detekt` → green.
- [ ] Commit: `feat(app): Chinese UI copy matching the WeChat mini program`

### Task 3: docs + acceptance

- [ ] `CLAUDE.md`: change the conventions line to "UI copy is Chinese, verbatim from the WeChat mini program where a counterpart exists; money formatted as `$X.XX`."
- [ ] `docs/superpowers/specs/2026-07-11-android-app-design.md`: same change in Tech Stack/invariants ("UI copy is English" line).
- [ ] Full `gradlew.bat test ktlintCheck detekt assembleDebug` → green; grep the app source tree for leftover English UI literals (spot-check list: "owes", "Save", "Delete", "Import", "Share", "Week") — hits only allowed in code identifiers/comments.
- [ ] Commit: `docs: UI language convention is Chinese (WeChat-verbatim)`

## Acceptance Checklist
- [ ] Gates green; 67 domain tests (1 skipped) — count unchanged
- [ ] No user-facing English string remains in `app/src/main` or domain display/reason strings
- [ ] Poster/labels byte-identical to WeChat where counterparts exist
