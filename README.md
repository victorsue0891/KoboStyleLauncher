# KoboStyle Launcher for BOOX Leaf2

讓 BOOX Leaf2 變成更簡潔的電子紙書房。這個以 Codex 協助開發的 Kotlin 專案提供 Kobo 風格首頁、封面書庫、最近開啟與書名／作者搜尋，同時保留 BOOX NeoReader 和原廠桌面。

以一般 App 啟動，透過 Android Storage Access Framework（SAF）加入內部儲存或 microSD 的 EPUB／PDF；以 Room 管理索引與自己的開啟紀錄，再透過 ACTION_VIEW 將書籍交給可處理的閱讀器。繁體中文為預設介面，另有英文資源。

這是獨立專案，非 Kobo 或 BOOX 官方產品，沒有 Kobo 商店、雲端同步或 NeoReader 閱讀進度整合。

<img src="docs/acceptance-images/20261116-kobo-home-preview.png" alt="KoboStyle 首頁，使用合成測試書籍" width="420">

上圖為 Leaf2 上使用合成書籍的介面驗收畫面。實際書庫畫面與橫向配置見 [UI 更新與驗收](docs/UI_REDESIGN_2026-11-16.md)。原始設計參考圖僅留在開發者本機，不納入儲存庫。

原始碼位於 `app/src/main/`，JVM 測試位於 `app/src/test/`，實機測試位於 `app/src/androidTest/`；建置與驗收腳本位於 `scripts/`。功能變更見 [更新紀錄](CHANGELOG.md)，完整驗收與歷史資料見 [文件導覽](docs/README.md)。

## 目前功能

- **書房與分類**：黑白首頁、可調欄數書封、搜尋、最近開啟、可選實體鍵翻頁；依 `[類別]-書名-作者名.epub/pdf` 提供 11 個分類、作者瀏覽及未分類書籍。見 [分類功能](docs/CATEGORIES_2026-11-18.md)。
- **喵館員**：第三分頁由墨喵提供離線對話選書與主題推薦；應用程式移至「更多」。推薦取自已匯入館藏，排除已知驗收書，可選擇啟用 OpenAI，最多傳送 20 本候選書目及當次需求。見 [館員驗收](docs/LIBRARIAN_DEVICE_2026-11-20.md)。
- **V4 書靈**：免費建立只依書目的 Lite 人格；透過 SAF 匯入單本知識包後，可依手動已讀章節查看本機片段，或選用附引用的 AI 問答與歡迎語精修。不傳書籍全文、不同步 NeoReader 進度。見 [操作方式](docs/v4/IMPLEMENTATION_2026-11-25.md) 與 [知識包格式](docs/v4/KNOWLEDGE_PACK.md)。
- **大書庫與費用控制**：V4 每頁最多 50 本、中文搜尋、批次索引與手動續掃；共用 AI 請求控制、費用預覽、日／月預算、用量紀錄及緊急停止，不自動重試。未知扣款保留估計額度；本機紀錄不是供應商帳單。
- **繁體注音**：已使用另外安裝的同文輸入法驗證；鍵盤與字典不包含在本 App。見 [輸入法紀錄](docs/INPUT_METHOD_2026-11-17.md)。

V4 與新書靈付費功能預設關閉，從「更多 → 書靈與費用設定」開啟。AI 需要使用者啟用、在裝置輸入自己的 Key 並主動觸發；V4 開啟時，每次新付費請求另確認費用。Key 以 Android Keystore 加密。停用 V4 可回到原介面並保留資料，但主書庫已升級至 schema 3，**不能直接安裝僅支援舊 schema 的 APK 降版**，詳 [復原方式](docs/RECOVERY.md)。

## 已驗證範圍（2026-11-25）

