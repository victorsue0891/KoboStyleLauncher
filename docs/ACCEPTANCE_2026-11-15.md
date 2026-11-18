# Leaf2 實機驗收 — 2026-11-15

範圍：使用者於本輪明確要求「請進行驗收」，接續先前提出的 APK 安裝與 P1–P3 實機驗收。這項授權不包含 P5 HOME 選擇。沒有修改系統設定、停用原廠套件、root 或私有閱讀器資料庫存取。

本輪核心結論：P1–P3 核心流程與解凍後短期啟動回歸通過。下文保留當時的失敗、修正及待測項目；其後已補上正常重開機、真實撤銷授權、實體 microSD 拔插等，最新結果請見 [補充驗收報告](EXTENDED_ACCEPTANCE_2026-11-15.md)。並非全項耐久與 OEM 邊界測試完成，P5 未執行。

## 已取得證據

1. 重新執行 `scripts/diagnose_device.ps1`：唯一 authorized Leaf2、Android 11/API 30、1264×1680、300 dpi，0 個唯讀命令失敗。HOME 仍解析到 `com.onyx/.StartupActivity`。
2. 安裝 APK SHA-256 `430B55E3224D697E69267B3F8F26132C7275174723D8EC29B9231FE9F724AE01`，`scripts/deploy_debug.ps1` 回傳 `Success`。
3. 第一次啟動失敗：`Error type 3 / Activity ... does not exist`。查詢本 App 的 package state 顯示 `installed=true hidden=false suspended=false enabled=3` 與 `lastDisabledCaller: com.onyx`。這是實際 OEM 停用狀態；尚不能由此判定原廠自動停用的完整政策。
4. 只對測試 App 執行 `adb shell pm enable --user 0 org.kobostyle.launcher`，回應 `new state: enabled`；沒有修改 BOOX 原廠套件。
5. 再次啟動成功：`Status: ok / LaunchState: COLD / TotalTime: 3410 / WaitTime: 3460`（毫秒）。擷取到 KoboStyle 空書庫與首頁，無 HOME 選擇器；UI hierarchy 確認前景套件為 `org.kobostyle.launcher`。這是一次啟動樣本，不是完整效能 benchmark。
6. 從 App 點「設定 → 開啟系統設定」，前景 Activity 確認為 `com.android.settings/.homepage.SettingsHomepageActivity`，返回鍵回到書房，並能繼續進入書庫。
7. 以 `scripts/create_acceptance_fixtures.py` 產生 10 EPUB / 10 PDF 與 2 個損壞文件，透過 `adb push` 放入 `/sdcard/Download/KoboStyleAcceptance-20261115`。均為合成資料；未讀取或修改私人書籍。valid 與 invalid 分開，以便先驗證 20 本正常文件。
8. 從書庫開啟 ACTION_OPEN_DOCUMENT_TREE 成功，前景是 `com.android.documentsui`。根目錄明確顯示「無法使用這個資料夾／為保護你的隱私，請選擇其他資料夾」。自動點選 Download 資料夾未產生導覽，已請使用者在系統 UI 手動授權 `Download/KoboStyleAcceptance-20261115/valid`；尚未將此項標為成功。

## 證據解讀

- 螢幕擷取是 Android 畫面內容，不代表電子紙實體殘影、閃爍或刷新品質已通過。
- 裝置狀態列時間與主機不同；本報告以工作階段日期 Asia/Taipei 記錄，不調整裝置時鐘。App 的上次開啟事件採裝置時鐘，驗收應看前後是否更新而非以主機時間判錯。
- OEM `screencap -p` 在 PNG 前輸出 `capture from screenshot!` 文字；擷取工具只去除 PNG signature 前的文字，沒有修改圖像內容。
- `uiautomator dump /dev/tty` 沒有回傳 hierarchy，因此改輸出到 `/data/local/tmp/kobostyle-acceptance.xml` 後讀取。這是 UI 擷取暫存檔，不是系統設定變更。
- 原始 UI/screenshot 暫存於被忽略的 `.tools/acceptance`，不將其他系統畫面的個人資訊加入共享報告。

