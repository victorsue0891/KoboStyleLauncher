# 注音輸入排查 — 2026-11-17

使用者回報 KoboStyle 搜尋無法使用繁體中文注音，進一步確認「整台裝置找不到注音選項」。

## 已確認

- Leaf2 本次 ADB 狀態為 device。
- 搜尋欄是標準 Android EditText，單行文字輸入；沒有僅限 ASCII、數字或密碼的 inputType，也沒有攔截中文組字的 InputConnection。
- 預設輸入法為 `com.onyx.latinime/.latin.LatinIME`。
- 實際可用 IME 為 AOSP 與 ONYX。系統「管理螢幕小鍵盤」頁也只顯示這兩個選項。
- 已安裝 Gboard `com.google.android.inputmethod.latin`，versionName `13.8.07.597073932-release-arm64-v8a`，installed=true，但套件 enabled=3（DISABLED_USER）。
- enabled_input_methods 的字串仍包含 Gboard，但 Gboard 沒有出現在 `ime list -a`；不能以該字串判定鍵盤目前可用。
- Android 應用程式資訊頁提供解除安裝／強制停止，沒有啟用按鈕。未點解除安裝或強制停止。

## 初次排查狀態（歷史）

已請使用者在 BOOX 原廠應用頁，僅對 Gboard 解除凍結。此時尚待回覆；未宣稱注音配置或實際組字測試成功。停用原因尚未完全證實，不單憑 enabled=3 宣稱由特定 OEM 程序造成。

未透過 `pm enable`、`ime set` 或 `settings put` 修改系統；未下載新輸入法、清除鍵盤資料或讀取鍵盤私人詞庫。尚未修改 App 原始碼，因目前證據指向鍵盤不可用，而非搜尋欄限制中文字元。

## 重現排查

以下狀態查詢為唯讀，不輸出使用者輸入內容：

```powershell
adb get-state
adb shell ime list -s
adb shell ime list -a
adb shell settings get secure default_input_method
adb shell settings get secure enabled_input_methods
adb shell dumpsys package com.google.android.inputmethod.latin
```

已透過公開系統設定入口查看 Gboard 資訊及鍵盤管理：

```powershell
adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:com.google.android.inputmethod.latin
adb shell am start -a android.settings.INPUT_METHOD_SETTINGS
```

接續步驟：解除凍結後確認 Gboard 可用、在鍵盤 UI 檢查繁體中文／注音配置、用實際注音按鍵組字及候選字驗證 KoboStyle 搜尋。先前透過 accessibility 直接填入中文的 UI 測試，不代表已驗證注音組字。

## 2026-11-17 後續實機排查

使用者回覆找不到 Gboard／解凍選項後，實際從原廠「應用 → system 分組（第二頁）→ Gboard」啟動。此操作使 enabled=3 變回 0，Gboard 出現在可用 IME 中。透過 Gboard 的 SELECT INPUT METHOD → 系統選擇器選 Gboard，設定頁確認原本已有「中文（繁體）（注音）、Alphabet（QWERTY）」，無需下載字典。

但回到 KoboStyle 後數十秒又變 enabled=3，預設輸入法退回 AOSP；logcat 的 ApplicationFreezeHelper 明確記錄對該套件呼叫 setApplicationEnabledSettingAsUser、enable:false。未把短暫顯示注音鍵盤當作修復完成。

已試原廠 UI 設定：

| 設定 | 實際結果／處置 |
|---|---|
| 不凍結在後台運行的 APP（音頻／輸入法） | false → true；仍被停用，已還原 false |
| Gboard 長按 → 優化 → 其他 → 後臺持續執行 | false → true；仍被停用，已還原 false |
| 安裝 APP 後自動開啟凍結 | true → false；仍被停用，已還原 true 並確認原廠提示 |
| 個別凍結名單 | 共 6 項，檢查兩頁及搜尋，沒有 Gboard；未更改其他 App 勾選 |
| 長時間未使用 App 的自動凍結 | 7 天 → 不凍結；仍被停用，已由原廠 UI 還原並確認 7 天 |

曾唯讀檢查 ONYX 輸入法宣告的設定 Activity；該活動未匯出，正常啟動遭 SecurityException，沒有繞過。一次讀取其公開 APK 的分析未產生可用 ZIP，沒有拿此結果推論其支援注音。未讀取鍵盤私人資料庫／詞庫，未重新安裝或清除 Gboard 資料。

