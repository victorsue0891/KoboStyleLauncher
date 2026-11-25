# 測試計畫與驗收紀錄

## 2026-11-25 V4 接機驗證

同日後續 Leaf2 已連線授權，新版已安裝。106 項 JVM／Robolectric 回歸通過，Lint 0 errors／61 warnings；V4 真實書庫／原生 DB／掃描／UI 四項及既有 Keystore／館員開書／分類／書庫四項實機測試分別通過。實際 13,618 本書與來源升級前後逐列一致，50 頁查詢 P95 267 ms，完成索引後程序冷啟動首畫面 P95 2010 ms。正式 SAF／真實 AI／人工顯示項目的最新狀態、失敗後修正及重現命令見 [P6 驗收紀錄](v4/DEVICE_ACCEPTANCE_2026-11-25.md)。下方同日「未接機」屬接機前的紀錄。

## 2026-11-25 V4 P1–P5 主機驗證

`assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console plain` exit 0，106 tests／0 failures／0 errors／0 skipped，Lint 0 errors／61 warnings；Python adapter 2/2 通過。包含共用 gateway／未知扣款、0／300／13,618／20,000 合成書各 10 次 lifecycle 零 API、Room 2→3 migration、FTS5／50 頁／增量索引、掃描 checkpoint、Lite、單書章節邊界、引用白名單、問答連按及知識包 rollback。實際命令、階段結果、主機預覽與限制見 [V4 完整紀錄](v4/IMPLEMENTATION_2026-11-25.md)。

全部假資料／fake transport，沒有真實 API。未接 Leaf2、未執行 ADB 或 instrumentation；APK 建置成功不代表安裝／性能／電子紙／SAF picker／真實問答已驗收。下一步是該文件 P6 清單。以下保留歷史結果。

## 2026-11-20 更換 Key 後真實 API／快取通過

正式 Leaf2 重新要求 AI 推薦，`gpt-4.1-mini` 回傳 **HTTP 200／ok**，耗時 10,430 ms，input 1,119／output 415 tokens，呼叫計數 4→5。畫面確認 OpenAI 推薦及 8,141 本館藏／20 本可讀候選；回應通過結構與候選 ID 驗證。再次要求同組推薦命中快取（42 ms），計數維持 5。沒有修改程式、重新建置或讀取 Key；未重跑不相關測試。詳 [最新實機紀錄](LIBRARIAN_DEVICE_2026-11-20.md)。

## 2026-11-20 新 Key 重試與 HTTP 診斷

原版重試仍 authentication（計數 2→3，2,013 ms）；新增僅記 HTTP 狀態及允許清單錯誤碼的診斷後，正式 Leaf2 重試明確收到 **401／invalid_api_key**（3→4，1,022 ms），正常回退離線，真實 AI 尚未通過。84 項 JVM／Robolectric 測試全數通過，Lint 0 errors／45 warnings，兩份 APK 建置成功，主 APK 已覆蓋安裝。書庫／來源／開啟紀錄摘要與 HOME 未變。完整命令、隱私保護及限制見 [最新實機紀錄](LIBRARIAN_DEVICE_2026-11-20.md)。

## 2026-11-20 OpenAI 真實重試

正式 App 再次送出推薦請求，呼叫計數 1→2，耗時 2,955 ms，結果仍為 `authentication`（現有程式合併 HTTP 401／403）。畫面確認回退離線，真實 AI 推薦未通過。沒有讀取 Key、修改程式或重跑不相關測試。詳 [最新實機紀錄](LIBRARIAN_DEVICE_2026-11-20.md)。

## 2026-11-20 實際館藏推薦範圍

