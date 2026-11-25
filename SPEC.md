# KoboStyle Launcher — Product & Engineering Specification

2026-11-25 V4 extension: P1–P6 development and scoped Leaf2 acceptance are complete. Book Spirits adds opt-in paged metadata, local Lite personas, bounded single-book Q&A and SAF knowledge packs, with a shared AI gateway and local cost reservations. The library schema migrates 2→3; a separate bundled-SQLite Room v1 store supplies FTS5. The updated APK is installed; the actual 13,618 books and sources survive migration, and eight distinct device tests have passed. SAF import, two explicitly approved live AI requests, feature rollback and process restart passed; the user confirmed physical E Ink display and page-key scrolling. Original reader/HOME/key behavior remains. See [implementation and limits](docs/v4/IMPLEMENTATION_2026-11-25.md) and [P6 evidence and scope limits](docs/v4/DEVICE_ACCEPTANCE_2026-11-25.md).

2026-11-20 extension: the user requested the Cat Librarian tab and explicitly included OpenAI integration. Apps now lives under More. AI is opt-in, uses a device-entered encrypted key and candidate metadata only, and preserves the existing Room v2/SAF/reader/HOME behavior. The current implementation, privacy boundary, host results and pending device gates are documented in [Cat Librarian development](docs/LIBRARIAN_2026-11-20.md). The baseline phases and device results below are historical; no device was connected for this extension.

Version: 1.0 (2026-11-14)\
Target: BOOX Leaf2, Android 11, 7-inch E Ink, 2 GB RAM\
Language: Traditional Chinese UI (default); English fallback\
Status: P0/P1-P3 and extended acceptance recorded; Kobo-style UI verified on 2026-11-16. After user-demonstrated recovery and approval, P5 selected through system UI. Home/Back, stock rollback, injected crash recovery, reboot preference persistence and PDF opening verified. BOOX still foregrounds stock UI at boot until Home is pressed; a HOME crash clears the default and shows the chooser. Endurance/OEM fault cases remain.

## 1. Goal
Build a light, Kobo-inspired Android HOME launcher while retaining the stock BOOX firmware and NeoReader. No root, system modification, bootloader unlock, firmware flash, or disabling stock launcher.

### Definition of success
- User can launch KoboStyle as an app without affecting current HOME.
- Home shows locally imported EPUB/PDF books, cover, author, title, and recent open time.
- User can open an imported book with NeoReader **if a supported exported reader activity accepts that URI**; otherwise a documented fallback chooser is available.
- Launcher can become default HOME **only if the installed BOOX firmware supports it and user approves**.
- User can recover to BOOX HOME using a documented path.

## 2. Device & environmental assumptions
Known public hardware: BOOX Leaf2; Android 11; 1264x1680 portrait physical panel (verify logical rotation using `wm size`); 2 GB RAM; hardware page turn keys; microSD. Firmware-specific home and NeoReader component names MUST be discovered on device. No guessed hard-coded package/activity identifiers.

Developer host: Windows 11, VS Code + Codex; Android SDK platform-tools, JDK 17, Gradle wrapper; Android Gradle Plugin / Kotlin versions compatible with each other must be selected and pinned by Codex. `minSdk=30`, `targetSdk` and `compileSdk` choose current stable supported toolchain and document behavior differences for Android 11. Never lower targetSdk only to circumvent security policy.

## 3. User flows
1. Install & open APK: normal app screen before HOME selection.
2. Library setup: choose one or more specific book folders with Android Storage Access Framework (SAF) `ACTION_OPEN_DOCUMENT_TREE` or select individual documents using `ACTION_OPEN_DOCUMENT`. Persist read grants when permitted; show an explicit reauthorize state if grant disappears.
3. Scan: index supported EPUB/PDF recursively in user-authorized roots; display progress, cancellation and failures. Never traverse outside grants.
4. Home: recent book, recent opens, bookshelf; no forced network calls; no animations.
5. Open: resolve `ACTION_VIEW` with `content://` URI and read URI permission; prefer discovered NeoReader exported handler when verifiable; otherwise show safe Android chooser or explanatory error. Never use internal NeoReader activity unless exported/supported.
6. Return: system Back returns to KoboStyle if platform navigation allows; do not assume special reader intents.
7. App drawer and stock settings accessible at all times, including escape/recovery.
8. Set as HOME: only upon explicit approval, after validation; use system default-app/role UI when available, never hidden writes.

