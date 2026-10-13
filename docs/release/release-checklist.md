# 上架前檢核表（對應 SA §17）

狀態：✅ 完成｜🟡 部分完成／待驗證｜⬜ 未開始（依約定延後）

| # | SA §17 項目 | 狀態 | 證據／說明 |
|---|---|---|---|
| 1 | 原生 Android 原始碼可獨立編譯與安裝 | ✅ | CI `build` job；每次 push 發佈 debug pre-release |
| 2 | 訂閱 CRUD、分類、搜尋、狀態 | ✅ | W3；`ManageSubscriptionsTest`、`SubscriptionViewModelsTest`、`ScreensTest` |
| 3 | Room + DataStore 持久化且可升版 | 🟡 | v1 schema 已提交且 CI 檢查 drift；第一次升 v2 時需補 `MigrationTestHelper` 測試 |
| 4 | 月底、閏年、週繳、各幣別單元測試 | ✅ | `BillingScheduleTest`（含 10 年逐日比對）、`MoneyTest` |
| 5 | Dashboard、月曆、報表標明「預估」 | ✅ | 所有畫面含「預計／預估」與免責文案 |
| 6 | 多幣別分開呈現，無不合理合併 | ✅ | `ForecastCalculatorTest`、`ScreensTest`（200% 字級） |
| 7 | 通知允許／拒絕／背景延遲情境 | 🟡 | `ReminderRunnerTest`（允許、拒絕後補發、重跑不重複）；**實機省電情境待手動驗證** |
| 8 | CSV 匯出成功、可取消、錯誤處理 | ✅ | `CsvExportTest`、`SettingsViewModelTest`（AT-26、AT-27） |
| 9 | 清除資料與通知取消／去重 | ✅ | `SettingsViewModelTest`（AT-28）、`RoomRepositoriesTest` |
| 10 | 離線、字體縮放、折疊裝置版面 | 🟡 | 無網路依賴；200% 字級 UI 測試；自適應導航；**折疊／旋轉待實機驗證** |
| 11 | README、隱私政策、Data safety | 🟡 | `README.md`、`privacy-policy.md`、`play-data-safety.md` 草稿；待 PO 審閱與託管 |
| 12 | Release AAB 與簽章管理流程 | 🟡 | CI 已建置**未簽章** release APK／AAB（驗證 R8）；簽章依約定延後 |

## 延後項目（功能開發完畢後處理）
1. **Release 簽章**：產生 upload key，存入 GitHub Secrets；啟用 Play App Signing；CI 產出已簽章 AAB。
2. **共用 debug 簽章**：設定 `DEBUG_KEYSTORE_BASE64`／`DEBUG_KEYSTORE_PASSWORD`（見 README）。
3. **Play Console**：建立 App（`applicationId` 一經上架不可更改，D-03）、填寫 Data safety、上傳隱私政策網址、內部測試軌 → Closed Testing（新個人帳號可能需 12 位測試者 14 天，推測，需依當期政策確認）。
4. **版本號策略**：目前 `versionCode` = CI run number，正式版需改為獨立遞增規則。

## 手動驗收腳本（實機）
1. 首次開啟 → 引導頁 → 選幣別 → 開始使用。
2. 新增：ChatGPT Plus USD 20（每月）、Netflix TWD 390（每月）、iCloud TWD 1200（每年）、試用中的服務一筆。
3. 總覽：TWD／USD 分行顯示；未來 30 天清單正確。
4. 月曆：切換月份、點選有扣款的日期、點項目開啟詳情。
5. 報表：月均／年均、分類占比依幣別分開。
6. 允許通知 → 把某筆下次扣款日改為 3 天後 → 等待（最多約 1 小時）收到提醒 → 點通知開啟詳情。
7. 拒絕通知 → 總覽出現「系統通知未啟用」橫幅，App 正常使用。
8. 設定 → 匯出 CSV（取消一次、成功一次）→ 用試算表開啟，中文不亂碼、`=1+1` 不被執行。
9. 設定 → 清除全部資料（兩次確認）→ 列表為空、偏好保留。
10. 旋轉螢幕、200% 字級、深色模式、TalkBack 走過主要流程。
