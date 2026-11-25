# V4 書靈：Leaf2 實機驗收

日期：2026-11-25，Asia/Taipei。使用者已接上並授權 Leaf2；本紀錄接續 [P1–P5 主機開發](IMPLEMENTATION_2026-11-25.md)。測試只更新 KoboStyle 及其測試 APK，沒有更改 HOME、系統設定或 BOOX 套件。

## 裝置與資料保留

`scripts/diagnose_device.ps1` 確認一台 authorized Leaf2，Android 11／API 30、1264×1680／300 dpi、韌體 4.0.2，唯讀命令失敗數 0。當前 HOME 為 `org.kobostyle.launcher/.ui.HomeEntry`，原廠 HOME `com.onyx/.StartupActivity` 仍可解析。EPUB／PDF capability discovery 回傳 `com.onyx.kreader/.ui.ReaderHomeActivity`；這是此次觀察，程式沒有硬編碼該元件。

實際書庫為 **13,618 本、4 個來源、17 本具有本 App 的開啟時間**。這次數量來自裝置，不是主機合成資料。升級前主書庫 schema 2，升級後 schema 3；原 books／sources 的全部欄位按主鍵排序後逐列比對相同，SQLite quick_check 通過。Key 保留在裝置，未擷取 Key／Keystore 檔案。

| 升級前後比較 | SHA-256（排序後列資料的 JSON） |
|---|---|
| books，13,618 列 | `b8aabf5945e0b216e424f4f394512641ef54fa13011709066b4242b558695d7d` |
| sources，4 列 | `a80df2da01644e365c2d16c5a66bc2ae393997444c0bd8ca503f5f0adebf9954` |

原始資料庫、文件 URI、裝置識別碼與私人書庫截圖只留在忽略的 `.tools/v4-device-20261125/`，不納入公開儲存庫。曾有一次在索引寫入中複製 DB／WAL 的副本不一致；程序停止後重新複製通過，不能把該不一致副本當成裝置資料庫毀損。

## 實測後修正與效能

初版逐本投影及確認變更，各自提交 SQLite transaction，在 Leaf2 首次建立索引太慢。改為每批最多 50 本寫入衍生索引，再以另一個 transaction 確認該批原始 revision；中途終止可重做，並保留同時發生的新 revision。增加超過 500 ms 的準備索引提示。資料表結構沒有再次改版。

- 修正前中斷時已投影 4,364 本，尚餘 9,254；修正後剩餘部分完成耗時 **87,295 ms**。這不是完整 13,618 本的首次建索引時間，也不是冷啟動時間。首次建立仍可能需數分鐘。
- 完成後衍生 catalog／FTS 各 13,618 筆，變更佇列為 0；中文搜尋、增量標題更新及舊詞移除通過。
- 真實書庫連續查詢 50 頁，每頁最多 50 本，**P95 267 ms**。抽樣 20 本推薦候選的 SAF descriptor 均可讀，原有四項持續讀取授權不變；沒有逐本驗證全部私人藏書。
- 完成索引後進行 10 次程序冷啟動，Android `am start -W` 首畫面 TotalTime：1927、1836、1885、1901、1768、1810、1749、1817、2010、1764 ms，nearest-rank **P95 2010 ms**。每次只 force-stop 本 App，不重開機，OS 檔案快取保留；這不是「全部封面完成」或首次投影時間。
- 一次穩定啟動取樣 TOTAL PSS 49,484 KB、RSS 92,408 KB、swap 1,669 KB。未執行長期待機／全庫每本開書壓測，不能推論所有操作的記憶體上限。

## 自動測試

主機 `testDebugUnitTest lintDebug` 通過：106 tests、0 failures／errors／skipped，Lint 0 errors／61 warnings。最後 `assembleDebug assembleDebugAndroidTest lintDebug` 成功。既有 Python adapter 2/2 的結果沿用同日主機開發紀錄；本輪未修改 adapter。主 APK 的 v2 debug 簽章驗證通過，SHA-256：`903590207FD85F8A9A81410D734E8AE45366784FECD5A67663C87152186C0602`。

以下 **8 個不同實機測試分別通過**，不是第一次整批執行即全數成功。測試中的 AI 均為 fake transport，沒有使用真實 Key 或付費 API。

| 測試 | 實際涵蓋 |
|---|---|
| `SpiritDeviceAcceptanceTest.actualCatalogProjectionFiftyPagesAndReadGrants` | 真實 13,618 筆投影、50 頁／中文查詢、20 本可讀候選、四項授權與原 AI diagnostics 不變 |
| `nativeLitePackSpoilersAndPersistentCostLedger` | 10 本 Lite 各重進 10 次、原生 Room 關閉重開、知識包交易回復、已讀第 3 章隔離、連按單一請求、取消後 UNKNOWN_BILLING 保留、刪書靈不刪原書 |
| `authorizedSyntheticScanCancelsAndResumesWithoutDeletingBooks` | 已授權合成資料夾實際 23 個 EPUB／PDF，以獨立書庫取消及恢復掃描；不更動正式索引 |
| `isolatedSpiritUiCreationRotationAndLocalEvidence` | 書靈小屋／Lite／無片段說明、Activity 重建、字級 1.3、48 dp 按鈕、標準 PageDown；獨立 runtime 不污染正式書庫 |
| `LibrarianKeyStoreTest` | 裝置 Keystore 使用測試 alias 加密、重存、拒絕竄改及刪除；不讀使用者 Key |
| `LibrarianDeviceAcceptanceTest` | 原 V3 離線選書 FSM、三本合成書、NeoReader 與 chooser、旋轉／字級；三本均一次 Back 返回 |
| `CategoryUiAcceptanceTest` | 分類、作者搜尋、無結果／未分類、返回、旋轉與全部書籍 |
| `ReaderUiAcceptanceTest` | 首頁四封面、網格／固定導覽、搜尋、閱讀器選單與重建 |