V4 P1–P6 本輪開發與驗收已完成：實際 13,618 本書與來源資料升級保留，合成知識包匯入、兩次真實 AI、功能開關及程序重啟保存通過，使用者確認電子紙顯示／刷新與上下實體鍵可接受。106 項 JVM／Robolectric、2 項 Python 測試通過；八項實機驗收分別通過，後續七項韌體回歸亦通過（兩組有重疊）。Lint 0 errors／61 warnings。

首次投影仍可能需要數分鐘；索引完成後十次程序冷啟動首畫面 P95 為 2.01 秒，不等於首次索引或所有封面載入時間。詳 [V4 實機報告與截圖](docs/v4/DEVICE_ACCEPTANCE_2026-11-25.md)。韌體只確認 **Leaf2 BOOX 4.0.2／Android 11／build 10545**；其他版本、機型與長期待機耗電尚未驗證，見 [韌體相容性報告](docs/FIRMWARE_COMPATIBILITY_2026-11-25.md)。

保留 BOOX NeoReader 與原廠桌面，沒有 root、ROM 修改、其他 App 私有資料庫存取或全檔案權限。HOME 入口預設停用，安裝／一般啟動不變更桌面。原開發裝置已在使用者手動復原示範與核准後，透過系統 UI 設為預設；歷史測試發現 BOOX 開機仍先顯示原廠介面，按 Home 才進入 KoboStyle，崩潰後可能重新要求選擇桌面。新裝置仍須先驗證復原，見 [HOME 紀錄](docs/HOME_SETUP_2026-11-16.md)。

## 建置（Windows PowerShell）

先安裝 Git、JDK 17、Android SDK platform 36／build-tools 35.0.0／platform-tools，並自行接受 Android SDK 授權。設定 `JAVA_HOME` 指向 JDK、`ANDROID_HOME` 指向 SDK，讓 `adb` 可由 PATH 執行，再執行：

```powershell
git clone https://github.com/victorsue0891/KoboStyleLauncher.git
cd KoboStyleLauncher
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --console plain
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test_diagnose_device.ps1
```

首次需連網下載建置相依套件；書庫、離線館員、Lite 書靈與已匯入的允許片段可離線使用。選用 OpenAI 推薦／問答／精修需網路及自行提供 API Key。Gradle wrapper JAR 與啟動腳本已納入專案，Gradle 發行包以 SHA-256 驗證。`.tools` 不隨儲存庫提供；只有已自行建立該目錄結構的開發環境才使用 `. .\scripts\use_local_tools.ps1`，它只調整當前 PowerShell 的環境變數。

