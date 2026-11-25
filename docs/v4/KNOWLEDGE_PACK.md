# PC 知識包轉換與 Android 匯入

這個專案只包含離線 adapter，不會安裝或執行外部 Book-to-Skill，不會掃描全書庫或呼叫任何 AI。上游工具若用模型處理書籍，屬另一個需自行確認授權、隱私與費用的工作。

## 已有章節 Markdown 時

需要 Python 3.9 以上，無第三方 Python 相依套件。章節位於選定目錄的 `chapters/*.md`，依檔名字母序編為第 1、2…章；請先使用 `001-`、`002-` 等前綴，人工核對順序，不推測 EPUB／PDF 頁碼。

```powershell
python scripts/convert_skill_to_pack.py --skill-dir 'D:\MyNotes\sample' --title '合成測試書' --author '測試作者' --output 'D:\MyNotes\sample-pack.json'
python -m unittest discover -s scripts -p test_convert_skill_to_pack.py -v
```

輸出檔必須尚不存在。adapter 讀取本機 Markdown，分段並計算 section SHA256，輸出 JSON；不執行 Markdown 程式碼或連結。最多 200 個章節檔、每檔 1 MB、500 段、整包 10 MB；每段最多 6,000 字且 UTF-8 不超過 20 KB。拒絕連結的章節目錄、超限及輸出覆寫。不是通用 EPUB/PDF 解析器。

`--fingerprint` 通常省略，由裝置上的使用者核對目標書。若提供，Android 只接受與選定書籍完全一致的 `metadata-v1:<App 內書目版本 SHA256>`；不能將檔案 SHA256 或任意字串冒充該版本。現在沒有自動交換 fingerprint 的功能。

## Android 合約

公開 [schema](schemas/knowledge_pack_v1.schema.json)、[合成範例](examples/knowledge_pack_sample.json)。Android 使用串流封閉格式檢查；不需下載 schema，也不執行包內內容。實作在 schema 基礎上進一步收緊：

- UTF-8 合法、schemaVersion 整數 1、不得有未知／重複欄位；packId、sectionId 限 ASCII 字母數字、點、底線、連字號。
- 全包最多 10,000,000 bytes、1–500 段，每段非空且最多 10,000 code points／20,000 UTF-8 bytes，chapterOrder 0–100,000。
- sectionId 不重複，contentSha256 為小寫 SHA256 並與實際文字相符；sourceLocator 只能是相對位置文字，拒絕冒號、反斜線、根路徑與 `..`。
- source.type 為 `book-to-skill`／`manual`／`local-notes`；時間欄位若提供須可解析為有 offset 的時間。
- 先完整讀取驗證，顯示包內書名／作者／來源／段數與目前書名供使用者確認；再重新讀取、比對整包 hash，於單一 Room transaction 替換。途中失敗則保留原包。

在「喵館員 → 書靈小屋」選書 →「匯入 PC 知識包（Deep）」使用 SAF 選取 JSON。App 只將內容存入自己的資料庫，不保留額外永久文件 grant，不修改原書或來源 JSON；刪除書靈／知識包也不刪原書。

匯入本身零 API。只有明確要求 AI 並同意當次預覽時，才可能傳送本書允許範圍內的少量片段。章節未知時拒絕取內容；讀到第 N 章只允許 <= N 的段落。人工核對外部筆記可能預先含有結局：章節標籤與 hash 無法判斷文字是否真的沒有劇透。
