# 補充實機驗收 — 2026-11-15

延續使用者「請繼續實作測試未完成的部分」。保留原廠 HOME 與 NeoReader；P5 未核准／未執行。本報告區分實機觀察、主機模擬與尚未完成的耐久測試。

## 單一文件與真實授權撤銷

使用者透過 ACTION_OPEN_DOCUMENT 選取 microSD 上專用合成 `KoboStyleCardTest-20261115/Card-Test.pdf`。測試使用獨立 in-memory Room DB，實際讀取該 URI、產生 PDF 封面，再僅釋放這份文件的持續讀取授權；沒有釋放內部儲存書庫或其他文件的授權。

結果 `OK (1 test)`：`SINGLE_DOCUMENT import=PASS grantRelease=PASS readDenied=PASS indexPreserved=PASS`。撤銷後 persisted grant 消失、Android 拒絕讀取、來源為 reauthorize、書籍為 unavailable、既有開啟時間保留。一般書庫 UI 也顯示「授權來源：2；需要重新授權：1」。使用者重新選取相同 PDF 後恢復為 0 個需重新授權來源。

## microSD 實體拔插

專用資料夾含 1 EPUB、1 PDF。使用者另外授權該子資料夾，未授權記憶卡根目錄；磁碟識別碼未納入報告。

| 階段 | 實機結果 |
|---|---|
| 拔卡前 | 2 本、0 失敗，封面可產生，`OK (1 test)` |
| 使用者實體拔出後 | 0 本、1 來源失敗；2 筆索引均標為 unavailable，時間保留，`OK (1 test)` |
| 使用者插回後 | 初次回報系統未辨識；隨後唯讀查詢顯示 mounted，2 本恢復讀取、0 失敗，`OK (1 test)` |

未執行 adb mount、格式化、修復或系統設定寫入。插回後沒有重新授權即可讀取。主 App 重掃後顯示「授權來源：3；需要重新授權：0」「已處理 26 本，失敗 3 項」：包含原先 23 文件、單一 PDF 來源與包含同一 PDF 的 2 本資料夾；處理次數不等於不重複書籍數。3 個失敗是原先刻意製造的異常文件。

## 閱讀器失敗分支（主機模擬）

新增 Robolectric 測試：PDF handler 不匹配 EPUB MIME、無 handler、不支援 MIME、不匯出／停用／需未授予權限／本 App 的處理器排除、預設處理器在發送時拋出 SecurityException 或 ActivityNotFoundException。驗證安全 chooser fallback 最多一次，chooser 也失敗則回傳錯誤，不寫入假的開啟事件。

主機 21 個測試通過。這些是 Android 11 模擬結果，不是假稱已在實機停用 NeoReader 或造出 OEM URI 拒絕。沒有變更原廠閱讀器或預設處理器。

最初測試以 Robolectric 的直接 resolver 註冊製造 PDF handler，該捷徑未依 MIME 過濾；已改成註冊 Activity 與真正 IntentFilter，驗證 PDF 能匹配、EPUB 不匹配。測試假設錯誤及修正留在本機日誌，不以刪除斷言處理。

## 隔離測試介面

僅 debug build 含不匯出的 AcceptanceActivity。測試透過記憶體內 repository 注入相同 MainActivity 與相同 RecyclerView，沒有外部 intent 參數能指定資料庫或字級。正式入口仍是原本 MAIN/LAUNCHER，release 不包含此 Activity。

新增字級 1.0／1.3、橫直向、1000 筆長繁體中文書名與預設封面的列表測試；透過 Activity context 覆寫字級，沒有改系統字級。此測試在列表全範圍每 25 列雙向取樣，檢查綁定內容與回收，不代表長時間連續滑動或實體殘影量測。

初次列表測試在末列對齊斷言失敗：RecyclerView 到底會保留前一列以填滿畫面，故改以末列可見判斷，不更改產品捲動行為。測試 APK 仍可能被 OEM 凍結；runner 只啟用 instrumentation 套件，主 App 若被停用就中止，避免掩蓋解凍後的回歸。

第一次完整跑完列表與 60 秒靜置後，因測試套件被停用，ActivityScenario 收尾啟動該套件 EmptyActivity 失敗。已改成直接結束本 App 的測試 Activity、等待 DESTROYED 再關閉 scenario；沒有在 instrumentation 執行中變更套件狀態。先前失敗 suite 不計為通過。

