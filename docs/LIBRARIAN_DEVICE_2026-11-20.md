# 喵館員 Leaf2 實機驗收

2026-11-20（Asia/Taipei）。使用者接上 Leaf2 並要求測試；沿用已授權的安裝與驗收範圍，不切換 HOME、不修改系統設定、不停用原廠套件。主機開發紀錄見 [喵館員開發](LIBRARIAN_2026-11-20.md)。

## 最新：更換另一把 Key 後真實 API 成功

使用者再次於裝置更換 Key 並要求重試。沿用已安裝版本，從正式喵館員推薦頁按「返回 → 請墨喵用 AI 推薦」，本機呼叫計數 **4→5**。模型 `gpt-4.1-mini` 回傳 **HTTP 200**，`last_outcome=ok`、`last_error_code=none`，耗時 **10,430 ms**，回報 input **1,119 tokens**／output **415 tokens**。畫面顯示「OpenAI 推薦 · 只使用候選書目」、本機館藏 8,141 本／本次確認可讀 20 本，以及三張推薦卡的第一張。成功結果已通過程式的結構與候選 ID 驗證；這次是真實 API 成功，不是 mock 或離線回退。

接著返回並再次要求同一組推薦，結果為 `cache`、42 ms，呼叫計數維持 **5**；畫面確認「AI 快取 · 翻頁不再發送請求」，沒有再次發出網路請求。最近 HTTP 狀態仍為前一次實際請求的 200，不能把快取算成第二次 HTTP 成功。

本輪沒有更動程式、重建或安裝 APK，沒有讀取／輸出 Key；只更新驗收紀錄。非敏感狀態存於已忽略的 `.tools/librarian-device-20261120/api-next-key-after.json` 與 `api-next-key-cache.json`；含私人書目的畫面不公開。這次未逐張開書或重跑閱讀器測試；推薦理由仍只根據書目推測，不代表已讀全文。一次成功不保證後續網路、額度或模型服務永遠可用。

## 歷史：重新輸入 Key 後的精確診斷

使用者表示已在裝置重新輸入 Key。先以原版重試，計數 2→3，耗時 2,013 ms，仍為合併的 `authentication`。為避免無法區分 401／403，補上安全診斷並覆蓋安裝：最近一次實際 HTTP 嘗試的狀態碼，以及固定允許清單中的錯誤代碼。每次開始 HTTP 嘗試先清除舊狀態；離線或快取不改寫最近 HTTP 狀態。錯誤本文最多讀取 8 KiB，只取 `error.code`，未知／損壞／過大資料記 `unknown`；不記錄或匯出原文、message、書目、URI 或 Key，重新導向仍不跟隨也不讀取本文。診斷格式版本為 2。

更新後正式操作「喵館員 → 逛逛主題書展 → 藏書尋寶 → 請墨喵用 AI 推薦」，計數 **3→4**，耗時 **1,022 ms**，取得 **HTTP 401／`invalid_api_key`**。因此確認目前裝置送出的憑證被 OpenAI 拒絕，不能宣稱 AI 已可用；仍無法區分輸入不完整、已撤銷或其他金鑰本身的問題。沒有讀取、替換或輸出使用者 Key。畫面正常回退離線，顯示 8,141 本館藏／20 本可讀候選。未再重複同一失敗呼叫。

```powershell
. .\scripts\use_local_tools.ps1
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console plain
$env:PATH = "$env:ANDROID_HOME\platform-tools;$env:PATH"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n org.kobostyle.launcher/.ui.MainActivity
# 在正式 UI 依上述步驟要求 AI 推薦；診斷報告可由 AI 設定匯出。
```