離線與 AI 共用篩選排除兩個已知驗收資料夾，保留原始索引與文件。主機 81 tests 全部通過，Lint 0 errors／43 warnings。覆蓋安裝後 `LibrarianRealLibraryTest` 1/1 通過，實際 8,166 筆索引排除 25 本驗收書，8,141 本可參與篩選；離線及模擬 AI 各核對 20 本可存取候選、產生 3 本真實館藏卡。正式介面亦已操作確認數量。沒有呼叫真實 API，更新前後書庫／來源／開啟時間完全一致。完整命令及限制見 [最新實機結果](LIBRARIAN_DEVICE_2026-11-20.md)。

## 2026-11-20 喵館員接機驗收

使用者後續接上並授權 Leaf2。真實 Keystore、喵館員合成書庫 UI／三本書的 ACTION_VIEW 與 chooser，以及既有分類／書庫回歸共 4 項 instrumentation 測試通過。79 項 JVM 測試重新通過，Lint 0 errors／43 warnings。升級前後 8,166 本書／4 個來源及開啟紀錄逐列一致；尚待真實 API 與人工顯示觀察。確切命令、測試過程遇到的限制及最新結果見 [實機驗收](LIBRARIAN_DEVICE_2026-11-20.md)。以下為較早的主機開發紀錄。

## 2026-11-20 喵館員與 OpenAI

`assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console plain` 通過，79 項 JVM／Robolectric 測試，0 failures／errors／skipped；Lint 0 errors／43 warnings；APK 簽章驗證通過。包含狀態機、20 本合成書介面流程、8,166 筆候選篩選、橫向字級 1.3、加密重存／竄改、取消、API 錯誤與回應驗證。網路全部使用 mock，不傳送真實 API 請求。新增 11 個 Lint warnings 為 ViewConstructor 1、UseKtx 5、SetTextI18n 5；未新增 error 或用 baseline 隱藏警告。

Leaf2 本輪未連線，未執行 ADB 或安裝。Keystore instrumentation 測試已建置但未執行，真實 API、喵館員開書、電子紙與實體鍵回歸均待實機。完整命令、已完成／未完成／阻礙與接機程序見 [喵館員紀錄](LIBRARIAN_2026-11-20.md)。以下日期為歷史結果。

## 2026-11-18 書籍分類

首頁分類、按作者瀏覽／搜尋、分類返回與旋轉狀態已實作。`assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console plain` 通過，43 項 JVM 測試無失敗，Lint 0 errors／32 warnings。`CategoryUiAcceptanceTest` 與 `ReaderUiAcceptanceTest` 實機 `OK (2 tests)`，使用獨立合成書庫。真實書庫升級前後 8,166 本的原有欄位及四個來源完全一致；沒有清空索引或重掃整個書庫。完整命令與限制見 [分類驗收](CATEGORIES_2026-11-18.md)。

## 2026-11-18 公開儲存庫整理驗證

本輪只整理公開文件、忽略規則及文字格式，未變更應用程式行為、安裝 APK 或操作實機。從 Git index 匯出僅含待上傳檔案的乾淨副本；副本沒有原工作目錄的建置產物或 `local.properties`，透過環境變數使用已安裝的 JDK 17、Android SDK 與既有 Gradle 下載快取。

```powershell
# 原工作目錄：僅在目的資料夾尚不存在時匯出
git checkout-index --all --prefix=.tools/publication-checkout/
# 以下本機輔助腳本只適用已備妥 .tools 的開發電腦；一般使用者依 README 設定 JDK/SDK
. .\scripts\use_local_tools.ps1
Set-Location .tools/publication-checkout
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console plain
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test_diagnose_device.ps1
```

- `BUILD SUCCESSFUL`，84 項 Gradle tasks 執行；主程式與 instrumentation 測試 APK 均成功建置。
- 35 項 JVM 測試，0 failures／0 errors／0 skipped。
- Lint 0 errors／32 warnings：相依版本提示 13、UseKtx 12、UnusedResources 5、PluralsCandidate 1、StaticFieldLeak 1。未以 baseline 隱藏既有警告。
- 診斷腳本 mock 測試 6/6 通過，不連接真實裝置。
- 建置副本的非 Markdown 檔案與 Git index 逐一比對相符（文字檔忽略 CRLF／LF 差異）；公開文件本機連結皆可解析，截圖無文字／EXIF metadata。
- 本次不重跑實機驗收；歷史結果與待驗證限制仍依下方日期紀錄。這也不是未預裝 SDK、無下載快取環境的安裝測試。