固定版本：AGP 8.13.2、Gradle 8.13、Kotlin 2.2.21、KSP 2.2.21-2.0.4、Room 2.8.4、minSdk 30、compileSdk/targetSdk 36。採 API 36 的穩定相容工具組，沒有為繞過權限而降低 target。AGP/JDK/Gradle 相容性依據：[Android 官方版本說明](https://developer.android.com/build/releases/agp-8-13-0-release-notes)。

在 Android 11 上，套件查詢受 `<queries>` 限制，SAF 選取根目錄亦有限制。Android 15/16 上處理 system bars insets，沒有強制方向或 HOME role；其他 Android 版本尚無實機測試。

APK 路徑：`app/build/outputs/apk/debug/app-debug.apk`。
Application ID：`org.kobostyle.launcher`。debug 簽章只供開發測試。

## 裝置測試

使用者連線且授權後，P0 唯讀命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_device.ps1
```

使用者已授權並完成實機安裝；以下命令可重現安裝：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\deploy_debug.ps1
```

從 BOOX Apps 手動啟動，不變更 HOME。開書前閱讀 [P3 逐步程序](docs/P3_PROCEDURE.md)；P5 必須有 [手動復原示範](docs/RECOVERY.md) 與明確核准。

## 使用方式與限制

- 「我的書籍」→ 加入資料夾或加入書籍。只保留持續「讀取」授權。來源不支援持續授權時提示重新選取。
- 點書封／書名開書；長按封面或書籍旁「⋯」開啟選單，可選「其他閱讀器」。頂端放大鏡搜尋本機索引中的書名／作者，支援「顯示全部書籍」。首頁僅顯示實際開啟日期，沒有虛構閱讀百分比。
- 頂端時鐘、電量與 Wi-Fi 於回到前景時更新，避免定時重繪。太陽圖示開啟系統設定，並非未經驗證的 BOOX 亮度控制。僅在本 App 隱藏系統狀態列，可由頂端滑出；不更改全域系統設定。
- 使用者觸發單一 IO coroutine 掃描，可以取消；旋轉保留 ViewModel。App process 結束時掃描停止，重開後可手動續掃；沒有常駐或週期性工作。
- 原始書籍不修改。Room 以 provider authority + document ID 去重，儲存來源 URI、大小、修改時間、作者、封面、掃描狀態及自己的開啟事件。重新掃描保留開啟紀錄。
- EPUB 支援 container → OPF、EPUB 2 cover meta、EPUB 3 cover-image。單一 archive 上限 128 MiB、4096 entries、中央目錄 4 MiB（開啟 ZIP 前檢查，拒絕 ZIP64／分卷）、宣告展開總量 256 MiB、XML 1 MiB、封面 4 MiB、200 倍壓縮比例、XML 深度 64。SVG 封面或不支援的格式使用預設圖示。這些上限可能拒絕部分合法大書。
- PDF 使用 PdfRenderer，僅渲染第一頁、檔案大小上限 256 MiB，縮圖最多 240×360。作者不推測，標題採 SAF 檔名；損壞、加密或不支援 seek 的 provider 回退預設封面。原生 PDF renderer 的所有惡意輸入風險不能只靠單元測試排除。
- 封面灰階、降採樣，磁碟 cache 32 MiB。cache 被系統清除或淘汰時顯示預設圖示；正常且未變更的中繼資料會重用，重掃不保證重建被淘汰的縮圖。
- 每次掃描最多 50000 個文件／資料夾、32 層，重複 document ID 不重訪。超限會顯示縮小來源的提示並保留既有書籍狀態，不當作授權失效；一般 provider 查詢錯誤提示重新掃描。provider 本身不回應時取消可能延遲。來源授權有效、檔案大小／修改時間相同的正常書籍會重用已儲存的中繼資料，減少重掃時間。
- 授權失效、來源遺失或 SD 拔除會顯示不可用／重新授權。沒有自動修復外部權限、複製書籍或清除原始檔案。
- 「上次開啟」是送出 intent 或 chooser 選定處理器的時間，沒有取得閱讀進度或成功載入證明。取消 chooser 不記錄；App 在 chooser 期間被系統銷毀可能遺失事件。
- 閱讀器 capability 查詢不等於實際相容性。沒有硬編碼 NeoReader 元件；預設處理器失敗時可按「其他閱讀器」。沒有 handler 時顯示錯誤。
- 「更多 → 應用程式」只顯示 PackageManager 可見的啟動活動；不是所有安裝套件清單。Wi-Fi／電量只在返回前景時取樣，不讀 SSID、不持續輪詢。
- 首頁／書庫支援標準 Page Up／Page Down。Leaf2 本次實體按鍵確認送出音量減／加事件；可在設定開啟「實體鍵翻頁」並切換一般／反向，立即移動約 90% 可視高度。新安裝預設關閉，不更改 BOOX 全域按鍵或 NeoReader 設定。
- E Ink controller 為 no-op；不宣稱支援 BOOX SDK 或全螢幕刷新。實體按鍵以本次觀察到的標準 Android 音量事件實作可選對應。XML theme + 原生 Views/RecyclerView、關閉 item animator／overscroll／ripple；長期殘影與效能統計仍待量測。

設計依據：[SAF 官方說明](https://developer.android.com/training/data-storage/shared/documents-files)、[Room 官方說明](https://developer.android.com/jetpack/androidx/releases/room)。
