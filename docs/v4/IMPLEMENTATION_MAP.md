# V4 最小增量對照

2026-11-25；基準 b6ef3c1。來源規格保留在 spec-librarian-v4。

| 階段 | 現有整合點／新增範圍 | 主機驗證 |
|---|---|---|
| P1 | librarian/CuratorService、LibrarianViewModel／Dialogs；新增 spirits 下 settings、gateway、cost ledger，沿用 KeyVault／transport | 0／300／13,618／20,000 假書、lifecycle／重建零 API；單次確認、未知價格、日月限額、並發、timeout／程序重建不重送、V3 回歸 |
| P2 | LibraryDatabase 2→3 可恢復 metadata 變更佇列；新增 V4 Room metadata projection＋FTS5／50 筆頁讀；MainActivity／LibraryViewModel 增量接入；LibraryRepository 掃描 checkpoint | schema migration 保留 history／URI、變更續處理、中文搜尋、50 頁／13,618 本、取消／恢復不誤標失蹤 |
| P3 | 新增 SpiritRepository／獨立 UI 分支；書籍選單與喵館員提供入口，原 Dialogue reducer／六張 PNG 保留 | 10 本離線建立／重開／刪除穩定 ID；flag 關閉可返回原功能 |
| P4 | 單本檢索／手動章節邊界／Responses 合約與 citation 驗證；付費預覽只在明確操作後 | 跨書／未讀章節拒絕、無資料退路、提示注入只當資料、取消／kill switch／限額／未知扣款 |
| P5 | SAF JSON importer＋嚴格 schema／UTF-8／大小／hash／重複 IDs／對書確認／transaction；PC 離線 converter | 成功／重匯／損壞／超限 rollback；原書不變、PC 假章節轉換驗證 |

保留：MainActivity HOME／recovery、NeoReaderBridge、ReaderResolver、KeyVault、黑白 UI 技術與 V3 FSM。Book identity 不變；書靈使用 SHA256 identity 的 opaque ID，解決規格 bookId 128 字上限與現有 URI-derived identity 的差異。

新增測試不得發出真實模型請求。每階段以 fake transport 記錄 calls，並執行既有 host suite；新 migration 要有 MigrationTestHelper 驗證。實機驗收與部署獨立為 P6，等待使用者接機指令。