新增標準 Android PAGE_UP／PAGE_DOWN 的前景書房捲動處理：首頁／書庫移動約 90% 可視高度、不開動畫、不攔截音量鍵或全域按鍵。實體按鍵觀察的首輪在 120 秒內沒有收到事件，且尚無使用者完成回覆；不能據此推論 BOOX 實體鍵碼或支援情況。

新增實機按鍵斷言曾抓到 Activity.onKeyDown 路徑未移動書架；改由書房內容 ViewGroup 的公開 dispatchKeyEvent 接收事件，避免先被子元件處理。沒有覆寫 AndroidX 標記為 RestrictedApi 的入口，也沒有停用 Lint 規則。

加入單一文件 grant 後，舊核心測試對所有 externalstorage grant 直接呼叫 getTreeDocumentId 而失敗；已先檢查 isTreeUri，核心回歸重新通過 `OK (2 tests)`。這是測試的來源篩選修正，沒有擴大產品掃描授權。

## 重現命令

```powershell
. .\scripts\use_local_tools.ps1
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -ExpectedBooks 23
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -Extended
```

以下必須配合使用者實際 SAF 選取與實體拔插，不能當作無人值守 suite。撤銷測試必須在資料夾授權前執行，且只匹配上述專用 PDF；之後需手動重新選取恢復授權。

```powershell
# 首次建立兩本合成書；若專用資料夾已存在，腳本拒絕覆寫：
python scripts/create_card_fixtures.py --push
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -RevokeSyntheticDocument
# 重新選取 PDF，並授權測試資料夾後：
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -CardState mounted
# 使用者安全退出並拔卡後：
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -CardState missing
# 使用者插回、系統辨識後：
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -CardState mounted
# 僅在使用者準備好依序按兩個實體翻頁鍵時：
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -ManualKeys
```

精確執行日誌保留於本機 `.tools/acceptance`；本報告不附裝置序號、記憶卡識別碼或私人書名。

## 前一輪修正版結果（實體鍵補測前）

- APK 已建置、安裝及驗證 v2 debug 簽章；8,950,380 bytes，SHA-256 `0F2B2E4F59A1E92BF95B2362441A2C7706ED8D023CB9C2A826581E5EFA6C3116`。
- 主機 21/21 測試通過。Lint 0 errors／20 warnings：13 個版本提示、6 個 KTX 寫法建議、1 個 debug 測試用靜態 repository 參照警告。測試參照在 finally 清除，正常使用為 null；release 不含測試 Activity。沒有以全域停用規則隱藏警告。
- 最新擴充實機 suite `OK (1 test)`，106.766 秒；四種字級／方向組合的 1000 筆列表雙向取樣全部通過。取樣各耗時 7.184、6.825、5.656、6.384 秒，僅此合成資料與測試程序，不是一般操作效能保證。
- 以 instrumentation 注入標準 PAGE_DOWN／PAGE_UP，在字級 1.0 與 1.3 都確認捲動與回到頂端。這不是實體按鍵確認。
- 靜置 60.025 秒，ViewTreeObserver.OnDrawListener 累計 0 次。這是 App 繪製觀察，沒有推論電子紙面板刷新或耗電；USB 連線供電下不能由電量不變推導低耗電。

## 一般模式重開機

使用一般 `adb reboot` 重開 Android；沒有改 HOME、系統設定或執行 recovery／bootloader 指令。重開前後讀取 boot_id 確認確實重啟，等待 sys.boot_completed=1，再核對原廠 HOME 與主 App 套件狀態。沒有對主 App 執行 pm enable。

結果：`REBOOT changed=PASS completed=PASS homeUnchanged=PASS appLaunch=PASS`。重開後核心實機回歸 `OK (2 tests)`（18.808 秒）、microSD 讀取 `OK (1 test)`（1.539 秒）；既有內部儲存與記憶卡 SAF grant 保留，無須重新授權。本結果不等於 P5 HOME 切換／復原演練。

