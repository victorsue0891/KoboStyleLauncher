# Kobo 風格 UI 更新 — 2026-11-16

依使用者提供的 `kobo-ui-example.png` 實作。這是 BOOX 上的獨立書房 App，保留原廠 HOME／NeoReader；沒有 Kobo 商店、雲端同步或閱讀進度 API。

## 已實作

- 首頁：上方四本實際書籍、大封面及自己的開啟日期；中段雙欄「我的書籍／最近開啟」及堆疊封面；下段加入資料夾與閱讀設定入口。窄螢幕顯示兩本，內容超出高度可捲動。
- 黑白線條圖示、白底、細分隔線、襯線書名、固定底部導覽。無陰影、動態轉場、週期性時鐘刷新或假閱讀百分比。
- 書庫使用 RecyclerView 網格，依螢幕寬度及字級選擇 2–5 欄；保留長書名省略、來源異常提示、實際作者及開啟日期。封面等比例，缺封面時使用可縮放文字的黑白書封。
- 放大鏡開啟本機搜尋，書名／作者不分英文大小寫；支援無結果提示、顯示全部、畫面重建保留搜尋條件。
- 點封面／書名直接開書；長按封面或「⋯」開啟選單，保留「其他閱讀器」安全選擇器入口。原有 SAF、Room 與 ACTION_VIEW 橋接不變。
- 狀態列圖示提供系統設定、Wi-Fi 設定、實際電量、重新掃描及搜尋。太陽圖示只是系統設定捷徑，不宣稱控制 BOOX 前光。時鐘／電量／Wi-Fi 在返回前景取樣。
- App 視窗隱藏系統狀態列，避免重複時鐘；可由頂端滑出。保留系統 Back／導航，不寫入系統設定。

## 重現命令

Windows PowerShell，已連線且授權的 Leaf2；以下安裝沿用使用者既有授權：

```powershell
. .\scripts\use_local_tools.ps1
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -ReaderUi
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -PageKeys
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -Extended
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -RotationOnly
```

測試 runner 僅啟用可能被 OEM 凍結的 instrumentation 套件，不會自動啟用主 App。ReaderUi、PageKeys、Extended 均使用獨立記憶體 Room DB，不把合成書籍加入使用者書庫，不撤銷授權，不開啟未授權文件。旋轉僅呼叫本 App Activity 的 requestedOrientation，並恢復。

## 驗證結果

最終 debug APK 已覆蓋安裝成功，大小 9,115,684 bytes，SHA-256：

`B287B9BC9144284953E0694028B75C341DF9D52B72E4AC2F88BF4D04DA573B86`

| 項目 | 結果 |
|---|---|
| Kotlin APK／instrumentation APK 建置 | `BUILD SUCCESSFUL in 21s` |
| JVM／Robolectric | 21 tests，0 failures、0 errors |
| Lint | 0 errors、31 warnings：13 個工具／相依版本建議、11 個 UseKtx、5 個舊資源未使用、1 個英文複數建議、1 個 debug 測試 repository 靜態引用提示 |
| 新版 UI 實機測試 | `OK (1 test)`，14.541 秒；四封面、固定導覽、橫直向、1.3 字級、網格、閱讀器選單、書名／作者搜尋、無結果、重建與清除篩選皆通過 |
| 千本書籍／標準鍵／閒置重繪 | `OK (1 test)`，143.153 秒；1000 本 + 標題列，1.0／1.3 字級各測橫向與直向、正反取樣捲動、回收後標題對應正確；兩字級 Page Down／Page Up 與視窗鍵碼均通過；60.026 秒 App `OnDraw` 次數 0 |
| 音量鍵選用翻頁 | `OK (1 test)`，5.570 秒；開啟、反向、Activity 重建保存、關閉、音量未變皆通過 |
| 正式書庫旋轉 | `OK (1 test)`，6.591 秒；橫向 1680×1264、書庫高 884 px；直向 1264×1680、書庫高 1300 px；字級 1.3，恢復 Activity 方向 |

第一次大型 UI 測試的 1.0 字級橫直向取樣通過，但緊接 `sendKeyDownUpSync` 與 `waitForIdleSync` 的 Page Down 捲動斷言失敗。保留原失敗紀錄；修正測試同步，改為有上限等待實際捲動狀態，並額外檢查視窗收到的 Page Down／Page Up 鍵碼，沒有刪除按鍵行為斷言。最終在兩字級皆通過，符合畫面更新時序問題；本次標準鍵仍是自動注入，並非重新要求使用者按實體鍵。

千本 UI 各組雙向取樣耗時：字級 1.0 橫向 19,824 ms／直向 15,790 ms，字級 1.3 橫向 13,127 ms／直向 13,291 ms。這是含測試同步的遍歷時間，不是頁面反應時間或耗電量測。`OnDraw = 0` 亦不代表整機零耗電／零刷新。

本機原始紀錄（`.tools/acceptance` 不納入版本管理）：`kobo-ui-build-final.log`、`kobo-ui-device-final.log`、`kobo-ui-extended-final.log`、`kobo-ui-keys-final.log`、`kobo-ui-rotation-final.log`、初次失敗的 `kobo-ui-extended.log`。所有實機結果以 runner 的 `OK` 標記判斷，不只依 adb 結束碼。

結束前唯讀確認 HOME 仍為 `com.onyx/.StartupActivity`，主 App 為 `enabled=0`（正常預設啟用），重新啟動成功並回到首頁。未開啟 HOME 選擇器。

正式首頁仍顯示先前匯入的 25 本驗收書籍與 4 本最近開啟，既有 PDF 第一頁及 EPUB 灰階封面正常顯示。最後停在正式首頁，沒有將隔離 UI 測試的四本樣書寫入正式資料庫。

## 實機截圖

下列圖像由 Leaf2 實際渲染，字級 1.3，使用隔離測試資料庫中的四本合成書籍及預設封面，不是插入正式書庫的樣書。書名與日期只是測試資料；正式首頁讀取使用者已匯入書籍。畫面上的圓形懸浮按鈕是 BOOX 原有覆蓋介面，未修改其設定。

- [直向首頁](acceptance-images/20261116-kobo-home-preview.png)
- [直向書庫](acceptance-images/20261116-kobo-library-preview.png)
- [橫向首頁](acceptance-images/20261116-kobo-home-landscape-preview.png)
- [正式書庫首頁：原有 25 本驗收書籍](acceptance-images/20261116-kobo-home-actual.png)

## 限制

- 圖示與版面參考 Kobo，沒有複製商標、商店或雲端服務。最近開啟是本 App 的事件，並非 NeoReader 閱讀進度。
- 本次裝置截圖可確認排版，不能證明實體電子紙殘影與觸感；先前使用者接受的實體顯示觀察屬舊版 UI，未冒稱已驗證新版。
- 在大字級／橫向下，首頁下段可能需要捲動，底部導覽固定。完整長書名可由書籍選單標題查看。
- 時鐘不是即時跳動時鐘；裝置日期與主機日期不同，未更改裝置時間。
- P5 HOME 切換仍未核准／未執行。沒有觸碰 NeoReader 私有資料、全域按鍵映射、原廠桌面或系統套件。
