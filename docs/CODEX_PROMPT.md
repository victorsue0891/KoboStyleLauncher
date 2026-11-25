# 後續開發交接提示

你是 BOOX Leaf2 KoboStyle Launcher 的 Android 工程師，請用繁體中文溝通。先讀 `SPEC.md`、`README.md`、`docs/RECOVERY.md`、`docs/TEST_PLAN.md`、`docs/DEVICE_REPORT.md`，再修改程式。

## 目前狀態

- **V4 P1–P6 本輪完成**：使用者已手動選好合成 pack，SAF 匯入／第 3 章邊界／本機引用成功。已使用既有裝置 Key 完成使用者核准的兩次真實 AI（ask／refine 各一次）；usage 恰好兩筆 SUCCESS，本機總計 USD 0.000651。**兩次測試授權已用完，不得自行追加付費測試**。V4 開關往返與程序重啟保留原書、pack、人格及 ledger；目前 V4／付費書靈開啟，單次 USD 0.02，每次仍需 UI 確認，未擷取 Key。使用者已回覆「確認ok, 請繼續」，確認本輪電子紙顯示／刷新及上下實體鍵捲動；P6 人工驗收通過，畫面留在合成書靈歡迎頁。最新結果及長期待機等範圍限制依 `docs/v4/DEVICE_ACCEPTANCE_2026-11-25.md`。收尾只更新文件，未追加 API、裝置操作或 HOME 選擇；下列未接機／待驗狀態皆為歷史紀錄。

- **2026-11-25 V4 後續接機**：已安裝新版並啟用 V4，實際 13,618 本／4 來源／17 本開啟紀錄升級保留。批次索引修正與裝置測試 commit `5570af0`；106 項主機及 8 項不同實機測試分別通過，Lint 0 errors／61 warnings。最新正式 SAF 匯入、兩次使用者已核准的合成資料 AI 測試及人工顯示觀察，以 `docs/v4/DEVICE_ACCEPTANCE_2026-11-25.md` 為準，未完成項不可推定通過。Key 未擷取、HOME 未更改；下列未接機狀態已被本段取代。

- **最新 2026-11-25 V4**：`feature/meow-v4-book-spirits`，V3 基準 `b6ef3c1`、P0 稽核 `9f2d70a`。使用者授權 P1–P5 主機實作，已完成；106 項 JVM／Robolectric、2 項 Python 測試通過，Lint 0 errors／61 warnings。Leaf2 明確未連接，未執行 ADB、安裝或真實 API；P6 待接機。先讀 `docs/v4/IMPLEMENTATION_2026-11-25.md` 與 `KNOWLEDGE_PACK.md`。V4 預設關閉，入口「更多 → 書靈與費用設定」；新付費預設關閉，已啟用的 V3 Key／偏好保留。主書庫 schema 3、獨立 spirits schema 1／bundled SQLite FTS5；不可直接降版 APK。所有正式 API 入口共用 gateway，取消自動重試。下列紀錄為歷史版本。

- 最新同日更新：正式喵館員排除已知的 25 本驗收書，離線與 AI 均基於其餘實際館藏；不刪書／索引。Leaf2 真實館藏稽核 1/1 通過（8,141 本符合候選條件，核對 20 本、推薦 3 本）。主機 84 tests 通過，Lint 0 errors／45 warnings，新版已安裝。更換另一把 Key 後，正式真實 API 回傳 HTTP 200／ok（計數 4→5，10,430 ms，input 1,119／output 415 tokens）；重複同組推薦命中快取（42 ms），計數仍為 5。此前 401／invalid_api_key 為歷史失敗，不能當作最新狀態。詳 `docs/LIBRARIAN_DEVICE_2026-11-20.md`。

- 同日後續使用者已接機並授權測試：Leaf2 唯讀診斷成功、覆蓋升級後 8,166 本及 4 個來源資料一致；Keystore、喵館員／三本合成書開啟與 chooser、分類／書庫回歸共 4 項實機測試通過。真實 API 與人工項目以 `docs/LIBRARIAN_DEVICE_2026-11-20.md` 為最新狀態。下列「未連線」為接機前紀錄。

- 2026-11-20 新增喵館員分頁，應用程式移到更多。依使用者擴大範圍，實作離線六表情選書及 opt-in OpenAI Responses API、Keystore AES-GCM、schema／ID 白名單、重試／取消／每日軟限額／記憶體快取。Room 仍為 v2。79 項主機測試通過，Lint 0 errors／43 warnings。Leaf2 本輪未連線，沒有 ADB、安裝或真實 API 呼叫；接機後依 `docs/LIBRARIAN_2026-11-20.md` 驗收。不要把 mock 成功說成真實 API 成功。

- 2026-11-18 新增首頁「書籍分類」、11 類別及作者搜尋。Room v2 新增 `displayName`，以明確的 1→2 migration 保留原資料。43 項 JVM 與 2 項實機 UI 測試通過；8,166 本實際索引的原欄位、來源及開啟紀錄升級前後一致。參見 `docs/CATEGORIES_2026-11-18.md`；以下掃描修復數字是較早驗證。

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
8. AI 預設關閉。Key 僅在裝置設定頁輸入，不要要求使用者貼到聊天、環境檔、命令列或原始碼；測試用 fake transport 與合成資料。模型僅輸出資料，不得派送為任意 Intent／系統操作。不得上傳書籍全文；傳送書目前需使用者啟用並觸發推薦。

先說明本次任務的現況、必要檢查與風險，再開始實作。已取得的使用者授權應延續，不重複要求相同確認。
