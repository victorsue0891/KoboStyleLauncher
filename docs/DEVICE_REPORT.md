# P0 裝置與環境報告

2026-11-25 V4 開發：使用者明確表示 Leaf2 沒有連接電腦，本輪未呼叫 ADB、診斷、安裝或真實 API。已確認的是 Windows 主機 106 項 JVM／Robolectric、2 項 Python 測試及 APK 建置通過；13,618／20,000 本皆為合成資料，不能當作本次實際書庫數量。新版 Room migration、bundled SQLite、SAF 知識包、AI 問答、E Ink 與 RAM／P95 尚待實機驗收，詳 [V4 紀錄](v4/IMPLEMENTATION_2026-11-25.md)。下方連線、HOME 與 API 成功皆為歷史觀察。

2026-11-20 最新館藏範圍更新已安裝：實際 Room 索引 8,166 本，排除 25 本既有驗收書後有 8,141 本參與推薦。本次實機核對 20 本文件可存取，離線及裝置內模擬 AI 各選出 3 本既有書籍；正式畫面已確認。書籍、來源及開啟時間與修改前逐欄一致，API 呼叫計數未增加。詳見 [實際館藏驗收](LIBRARIAN_DEVICE_2026-11-20.md)。下方為較早的驗收紀錄。

2026-11-20 後續接機：使用者已連接授權 Leaf2，重新執行唯讀診斷，Android 11／API 30、1264×1680／300 dpi，0 個診斷失敗。KoboStyle 仍是預設 HOME，原廠 HOME 可解析；同文輸入法未變更。覆蓋升級後資料逐列保留，Keystore 與喵館員／閱讀器實機驗收見 [最新紀錄](LIBRARIAN_DEVICE_2026-11-20.md)。下方同日「未連線」敘述屬接機前狀態。

2026-11-20 喵館員開發：使用者明確告知 Leaf2 尚未連接。本輪僅確認主機 JDK 17、SDK 36、build-tools 35.0.0、Gradle 8.13 與 adb 工具，完成本機建置及 mock 測試；沒有執行裝置診斷或任何 ADB 命令。新功能尚未證實可在 Leaf2 執行，HOME／閱讀器／Keystore 目前狀態待接機觀察。詳見 [喵館員待實測清單](LIBRARIAN_2026-11-20.md)。下方實機數據保留為歷史觀察。

2026-11-18 分類功能開發前重新執行唯讀診斷：一台已授權 Leaf2，Android 11／API 30，1264×1680／300 dpi，0 個診斷命令失敗；預設 HOME 為 KoboStyle HomeEntry，原廠 HOME 仍可解析。升級後再次確認 HOME 與同文輸入法未變更。分類與 UI 實機驗證詳見 [分類紀錄](CATEGORIES_2026-11-18.md)。

2026-11-16 後續使用者完成手動復原示範並核准，已透過 HOME_SETTINGS 設定 `org.kobostyle.launcher/.ui.HomeEntry` 為預設桌面。原廠 `ContentBrowser` 可由系統 UI 恢復；重開機保留預設，但 BOOX 仍先顯示原廠介面。刻意崩潰後系統清除預設、顯示兩桌面選擇器，已實測恢復原廠並再設回 KoboStyle。下方 P0 的 HOME 觀察為歷史狀態，最新證據見 [HOME 設定紀錄](HOME_SETUP_2026-11-16.md)。

日期：2026-11-15（Asia/Taipei）。使用者已確認 USB 連線並授權。

## 實際觀察

唯一裝置狀態為 `device`。僅透過 `scripts/diagnose_device.ps1` 執行唯讀裝置命令；本次 0 個命令失敗。序號未寫入報告。

| 項目 | 結果 | 證據 | 判定 |
|---|---|---|---|
| 型號／製造商 | Leaf2 / ONYX | getprop ro.product.model / manufacturer | 已確認 |
| Android / API | 11 / 30 | getprop ro.build.version.release / sdk | 已確認 |
| 韌體 | 2025-06-21_19-01_4.0.2-rel_0621_da686c4b1，build 10545 | getprop ro.build.fingerprint | 已確認；未記錄裝置識別碼 |
| 實體尺寸／密度 | 1264×1680 / 300 dpi | wm size / wm density | 已確認；未測旋轉或 App override |
| 目前 HOME | com.onyx/.StartupActivity | cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | 已確認 |
| HOME 候選 | 上述原廠 HOME、com.android.settings/.FallbackHome | cmd package query-activities（MAIN/HOME） | 已確認；FallbackHome 不代表可用復原桌面 |
| 閱讀器候選套件 | com.onyx.kreader | pm list packages（本機篩選）；後續 chooser UI | 已安裝；後續 UI 確認 Neo Reader V3.0 並實際開書 |
| EPUB content scheme handler | com.onyx.kreader/.ui.ReaderHomeActivity | query-activities VIEW/DEFAULT，content://org.kobostyle.probe/document/probe，application/epub+zip | 已確認 resolver 回應 |
| PDF content scheme handler | 同上 | 同上，application/pdf | 已確認 resolver 回應 |
| NeoReader 實際 SAF URI 讀取 | 合成 EPUB/PDF 可載入、翻頁，正常 Back 回書房 | P3 實機截圖及 UI hierarchy | 此韌體／externalstorage provider／測試文件已確認 |
| App UID 可見性／exported／URI grant 傳遞 | App 內能力查詢後實際開書成功 | PackageManager／ACTION_VIEW／ClipData 實機流程 | 已確認測試路徑；不推論其他 provider |
| HOME 選擇器／復原 | 未開啟、未變更 | 等待手動示範與 P5 核准 | 未確認 |
| 硬體翻頁鍵 | 後續人工依序按鍵取得 25／24，新增可選翻頁對應 | Window.Callback 實機觀察及對應事件測試 | 本次操作已確認；不推論所有方向／OEM 設定 |
| E Ink SDK | 未引入 | P4 no-op controller | 未確認 SDK 支援 |

