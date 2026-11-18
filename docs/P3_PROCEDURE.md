# P3 實機測試程序（執行前公開）

以下為首次 P3 的程序與歷史限制。2026-11-16 使用者完成復原示範並另行核准 P5 後，已將 KoboStyle 設為預設 HOME；重開機後使用相同合成 PDF 重驗實際內容、Back 與 Home 返回，詳 [HOME 設定紀錄](HOME_SETUP_2026-11-16.md)。舊版沒有 HOME 的敘述不代表最新 APK。

此程序已開始執行，詳 ACCEPTANCE_2026-11-15.md。P0 的 handler 發現不等於 NeoReader 已成功整合。使用者後續已授權安裝與 P1–P3 實機驗收，因此使用 ADB 安裝、前景 App 啟動／點選／返回及畫面擷取；不切換 HOME、不啟動 HOME 選擇器，不寫入系統設定。

1. 先在原廠桌面確認 BOOX 設定與 NeoReader 能正常開啟。保留原廠 HOME。
2. 只在 APK 建置成功後安裝 `org.kobostyle.launcher`，從 BOOX「應用程式」手動開啟 KoboStyle。
3. 使用者透過既有檔案傳輸方式建立專用 `KoboStyleTest` 子資料夾，放入可公開分享、無 DRM 的測試 EPUB 和 PDF 各一本；不使用私人書名或敏感內容作為報告證據。另準備至少 10 EPUB、10 PDF 供 P2 驗收。
4. 在書庫按「加入資料夾」，透過系統 SAF 選取該子資料夾；記錄是否提供持續讀取權限。Android 11 可能限制儲存根目錄與 Download 根目錄，請選子資料夾或使用「加入書籍」。
5. 確認書名、作者（EPUB 若有）、灰階封面或明確預設封面。重開 App，確認索引保留。記錄掃描失敗數，不分享完整 URI、私人檔名或序號。
6. 按 EPUB。App 先確認來源可讀，再對該 `content://` URI + `application/epub+zip` 查詢可見、啟用、exported 的 handler，使用 ACTION_VIEW + CATEGORY_DEFAULT + FLAG_GRANT_READ_URI_PERMISSION + ClipData。未釘選任何 BOOX 元件。
7. 若沒有可解析的預設 handler，系統顯示 chooser；由使用者確認畫面標示的 NeoReader。若走預設卻未成功載入，返回書房按「其他閱讀器」以 chooser 重試。App 不能自行判定閱讀器畫面是否真的載入。
8. 記錄：選到的閱讀器、書籍是否顯示、能否翻頁、系統 Back 是否回書房、任何可見錯誤。禁止以 intent 成功送出當作開書成功。
9. 對 PDF 重複步驟 6–8，MIME 為 `application/pdf`。
10. 測試取消 chooser，確認不新增開啟紀錄。紀錄只代表要求送出／chooser 選定，沒有讀取進度或載入成功保證；若 App 在 chooser 期間被系統銷毀，事件可能遺失。
11. 若 NeoReader 拒絕 URI，記錄實際錯誤與格式，使用 chooser 選擇其他已安裝的相容閱讀器；若沒有其他 handler，保留清楚錯誤。此版不自動複製檔案、不改用 file://，也不修改來源。
12. 補測移除記憶卡／重新授權、損壞 EPUB、加密／損壞 PDF、旋轉、字級 1.3。這些操作由使用者透過原廠 UI 或手動方式完成，無 ADB 設定寫入。

## 記錄表

| 測試 | 實際結果 | 狀態 |
|---|---|---|
| EPUB → NeoReader 內容載入與返回 | 合成 EPUB 顯示實際內容，1/11→2/11，Back 回書房 | 通過；限已測韌體／provider |
| PDF → NeoReader 內容載入與返回 | 合成 PDF 顯示實際內容，1/2→2/2，普通文件 Back 回書房；移動再還原文件可能返回原廠書庫 | 正常路徑通過；返回限制已記錄 |
| 取消 chooser 不寫紀錄 | 取消後尚未開啟，選定 Neo Reader V3.0 後才寫入 | 通過 |
| handler 拒絕 URI 時 chooser 可用 | 未測 | 待驗收 |
| 原廠桌面與設定仍可使用 | HOME 鍵到原廠桌面，App 設定入口可開系統設定並返回 | 通過；不等同 P5 復原演練 |

P5 必須先完成 `RECOVERY.md` 的手動復原示範，再取得明確核准。此 APK 沒有 HOME intent filter，也沒有 HOME 選擇入口。