已用螢幕上的注音鍵實際組出「驗收」候選，但第一輪在候選提交之前又遭凍結，因此該輪不列入搜尋通過；不以直接填入中文字的 accessibility 測試冒充注音測試。

## 同文替代方案與實測結果

使用者明確同意安裝同文輸入法後，安裝官方 [Trime 3.3.13](https://github.com/osfans/trime/releases/tag/v3.3.13) 並配置 Rime 注音。這是改用同文的修復；Gboard 本身的 OEM 凍結問題仍未解決。鍵盤、字典和 APK 不包含在 KoboStyle 儲存庫。

- APK：`com.osfans.trime-v3.3.13-0-gb5cad1a4-arm64-v8a-release.apk`，9,252,039 bytes。
- SHA256：`e7535ea421a25962eb2ea37c46ff867838e69d33ba6d935876ab84d108de5020`，與官方 release metadata 相符。
- apksigner verify 成功；簽章憑證 SHA256：`39f51b81bc3e0eeb7c3ae381755e33bdbf6bd9f4623719ca9ed82392f81a06a8`。工具另有兩個 META-INF/services 項目不受 JAR 簽章保護的警告。
- 實際 APK manifest：minSdk 21、targetSdk 37、arm64-v8a，未宣告 INTERNET 權限。
- [Rime 注音](https://github.com/rime/rime-bopomofo) 固定 commit：`6085c9a38a4a728047862b33d67eee18aa86f3b9`。
- [地球拼音字典](https://github.com/rime/rime-terra-pinyin) 固定 commit：`723e51bc266cf9464530c1ddedb856aa18e3da34`。
- 使用者手動授權 `Download/KoboStyleZhuyin`，加入 `bopomofo.schema.yaml`、`zhuyin.yaml`、`terra_pinyin.dict.yaml` 與自訂檔。透過 Android 原生 UI 啟用及選取同文，未使用 `ime set`。

| 測試 | 結果 |
|---|---|
| 注音部署與組字 | 通過，方案頁顯示注音 |
| IME → EditText → 搜尋 | 通過；螢幕按鍵輸入 ㄧ、ㄢ、ˋ、ㄕ、ㄡ、空白，按 Enter 提交「驗收」，顯示 11 本搜尋結果 |
| 原廠桌面往返後重試 | 通過，再次搜尋「驗收」仍為 11 本 |
| 短期保持啟用 | 數分鐘內 default_input_method 維持同文、enabled=0，未重現 Gboard 數十秒內停用 |
| HOME | 維持 KoboStyle HomeEntry |
| 重開機／隔夜待機 | 本次未驗證，不保證長期不受 OEM 凍結影響 |

測試透過 `adb shell input tap` 點擊注音鍵，經過真正 IME 組字與提交，沒有直接注入中文字或用 accessibility setText 冒充注音輸入。首輪點候選未確認提交至 EditText；後續 Enter 提交通過。曾有 UI hierarchy 未提供畫面文字造成 assertion 失敗，該斷言不列為通過，搜尋結果另以螢幕擷取確認。

初次重開搜尋曾回到字母版面，加入 `preset_keyboards/bopomofo/lock: true` 並部署後，重開仍顯示注音。自訂檔見 [設定備份](keyboard-config/README.md)。空字型欄位未證明能更換裝置字形。

### 使用與復原

1. KoboStyle 右上放大鏡 → 點搜尋欄 → 注音組字／選字。
2. 按鍵盤右下 Enter，確認文字進入搜尋欄，再按「搜尋」。
3. 配色為「墨池／Ink」。保留已授權的 `Download/KoboStyleZhuyin` 配置資料夾。
4. 若要改回原有鍵盤，使用系統輸入法選擇器選 AOSP 或 ONYX；不必停用 App 或修改 HOME。

```powershell
adb shell am start -a android.settings.INPUT_METHOD_SETTINGS
adb shell am start -n org.kobostyle.launcher/.ui.MainActivity
# 以下為唯讀狀態檢查
adb shell settings get secure default_input_method
adb shell ime list -s
adb shell dumpsys package com.osfans.trime
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

本次未修改 KoboStyle APK，未重跑無關的 Gradle 測試。實測輸入法元件為 `com.osfans.trime/.ime.core.TrimeInputMethodService`。原始證據留在本機 `.tools/acceptance`，含 `trime-enter-commit.png`、`trime-search-state.png`、`trime-reopened.png`、`trime-reentry-result.png`；不隨公開儲存庫提供。
