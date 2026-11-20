# 喵館員 Leaf2 實機驗收

2026-11-20（Asia/Taipei）。使用者接上 Leaf2 並要求測試；沿用已授權的安裝與驗收範圍，不切換 HOME、不修改系統設定、不停用原廠套件。主機開發紀錄見 [喵館員開發](LIBRARIAN_2026-11-20.md)。

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
