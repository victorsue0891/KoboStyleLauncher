# HOME 設定與復原 — 2026-11-16

使用者要求「請幫我修改系統讓這個 APP 成為預設桌面」。採 Android 公開 HOME 與設定頁流程；不修改 ROM、root、停用原廠桌面、寫系統設定值或 shell 強制指定預設桌面。

## 目前進度

- 已建置、安裝包含 HOME 支援的新 APK。
- HOME alias `.ui.HomeEntry` 在 manifest 預設停用。安裝、一般啟動及旋轉不會啟用它或開啟桌面選擇器。原有 MAIN/LAUNCHER 入口保留。
- 「更多 → 桌面與復原」可開啟已驗證並保存的原有 HOME，及 Android HOME_SETTINGS。使用者確認復原後，只啟用本 App 的 alias，隨後以系統 UI 選預設值。程式不直接修改預設活動。
- 主 Activity 使用 singleTask；新的 MAIN/HOME intent 回到書房首頁。當本 App 是預設 HOME 時，Back 從其他頁回到首頁，首頁 Back 留在首頁；一般 App 模式保持正常返回。
- 使用者已回覆「已完成復原示範，請繼續切換」。之後才從 App 確認啟用 HOME 入口，並在 Android「預設的主畫面應用程式」點選「KoboStyle 書房」。目前已設為預設，未使用 shell 指定 preferred activity。

## 實機證據

| 項目 | 觀察 |
|---|---|
| 連線 | `adb get-state` 回覆 device，無序號寫入本報告 |
| 僅更新 APK 後解析 HOME | `com.onyx/.StartupActivity`，沒有自動切換 |
| 僅更新 APK 後 HOME 候選 | 原廠 `com.onyx/.StartupActivity` 與系統 `com.android.settings/.FallbackHome`；當時 KoboStyle 尚不在啟用候選中 |
| 使用者示範並核准後，經系統 UI 切換 | `org.kobostyle.launcher/.ui.HomeEntry`，Home 鍵前景亦為該 Activity |
| Home／Back | 從「更多」按 Home 回首頁；「更多」按 Back 回首頁，首頁再按 Back 維持 KoboStyle |
| 恢復原廠預設並切回 | 系統 UI 選 ContentBrowser，HOME 解析回 `com.onyx/.StartupActivity`，Home 鍵進入原廠；再由相同系統頁選 KoboStyle，恢復為 `.ui.HomeEntry` |
| 刻意崩潰 | `adb shell am crash org.kobostyle.launcher`；ApplicationExitInfo 確認 reason=4 APP CRASH(EXCEPTION)。系統清除原本預設，HOME 變為 ResolverActivity，按 Home 出現兩桌面選擇器 |
| 崩潰後復原 | 從畫面選 ContentBrowser → 一律採用，成功回原廠並恢復其預設；未重新啟用主 App（enabled=0），一般 App 入口仍可冷啟動，再經系統 UI 設回 KoboStyle |
| 正常重開機 | `sys.boot_completed=1`，本機比較 boot_id 已改變；預設仍為 `org.kobostyle.launcher/.ui.HomeEntry`，主 App 仍正常啟用 |
| 開機前景限制 | BOOX 先啟動 `com.onyx/.StartupActivity`，接著顯示原廠 `.reader.main.ui.MainActivity`；約開機 75 秒及再等待 10 秒仍為原廠前景。按 Home 後才進入 KoboStyle。沒有把「預設保留」誤記為「開機立即顯示 KoboStyle」 |
| 重開機後讀書 | 點既有合成 `Acceptance-PDF-09`，等待 NeoReader 載入後看到「KoboStyle PDF 09 PAGE 1」及 1/2；Back 返回 `.ui.HomeEntry`，再次開書後按 Home 亦返回 `.ui.HomeEntry` |
| 最終狀態 | KoboStyle、BOOX 原廠桌面及 NeoReader 均 installed=true、enabled=0；保持 KoboStyle 預設，前景停在書房 |
| HOME_SETTINGS 解析 | `com.android.permissioncontroller/.role.ui.HomeSettingsActivity` |
| App 原有桌面入口 | 實際開啟後前景為 `com.onyx/.reader.main.ui.MainActivity`（原廠入口轉向）；未讀取原廠私人書庫 |
| App 預設桌面設定入口 | 實際前景 `com.android.permissioncontroller/.role.ui.DefaultAppActivity`，標題「預設的主畫面應用程式」，原廠選項 `ContentBrowser` |
| 復原辨識 | 程式依 PackageManager 實際 HOME 候選驗證並保存元件，每次使用再驗證；沒有硬編碼 com.onyx 元件。排除自己、系統設定 fallback、不可用與未匯出候選 |

