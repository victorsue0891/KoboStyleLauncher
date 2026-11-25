# V4 P0 稽核（2026-11-25）

基準：`D:\test\KoboStyleLauncher`，`feature/cat-librarian-v3`，`b6ef3c1`。tracked files clean；`spec-librarian/`、`spec-librarian-v4/` 為使用者未追蹤資料，保留原位。規格寫的 `planning/v4/` 實際對應後者，不搬移或覆蓋。

使用者本輪已明確授權 P0 後繼續 P1–P5，解除規格包逐階段等待指令的限制；P6 仍待接機。沒有裝置連線、ADB、安裝或真實 API 呼叫。

## 現有實作

| 項目 | 實際觀察 |
|---|---|
| 主機 | Windows 10；Temurin 17.0.21，Gradle 8.13；AGP 8.13.2、Kotlin 2.2.21、KSP 2.2.21-2.0.4；SDK 36、minSdk 30、targetSdk 36 |
| 模組／入口 | 僅 `:app`；`org.kobostyle.launcher`；`ui/MainActivity.kt`，原生 Views／RecyclerView，非 Compose |
| 素材 | `app/src/main/res/drawable-nodpi/cat_{welcome,thinking,recommend,curious,happy,reading}.png`，六張均 320×320；不替換 |
| FSM | `librarian/Dialogue.kt`；WELCOME、MOOD、DURATION、PREFERENCE、THEMES、CANDIDATES、LOADING、CARDS、DETAIL、CONFIRM。新增書靈採獨立分支 |
| Repository | 實名 `data/books/LibraryRepository.kt`，不是規格中的 BookRepository；identity 為 provider＋document ID 編碼，可能超過 128 字，不可直接拿來當對外書靈 ID |
| Room | `data/db/LibraryDatabase.kt` version 2，books／sources；1→2 只新增 displayName。沒有 destructive migration；schema 1/2 已匯出 |
| 書籍欄位 | identity、uri、sourceUri、documentId、title、author、mime、lastModified、size、lastOpenedAt、coverPath、scanStatus、scanToken、displayName |
| 大書庫缺口 | observeBooks 整表 List，LibraryViewModel／BookCatalog 在記憶體分組搜尋；LibrarianViewModel 也先取整庫再排名。沒有 FTS、DAO 分頁 |
| 掃描 | 手動掃描或 SAF 選取完成後觸發；onResume 只檢查授權。50,000 documents／depth 32 上限，有取消與 metadata 快取；無持久化 traversal checkpoint，單資料夾 query 可能累積大量 Document |
| 金鑰 | AndroidKeyVault／AndroidVaultKeys，Keystore AES-256-GCM，noBackupFilesDir 密文原子替換；設定 FLAG_SECURE／password／禁止 autofill；manifest 關閉 backup、cleartext |
| 閱讀器 | reader/NeoReaderBridge.kt、ReaderResolver.kt；capability discovery、content URI／ClipData／read grant、chooser。沿用，不硬編元件 |
| HOME | platform/HomeRoleHelper.kt；HomeEntry alias 預設 disabled，啟用須原有復原 UI；歷史使用者已設定 HOME，不代表本次連線 |
| E Ink | EinkRefreshController.NONE，無 OEM SDK；不宣稱刷新支援 |
| 注入／旗標 | MainActivity factory 可注入 ViewModel，transport／vault 可 fake；目前沒有 V4 flag／CostGate／usage ledger |

## 所有已發現的模型呼叫路徑

`MainActivity.librarianAction → DialogueMachine.reduce → DialogueEffect.Recommend → LibrarianViewModel.recommend → CuratorService.curate → CuratorTransport.post → OpenAiTransport`。

- 只有 CANDIDATES 的 REQUEST，及 CARDS 的 MORE（沿用 onlineRequest）會要求線上推薦；OFFLINE 走同 service 但先回退。
- CuratorService 在 enabled／Key／network／daily count／cache 檢查後才送出；429／5xx 目前可自動再試一次，V4 必須改為手動重試。
- 固定 HTTPS `/v1/responses`；無 embeddings、模型列表、價格更新、warm-up、遠端圖片或 WorkManager 依賴／worker。
- onCreate 建構物件與本機訂閱，onResume.refreshAccess，onNewIntent HOME navigate，Flow render，onStop.cancel；搜尋和翻頁不觸發網路。此為原始碼觀察，不等於 V4 零呼叫壓測已通過。
- 防護缺口：目前沒有單次費用預覽、日／月美元預算、未知單價拒絕、持久化 ambiguous reservation、跨推薦與問答的並發鎖。不能把每日次數上限當成費用硬限制。

## 基準驗證與限制

`. .\scripts\use_local_tools.ps1; java -version`、`.\gradlew.bat --version`：exit 0。

`python spec-librarian-v4/scripts/verify_package.py`：exit 0，13,618 假資料唯一 ID／範例 section SHA256／JSON parse 通過；不是完整 JSON Schema 驗證或 Android 匯入成功。

`.\gradlew.bat testDebugUnitTest lintDebug --offline --console plain`：exit 0，BUILD SUCCESSFUL；test task UP-TO-DATE，沿用 84 tests 成功基準；Lint 重跑。後續變更須重新執行受影響測試。

## 風險／決策

1. Android 系統 SQLite 不可假定 FTS5。採 bundled SQLite 的獨立 V4 Room DB 保存書靈／知識／metadata 搜尋投影，既有 library.db 仍是原書唯一來源；加入可恢復的變更佇列，避免每次啟動重建全庫。參考 [Android FTS5 說明](https://android.googlesource.com/platform/frameworks/support/+/835b2a85be93812f792a3ac2e9860a6f83c7174e)、[Room 2.8 wrapper](https://developer.android.com/jetpack/androidx/releases/room)。不升級整套 Room 到 3.x。
2. 本機價格由使用者確認輸入，不內建永久報價；V3 已啟用設定／Key 保留，V4 新付費能力預設關閉，緊急停止適用所有模型入口。
3. 新 schema 的恢復是停用 V4 功能，非直接安裝無新 migration 的舊 APK；禁止破壞式降版。
4. 未知閱讀進度不提供章節內容；只有手動已讀邊界或明確允許劇透才允許送出引用。Key／URI／問題原文不寫用量帳本。
5. 13,618 是合成目標，歷史實際索引 8,166 不是本次裝置觀察；RAM、冷啟動 P95、E Ink／NeoReader／SD 拔插待 P6。

P0：PASS（盤點完成，使用者已授權繼續）。V4 各功能尚待實作與測試，不能以本報告宣稱通過 P1–P5。
