# 文件導覽

目前功能與建置方式請先看 [專案 README](../README.md)。以下實機報告是特定 Leaf2 與當時韌體的歷史觀察，不代表所有 BOOX 裝置都有相同行為。

| 文件 | 內容 |
|---|---|
| [產品規格](../SPEC.md) | 專案範圍、安全邊界與設計要求 |
| [V4 書靈開發與主機驗證](v4/IMPLEMENTATION_2026-11-25.md) | P1–P5 實作、費用控制、分頁／續掃、Lite／單書問答、預覽與 P6 接機清單 |
| [V4 Leaf2 實機驗收](v4/DEVICE_ACCEPTANCE_2026-11-25.md) | 13,618 本升級、批次索引修正、效能、原生測試與正式畫面驗收 |
| [知識包轉換與匯入](v4/KNOWLEDGE_PACK.md) | Windows 離線 adapter、SAF JSON 合約、大小／hash／章節限制 |
| [裝置摘要](DEVICE_REPORT.md) | 已確認的裝置能力及後續驗證摘要 |
| [測試計畫與結果](TEST_PLAN.md) | 自動測試命令、實機測試及未完成項目；依日期保留歷史結果 |
| [桌面復原](RECOVERY.md) | 使用原廠桌面與恢復預設桌面的路徑 |
| [閱讀器實測程序](P3_PROCEDURE.md) | SAF content URI 與 NeoReader 的驗證步驟 |
| [初次驗收](ACCEPTANCE_2026-11-15.md) | 合成 EPUB/PDF、掃描、旋轉與閱讀器測試 |
| [補充驗收](EXTENDED_ACCEPTANCE_2026-11-15.md) | microSD、授權、翻頁鍵與待機觀察 |
| [介面更新](UI_REDESIGN_2026-11-16.md) | Kobo 風格設計與合成書籍畫面 |
| [預設桌面驗證](HOME_SETUP_2026-11-16.md) | 使用者復原示範、HOME 選取及已知限制 |
| [繁體注音](INPUT_METHOD_2026-11-17.md) | 同文輸入法設定與實際組字搜尋結果 |
| [大型書庫修復](SCAN_FIX_2026-11-17.md) | 掃描額度、錯誤分類及 8,141 本書比對結果 |
| [書籍分類](CATEGORIES_2026-11-18.md) | 檔名分類、作者搜尋及保留原有書庫的升級驗證 |
| [喵館員與 OpenAI](LIBRARIAN_2026-11-20.md) | 新分頁、離線選書、API／金鑰設計、主機驗證與待接機程序 |
| [喵館員實機驗收](LIBRARIAN_DEVICE_2026-11-20.md) | Leaf2 升級資料比對、Keystore、喵館員與合成 EPUB／PDF 開書測試 |
| [後續開發提示](CODEX_PROMPT.md) | 交接給 Codex 時使用的現況與開發規則 |

`acceptance-images/` 的公開截圖僅包含合成測試書籍或系統桌面選項。報告中提及的 `.tools/acceptance/`、原始診斷、私人書庫資料庫與部分截圖僅留在原開發電腦，不隨儲存庫提供。原始 UI 參考圖 `kobo-ui-example.png` 亦未納入。