---

紀錄日期：2026-11-15（Asia/Taipei）。自動測試不替代 BOOX 實機驗收。

2026-11-16 Kobo 風格 UI 更新、截圖與回歸命令見 [UI 驗收紀錄](UI_REDESIGN_2026-11-16.md)。以下 2026-11-15 的驗收結果保留為歷史紀錄；最新 Lint 為 0 errors／31 warnings，JVM 測試仍為 21/21。

同日後續使用者完成手動復原示範並核准切換，已透過系統 UI 設定 KoboStyle 為預設桌面。Home／Back、恢復原廠並切回、刻意崩潰後的選擇器復原、正常重開機保留預設與 PDF 開書均已驗證；BOOX 開機仍先顯示原廠介面，需按 Home 進入 KoboStyle。最新主機 27 tests／0 failures，Lint 0 errors／32 warnings。實際 HOME 狀態、APK 與限制以 [HOME 設定紀錄](HOME_SETUP_2026-11-16.md) 為準，以下 P5 未核准／無 HOME 的敘述屬先前版本。

## 階段狀態與命令

| 階段 | 精確命令 | 已知結果／限制 |
|---|---|---|
| P0 唯讀發現 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_device.ps1` | 已執行，1 台 authorized Leaf2，0 個唯讀命令失敗；詳 DEVICE_REPORT.md |
| P0 腳本防護 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test_diagnose_device.ps1` | 6/6 主機 mock 案例通過；未接觸實機 |
| P1 建置 | `.\gradlew.bat assembleDebug` | 已安裝；啟動／旋轉通過，使用者解凍後返回桌面再開與冷啟動通過；長期待機未測 |
| P1–P3 自動測試 | `.\gradlew.bat testDebugUnitTest lintDebug` | 最新 21/21 測試通過，Lint 0 errors／24 warnings；明細見補充驗收報告 |
| P2 SAF 與書庫 | 同上，加下方 T04–T11 手動測試 | 20 本匯入／持續性、異常文件、100／1000 本索引、單一文件授權撤銷／恢復與實體 SD 拔插皆已測 |
| P3 NeoReader | 無自動裝置開書命令；依 P3_PROCEDURE.md 手動操作 | EPUB/PDF 真實 SAF URI 載入與正常返回通過；文件異動後返回原廠書庫的限制已記錄 |
| P4 E Ink | 原廠 UI 手動調整／觀察 | 使用者目視可接受，controller 仍為 no-op，未做長期量測 |
| P5 HOME | 無 | 尚未核准；此版不宣告 HOME、不提供選擇器 |

本機先執行 `. .\scripts\use_local_tools.ps1` 設定當前 PowerShell 環境。合併驗證命令：

```powershell
. .\scripts\use_local_tools.ps1
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --console plain
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test_diagnose_device.ps1
```

## 自動測試內容

- 診斷：無裝置、未授權、離線、多裝置時不進入 shell；正常情況去除序號；不支援的 OEM 命令記錄失敗後繼續，且只允許唯讀命令。
- EPUB：不同 metadata namespace、相對封面路徑、EPUB 2 legacy cover、畸形 XML、DOCTYPE／外部實體、ZIP 路徑跳脫、高壓縮比、超額串流、外部 cover URI。
- Room／repository：重掃保留開啟事件、相同 document 去重、遺失後重掃恢復、不同 provider/document 不碰撞、MIME 保守回退、授權消失標示重新授權。
- Reader：content scheme、MIME、唯讀 flag、ClipData、不固定套件；拒絕 file URI；chooser 選定之前不寫紀錄。
- P1：manifest 有 LAUNCHER、沒有 HOME／網路／全檔案權限；Robolectric Activity 啟動與重建。