P0 的合成 probe content URI 僅用來查詢 intent filter，不存在實際書籍、不授權、不呼叫 ACTION_VIEW 啟動。後續 P3 已依公開程序使用真實合成書籍與使用者授權的 SAF URI 測試。沒有讀取私有資料庫、停用原廠 App、變更 HOME 或系統設定。

## 主機初始環境

- 實際為 Windows 10 Pro x64，10.0.19045；規格的 Windows 11 是未成立的假設。
- PowerShell 5.1；Git 可執行，但此資料夾沒有 .git。
- adb 1.0.41 / platform-tools 37.0.0-14910828 可用。
- java、javac、gradle、sdkmanager 不在 PATH；JAVA_HOME / ANDROID_HOME / ANDROID_SDK_ROOT 空白。
- 一般 Android SDK 與 JDK 安裝路徑未找到工具。這不排除其他自訂路徑有工具。
- 初始沒有 Android source、Gradle wrapper 或 APK。
- 使用者已接受 Android SDK 授權，允許在專案 .tools 安裝並建置；不修改全域 PATH。

## P0 決策與風險

P0 已完成唯讀發現。先採原生 Views、minSdk 30、SAF 唯讀持續授權、Room、本 App 開啟事件及無動畫灰階介面。P1–P3 不提供 HOME intent filter 或選擇按鈕，不使用 BOOX 私有 SDK，也不硬編碼閱讀器元件。

主要未決風險：NeoReader 可能宣告支援卻拒絕特定 provider URI；記憶卡拔除／授權消失需要重新選取；沒有可驗證的閱讀進度 API；E Ink 實際殘影與 OEM HOME 限制只能在裝置驗收。P0 通過不代表 P1–P5 通過。

重現命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_device.ps1
```

## 主機補齊工具（同日）

已在專案 `.tools` 安裝 Temurin JDK 17.0.21+9、Gradle 8.13、Android SDK platform 36 / build-tools 35.0.0。Gradle 另外安裝專案 SDK 的 platform-tools 37.0.1；P0 實際使用的是原本 PATH 上的 adb 37.0.0。沒有用新的 adb 重跑或修改裝置。

`gradlew.bat`、`gradlew`、wrapper JAR 與帶 distributionSha256Sum 的 properties 已建立。主機工具初始缺項已排除；SDK 與 Gradle cache 不納入原始碼。實際建置／測試結果以 TEST_PLAN.md 為準。

## 後續驗收進度

同日使用者已授權 P1–P3 實機驗收，APK 已安裝。實測發現原廠 `com.onyx` 將新裝 App 停用（enabled=3）；僅啟用 `org.kobostyle.launcher` 後成功啟動，並確認 App 的系統設定入口與返回可用。未切換 HOME。完整命令、截圖解讀及未完成項目見 [ACCEPTANCE_2026-11-15.md](ACCEPTANCE_2026-11-15.md)。

後續又觀察到停用，某次 lastDisabledCaller 為 android；完整 OEM 政策未確認。使用者在原廠介面解除 KoboStyle 凍結後，未再使用 pm enable，兩次返回桌面再開及 force-stop 後冷啟動皆成功，enabled=0，HOME 不變。短期阻擋已解除；長期待機、重開機與更新後持續性未測。使用者目視確認文字清楚、殘影／刷新可接受；未引入 E Ink SDK。

## 同日補充驗收更新

後續已完成一次正常重開機：boot_id 改變、開機完成、原廠 HOME 不變、主 App 未重新啟用即可開啟；內部儲存與 microSD 的既有 SAF grant 仍可讀取。單一文件真實撤銷／恢復授權、microSD 實體拔插／恢復、字級 1.0／1.3、1000 筆 UI 取樣及 60 秒 App 無重繪已通過。記憶卡上的合成 PDF 已在 NeoReader 實際載入並返回書房。

標準 PAGE_UP／PAGE_DOWN 注入事件已實機驗證。使用者後續依序按上方、下方實體鍵，公開 Window.Callback 觀察到 25／24（音量減／加）；新增可選書房翻頁對應與反轉方向，實機注入相同鍵碼驗證捲動、保存設定與音量不變。未假設其他旋轉方向或 OEM 設定一致。未驗證隔夜耗電、多次更新／重開機循環或 BOOX E Ink SDK。最新證據與限制見 [補充驗收](EXTENDED_ACCEPTANCE_2026-11-15.md)。

## 2026-11-17 繁體注音

經使用者同意安裝同文，官方 Rime 注音已部署。實際注音組字、Enter 提交及兩輪搜尋「驗收」顯示 11 本結果通過，原廠桌面往返後仍可用。Gboard 自身凍結問題未修復；同文的重開機／隔夜持續性未測。詳 [輸入法報告](INPUT_METHOD_2026-11-17.md)。

## 2026-11-17 大型書庫掃描

修正 10,000 項上限與超限誤判授權問題，現在有界處理 50,000 項並保留既有狀態。35 項 JVM 測試通過，Lint 0 errors／32 warnings，唯讀來源診斷 OK (1 test)。實際 Books 8,141 本全數入庫、缺漏 0，補入 4,049 本；全書庫 8,166 本、四個來源皆 ok、開啟紀錄保留。162 本採中繼資料回退，不宣稱全部書籍已由閱讀器實際開啟。詳 [修復紀錄](SCAN_FIX_2026-11-17.md)。
