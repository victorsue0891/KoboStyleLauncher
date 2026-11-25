# 韌體相容性驗證

日期：2026-11-25，Asia/Taipei。結論：**目前連線 Leaf2 的 BOOX 4.0.2／Android 11，通過本輪系統能力與功能回歸檢查**。只有這一個韌體版本的實機證據；不能據此宣稱其他 BOOX 韌體或其他機型相容。

本輪沒有刷機、OTA 更新、切換預設 HOME、修改系統設定或發出付費 API。僅新增並安裝 instrumentation 測試 APK；正式程式未更動，仍是先前已驗收的 APK，SHA-256 `903590207FD85F8A9A81410D734E8AE45366784FECD5A67663C87152186C0602`。測試後已回到正式喵館員畫面。

## 版本與相容性矩陣

| 環境 | 證據 | 結論 |
|---|---|---|
| Leaf2，BOOX 4.0.2，Android 11／API 30 | 本輪實機七項測試，以及同日 [V4 完整流程驗收](v4/DEVICE_ACCEPTANCE_2026-11-25.md) | 已確認下列範圍 |
| 相同 4.0.2 但其他 build 或 NeoReader 版本 | 未取得對應實機 | 未驗證 |
| 其他 BOOX 韌體／其他機型 | 未取得對應實機；未更新或刷寫目前裝置 | 未驗證 |
| 其他 Android 11 以上裝置 | minSdk 30 僅表示安裝門檻，不是 OEM 相容保證 | 未驗證 |
| Android 10／API 29 以下 | 本 APK 的 minSdk 為 30 | 不在支援範圍 |

本輪 build fingerprint：`Onyx/Leaf2/Leaf2:11/2025-06-21_19-01_4.0.2-rel_0621_da686c4b1/10545:user/release-keys`。這是韌體 build 資訊，不含裝置序號。實機程序為 64-bit，裝置列出的 ABI 為 arm64-v8a、armeabi-v7a、armeabi；只實際驗證了目前的 64-bit 執行路徑。螢幕 1264×1680／300 dpi。

## 本輪實際檢查

新增 `FirmwareCompatibilityTest`，從 **App UID** 查詢公開能力，不只依賴權限較大的 ADB shell。另執行原有 Keystore 與 V4 原生回歸。總計 **OK (7 tests)，37.896 秒**。

| 項目 | 本輪觀察／結果 | 驗證界線 |
|---|---|---|
| 唯讀診斷 | 前後各一次，皆 0 個命令失敗，已授權 Leaf2 | 不更新韌體或修改裝置 |
| 一般啟動入口 | 正式 MAIN／LAUNCHER 可解析且 exported | 測試後正式畫面已恢復 |
| SAF JSON／資料夾選擇器 | App 可解析 `com.android.documentsui/.picker.PickActivity`，啟用／exported／權限檢查通過 | 本輪只查詢；實際 JSON 選取／匯入沿用同日 V4 驗收，OEM 清單曾需手動點選 |
| HOME 與原廠復原 | App 可見兩個可用 HOME；KoboStyle 為預設，保存的原廠復原元件有效，HOME_SETTINGS 可解析 | 未開啟 HOME 選擇器、未改預設；shell 另列出不可用作一般桌面的 FallbackHome，數量不同屬過濾結果 |
| NeoReader EPUB／PDF | App discovery 回傳 `com.onyx.kreader/.ui.ReaderHomeActivity`，版本 `38137 - 6a1ec45f42f`／code 38137 | 本輪沒有開書；同一韌體下的合成 EPUB／PDF 載入、翻頁與返回沿用同日已通過驗收。程式沒有硬編碼元件 |
| SQLite native／FTS5 | bundled SQLite 3.50.1 載入、中文 trigram 建表／查詢通過 | 使用拋棄式記憶體 DB；不依賴 BOOX 系統 SQLite 是否提供 FTS5 |
| Keystore | 獨立測試 alias：加密、重存、拒絕竄改、刪除均通過 | 僅合成憑證；未擷取使用者 Key |
| TLS／明文限制 | AndroidOpenSSL 本機預設參數提供 TLS 1.2／1.3，App 禁止對 API 網域使用明文 | 未連網握手；不能推論所有伺服器／憑證鏈。輸出亦列出舊協定，未修改系統 TLS 設定 |
| 輸入法 | App 可見 3 個啟用的輸入法 | 未重測注音組字；已有較早同文輸入驗收 |
| 真實書庫與來源 | 13,618 筆索引、50 頁查詢、20 本抽樣可讀、四項讀取授權不變 | 本輪分頁查詢 P95 407 ms，非所有私人書都逐本開啟 |
| 書靈與掃描 | 10 本 Lite／各重進 10 次、原生 DB 重開、章節隔離、匯入回復、未知扣款保留、UI 重建／字級 1.3／PageDown、合成 23 文件取消續掃通過 | 測試使用獨立資料；標準鍵碼注入不替代實體鍵。本輪實體鍵／E Ink 人工結果沿用同日使用者確認 |