## 實機驗收

| ID | 測試 | 預期 | 實際／狀態 |
|---|---|---|---|
| T01 | USB ADB 授權 | 唯一 device | 通過，2026-11-15 |
| T02 | 一般 App 啟動 | 無 HOME 選擇、正常畫面 | 解凍後兩次返回桌面再開、force-stop 冷啟動、後續更新與一次正常重開機皆通過；沒有自動啟用主 App 掩蓋停用問題。長期待機未測 |
| T03 | 開啟原廠設定 | 系統設定可見 | 通過；SettingsHomepageActivity 可見且能返回書房 |
| T04 | SAF 選取資料夾／文件 | 持續讀取授權或明確錯誤 | 資料夾與單一 PDF 均實機通過；內部儲存與 microSD 子資料夾、單一文件可持續讀取 |
| T05 | 10 EPUB + 10 PDF | metadata／封面或合理 fallback | 通過；10 EPUB + 10 PDF 處理 20 本、0 失敗，EPUB 書名／作者與灰階封面、PDF 縮圖可見 |
| T06 | 重開 App／失效授權 | 索引保留、無崩潰、可重新授權 | 真實釋放合成單一 PDF 的持續授權後，Android 拒絕讀取、App 標示重新授權並保留紀錄；使用者重新選取後恢復。重啟、更新、重掃保存亦通過 |
| T07 | EPUB → NeoReader | 真正載入或具體相容性失敗紀錄 | 通過；Neo Reader V3.0 實際顯示合成 EPUB，1/11→2/11，正常 Back 回書房 |
| T08 | PDF → NeoReader | 同上 | 通過；Neo Reader V3.0 實際顯示合成 PDF，1/2→2/2，正常 Back 回書房；異動文件邊界見 T16 |
| T09 | 畸形 EPUB／損壞或加密 PDF | 不崩潰、顯示 fallback | 通過；損壞 EPUB/PDF 與密碼保護 PDF 均安全 fallback；已修正加密 PDF 被誤標為需重新授權 |
| T10 | microSD 拔除／插回 | 來源不可用、重新掃描恢復 | 使用者實體拔插通過；拔出後不可用、索引與時間保留；插回後系統稍後回報 mounted，既有授權可讀，重掃恢復。未用 ADB 掛載／修復 |
| T11 | 旋轉／字級 1.0、1.3／長 CJK | 文字可讀、可點擊 | 實機 context 字級 1.0／1.3 與橫直向皆通過，1000 筆長 CJK 書名；未修改系統字級。一般 App 的系統字級 1.3 亦已測 |
| T12 | BOOX 每 App E Ink 最佳化 | 無持續刷新、殘影可接受 | 使用者目視回報文字清楚、殘影／刷新可接受；未做長時間量測或調整 BOOX 刷新模式 |
| T13 | 原廠 HOME／NeoReader | 保留、可使用 | 通過保留／可達性：HOME 仍是 com.onyx/.StartupActivity，HOME 鍵進入原廠桌面；沒有停用原廠套件 |
| T14 | HOME 選擇 | 需明確核准 | 未核准／未執行 |
| T15 | 原廠 HOME 復原 | 實機手動示範 | 未執行 |
| T16 | chooser 取消／選擇／拒絕 URI | 紀錄正確、失敗可恢復 | 實機 chooser 取消／選定通過；無 handler、MIME 不符、權限拒絕與處理器移除競態新增主機測試通過，未聲稱 OEM 實機拒絕分支通過；異動文件可能返回原廠書庫 |
| T17 | 0／100／1000 本與取消掃描 | 無 ANR、可取消、無重複 | 0 本 UI、100／1000 本索引與取消通過；新增同一 UI 的隔離書庫 1000 筆資料、四種字級／方向組合、全範圍雙向每 25 列取樣通過；不是長時間連續滑動 benchmark |
| T18 | App 停在前景／背景待機 | 無週期工作或秒鐘刷新 | 前景靜置 60 秒，App OnDraw 次數 0；不代表電子紙硬體刷新次數。程式無 timer／periodic worker；隔夜待機與未接 USB 的耗電尚未量測 |
| T19 | 實體翻頁鍵與可選對應 | 確認事件、僅作用於書房 | 人工依序按鍵取得 25／24；實機注入相同事件驗證啟用、停用、反向、重建保存及音量不變。未將注入捲動稱為人工目視捲動 |

