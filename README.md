# Subscription Hub (Android)

本機優先的個人訂閱管理 App：集中管理訂閱、**預估**扣款、到期提醒與 CSV 匯出。
規格：[`docs/SubscriptionHub_Android_SA_v1.0.md`](docs/SubscriptionHub_Android_SA_v1.0.md)｜開發計畫：[`docs/SubscriptionHub_Dev_Plan.md`](docs/SubscriptionHub_Dev_Plan.md)

> 本 App 只記錄使用者自行輸入的資料；所有日期與金額均為預估，不代表實際扣款。

## 功能狀態

| Wave | 內容 | 狀態 |
|---|---|---|
| W0 | 專案骨架、Compose / M3、Hilt、自適應導航、CI | ✅ 完成（App 編譯由 CI 驗證） |
| W1 | Domain：金額、續訂日期引擎、預估、提醒規劃、CSV、表單驗證、搜尋排序 | ✅ 完成，74 項單元測試 |
| W2 | Room v1 schema、DataStore 設定、Repository、Hilt 資料模組 | ✅ 完成，11 項 Robolectric / JVM 測試 |
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
  repository/  Repository 介面（Subscription / ReminderLog / Settings）
  time/        TimeSource（每次呼叫都重新讀取時區）
app/           Android App（Compose UI、Hilt、Navigation）
  data/local/        Room entities、DAO、SubscriptionHubDatabase、mappers
  data/preferences/  DataStore 設定
  data/repository/   Repository 實作
  di/                Hilt 模組
app/schemas/   Room schema 歷史（已提交；CI 會檢查與 entity 是否一致）
```

與 SA §8.3 的差異：domain 層獨立為 Gradle 模組 `:core:domain`（SA 原本放在 `app/.../domain/`），讓日期與金額規則可以在任何 JVM 上測試，並由 `-Xjdk-release=1.8` 擋下 minSdk 26 不支援的 JDK API。

## 建置與測試

需求：JDK 17+、Android SDK（compileSdk 36）。

```bash
./gradlew :core:domain:test          # domain 單元測試（不需 Android SDK）
./gradlew :app:assembleDebug         # Debug APK
./gradlew :app:lintDebug :app:testDebugUnitTest
```

CI：`.github/workflows/android-ci.yml`，每次 push 都會跑 domain 測試、App 單元測試（Robolectric）、lint、Debug build，並檢查測試數量（任一模組 0 項即失敗）與 Room schema drift，最後上傳 APK artifact。

## 下載測試版 APK

每次 push 到分支後，CI 會發佈一個滾動更新的 **pre-release**（每個分支一個，固定網址，手機瀏覽器可直接下載，不需登入）：

```text
https://github.com/Howard486/Games/releases/download/debug-<branch>/subscription-hub-debug.apk
```

- 套件名稱為 `com.howardaistudio.subscriptionhub.debug`，可與日後的正式版並存。
- `versionCode` = CI run number，`versionName` = `0.1.0-ci.<run>-debug`，新版可直接覆蓋安裝。
- 僅供內部測試；repo 為 public，任何人都能下載此測試版。

### 共用 debug 簽章（GitHub Secrets）

為了讓每次建置都能覆蓋安裝，CI 使用同一把 debug key 簽章。key **只存在 GitHub Secrets**，不提交進 repo（repo 為 public）：

| Secret | 內容 |
|---|---|
| `DEBUG_KEYSTORE_BASE64` | PKCS12 keystore 的 base64（alias 固定為 `debug`） |
| `DEBUG_KEYSTORE_PASSWORD` | keystore 與 key 共用的密碼 |

設定位置：GitHub repo → Settings → Secrets and variables → Actions → New repository secret。未設定時 CI 改用臨時 key（會出現 warning），APK 仍可安裝，但無法覆蓋前一版。

自行重新產生 key：

```bash
keytool -genkeypair -keystore debug.keystore -storetype PKCS12 -alias debug \
  -keyalg RSA -keysize 2048 -validity 10950 -dname "CN=Subscription Hub Debug, O=HowardAIStudio, C=TW"
base64 -w0 debug.keystore   # 貼到 DEBUG_KEYSTORE_BASE64
```

更換 key 後，手機上的舊版必須先移除一次。正式版（release）簽章與 Play App Signing 屬於 W7，會使用另一把 key。

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

- 依賴版本已在 kickoff 時固定（`gradle/libs.versions.toml`），並已通過 CI（AGP 8.13.0 / Kotlin 2.2.21 / Compose BOM 2025.08.00）。雲端開發 container 連不到 Google Maven（`dl.google.com`），所以 App 模組只能在 CI 或本機建置。
- `gradle-wrapper.properties` 尚未設定 `distributionSha256Sum`（開發環境無法取得官方 checksum），建議在本機補上。
- 提醒採 WorkManager，不保證準時（SA §5.3）；目前尚未實作（W5）。

## 資料庫變更規則

1. 修改 entity 時，同時把 `SubscriptionHubDatabase.version` 加 1，並撰寫 `Migration`。
2. 提交 Room 新產生的 `app/schemas/.../<version>.json`；CI 的 schema drift 檢查會擋下未提交的變更。
3. 新增 `MigrationTestHelper` 測試；正式版不得使用 `fallbackToDestructiveMigration`。

## 下一步

1. W3：訂閱列表、表單（含月底確認）、詳情頁，以及 ViewModel / UseCase。
2. W4：Dashboard、月曆、報表。