遇到的失敗與處理：掃描測試原先假設資料夾只有 20 本，但實際包含後續新增的合成書共 23 本，改為列舉目前符合格式的文件數；書靈 UI 測試誤選同名但不可點的封面文字，改找可點控制項。NeoReader 返回時無障礙快照較舊，造成錯誤的「仍在閱讀器」判斷；改在主執行緒觀察 Activity 視窗焦點，最後三本都只需一次 Back。沒有修改 NeoReader 或以額外 Back 掩蓋回程問題。分類測試受前一失敗的未清理畫面影響，獨立重跑通過。

NeoReader 測試前已展示程序，只開啟既有授權的合成 EPUB／PDF，經 ACTION_VIEW／read grant，核對翻頁及返回。畫面確認合成 EPUB 頁碼 6→7／10、PDF 1→2／2；本 App 記錄自己的開啟事件，不讀 NeoReader 私有 DB 或推測閱讀進度 API。

## 正式畫面及真實 AI

正式 MainActivity 已找到既有合成 `Acceptance-EPUB-01.epub`，從選單進入書靈並免費建立 Lite，畫面正確揭露「未讀全文」。已準備四章合成筆記 `Download/KoboStyleV4Test-20261125/KnowledgePack-Test.json`；前三章是觀察、閱讀與查證練習，第四章包含未讀邊界測試字串，不是真實書籍內容。

目前系統 SAF 選擇器已開啟，但清單自動點選沒有反應，正在等待使用者手動選取該 JSON。尚未完成正式匯入確認。使用者已明確同意 **一次合成筆記問答、一次歡迎語精修**，使用裝置既有 Key、各次本機估計不超過 USD 0.02、不重試；**本輪目前真實 API 請求數為 0**。不得以上方 fake transport 結果代替真實 API 成功。

接續驗收：

1. 手動選檔後核對書名與四個章節、確認匯入，設定已讀第 3 章，先核對免費本機片段。
2. 在正式費用設定核對模型價格與單次上限，分別查看費用預覽，再依已取得的授權各送一次；記錄 HTTP、有效回答／引用及 ledger 的 usage。不得送私人書籍或額外重試。
3. 人工確認本輪電子紙文字、殘影／刷新與實體鍵；主機畫面／注入 PageDown 不替代實體觀察。
4. 正式畫面關閉／開啟 V4，核對原書與新知識包保留，停止本 App 後做最終一致性快照。旗標往返已有主機測試，但此次實機往返尚未完成。

上述項目完成前，P6 保持部分驗收狀態。接機測試結束前的唯讀診斷再次為 0 個失敗，既有 HOME／閱讀器解析未變。

## 重現命令

以下只在使用者接機並授權後執行；有多台裝置時先明確選定目標。實機測試會更新本 App 及開啟合成測試書，故應先展示開書程序。測試 APK 不隨正式發行提供。

```powershell
# 本開發電腦的工具環境；一般 clone 依 README 設定 JDK／SDK
. .\scripts\use_local_tools.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_device.ps1
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class org.kobostyle.launcher.SpiritDeviceAcceptanceTest org.kobostyle.launcher.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r -e class org.kobostyle.launcher.LibrarianKeyStoreTest,org.kobostyle.launcher.LibrarianDeviceAcceptanceTest,org.kobostyle.launcher.CategoryUiAcceptanceTest,org.kobostyle.launcher.ReaderUiAcceptanceTest -e openReaders true org.kobostyle.launcher.test/androidx.test.runner.AndroidJUnitRunner
# 單項重跑：將 -e class 的值換為 完整類別名稱#方法名稱
adb shell am force-stop org.kobostyle.launcher
adb shell am start -W -n org.kobostyle.launcher/.ui.MainActivity
adb shell dumpsys meminfo org.kobostyle.launcher
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --verbose app/build/outputs/apk/debug/app-debug.apk
git diff --check
```

實機 V4 測試需要已索引且可讀的書庫，以及歷史驗收資料夾 `Download/KoboStyleAcceptance-20261115/valid` 的 SAF grant；不會自動取得全磁碟權限。Reader bridge 測試需要該資料夾內既有合成 EPUB／PDF。其他電腦／裝置應先建立並授權相同的測試資料，不能直接套用本機資料庫或 Key。

## 範圍限制

本次只驗證此 Leaf2／韌體／provider。新韌體、其他 provider、長期待機耗電、實體拔插／重開機故障矩陣未在 V4 重做，歷史結果不等於這次重測。沒有閱讀進度同步、全書 AI、背景批次孵化或自動重送。功能回退是關閉 V4，保留 schema 3 資料；不支援直接安裝舊 schema 2 APK。
