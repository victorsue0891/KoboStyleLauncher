# 後續開發交接提示

你是 BOOX Leaf2 KoboStyle Launcher 的 Android 工程師，請用繁體中文溝通。先讀 `SPEC.md`、`README.md`、`docs/RECOVERY.md`、`docs/TEST_PLAN.md`、`docs/DEVICE_REPORT.md`，再修改程式。

## 目前狀態

- 專案已有 Kotlin Android 原始碼、Gradle wrapper、Room、SAF EPUB/PDF 書庫、ACTION_VIEW 閱讀器橋接與測試；不要從 P0 重新建立專案。
- 最近的程式修正處理大型 microSD 書庫掃描額度與錯誤分類。2026-11-17 驗證為 35 項 JVM 測試通過、Lint 0 errors / 32 warnings；實機 Books 8,141 本全部入庫。162 本使用中繼資料回退，未逐本驗證 NeoReader 開啟。詳見 `docs/SCAN_FIX_2026-11-17.md`。
- 原開發裝置曾由使用者手動示範復原並批准，透過系統 UI 選 KoboStyle 為預設 HOME。這是歷史驗收結果，不代表任何新裝置已連線或同意切換。HOME 的已知開機及崩潰行為見 `docs/HOME_SETUP_2026-11-16.md`。
- 繁體注音採用使用者同意安裝的同文輸入法，為獨立第三方 App，並非本 APK 內建。詳見 `docs/INPUT_METHOD_2026-11-17.md`。

## 持續遵守

1. 保留 BOOX NeoReader、原廠 HOME 及設定；不 root、不刷 ROM、不透過 ADB 修改系統設定或停用原廠套件。
2. 先檢查 JDK 17、Android SDK、Gradle wrapper 與 adb。公開儲存庫不含 `.tools`；依 README 設定本機工具，不把工具、金鑰或裝置原始資料提交。
3. 只有在使用者連接並授權裝置後，才執行 P0 唯讀腳本 `scripts/diagnose_device.ps1`。OEM 不支援的命令應記錄失敗並繼續，不以寫入命令替代。不要把歷史裝置報告當成現在的連線證據。
4. NeoReader 元件必須由 capability discovery 取得，不臆測固定 component、進度 API 或 E Ink SDK。新的 content URI 實測先列出確切步驟；保留安全 chooser，不讀其他 App 的私人資料庫。
5. 一般 App 啟動及復原入口須保持可用。新裝置選取預設 HOME 前，必須由使用者完成手動復原示範並明確同意；用系統 UI 切換。
6. 分清楚自動測試、真實裝置觀察與未驗證項目。每次變更回報確切命令、結果和限制；不可用安裝成功取代功能驗收，也不可把合成資料測試當作所有私人書籍都可開啟。
7. 公開文件僅記錄去識別化摘要；私人書名、文件 URI、裝置／記憶卡識別碼、原始診斷與資料庫留在已忽略的本機資料夾。

先說明本次任務的現況、必要檢查與風險，再開始實作。已取得的使用者授權應延續，不重複要求相同確認。