## 4. Functional requirements
### FR-01 UI/UX
- Portrait baseline; adapt to actual density and rotation, avoid hardcoded px.
- Black/white/gray palette; high contrast; minimum 48dp touch targets; body >=16sp recommended; large primary buttons; avoid continuous clocks, animations, scrolling effects and heavy recomposition.
- Home sections: Continue Reading (last opened, not verified true progress), Recent Books, Library, Apps, Settings.
- Book covers from EPUB `META-INF/container.xml` -> OPF metadata manifest / cover properties with safe ZIP parsing and size limits, or PDF first page using `PdfRenderer` when valid; deterministic grayscale placeholder fallback.
- Empty states for no grants, no books, missing reader, absent SD card.

### FR-02 Book indexing and metadata
- Only `content://` URIs via SAF/DocumentFile/ContentResolver; stable document IDs where available.
- Supported first release: EPUB, PDF. Others non-goal until validated.
- Store title, author, mime, source URI, document identity, lastModified, size, lastOpenedAt (own metadata), cached cover path, scan status in Room DB.
- De-duplicate primarily by persisted URI or provider document ID; hash only as optional explicit scan, avoid heavyweight full scans.
- Scan with background WorkManager job or bounded coroutine, explicit user-triggered; avoid periodic wakeups; allow cancel.
- Distinguish lastOpenedAt (owned data) vs actual reader page/progress (not available by default).
- Optimize cache size, survive missing cards, revoked grants, corrupt archives and PDF errors.

### FR-03 NeoReader bridge — proof required
- Collect package list and `ACTION_VIEW` handlers via PackageManager with Android 11 package-visibility-aware `<queries>` declarations.
- Use test EPUB/PDF stored in an authorized shared folder and open via `Intent(ACTION_VIEW).setDataAndType(uri, mime).addFlags(FLAG_GRANT_READ_URI_PERMISSION)`; add `ClipData` when necessary for URI grant propagation.
- Check exported component, applicable intent filters and package visibility; handle ActivityNotFoundException / SecurityException.
- **Do not assume NeoReader supports arbitrary SAF content URIs.** If it cannot, offer chooser or copy to an explicitly user-selected accessible import directory through SAF, then open via documented supported flow; no `file://` or direct private DB access.
- Prefer default system handler to a package-specific call unless NeoReader compatibility is verified on-device.
- Track only our own open event; label it as "上次開啟" rather than "閱讀進度".

### FR-04 Android HOME behavior
- Manifest `MAIN` + `HOME` + `DEFAULT` on HomeActivity, `android:exported=true`; separate launcher entry `MAIN` + `LAUNCHER` for safe testing as necessary.
- Before any HOME change: record baseline resolved HOME, verify app screen, stability, reader open, return mechanism, stock HOME visibility.
- Use Android-supported settings/RoleManager flow if available, with system UI. OEM behavior may block.
- Recovery entry in Settings: open system home settings where supported and display fallback ADB instructions. No `pm disable-user`, `pm uninstall --user`, `settings put secure`, root, or undocumented broadcasts.

### FR-05 System integration
- Battery: BatteryManager or sticky ACTION_BATTERY_CHANGED; no continuous polling.
- Wi-Fi: display status using public APIs and permissions where required; not demand SSID/location.
- Apps: launch installed visible activities via PackageManager; handle Android 11 package visibility with targeted queries; don't claim to show all apps when filtered.
- Settings: launch `Settings.ACTION_SETTINGS`, optional `Settings.ACTION_WIFI_SETTINGS`, no privileged control promises.
- Hardware page keys: probe ADB key events and respond only to events actually delivered to launcher; do not intercept globally.

### FR-06 E Ink optimization
- No continuous animations, videos, shadows or parallax. Minimize redraw and repeated layout invalidations.
- Start with BOOX app-specific E Ink refresh mode and DPI settings through stock UI.
- Avoid undocumented BOOX classes/JARs or reflection in MVP; optional module only after legal/technical validation on device. Keep vendor-specific implementation behind `EinkRefreshController` no-op interface.
- Do not promise manual full-screen refresh from standard Android APIs.