## 使用者手動步驟（切換前）

1. 在 KoboStyle 點「更多 → 桌面與復原 → 開啟原有桌面」。
2. 在 BOOX 原廠「應用」重新開啟 KoboStyle。
3. 點「更多 → 桌面與復原 → 預設桌面設定／恢復原有桌面」，確認看得到 `ContentBrowser`。
4. 示範成功後回覆確認繼續切換，再使用「使用 KoboStyle 作為桌面 → 已確認復原，繼續」，在系統頁選 KoboStyle。

只開啟 ContentBrowser 並不恢復預設值；正式恢復需要在系統預設主畫面頁選 ContentBrowser。保持 BOOX 原廠套件啟用與安裝。ADB 備援及無法復原時的限制見 [RECOVERY.md](RECOVERY.md)。

## 建置與測試

```powershell
. .\scripts\use_local_tools.ps1
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -PageKeys
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run_device_acceptance.ps1 -ReaderUi
```

- 建置 `BUILD SUCCESSFUL in 24s`；兩個 APK 安裝 Success。
- 主機最終 27 tests，0 failures／errors；包含重複 HOME intent 回到首頁、singleTask manifest，以及成為預設後仍保留原有桌面辨識。補充命令 `.\gradlew.bat testDebugUnitTest lintDebug --console plain`，`BUILD SUCCESSFUL in 14s`。
- Lint 0 errors、32 warnings（未停用 lint 規則）。
- 翻頁鍵實機 `OK (1 test)`，6.702 秒，開關／反向／重建／音量保持通過。
- UI 實機 `OK (1 test)`，14.263 秒，搜尋、重建、書籍選單、字級與橫直向通過。
- 主機模擬驗證 HOME 初始停用、不自動開系統頁、缺少復原目標／系統設定時拒絕啟用、僅啟用自己的 alias、不把啟用候選當作已設預設。模擬測試不等同真實 HOME 選擇驗收。

APK：`app/build/outputs/apk/debug/app-debug.apk`，debug 簽章，9,010,640 bytes。
SHA-256：`6C60A97AF56DC42C932FED837C194E2FFE70F7F59C732DFC886AD0C859E58139`。
本機紀錄：`.tools/acceptance/home-build.log`、`home-final-host.log`、`home-keys.log`、`home-ui.log`。

唯讀重現命令：

```powershell
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell cmd package resolve-activity --brief -a android.settings.HOME_SETTINGS
```

## P5 故障與重開機驗證命令

以下只在上述復原示範及使用者核准後執行；崩潰指令只針對本 App。此測試故意終止桌面，會使系統重新要求選擇 HOME；不應拿來作日常操作。

```powershell
adb shell am crash org.kobostyle.launcher
adb shell dumpsys activity exit-info org.kobostyle.launcher
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell input keyevent 3
# 在畫面選 ContentBrowser → 一律採用，確認復原；再由系統設定選回 KoboStyle。
adb reboot
adb get-state
adb shell getprop sys.boot_completed
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell dumpsys activity activities
```

重開機前後只在本機比較 boot_id，不將識別值寫入報告。未讀取原廠書庫或私人資料庫，沒有停用／卸載系統套件。

## 已知限制與待完成項目

本次韌體在預設 HOME App 崩潰後會清除其預設選擇，顯示系統桌面選擇器。這是已觀察到的失效處理；不能宣稱崩潰後仍自動維持 KoboStyle 預設。使用者可選原廠 ContentBrowser 復原，再於系統設定重新指定 KoboStyle。

正常重開機會保留 KoboStyle 預設，但這版 BOOX 韌體仍先開原廠介面，需按一次 Home 才進入 KoboStyle。沒有修改 BOOX 開機流程或加入競速啟動／系統層繞過機制。

未驗證長期待機／OEM 長期凍結政策、多次重開機循環或其他韌體；不將本輪成功推論至所有裝置。此次只重驗一份既有合成 PDF；EPUB 的完整 P3 驗收屬先前紀錄，未冒稱本輪也重測。

最初開書立即擷取只見前一個書房畫面，未當作閱讀器載入成功；重新開書等待 8 秒後才核對實際 PDF 頁面及 ReaderTab1Activity。原始裝置日期與主機日期不同，未更改時鐘。

實機證據：[預設桌面首頁](acceptance-images/20261116-home-selected.png)、[重開機後 NeoReader 實際頁面](acceptance-images/20261116-home-reader-after-reboot.png)。未在此輪修改 APK，沿用上列已驗證雜湊版本。