建置成功，**84 tests／0 failures／0 errors／0 skipped，Lint 0 errors／45 warnings**（新增兩項 SharedPreferences UseKtx 建議，未隱藏）。新增測試涵蓋錯誤碼允許清單、原始敏感文字不外流、重複 JSON key／過大／非 JSON／讀取失敗退路、狀態重設及 HTTP 狀態保留。主 APK 安裝成功，SHA-256 `38C08A506220FCF9C3E4FCD0B1EF9A57D44E9ACC031B1BBD79098274EF34BAC1`。本輪未重跑不相關的閱讀器 instrumentation。

更新前後 8,166 筆書籍、4 個來源、11 筆非零開啟紀錄的逐列摘要一致；HOME 仍為 `org.kobostyle.launcher/.ui.HomeEntry`。非敏感結果保留於忽略目錄 `.tools/librarian-device-20261120/api-diagnostics-after.json`，私人畫面不公開。後續須在 OpenAI 平台確認／建立有效 Key，再於裝置 AI 設定儲存後重驗；不要把 Key 貼到聊天或命令列。

## 歷史：真實 OpenAI 再次檢查

使用者要求再次檢查呼叫，已於正式喵館員從推薦頁返回，再按「請墨喵用 AI 推薦」。啟用狀態為 true、模型 `gpt-4.1-mini`；本機呼叫計數由 1 增為 2，耗時 2,955 ms，結果代碼仍是 `authentication`。畫面明確顯示 API Key／專案權限無法使用，並回退離線選書，**真實 AI 推薦仍未成功**。這次沒有修改程式或 Key，也沒有讀取或輸出 Key。

目前程式將 HTTP 401／403 合併為同一結果，不保留錯誤回應原文，因此無法僅據此斷定是金鑰無效、專案權限或其他存取限制；不是逾時、每日次數達上限或快取命中。未重複發送第二次測試請求。非敏感狀態保留於已忽略的 `.tools/librarian-device-20261120/api-recheck-after.json`，含私人書目的畫面不公開。後續須確認裝置內儲存的 Key 與 OpenAI 專案存取設定，再重驗；不要把 Key 放入聊天或命令列。

## 後續更新：只推薦實際館藏

使用者要求推薦以 Leaf2 的實際書籍為基礎。正式版原本已接到自身 Room 書庫，但索引也含先前留下的驗收書；現在在離線與 AI 共用的候選篩選處，排除已知的 `Download/KoboStyleAcceptance-20261115/` 及 `KoboStyleCardTest-20261115/` 文件，僅對 externalstorage provider 已知 document ID 路徑套用。沒有依書名關鍵字誤排除正常書籍，也沒有移除測試文件或索引。獨立合成書庫測試須明確 opt in，正式 Activity 沒有這個入口。

更新後 `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console plain` 成功，**81 tests／0 failures／0 errors／0 skipped，Lint 0 errors／43 warnings**。已覆蓋安裝到 Leaf2，APK SHA-256：`5A6269237BBB271BEA60BD0F4E84A1510A84508D586CDEE12F575B6AE1D237BC`。

新增的實機 `LibrarianRealLibraryTest` 通過（1 test，7.425 秒）：以本 App 實際 Room DB 唯讀稽核，兩條路徑均得到 `indexed=8166 fixtures=25 eligible=8141 readableCandidates=20 cards=3`。每張卡必須逐欄符合現有 Room 書籍，且可重新開啟 read-only file descriptor。離線與 **模擬 AI** 皆通過；fake transport 只在裝置記憶體處理書目，沒有傳到 OpenAI，沒有存取使用者 Key。測試只輸出數量與判定，不輸出私人書名或 URI。

```powershell
adb shell am instrument -w -r -e class org.kobostyle.launcher.LibrarianRealLibraryTest org.kobostyle.launcher.test/androidx.test.runner.AndroidJUnitRunner
```

正式 MainActivity 另以「喵館員 → 逛逛主題書展 → 藏書尋寶 → 這次只用離線選書」實際操作，畫面顯示「本機館藏：8141 本 · 本次確認可讀：20 本」及實際書籍卡片。此頁面的館藏數是符合候選條件的索引筆數，不表示已逐本打開全部 8,141 本；可讀候選數才是這次實際檢查的文件數。仍維持每次最多核對排序前 60 本、取 20 本候選的上限。

