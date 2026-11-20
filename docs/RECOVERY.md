# Restore BOOX HOME — safe recovery

2026-11-20 導覽更新：「應用程式」移到「更多」，桌面復原仍位於「更多 → 桌面與復原」。喵館員異常可先在「更多 → 喵館員 AI 設定」停用 AI；一般書庫與開書不依賴 API。此次只在主機開發，未重新切換或驗證 Leaf2 HOME。金鑰刪除／程式回退細節見 [喵館員紀錄](LIBRARIAN_2026-11-20.md)。

## 2026-11-16：新增桌面與復原入口

使用者已要求設定 KoboStyle 為預設桌面。新版在「更多 → 桌面與復原」提供下列入口，安裝本身不切換桌面；實際進度見 [HOME 設定紀錄](HOME_SETUP_2026-11-16.md)。

使用者後續已完成手動示範並明確核准，現在 Leaf2 預設為 KoboStyle。系統 UI 恢復 ContentBrowser 並切回已實測；一次正常重開機保留 KoboStyle 預設，但 BOOX 會先顯示原廠介面，按 Home 即進入書房。

**若書房崩潰後看到「選取主畫面應用程式」：**這是本次故障注入已實測的系統行為。要復原原廠桌面，選 **ContentBrowser → 一律採用**；原廠桌面會重新成為預設。KoboStyle 仍保留為一般 App，可從原廠應用頁重新開啟。若再次選 KoboStyle，請先確認 App 能正常啟動。

1. 「開啟原有桌面」：啟動安裝後從 PackageManager 驗證並保存的原有 HOME。本機基準為 `com.onyx/.StartupActivity`，程式沒有硬編碼該名稱；每次啟動仍確認候選可用。這個動作只是開啟桌面，不更改預設值。
2. 「預設桌面設定／恢復原有桌面」：開啟 Android 的 HOME_SETTINGS。要恢復預設值，在該頁選原有桌面。
3. 「使用 KoboStyle 作為桌面」：先要求確認已測試復原，再只啟用本 App 的 HOME alias 並開啟系統設定，仍由系統 UI 選擇預設桌面。找不到已驗證原有 HOME 或設定頁時不啟用。

手動示範：從 KoboStyle 開啟原有桌面，在原廠應用頁重新開啟 KoboStyle，再開啟上述預設桌面設定並確認原有桌面選項。這項示範完成前，不選 KoboStyle 為預設。切換後需實測 Home 鍵、恢復原有桌面／切回、重開機與程序故障復原；未完成項目不可宣稱通過。

以下為原有備援程序與歷史紀錄。

1. Before any change, record the current HOME component with `adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME`. Preserve its output locally.
2. Do not disable/uninstall the BOOX Launcher, NeoReader, Settings or other stock components.
3. If KoboStyle crashes or navigation is broken, connect USB ADB (already authorized) and open Android Home settings if supported:
   `adb shell am start -a android.settings.HOME_SETTINGS`
   Select the recorded original BOOX Home using the on-device UI. Some BOOX ROMs do not expose this page.
4. Another possible recovery: launch stock settings with `adb shell am start -a android.settings.SETTINGS`, then navigate to Default Apps > Home if firmware exposes it.
5. If the HOME picker is unavailable, **do not run guessed shell component IDs**. Re-run read-only HOME resolution and inspect `DEVICE_REPORT.md`. Use stock UI first; seek exact OEM-specific procedure only after verification.
6. Only after confirming stock HOME works, uninstall just our app using `adb uninstall <actual.kobostyle.package.id>` (replace with the app ID printed by Gradle; NEVER use this on BOOX packages).
7. If ADB is unavailable, no universal Leaf2 hardware-key recovery can be guaranteed; restoring original HOME through accessible system settings is the preferred path. Avoid factory reset unless device is otherwise unrecoverable and user accepts data loss.

## 2026-11-15 baseline（尚未示範復原）

唯讀 P0 已確認當前 HOME 為 `com.onyx/.StartupActivity`，裝置為 Leaf2 / Android 11。這只記錄既有解析結果，不保證 HOME 設定頁或復原流程可用。此次未執行上述任何 `am start` 或 uninstall 命令，也未變更 HOME。

目前 App ID 為 `org.kobostyle.launcher`，此版僅提供 MAIN/LAUNCHER。日後若使用第 6 步，仍須先確認原廠 HOME 正常，且只能移除本 App。P5 必須另有實機手動示範與使用者明確核准。