## 5. Nonfunctional requirements
- Operate fully offline; no analytics, trackers, telemetry, internet permission by default.
- No request for `MANAGE_EXTERNAL_STORAGE`, accessibility service, device-admin, overlay, or root.
- Small apk and memory footprint; image decoding downsampled, thumbnail caching; no full-resolution book covers kept in memory.
- Never mutate book content. Store only metadata/cache and preserve original files.
- Crash resilience: app exceptions do not change BOOX system apps; missing folder and NeoReader failures are recoverable.
- Privacy: ADB diagnostics report strips device serial, account information, SSID, user filenames and other identifying data before sharing.

## 6. Architecture
```
app/
  ui/{home,library,apps,settings}/
  data/{db,books,metadata,cover,permissions}/
  reader/{ReaderResolver,NeoReaderBridge,IntentOpenStrategy}/
  platform/{BatteryStatus,WifiStatus,EinkRefreshController,HomeRoleHelper}/
  di/
```
Use MVVM + Kotlin Flow + Room + coroutines; choose XML Views or Compose after measuring cold start/refresh; baseline recommendation XML Views/RecyclerView for predictable E Ink behavior, but either acceptable with tests. Small scoped modules, no service always running.

## 7. Staged plan and acceptance gates
**P0 Discovery (read-only ADB):** `docs/DEVICE_REPORT.md` has Android properties, screen/density, current HOME resolution, discovered reader handlers, package visibility/intent observations, ROM restrictions and risks. No changes to device. Gate: adb authorized and data collected or documented blocker.

**P1 UI shell:** runnable app with book placeholders, navigation, system settings, and safe entry point. Gate: install, open, rotate, return; no HOME change.

**P2 Book library:** SAF grants, persistence, indexing, EPUB metadata, PDF thumbnail, storage fault cases. Gate: import 10 EPUB + 10 PDF, corrupt input safely handled, persists after app restart.

**P3 NeoReader bridge:** discover exported handlers, run actual end-to-end EPUB and PDF tests. Gate: both open in NeoReader OR record exact incompatibility with stable chooser fallback; never falsely claim integration.

**P4 E Ink optimization:** app-specific BOOX settings, contrast and page-key exploration. Gate: visually acceptable on real Leaf2, no unwanted animation/continual refresh.

**P5 HOME selection (user approval required):** confirm rollback, select HOME via supported UI. Gate: HOME press, reboot, app crashes, fallback all verified; if OEM prevents, document supported alternative (manual launch/widget/shortcut), not hacks.

## 8. Tests / acceptance cases
- ADB: device/unauthorized/offline/no device output handling.
- UI: 1264x1680 baseline, rotation, font scale 1.0/1.3, long CJK titles, blank covers.
- File: normal/malformed EPUB, OPF alternate metadata, zip bomb limits, PDF encrypted/corrupt, duplicate names, duplicate URI, revoked SAF grant, missing SD, 0/100/1000 books; no ANR.
- Bridge: exported NeoReader with `content://`, no compatible handler, MIME mismatch, app upgrade/change, failed permission, user cancels chooser.
- HOME: existing stock HOME remains installed/enabled; default changes only manually; both directions and reboot recovery verified.
- Battery: idle state does not schedule recurring work or redraw clock every second.

## 9. Risk register
- **R1 High**: OEM blocks third-party HOME: safe fallback is launch as ordinary app, do not alter system.
- **R2 High**: NeoReader does not accept third-party SAF URI: verified chooser/import fallback.
- **R3 Medium**: NeoReader reading progress inaccessible: only launcher last-open record; no reverse engineering private DB in MVP.
- **R4 Medium**: E Ink ghosts/refresh incompatibility: BOOX native per-app optimization, no undocumented SDK dependency.
- **R5 Medium**: Android 11 SAF selection limits (root/Download): choose specific directories, or per-document imports.

## 10. Deliverables and repository
- Source project buildable from clean checkout, pinned wrapper/toolchain.
- `SPEC.md`, `README.md`, `docs/DEVICE_REPORT.md`, `docs/TEST_PLAN.md`, `docs/RECOVERY.md`, `docs/CODEX_PROMPT.md`.
- `scripts/diagnose_device.ps1`, `scripts/deploy_debug.ps1`.
- Signed debug APK only after a successful local build, no fabricated APK.
- Automated unit tests for metadata parsing, repository and intent-building; manual acceptance log for physical device.

## 11. External references
- https://shop.boox.com/products/leaf2
- https://developer.android.com/tools/adb
- https://developer.android.com/training/data-storage/shared/documents-files
- https://developer.android.com/about/versions/11/privacy/storage
- https://booxsupport.zendesk.com/hc/en-us/articles/8569442137108-App-Optimization