更新與操作後再次比對，8,166 筆書籍、4 個來源與 11 筆非零開啟紀錄的全部欄位完全相同。使用者原有 AI 模型／上限／啟用設定保留，API 呼叫計數維持 1，沒有新增付費請求。修改前唯一一次線上呼叫的非敏感結果代碼為 `authentication`；不宣稱真實 AI 推薦已成功。實際書庫畫面僅留在已忽略的 `.tools/librarian-device-20261120/real-library-result.png`，不公開。

以下為本次修改之前的接機驗收歷史。

## P0 與資料保全

執行 `scripts/diagnose_device.ps1`：一台已授權 Leaf2／ONYX，Android 11／API 30，1264×1680／300 dpi，唯讀診斷 0 failures。當前 HOME 為 KoboStyle HomeEntry；原廠 HOME 仍可解析。EPUB／PDF capability 查詢均找到裝置上的閱讀器 handler，接著另做真實 content URI 測試，沒有把 capability 當作開書證明。

覆蓋安裝前後，以本 App 的 `run-as` 讀取自身 Room DB／WAL 建立本機副本，SQLite quick_check 通過；8,166 本書的全部欄位、4 個來源及 11 筆非零開啟紀錄逐列一致。不讀其他 App 私有資料庫，不清資料或重掃真實書庫。私人副本只保留於已忽略的 `.tools/librarian-device-20261120/`。

四項實機測試完成後再次快照比對，全部書籍／來源欄位及開啟時間仍與測試前一致；再次唯讀診斷 0 failures，HOME 仍是 KoboStyle HomeEntry，原廠 HOME 可解析，同文輸入法仍為目前預設。沒有將合成書開啟事件寫入使用者書庫。

## 實機測試設計

- `LibrarianKeyStoreTest`：真實 AndroidKeyStore，獨立 alias／測試目錄，合成假 Key；測加密、重存產生不同密文、替換 Key、重新解密、竄改拒絕與刪除。`OK (1 test)`，0.524 秒。未讀取使用者 Key。
- `LibrarianDeviceAcceptanceTest`：使用既有 SAF 授權的 `Download/KoboStyleAcceptance-20261115/valid` 子資料夾，只取 20 本 `Acceptance-EPUB/PDF-NN` 合成書。使用獨立記憶體 Room、測試偏好及不連線的 transport；不影響真實書庫／AI 設定。先測新導覽、離線三卡、旋轉、字級 1.3 直橫向、按鈕可捲到、標準 Page Down，再以 `openReaders=true` 明確啟用實際閱讀器測試。
- 閱讀器程序已先向使用者說明：從喵館員確認選書後開 EPUB、PDF、第三張卡的 chooser；取消 chooser 不應寫入開啟時間，選定閱讀器後才記錄。檢查實際書頁、翻頁與 Back 返回，所有開啟事件只寫入獨立測試 DB。

測試修正紀錄：最初使用 Instrumentation.sendKeyDownUpSync 跨 App 注入被 Android 拒絕，改用 UiAutomation，不新增系統權限；PDF 因記住上次末頁位置，下一頁會顯示「附近的書」，改先回到合成 PDF 第 1 頁再測 1→2；OEM chooser 會截短名稱且不一定接受 ACTION_CLICK，改由本次 discover 的 label 比對可見文字後以測試觸控選取。保留原始失敗紀錄，未將其當作成功。

## 已確認結果