接續從一般書房開啟記憶卡 `Card-Test.pdf`，NeoReader 實際顯示 `KoboStyle PDF 101 PAGE 1` 與頁碼 1/2，系統 Back 回到 KoboStyle MainActivity。[合成 PDF 載入證據](acceptance-images/24-card-reader-after-reboot.png)。未將一次點選下一頁當作翻頁成功證據；此輪補測確認的是記憶卡 URI 載入與正常返回。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify_manual_reboot.ps1 -RecordBaseline
adb reboot
# 等待正常開機完成後：
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify_manual_reboot.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -ExpectedBooks 23
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -CardState mounted
```

## 仍未完成的範圍

- 實體翻頁鍵已於下方後續補測確認；不同握持方向／OEM 按鍵設定仍可能改變對應，可使用 App 的方向切換。
- 隔夜待機、未接 USB 的耗電、長期殘影與多次重開機／更新循環；60 秒無繪製及一次重開機不能替代。
- OEM 實際無 handler／閱讀器拒絕 URI／閱讀器升級分支：目前以主機故障模擬驗證，未停用或移除原廠閱讀器製造情境。
- P5 HOME 選擇與復原：未核准，未執行；本 APK 仍沒有 HOME intent filter。

## 實體翻頁鍵後續補測

使用者依序按上方、下方實體鍵。最初的內容 ViewGroup 探測未收到事件，不等於硬體無作用；改用公開 Window.Callback，在預設視窗處理前觀察後，取得 `PHYSICAL_KEYS codes=[25, 24]`，`OK (1 test)`，32.379 秒。依本次使用者的操作順序，分別為 Android KEYCODE_VOLUME_DOWN 與 KEYCODE_VOLUME_UP；不假設其他旋轉方向或 OEM 設定都相同。沒有以 ADB 注入冒充這次人工按鍵。

新增「實體鍵翻頁：開啟／關閉」與「翻頁鍵方向：一般／反向」設定。新安裝預設關閉；開啟後僅在本 App 的首頁／書庫處理上述音量鍵，預設音量加往下一畫面、音量減往上一畫面，可反轉。位移約可視高度 90%，沒有平滑動畫；長按重複事件不連續翻動。系統音量、全域按鍵對應、原廠 HOME 與 NeoReader 設定不被寫入。

`PageKeySettingsTest` 使用獨立測試偏好及 in-memory Room DB，實機注入已觀察到的 24／25 事件。結果 `OK (1 test)`，5.175 秒：開關、方向反轉、Activity 重建後保存、停用後不處理、設定頁不處理皆通過；開啟功能時，音樂／鈴聲／通知／鬧鐘音量在翻頁前後相同。關閉情境只檢查事件不被接管，不發送會改變系統音量的實體事件。

因此分別確認了「人工實體鍵到達視窗的實際鍵碼」及「相同鍵碼在 App 的捲動與設定行為」，沒有把注入事件稱為人工捲動觀察。

```powershell
. .\scripts\use_local_tools.ps1
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# 使用者就緒後，依序按兩個實體鍵；不可用 input keyevent 代替：
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -ManualKeys
# 可自動執行的設定、捲動、方向及音量不變測試：
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -PageKeys
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -Extended
```

本次 APK：8,959,164 bytes；SHA-256 `AFE12DEC02934A53918EE727913E9511F6B03322580DD58C565ACAC6B6C7F4B0`。主機 21/21 測試通過；Lint 0 errors／24 warnings（13 版本、10 KTX 寫法、1 debug 測試參照），未停用規則。

最後完整擴充回歸 `OK (1 test)`，107.523 秒，包含字級 1.0／1.3、橫直向、1000 筆列表、標準按鍵及 60 秒無重繪。首輪回歸在 PAGE_DOWN 斷言失敗；排查測試僅等待 Looper idle，沒有等待 RecyclerView 已要求的 frame layout，補上排版完成／視窗焦點／回到第 0 列的明確就緒檢查後重跑通過，保留原有捲動斷言。

已在此 Leaf2 的一般書房 UI 開啟「實體鍵翻頁」，方向為「一般」。force-stop 僅本 App 再冷啟動後，設定頁仍顯示開啟／一般；最後返回書庫。新安裝的其他裝置仍預設關閉。原廠 HOME 最後仍為 `com.onyx/.StartupActivity`，P5 未執行。