## 本輪使用的命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_device.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\deploy_debug.ps1
adb shell am start -W -n org.kobostyle.launcher/.ui.MainActivity
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p org.kobostyle.launcher
adb shell dumpsys package org.kobostyle.launcher
# 僅在已觀察到本測試 App 被原廠停用時：
adb shell pm enable --user 0 org.kobostyle.launcher
adb shell am start -W -n org.kobostyle.launcher/.ui.MainActivity
python scripts/create_acceptance_fixtures.py
adb push .tools/acceptance/fixtures/KoboStyleAcceptance-20261115 /sdcard/Download/
```

前景互動使用 `adb shell input tap <x> <y>`、`input swipe ...`、`input keyevent 4`，坐標由當下 UI hierarchy 決定，不假設其他裝置或方向相同。畫面證據使用 `adb exec-out screencap -p` 及 `uiautomator dump`；只查詢當下前景 Activity，未讀取閱讀器的私有資料庫。

## SAF 授權前的暫停點（歷史紀錄）

當時等待系統 SAF 資料夾授權；後續已取得並完成下列測試。microSD 實體拔插仍未測；E Ink 觀感已由使用者確認。P5 保持未核准／未執行。

## SAF 授權後：正常文件、閱讀器與持續性

使用者已確認手動授權 `valid` 子資料夾，後續實際觀察如下：

- 書庫顯示「授權來源：1；需要重新授權：0」及「掃描結束：已處理 20 本，失敗 0 項」。PDF 第一頁縮圖可見；EPUB 的繁體中文 OPF 書名、作者「合成測試作者」與兩階灰階封面可見。
- `Acceptance-PDF-01` 從書房點開後，前景為 `com.onyx.kreader`，截圖顯示實際測試文字 `KoboStyle PDF 01 PAGE 1`。點翻頁後 UI 顯示 `2/2`；系統 Back 回到 KoboStyle，並顯示本 App 的上次開啟事件。
- EPUB 01 同樣顯示實際 XHTML 文字、繁體中文測試段落，頁碼從 `1/11` 變成 `2/11`，Back 回書房。這是本韌體、此 SAF provider 與這兩份測試文件的相容性證據，不代表所有 EPUB／PDF／provider 都相容。
- 「其他閱讀器」叫出 Android chooser，畫面包含 `Neo Reader V3.0`。第一次取消後，PDF 09 仍顯示「尚未開啟」；第二次選擇 Neo Reader 後，PDF 09 新增開啟事件。
- 對本 App 執行 `am force-stop` 後重新啟動成功（本次冷啟動 2053 ms）；三筆開啟事件仍在，書籍、封面、授權均保留。手動重新掃描 20 本再次 0 失敗，原有開啟時間沒有被重掃清除。
- 使用者明確回報「文字清楚，殘影／刷新可接受」。此項記為使用者目視結果；未量測耗電、長時間殘影，也未調整 BOOX 每 App 刷新設定。

## 本輪發現並修正的問題

加入合成 `Corrupt.epub`、`Corrupt.pdf`、`Encrypted.pdf` 後，App 可完成掃描且未崩潰，但密碼保護 PDF 被錯誤標為「來源無法存取／請重新授權」。原因是 PdfRenderer 的 SecurityException 被與 SAF 權限錯誤混在一起。

已修正 `CoverCache.pdf`：開啟 SAF descriptor 的權限例外仍視為來源不可用；descriptor 已成功開啟後，PdfRenderer 因密碼保護拒絕解析的例外改歸為 metadata error，顯示預設封面／解析失敗，不誤導使用者重新授權。

新增實機 instrumentation 測試 `DeviceAcceptanceTest`，使用實際 SAF grant、原生 PdfRenderer 與全新 in-memory Room 測試資料庫。它不讀取 NeoReader 資料庫，也不寫入使用者主要書庫索引。初次測試確實捕捉到上述分類錯誤。另一次測試失敗來自測試 APK 被停用；只啟用本 App 與本測試 APK 後重驗，沒有停用任何原廠套件。

## 100 本與旋轉測試

- 100 本正常文件（50 EPUB / 50 PDF）加 3 個刻意異常文件：`expected=103 indexed=103 failures=3 elapsedMs=11870 grayscale=PASS`。
- 無重複 document identity；正常文件狀態皆為 ok，3 個異常文件皆為 metadata_error。
- EPUB 封面實際像素 R/G/B 相等；取消掃描於 10 筆後停止，保留已完成項目。
- 實機 Activity 方向切換通過：橫向 content 1680×1178、直向 content 1264×1594；當前裝置實際 fontScale=1.3。使用 Activity.requestedOrientation，只改本 Activity 並恢復原值，未寫入系統旋轉／字級設定。
- 最終此輪 instrumentation：`OK (2 tests)`。初次失敗紀錄保留在本機，沒有以失敗的 runner 結束碼宣稱通過。
- 1.0 字級、實體 microSD 拔插、完整長時間耗電與無 handler 情境仍未被此測試覆蓋。

## 1000 本與最終修正版

- 1000 本正常文件 + 3 異常文件的 scanner 子測試通過：`expected=1003 indexed=1003 failures=3 elapsedMs=158128 grayscale=PASS`；取消測試於 10 筆停止。這是實體 Leaf2 的 SAF／解析／獨立 Room 索引測試，不是 1000 列主要 UI 的長時間捲動 benchmark。
- 同次 suite 的旋轉子測試因 App 再次停用而失敗；沒有將該次 suite 整體標成成功。旋轉後續單獨重驗 `OK (1 test)`，最後修正版又以 23 個文件完整回歸 `OK (2 tests)`。
- 橫向書庫的操作按鈕原本直排四列，占用過多空間。已改為兩列雙欄、維持 48dp 最小觸控高度，並修正掃描期間按鈕啟用狀態。實機 regression 明確檢查 RecyclerView 至少 120dp 高；當前字級 1.3 下，橫向書架高度 449px、直向 865px。
- 模擬單本合成 PDF 暫時不存在：顯示「來源無法存取／請重新授權」，App 未崩潰；恢復原路徑、重掃後狀態恢復。檔案內容沒有變更。此測試不能取代實體 microSD 拔插或真實撤銷 SAF grant。
- 該移動過的文件在 NeoReader 出現「文檔發生改變，需要重新打開」，後續可載入；此分支的 Back 回到原廠書庫而非 KoboStyle。普通未異動 EPUB/PDF 路徑先前可回 KoboStyle，因此返回行為須視第三方閱讀器流程而定，不能保證所有情況相同。
- 980 個額外壓力測試文件已逐項核對 SHA-256，確認目錄沒有額外文件後，以明確檔名刪除，最後 rmdir 空目錄；未使用遞迴刪除。主要 Room 書庫未被大量測試索引寫入。
- 最終 APK SHA-256：`57A880C74F8C28469668F8EA2BBC58B285B537A7343D54A3EB938842E3E7C8E2`；8,875,588 bytes；已安裝，簽章驗證 exit 0。18/18 主機測試通過，Lint 0 errors / 19 warnings。

## 解凍前的啟動阻擋（歷史紀錄）

不只初裝／更新會停用：最後一般流程返回原廠桌面後，本 App 的 package state 又是 `enabled=3`，後續 `am start` 回應 Activity does not exist；該次 `lastDisabledCaller` 為 `android`。不能將所有停用都直接歸因為某一未查證的 OEM 政策。

只使用 `pm enable` 暫時恢復不足以保證日常使用。已請使用者從原廠「應用」介面檢查 KoboStyle 的單一 App 凍結／解凍設定；未更改全域凍結政策、未碰其他 App 或 HOME。等待使用者回報並重驗返回桌面後仍可開啟。P1 在此之前屬於有條件通過，不能整體驗收結案。

HOME resolver 最後仍回應 `com.onyx/.StartupActivity`，HOME 鍵可到原廠 `com.onyx/.reader.main.ui.MainActivity`。這證明目前原廠桌面可達，不代表已演練「切換成 KoboStyle HOME 後再復原」；P5 沒有執行。

## 可重現的額外測試命令

先依 P3_PROCEDURE.md 授權合成 valid 子資料夾。異常樣本另以 `create_encrypted_fixture.py` 建立密碼 PDF（測試用 pypdf 安裝在 .tools/acceptance/vendor），再將三個異常文件放入已授權的 valid 資料夾。不得替換為私人書籍。

```powershell
python scripts/create_acceptance_fixtures.py
python -m pip install --target .tools/acceptance/vendor pypdf==6.1.1
python scripts/create_encrypted_fixture.py
python scripts/create_scale_fixtures.py --pairs 40
adb push .tools/acceptance/scale-acceptance-20261115 /sdcard/Download/KoboStyleAcceptance-20261115/valid/
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -ExpectedBooks 103
# 待上一步完成後擴充：
python scripts/create_scale_fixtures.py --pairs 490
adb push .tools/acceptance/scale-acceptance-20261115 /sdcard/Download/KoboStyleAcceptance-20261115/valid/
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -ExpectedBooks 1003
# 所有讀取測試完成後，只清除已驗證的合成 scale 文件：
python scripts/cleanup_scale_fixtures.py
```

## 合成資料的畫面證據

- [20 本正常書籍匯入完成](acceptance-images/05-after-grant.png)
- [PDF 真實內容載入](acceptance-images/06-pdf-open.png)
- [EPUB 真實內容載入](acceptance-images/09-epub-open.png)
- [損壞文件預設封面與錯誤狀態](acceptance-images/14-malformed-fallback.png)
- [文件暫時遺失的可恢復狀態](acceptance-images/17-missing-source.png)
- [修正後的橫向書庫](acceptance-images/19-landscape-fixed.png)

共享圖片只包含合成測試內容。碰到原廠私人書庫的意外截圖已從本機暫存移除，沒有納入本報告。

## 使用者解凍後的最終回歸

使用者回覆「已解除 KoboStyle 的凍結」。之後沒有再執行 `pm enable`，直接讀取套件狀態並操作：

- package state 為 `installed=true hidden=false suspended=false enabled=0`。0 是採用 manifest 的預設啟用狀態；實際啟動成功，不能誤判為 disabled。
- 首次冷啟動成功，TotalTime 1625 ms。兩次按 HOME 回原廠桌面，在桌面停留並處理報告後再次開啟，均成功且保持 enabled=0；熱啟動樣本為 352／491 ms。
- 本 App「應用程式」清單可顯示並點選 KoboStyle 項目，前景確認回到本 App；沒有公開其他安裝 App 清單。
- 書庫仍顯示授權來源 1、需要重新授權 0，合成 PDF 及先前上次開啟紀錄仍在。
- 僅 force-stop 本 App 後再次冷啟動成功，TotalTime 1741 ms，套件仍為 enabled=0。以上時間都是單次樣本，不是效能統計。
- HOME resolver 仍為 `com.onyx/.StartupActivity`；沒有開啟 HOME 選擇器或變更預設桌面。

補充命令差異：`am start -a MAIN -c LAUNCHER -p ...` 的隱式 shell 啟動曾回覆 unable to resolve；`cmd package query-activities` 實際找到 MAIN/LAUNCHER 活動，且明確元件啟動與 App 清單點選均成功。此 filter 沒有 DEFAULT；不將上述 shell 命令當作一般 Launcher 不可啟動的證據，也未為它新增 HOME／DEFAULT filter。

```powershell
adb shell dumpsys package org.kobostyle.launcher
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p org.kobostyle.launcher
adb shell am start -W -n org.kobostyle.launcher/.ui.MainActivity
adb shell input keyevent 3
# 桌面停留後再執行上方明確元件啟動；重複兩輪。
adb shell am force-stop org.kobostyle.launcher
adb shell am start -W -n org.kobostyle.launcher/.ui.MainActivity
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

結論：先前停用阻擋在使用者手動解凍後的短期回歸未再重現。未驗證解凍在重開機、長期待機或下次更新後是否持續有效；不推測 OEM 凍結政策。實體顯示由使用者回覆「文字清楚，殘影／刷新可接受」。完整未測邊界見 TEST_PLAN.md；P5 仍需手動復原示範及明確核准。