| 已完成 | 未完成 | 阻礙／限制 |
|---|---|---|
| 裝置 Keystore 1/1 通過 | 長期重啟／鎖定／韌體更新後的 Key 保存 | 本輪只測正常加解密、重新建立 vault 讀取與刪除 |
| 喵館員實機測試 1/1 通過，59.737 秒 | 實體翻頁鍵及長期電子紙殘影觀察 | 標準 Page Down 已測，硬體操作及肉眼觀察需使用者配合 |
| 三本合成書載入、翻頁、Back 返回；chooser 取消不寫開啟紀錄、選定後寫入 | 其他文件提供者及所有私人書籍相容性 | 只驗證本次韌體及 externalstorage SAF 合成文件 |
| 分類與原書庫 UI 回歸 2/2 通過，22.614 秒 | 長期待機、冷啟動 P95 | 本輪未做耗電與長期效能量測 |
| 79 項 JVM 測試通過，Lint 0 errors／43 warnings | 真實 API、實體顯示的人工確認 | 正等待使用者在裝置輸入 Key 與觀察回覆 |

共四項 instrumentation 測試通過；喵館員測試涵蓋多個操作步驟，不能把每個步驟都算成獨立測試數。三本書的截圖已檢視，確認不是載入畫面：EPUB 有合成段落與頁碼變化，PDF 明確顯示 PAGE 1→PAGE 2；第三本 EPUB 經 chooser 開啟後由 2/10→3/10。未讀取 NeoReader 進度 API 或私人資料庫，頁碼只來自畫面觀察。

本輪主 APK 僅新增 debug 測試用的非 exported Activity 工廠注入；正式喵館員邏輯未更動。已安裝 APK SHA-256：`084B130FEC49D2A6378E07EC792637D380872AAEA52EA66C7D8B2C48EC26060B`。原始診斷、測試 stdout 與私人快照保留在 `.tools/librarian-device-20261120/`，不公開。

以下為本次實機截圖，僅含合成書目：

<img src="acceptance-images/20261120-cat-welcome-device.png" alt="Leaf2 喵館員歡迎畫面" width="380">

<img src="acceptance-images/20261120-cat-cards-device.png" alt="Leaf2 合成書庫離線推薦，大字版" width="380">

## 重現命令

```powershell
. .\scripts\use_local_tools.ps1
$env:PATH = "$env:ANDROID_HOME\platform-tools;$env:PATH"
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_device.ps1
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console plain
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\deploy_debug.ps1
adb install -r .\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w -r -e class org.kobostyle.launcher.LibrarianKeyStoreTest org.kobostyle.launcher.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r -e class org.kobostyle.launcher.LibrarianDeviceAcceptanceTest -e openReaders true org.kobostyle.launcher.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r -e class org.kobostyle.launcher.CategoryUiAcceptanceTest,org.kobostyle.launcher.ReaderUiAcceptanceTest org.kobostyle.launcher.test/androidx.test.runner.AndroidJUnitRunner
```

須已有使用者授權的上述合成資料夾。測試 Key 與對話均為獨立資料；`openReaders=true` 會實際開啟合成書。此測試的兩頁 PDF 已在本裝置讀到第 2 頁，程式先送 Page Up 再檢查 Page Down；其他裝置需確認測試起始頁，不以送出按鍵推論確實翻頁。實際內容須另檢視測試截圖。

## 真實 API 與人工項目

使用者已表示有可用 Key，將在裝置自行輸入。此部分結果待完成後補記；不將 mock 回應、安裝成功或 Key 存入成功當作真實 AI 推薦成功。Key 不進命令列、聊天、報告或截圖。

已將一般 App 停在「喵館員 AI 設定」，請使用者勾選啟用、輸入 Key、同意候選書目傳送並儲存後回覆。等待期間未擷取畫面或讀取輸入欄。另已詢問實體文字／殘影／刷新觀察；未收到回覆前保持待確認。ADB 的 `uiautomator dump /dev/tty` 在此 OEM 只回報輸出位置、未傳回 XML，已記錄限制；沒有改用系統設定寫入來解決，設定頁導覽改依已觀察畫面點選。