P3 詳細步驟見 [P3_PROCEDURE.md](P3_PROCEDURE.md)。沒有以本機測試宣稱實機畫面、閱讀器整合、復原或 E Ink 支援成功。


## 首次交付的本機驗證結果（歷史紀錄）

- `assembleDebug testDebugUnitTest lintDebug --console plain`：BUILD SUCCESSFUL。
- 18 個 Robolectric/JUnit 測試，0 failures、0 errors：EPUB 9、Room/repository 4、reader Intent/chooser 3、啟動/manifest 2。
- 診斷腳本 mock 測試 6/6 通過；沒有在測試中呼叫真實 adb。
- Activity 測試僅涵蓋本機 Android 11 模擬環境的建立／重建，不等於裝置上的旋轉、字級或電子紙品質驗收。
- 非匯出的 chooser receiver 使用 AndroidX 相容實作。Robolectric 測試先核對 merged manifest 有宣告並請求本 App 的 signature permission，再模擬 Android 安裝時自動授予它；沒有透過略過權限檢查來隱藏 manifest 缺漏。
- 初次 Lint 的 DiffUtilEquals 錯誤已以明確的 ShelfRow 資料類型及穩定 key 修正，沒有使用 baseline 或停用檢查。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，8,875,588 bytes，applicationId `org.kobostyle.launcher`，version `0.1.0`。
- SHA-256：`430B55E3224D697E69267B3F8F26132C7275174723D8EC29B9231FE9F724AE01`。
- APK 簽章驗證：`apksigner verify` exit 0（v2 debug 簽章）。沒有安裝到 Leaf2。
- 主機工具／相依套件雖已固定版本，仍有較新版本提示；沒有宣稱這是最新工具組。

簽章重現命令（此主機）：

```powershell
. .\scripts\use_local_tools.ps1
& .\.tools\android-sdk\build-tools\35.0.0\apksigner.bat verify --verbose .\app\build\outputs\apk\debug\app-debug.apk
Get-FileHash .\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
```

本機報告：`app/build/reports/tests/testDebugUnitTest/index.html`、`app/build/reports/lint-results-debug.html`。這些是產生物；在乾淨 checkout 執行上方命令即可重建。debug signing key 由各主機產生，因此不同主機的 APK hash 不必相同。

最終 Lint：`0 errors, 16 warnings`。10 項是固定相依版本有更新的提示，6 項是 UseKtx 寫法建議；未停用規則。因前一次增量報告仍引用舊 manifest，額外執行 `.\gradlew.bat lintDebug --rerun-tasks --console plain` 更新報告，結果 BUILD SUCCESSFUL。APK SHA-256 未改變。


## 後續實機驗收

使用者已要求進行驗收，APK 已安裝並完成基本啟動／設定入口驗證。上方「最終本機驗證」的未安裝敘述是前一輪歷史狀態。完整實測證據、OEM 初裝停用行為與進行中項目見 [ACCEPTANCE_2026-11-15.md](ACCEPTANCE_2026-11-15.md)。


## 第一輪核心驗收修正版（歷史紀錄）

以上「最終本機驗證」段落保留首次交付歷史資訊。本輪修正加密 PDF 分類及橫向書庫按鈕排版後，已重建並安裝新版：

