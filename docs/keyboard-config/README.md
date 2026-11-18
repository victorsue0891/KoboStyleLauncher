# Leaf2 同文注音設定備份

此處僅備份同文輸入法的兩份自訂設定，不修改 KoboStyle 或系統 HOME。

目前實機使用同文 3.3.13，資料夾為 `Download/KoboStyleZhuyin`，由使用者透過 SAF 授權。另需官方 `bopomofo.schema.yaml`、`zhuyin.yaml` 與 `terra_pinyin.dict.yaml`，固定來源版本及 APK 校驗資訊見 [輸入法報告](../INPUT_METHOD_2026-11-17.md)。下載來源與雜湊記錄留在專案 `.tools/acceptance`。

- `default.custom.yaml`：啟用注音方案。
- `trime.custom.yaml`：保留注音版面；空字型設定不保證改變系統字形。

還原時將這兩份檔案放入上述已授權資料夾，在同文主畫面按「部署」。同文內建配色另由 UI 選擇「鍵盤樣式 → 配色 → 墨池／Ink」。此設定不接受任意第三方可執行程式。

輸入方式：注音組字／選字後按右下 Enter，確認文字已進入搜尋欄，再按搜尋。首次部署及原廠桌面往返已實測；重開機和隔夜待機尚未驗證。
