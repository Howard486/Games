# Subscription Hub｜Android MVP 開發計畫 v0.1

> 依據：`SubscriptionHub_Android_SA_v1.0`（2026-10-10，開發基準草案）
> 本文件：將 SA 轉為可執行的 Sprint / Codex 工作包、補齊 SA 缺口、列出開工前必須凍結的決策。
> 標示「推測」者為估算或需查證之資訊。

---

## 1. 結論與決策

| 項目 | 建議 |
|---|---|
| 開發路線 | 完全依 SA 的 W0→W7，但 **W1（純 Kotlin domain + 測試）先行且獨立驗收**，UI 不得早於 W1 合併 |
| 工期（1 名 Android 開發者 + Codex） | 約 **7–9 週** 到 Closed Testing（推測，取決於 UI 精緻度與 QA 裝置數） |
| minSdk | **26**：原生 `java.time`、原生 Notification Channel，免 core library desugaring |
| DI | Hilt（KSP） |
| 導航 | Navigation Compose（type-safe routes）+ Material 3 Adaptive `NavigationSuiteScaffold`（手機底部分頁／大螢幕 Rail 自動切換） |
| Repo | 建議另開專用 repo（例：`subscription-hub-android`），不要放在 `games` |
| CI | GitHub Actions：`lint`、`testDebugUnitTest`、`assembleDebug`、Room schema 檢查；W7 加 `bundleRelease` |
| 開工前必須凍結 | D-02 minSdk、D-03 applicationId、D-06 備份策略、**下方 §3 的 G1/G2 日期模型** |

---

## 2. Sprint 計畫

| Sprint | Wave | 期間（推測） | 交付 | 驗收（AT） | Exit Criteria |
|---|---|---|---|---|---|
| S0 | W0 | 2–3 天 | Gradle version catalog、Compose/M3 骨架、Hilt、KSP、Room schema export、CI、README 骨架 | — | CI 綠燈、空 App 可安裝 |
| S1 | W1 | 5 天 | `Money`/`CurrencyCatalog`、`RecurringDateCalculator`、`ForecastCalculator`、`ReminderPlanner`、`CsvEncoder`（皆為純 Kotlin） | AT-04, 09–12, 14, 25, 27 | 邊界測試 100% 通過；不依賴 Android Context |
| S2 | W2 | 3–4 天 | Room Entities/DAO（v1 schema 定版）、Repository、DataStore | DAO 測試 | `schemas/1.json` 提交；FK cascade 測試通過 |
| S3 | W3 | 7 天 | 訂閱列表／表單／詳情、搜尋排序篩選、狀態切換、表單驗證 | AT-01–03, 05–08 | Loading/Empty/Error/Success 四態齊全 |
| S4 | W4 | 5 天 | Dashboard、月曆、報表（按幣別分組） | AT-09, 10, 14, 23–25 | 無跨幣別合計；全部標示「預估」 |
| S5 | W5 | 5 天 | WorkManager 巡檢、Channel、POST_NOTIFICATIONS、去重、Deep link | AT-16–22 | 拒絕權限不崩潰；重跑 Worker 不重複推送 |
| S6 | W6 | 3–4 天 | SAF CSV 匯出、提醒設定、清除資料、Onboarding、隱私說明頁 | AT-26–29, AT-15 | 取消匯出不宣稱成功；清除後 Room/ReminderLog 為空 |
| S7 | W7 | 5 天 | 回歸／a11y（TalkBack、字體 200%）、1,000 筆效能、AAB、簽章、隱私政策、Data safety | AT-30 + 全部 | §17 檢核表全勾、上傳 Closed Testing |

> **上架時程風險**：Google Play 對新建「個人」開發者帳號要求先進行 Closed Testing（過往規則為 ≥12 位測試者連續 14 天）才可申請正式版（推測：需於上架前在 Play Console 確認現行規則）。若適用，正式上架需再加約 2–3 週。

---

## 3. SA 缺口與修正建議（開工前定案）

| ID | 嚴重度 | 缺口 | 建議解法 |
|---|---|---|---|
| G1 | 高 | **未定義 `next_billing_date` 過期後如何前進**（誰推進、何時推進）。Dashboard、月曆、提醒都依賴它 | 以 `anchor_date + cycle` 為唯一真相；`nextOccurrence(today)` 為純函式即時計算。`next_billing_date` 僅作「使用者確認值／快取」，App 啟動與 Worker 掃描時重算回寫 |
| G2 | 高 | §6.1 邊界規則「next_billing_date 與錨點不符 → 改為新錨點」，加上表單「儲存時同步設為錨點」，會讓使用者輸入 2/28（實為每月 31 日扣款）後永久錨定 28 日，**違反 FR-011** | v1 schema 增加 `billing_day`（1–31）欄位；使用者輸入的日期為該月最後一天時，表單詢問「是否每月月底扣款？」。現在加欄位成本最低，避免日後 migration |
| G3 | 高 | `ReminderLog` UNIQUE(subscription_id, renewal_date, days_before) 與 §9.2-5「SKIPPED_PERMISSION 不可阻擋未來 SENT」衝突 | DAO 使用 `INSERT … ON CONFLICT DO UPDATE … WHERE status <> 'SENT'`；去重判斷只看 `status = 'SENT'` |
| G4 | 中 | 「不補發舊提醒」未定義遲到時的行為（例：D-3 漏跑，D-2 才執行） | 每個 occurrence 每次掃描最多送 1 則：取 `min{t ∈ 已選天數 | t ≥ daysUntil}` 為去重鍵，文案用實際 `daysUntil` |
| G5 | 中 | TRIAL 到首次付費日後狀態是否自動轉 ACTIVE 未定義 | 建議自動轉 ACTIVE 並於詳情頁標示「試用已結束（預估）」 |
| G6 | 中 | Android Auto Backup 預設開啟，與 D-06「不提供同步」認知衝突 | `dataExtractionRules`：**排除 cloud-backup、允許 device-transfer**（換機可帶走、不上雲）；PO 確認 |
| G7 | 中 | 「清除全部資料」未涵蓋已顯示通知與 DataStore | 清除 Room + ReminderLog + `NotificationManager.cancelAll()`；DataStore 是否重置由 PO 決定（建議保留偏好、重置 onboarding 以外不動） |
| G8 | 低 | 時區／日期變更觸發未定義 | 監聽 `ACTION_TIMEZONE_CHANGED`、`ACTION_TIME_CHANGED`、`ACTION_DATE_CHANGED`（皆為隱式廣播豁免）→ enqueue OneTimeWork |
| G9 | 低 | TWD scale=2 與一般顯示習慣（無小數）不同 | 儲存依 SA（scale 2），顯示層 TWD 預設 0 位小數；`CurrencyCatalog` 為程式內固定表，**不依賴 ICU** |
| G10 | 低 | 通知 Deep link 的 back stack 未定義 | PendingIntent → `MainActivity` + route；詳情查無資料時導向清單並 Snackbar 提示 |