原有 HOME／閱讀器的公開能力會在執行時重新查詢。E Ink controller 仍為 no-op，不使用 BOOX 私有 SDK；這降低部分韌體依賴，**不是未測版本相容性的證明**。

## 資料完整性與量測限制

以本輪開始前的新快照為基準，結束後 books／sources 所有欄位逐列 hash、17 本開啟紀錄摘要及原 AI diagnostics 完全一致；書靈、知識包、片段、邊界、usage 的每列亦相同，兩個資料庫 quick_check 通過。V4 usage 維持先前的兩筆，沒有新增呼叫。私人書名／URI／原始資料庫不納入儲存庫。

測試後 `am start -W` 回傳 Status ok，WaitTime 2421 ms，卻同時回報 TotalTime 611363 ms；和命令的實際完成時間不一致，正式畫面也已確認正常。**這次 TotalTime 不作效能樣本**，原因尚未確定，沒有將它解讀成實際卡住十分鐘或拿來更新冷啟動 P95。先前十次完整量測仍見 V4 驗收紀錄；日後應同時記錄 wall time 與畫面就緒時間核對 OEM 回報。

## 重現命令

Windows PowerShell，已安裝本專案正式 debug APK、已授權裝置及既有合成驗收資料夾。`SpiritDeviceAcceptanceTest` 需要已有可讀書庫，以及 `Download/KoboStyleAcceptance-20261115/valid` 的 SAF grant；新裝置不具備時不能直接宣稱整套失敗是韌體問題。

```powershell
. .\scripts\use_local_tools.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_device.ps1
.\gradlew.bat assembleDebugAndroidTest lintDebug --console plain
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class org.kobostyle.launcher.FirmwareCompatibilityTest,org.kobostyle.launcher.LibrarianKeyStoreTest,org.kobostyle.launcher.SpiritDeviceAcceptanceTest org.kobostyle.launcher.test/androidx.test.runner.AndroidJUnitRunner
adb shell am force-stop org.kobostyle.launcher
adb shell am start -W -n org.kobostyle.launcher/.ui.MainActivity
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_device.ps1
git diff --check
```

建置 exit 0，Lint 0 errors／61 warnings；實機七項全通過。本輪僅新增測試，正式程式未變，因此沒有重跑已通過的 106 項主機測試或先前兩次付費 AI。原始本機 logs 位於忽略的 `.tools/firmware-compat-20261125/`。

驗證另一版本時須取得對應實機並記錄完整 build／NeoReader 版本，執行上述能力與資料回歸，另外重做 SAF 手動匯入、合成開書／返回、原廠復原路徑與人工電子紙／實體鍵；OTA 後的授權、Key、HOME 保留需有更新前後證據。模擬器或官方版本說明不能替代 BOOX OEM 實機結果。這次沒有執行 OTA、重開機故障矩陣或長期待機耗電測試。
