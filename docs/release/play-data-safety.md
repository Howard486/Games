# Google Play Data safety 填答建議（MVP 0.1.x）

> 依實際程式碼（2026-10-13，commit 以 `docs/release` 所在版本為準）整理。上架前請在 Play Console 依**當期表單**重新核對（SA R7）。

## 資料收集與分享
| 問題 | 建議答案 | 依據 |
|---|---|---|
| App 是否收集或分享任何必要揭露的使用者資料類型？ | **否** | 無網路傳輸；資料只存在本機（SA §10.2） |
| 資料是否在傳輸中加密？ | 不適用（無傳輸） | 未使用網路權限 |
| 使用者能否要求刪除資料？ | 可於 App 內刪除（本機） | 設定 → 清除全部資料（FR-028） |

> Play 的定義中，「只在裝置上處理、不傳出裝置」的資料不屬於「收集」。若日後加入分析 SDK、當機回報或雲端同步，本表必須重填。

## 權限清單（需與 Console 宣告一致）
| 權限 | 用途 | 備註 |
|---|---|---|
| `POST_NOTIFICATIONS` | 本機續訂提醒（Android 13+） | 僅在使用者於總覽橫幅點選「允許通知」時請求 |
| WorkManager 相依權限（`RECEIVE_BOOT_COMPLETED`、`WAKE_LOCK` 等，由函式庫合併） | 重新開機後恢復排程 | 由 androidx.work 自動加入 |
| 無 `INTERNET`、無儲存空間權限、無精確鬧鐘權限 | — | CSV 匯出使用 Storage Access Framework |

## 驗證步驟（上架前）
1. 從 release AAB 解出合併後的 manifest：`bundletool dump manifest --bundle app-release.aab`，確認權限清單與上表一致。
2. 確認 `INTERNET` 未被任何相依套件帶入；若出現，評估來源並更新本文件。