---

## 4. Codex 工作包（Ticket 拆分）

每張 ticket 依 SA §13.1 DoD 驗收；一張 ticket 一個 PR。

| Ticket | Wave | 內容 | 對應 FR / AT |
|---|---|---|---|
| T01 | W0 | 專案初始化、version catalog、Hilt/KSP、CI workflow | NFR-07, 11 |
| T02 | W0 | Theme（M3 dynamic color 可關）、共用元件、strings.xml 骨架 | NFR-09, 10 |
| T03 | W1 | `Money`、`CurrencyCatalog`（TWD/USD/JPY/EUR）、解析與溢位檢查 | FR-004 / AT-04 |
| T04 | W1 | `RecurringDateCalculator`（anchor + n、月末、閏年、週繳） | FR-011, 012 / AT-03, 11, 12 |
| T05 | W1 | `ForecastCalculator`（當月預估、月均、年均、按幣別分組） | FR-009, 010, 014 / AT-09, 10, 14 |
| T06 | W1 | `ReminderPlanner`（G4 規則、去重鍵、狀態排除） | FR-016, 019, 022 |
| T07 | W1 | `CsvEncoder`（UTF-8 BOM、RFC 4180、公式注入防護） | FR-027 / AT-27 |
| T08 | W2 | Room v1：Subscription（含 G2 `billing_day`）、ReminderLog、DAO、cascade | §7 |
| T09 | W2 | Repository + DataStore（AppSettings） | §7.1 |
| T10 | W3 | 訂閱表單 + 驗證 | FR-001, 002, 005 / AT-01, 02 |
| T11 | W3 | 列表（搜尋／排序／篩選／重複提示） | FR-007, 008 / AT-07, 08 |
| T12 | W3 | 詳情 + 狀態切換 + 刪除 | FR-006 / AT-06 |
| T13 | W4 | Dashboard | FR-023 / AT-23 |
| T14 | W4 | 月曆 | FR-024 / AT-24 |
| T15 | W4 | 報表 | FR-025 / AT-25 |
| T16 | W5 | ReminderWorker + 唯一排程 + 時區廣播 | FR-017, 018 / AT-17, 18 |
| T17 | W5 | 通知權限流程、Channel、Deep link | FR-020, 021 / AT-20, 21 |
| T18 | W6 | SAF CSV 匯出 | FR-026 / AT-26 |
| T19 | W6 | 設定頁、清除資料（G7）、Onboarding | FR-028 / AT-28 |
| T20 | W7 | 回歸、a11y、效能、AAB、隱私政策、Data safety | §17 |

---

## 5. 測試重點（Golden Cases，W1 必須先寫）

| 類別 | 案例 |
|---|---|
| 月末 | 錨點 1/31 → 2/28 → 3/31 → 4/30；閏年 2/29；錨點 30、29 日 |
| 年繳 | 2024-02-29 → 2025-02-28 → 2028-02-29 |
| 週繳 | 跨月、跨年 7 日遞推 |
| 當月範圍 | `[月 1 日, 次月 1 日)`；週繳單月 4 或 5 次 |
| 幣別 | USD 9.99→999/2、JPY 1000→1000/0、TWD 199→19900/2；TWD+USD 不相加 |
| 狀態 | PAUSED/CANCELLED/ARCHIVED 不產生事件與提醒；TRIAL 首次付費日 |
| 提醒 | D-7/3/1/0；Worker 重跑不重複；SKIPPED 後授權可補 SENT；漏跑只送一則 |
| CSV | 逗號、雙引號、換行、`=1+1`、`+`、`-`、`@`、Tab 開頭 |
| 時區 | Asia/Taipei ↔ Europe/London，LocalDate 不位移 |

---

## 6. 下一步清單

| 優先 | 行動 | Owner |
|---|---|---|
| P0 | 定案 G1/G2 日期模型（影響 schema v1，最晚 S1 結束前） | PO / Android Lead |
| P0 | 定案 D-02 minSdk（建議 26）、D-03 applicationId（上架後不可改） | PO |
| P0 | 建立專用 repo，把 SA 放入 `docs/` 作為 Codex context | Howard |
| P1 | 定案 G6 備份策略、G5 TRIAL 轉換、G7 清除範圍 | PO |
| P1 | 確認 Play Console 新帳號 Closed Testing 規則，排入時程 | PO |
| P2 | 準備隱私政策託管頁面（GitHub Pages 即可） | Howard |