- APK SHA-256：`57A880C74F8C28469668F8EA2BBC58B285B537A7343D54A3EB938842E3E7C8E2`；8,875,588 bytes；debug 簽章驗證 exit 0。
- `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console plain`：BUILD SUCCESSFUL；18/18 JVM 測試通過，Lint 0 errors / 19 warnings（版本與 KTX 寫法建議）。
- 最終 23 文件（20 正常 + 3 異常）實機回歸 `OK (2 tests)`：旋轉／書架可用高度、真實 SAF 掃描、錯誤分類、灰階、去重、取消。
- 100 文件 + 3 異常的完整實機 suite 曾通過 `OK (2 tests)`。
- 1000 文件 + 3 異常的 scanner/cancel 測試通過；同次 suite 的 rotation 子測試因套件被停用而失敗，不將整次 suite 宣稱為全數通過。後續 rotation 單獨重驗 `OK (1 test)`，最終版本又以 23 文件完整回歸通過。
- 執行 instrumentation 前需先啟用「本 App 與本測試 APK」；不能在測試進行中變更自身套件 enabled state，否則可能終止測試程序。已提供具結果判斷的 `scripts/run_device_acceptance.ps1`，不能只看 adb 的程序 exit code。
- 已校驗雜湊並清除 980 個額外壓力測試文件；主要書庫保留 20 本正常合成書與 3 個異常樣本，沒有被壓力測試索引污染。

最終 23 文件實機回歸（需已安裝本 App/test APK，且使用者已授權合成 valid 資料夾）：

```powershell
. .\scripts\use_local_tools.ps1
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console plain
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\deploy_debug.ps1
adb install -r .\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run_device_acceptance.ps1 -ExpectedBooks 23
```

上述為首次核心驗收版本；後續補測已完成實體 microSD 拔插、真實撤銷／恢復 SAF 授權、字級 1.0／1.3、1000 列 UI 取樣、標準翻頁事件、60 秒無 App 重繪、一次解凍後重開機，以及本次實體按鍵事件辨識與可選捲動對應。最新 APK 雜湊、精確命令與結果以 [補充驗收報告](EXTENDED_ACCEPTANCE_2026-11-15.md) 為準。隔夜待機耗電、OEM 無 handler／拒絕 URI 邊界與 P5 仍未完成，不能將所有項目宣稱全數通過。

## 2026-11-17 繁體注音

同文配置完成後，實際注音組字、Enter 提交與兩輪「驗收」搜尋皆顯示 11 本結果；原廠桌面往返後仍可用。Gboard 本身凍結問題仍未修復，同文重開機／隔夜持續性尚未測。詳 [輸入法報告](INPUT_METHOD_2026-11-17.md)。

## 2026-11-17 掃描完成文案（歷史版本）

依要求移除完成提示的失敗數，中英文同步調整，掃描中與來源錯誤提示保留。

```powershell
. .\scripts\use_local_tools.ps1
.\gradlew.bat assembleDebug lintDebug --console plain
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/deploy_debug.ps1
adb shell am start -W -n org.kobostyle.launcher/.ui.MainActivity
```

BUILD SUCCESSFUL；Lint 0 errors／32 warnings，簽章驗證、安裝與冷啟動通過。以 aapt2 核對打包文案；此輪未重掃完整書庫。歷史 APK 為 9,010,636 bytes，SHA256 `093284D857B1208677195D1F6C7D4E73206C8FC70469E9AEBE5BA660360DC9CA`。

## 2026-11-17 大型 microSD Books 修復

舊上限與超限誤判授權造成漏掃；已修正為有界 50,000 項、區分來源錯誤並重用可驗證的中繼資料。35 項 JVM 測試通過，Lint 0 errors／32 warnings，唯讀來源診斷 OK (1 test)。

實際 Books 8,141 本全部入庫，補入 4,049 本；全書庫 8,166 本，四個來源皆 ok，原有開啟紀錄保留。162 本採中繼資料回退。完整命令、雜湊、失敗嘗試及限制見 [掃描修復紀錄](SCAN_FIX_2026-11-17.md)。
