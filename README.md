# Subscription Hub (Android)

本機優先的個人訂閱管理 App：集中管理訂閱、**預估**扣款、到期提醒與 CSV 匯出。
規格：[`docs/SubscriptionHub_Android_SA_v1.0.md`](docs/SubscriptionHub_Android_SA_v1.0.md)｜開發計畫：[`docs/SubscriptionHub_Dev_Plan.md`](docs/SubscriptionHub_Dev_Plan.md)

> 本 App 只記錄使用者自行輸入的資料；所有日期與金額均為預估，不代表實際扣款。

## 功能狀態

| Wave | 內容 | 狀態 |
|---|---|---|
| W0 | 專案骨架、Compose / M3、Hilt、自適應導航、CI | ✅ 完成（App 編譯由 CI 驗證） |
| W1 | Domain：金額、續訂日期引擎、預估、提醒規劃、CSV、表單驗證、搜尋排序 | ✅ 完成，73 項單元測試 |
| W2 | Room / DataStore / Repository | ⏳ 下一步 |
| W3–W7 | 訂閱 CRUD、Dashboard / 月曆 / 報表、提醒、匯出、QA | ⏳ |

## 模組結構

```text
core/domain/   純 Kotlin（JVM）模組，無 Android 依賴（SA NFR-06）
  money/       Money、CurrencyCatalog、MoneyParser、MoneyFormatter
  schedule/    BillingSchedule（錨點推算、月末、閏年、週繳）
  forecast/    ForecastCalculator（當月預估、月均／年均、分類占比，皆依幣別分組）
  reminder/    ReminderPlanner（D-7/3/1/0、去重鍵、漏跑合併）
  export/      CsvEncoder、SubscriptionCsvExporter（UTF-8 BOM、RFC 4180、公式注入防護）
  validation/  SubscriptionDraftValidator（FR-001/002/004）
  query/       SubscriptionQuery、DuplicateNames（FR-007/008）
app/           Android App（Compose UI、Hilt、Navigation）
```

與 SA §8.3 的差異：domain 層獨立為 Gradle 模組 `:core:domain`（SA 原本放在 `app/.../domain/`），讓日期與金額規則可以在任何 JVM 上測試，並由 `-Xjdk-release=1.8` 擋下 minSdk 26 不支援的 JDK API。

## 建置與測試

需求：JDK 17+、Android SDK（compileSdk 36）。

```bash
./gradlew :core:domain:test          # domain 單元測試（不需 Android SDK）
./gradlew :app:assembleDebug         # Debug APK
./gradlew :app:lintDebug :app:testDebugUnitTest
```

CI：`.github/workflows/android-ci.yml`，每次 push 都會跑 domain 測試、lint 與 Debug build，並上傳 APK artifact。

## 已採用的預設決策（可由 PO 推翻）

| ID | 決策 | 實作位置 |
|---|---|---|
| D-02 | minSdk 26、targetSdk / compileSdk 36 | `app/build.gradle.kts` |
| D-03 | `applicationId = com.howardaistudio.subscriptionhub`（首次上架後不可更改） | `app/build.gradle.kts` |
| G1 | 下一次扣款日一律由 `anchor_date + cycle` 即時推算，不依賴儲存值 | `BillingSchedule.firstOnOrAfter` |
| G2 | 新增 `billing_day`；輸入月底日（如 2/28）時可確認為「每月最後一天」 | `BillingSchedule.fromUserInput` |
| G3 | 去重只認 `SENT`；權限被拒時略過的提醒，授權後仍會發送 | `ReminderPlanner` |
| G4 | 漏跑時每筆續訂只補發 1 則（取最接近且尚未發送的提前天數） | `ReminderPlanner` |
| G5 | TRIAL 到首次付費日後轉為 ACTIVE | `Subscription.withTrialResolved` |
| G6 | `allowBackup=false`：不做雲端備份；Android 12+ 換機時仍可裝置對裝置轉移 | `AndroidManifest.xml` |
| — | TWD 以 scale 2 儲存、預設顯示 0 位小數；有小數時顯示 2 位 | `CurrencyCatalog`、`MoneyFormatter` |

## 已知限制

- 依賴版本已在 kickoff 時固定（`gradle/libs.versions.toml`）。開發 container 連不到 Google Maven，因此 AndroidX / AGP 版本由 CI 首次建置驗證。
- `gradle-wrapper.properties` 尚未設定 `distributionSha256Sum`（開發環境無法取得官方 checksum），建議在本機補上。
- 提醒採 WorkManager，不保證準時（SA §5.3）；目前尚未實作（W5）。

## 下一步

1. W2：Room v1 schema（含 `billing_day`、ReminderLog upsert）、DataStore、Repository。
2. W3：訂閱列表、表單（含月底確認）、詳情頁。
