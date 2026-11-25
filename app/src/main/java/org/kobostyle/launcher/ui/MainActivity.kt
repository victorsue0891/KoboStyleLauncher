package org.kobostyle.launcher.ui

import android.app.AlertDialog
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import org.kobostyle.launcher.R
import org.kobostyle.launcher.data.db.Book
import org.kobostyle.launcher.data.books.BookCatalog
import org.kobostyle.launcher.data.books.BookFilename
import org.kobostyle.launcher.reader.NeoReaderBridge
import org.kobostyle.launcher.platform.HomeRoleHelper
import org.kobostyle.launcher.librarian.*
import org.kobostyle.launcher.spirits.*
import java.text.DateFormat
import java.util.Date

private data class ShelfRow(val key: String, val kind: String = "book", val text: String = "", val book: Book? = null, val books: List<Book> = emptyList())

open class MainActivity : ComponentActivity() {
    protected open fun spiritRuntime(): SpiritRuntime = SpiritRuntime.get(application)
    protected open fun libraryModelFactory(): androidx.lifecycle.ViewModelProvider.Factory = defaultViewModelProviderFactory
    protected open fun controlPreferences(): SharedPreferences = getSharedPreferences("reading_controls", MODE_PRIVATE)
    private var volumePaging = false
    private var reversePaging = false
    private val model: LibraryViewModel by viewModels { libraryModelFactory() }
    protected open fun librarianModelFactory() = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            check(modelClass == LibrarianViewModel::class.java)
            return LibrarianViewModel.create(application, model.repository, v4::confirm) as T
        }
    }
    private val librarian: LibrarianViewModel by viewModels { librarianModelFactory() }
    private lateinit var librarianPanel: LibrarianPanel
    private lateinit var spiritPanel: SpiritPanel
    private val spirit: SpiritViewModel by viewModels {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val network = application.getSystemService(ConnectivityManager::class.java)
                return SpiritViewModel(SpiritRepository(v4.database, model.repository.dao), v4.settings, librarian.preferences, AndroidKeyVault(application), v4.gateway, {
                    network.getNetworkCapabilities(network.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                }) as T
            }
        }
    }
    private var selectedSpiritId: String? = null
    private var choosingSpirit = false
    private var pendingPackBook: String? = null
    private var importJob: Job? = null
    private val packPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = pendingPackBook; pendingPackBook = null
        if (uri != null && id != null) importKnowledge(id, uri)
    }
    private lateinit var bridge: NeoReaderBridge
    private val homeRole by lazy { HomeRoleHelper(this) }
    private lateinit var content: LinearLayout
    private lateinit var list: RecyclerView
    private lateinit var grid: GridLayoutManager
    private lateinit var clock: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var wifiButton: ImageButton
    private lateinit var scanButton: ImageButton
    private lateinit var progress: TextView
    private lateinit var sourceStatus: TextView
    private lateinit var actions: LinearLayout
    private val libraryActions = mutableListOf<Button>()
    private val navigation = linkedMapOf<String, Button>()
    private lateinit var bookAdapter: BookAdapter
    private var page = "home"
    private var choosingTree = false
    private var recentOnly = false
    private var searchText = ""
    private var selectedCategory: String? = null
    private var selectedAuthor: String? = null
    private var authorQuery = ""
    private var catalog = BookCatalog(emptyList())
    private var displayBooks: List<Book> = emptyList()
    private val v4 by lazy { spiritRuntime() }
    private val pagedCatalog by lazy { PagedCatalog(model.repository.dao, v4.database.dao()) }
    private var catalogJob: Job? = null
    private var catalogOffset = 0
    private var catalogTotal = 0
    private var catalogRecent = 0
    private val diagnosticPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) lifecycleScope.launch {
            val report = librarian.preferences.diagnostics().toString(2)
            val saved = withContext(Dispatchers.IO) {
                try { contentResolver.openOutputStream(uri, "wt")?.use { it.write(report.toByteArray(Charsets.UTF_8)); true } ?: false }
                catch (_: Exception) { false }
            }
            Toast.makeText(this@MainActivity, if (saved) "已匯出診斷報告" else "無法寫入診斷報告", Toast.LENGTH_LONG).show()
        }
    }
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.let { data ->
            val uris = data.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } } ?: listOfNotNull(data.data)
            model.add(uris, choosingTree, data.flags)
        }
    }
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        v4.bindConsent(this)
        page = savedInstanceState?.getString("page") ?: "home"
        choosingTree = savedInstanceState?.getBoolean("choosingTree") ?: false
        recentOnly = savedInstanceState?.getBoolean("recentOnly") ?: false
        searchText = savedInstanceState?.getString("searchText").orEmpty()
        selectedCategory = savedInstanceState?.getString("selectedCategory")
        selectedAuthor = savedInstanceState?.getString("selectedAuthor")
        authorQuery = savedInstanceState?.getString("authorQuery").orEmpty()
        catalogOffset = savedInstanceState?.getInt("catalogOffset") ?: 0
        selectedSpiritId = savedInstanceState?.getString("spiritBook")
        choosingSpirit = savedInstanceState?.getBoolean("choosingSpirit") ?: false
        pendingPackBook = savedInstanceState?.getString("packBook")
        if (page == "spirit" && !v4.settings.enabled) page = "librarian"
        volumePaging = controlPreferences().getBoolean("volume_paging", false)
        reversePaging = controlPreferences().getBoolean("reverse_paging", false)
        bridge = NeoReaderBridge(this)
        content = object : LinearLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean = if (handleContentKey(event)) true else super.dispatchKeyEvent(event)
        }.apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), 0, dp(12), 0) }
        clock = label("", 16f)
        top.addView(clock, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(iconButton(ReaderIcon.Kind.LIGHT, R.string.system_settings) { safeStart(Intent(Settings.ACTION_SETTINGS)) })
        wifiButton = iconButton(ReaderIcon.Kind.WIFI, R.string.wifi_settings) { safeStart(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        top.addView(wifiButton)
        batteryStatus = label("", 14f).apply { gravity = Gravity.CENTER; minWidth = dp(48); minimumHeight = dp(48) }
        top.addView(batteryStatus)
        scanButton = iconButton(ReaderIcon.Kind.REFRESH, R.string.scan) { model.scan() }
        top.addView(scanButton)
        top.addView(iconButton(ReaderIcon.Kind.SEARCH, R.string.search_books) { showSearch() })
        content.addView(top)
        actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(24), dp(4), dp(24), dp(4)) }
        content.addView(actions)
        sourceStatus = label("", 14f).apply { setPadding(dp(24), dp(4), dp(24), 0) }
        content.addView(sourceStatus)
        progress = label("", 14f).apply { setPadding(dp(24), dp(4), dp(24), dp(4)); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        content.addView(progress)
        grid = GridLayoutManager(this, columns())
        list = RecyclerView(this).apply {
            id = R.id.shelf
            layoutManager = grid; itemAnimator = null; overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(dp(24), dp(12), dp(24), dp(12)); clipToPadding = false
        }
        bookAdapter = BookAdapter()
        grid.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = when {
                page == "categories" && position >= 2 -> 1
                page == "library" && list.adapter === bookAdapter && bookAdapter.currentList.getOrNull(position)?.kind == "book" -> 1
                else -> grid.spanCount
            }
        }
        content.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        librarianPanel = LibrarianPanel(this, ::librarianAction, ::showLibrarianSettings, if (!v4.settings.enabled) null else {
            { val book = librarian.state.value.card?.book?.takeIf { librarian.state.value.stage == DialogueStage.DETAIL }
                if (book != null) showSpirit(book.identity) else { navigate("library"); choosingSpirit = true; renderList() } }
        }).apply { visibility = View.GONE }
        content.addView(librarianPanel, LinearLayout.LayoutParams(-1, 0, 1f))
        spiritPanel = SpiritPanel(this, ::spiritAction).apply { visibility = View.GONE }
        content.addView(spiritPanel, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(rule())
        val bottom = LinearLayout(this).apply { setPadding(dp(8), dp(4), dp(8), dp(2)) }
        val tabs = listOf(Triple("home", R.string.home, ReaderIcon.Kind.HOME), Triple("library", R.string.library, ReaderIcon.Kind.BOOKS), Triple("librarian", R.string.librarian, ReaderIcon.Kind.CAT), Triple("settings", R.string.settings, ReaderIcon.Kind.MORE))
        tabs.forEach { (key, title, icon) ->
            val tab = button(getString(title), outlined = false) { navigate(key) }.apply {
                textSize = 14f; minHeight = dp(64); gravity = Gravity.CENTER
                setCompoundDrawablesWithIntrinsicBounds(null, ReaderIcon(icon, resources.displayMetrics.density), null, null)
                compoundDrawablePadding = dp(3); tag = icon
            }
            navigation[key] = tab
            bottom.addView(tab, LinearLayout.LayoutParams(0, -2, 1f))
        }
        content.addView(bottom)
        setContentView(content)
        WindowCompat.getInsetsController(window, content).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val originalCallback = window.callback
        window.callback = object : Window.Callback by originalCallback {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean = if (handleContentKey(event)) true else originalCallback.dispatchKeyEvent(event)
        }
        render()
        homeRole.rememberOriginal()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (page == "spirit") { navigate("librarian"); return }
                if (page == "apps") { navigate("settings"); return }
                if (page == "librarian") {
                    if (librarian.state.value.stage == DialogueStage.WELCOME) navigate("home") else librarianAction(DialogueAction.BACK, "")
                    return
                }
                if (goBackInCatalog()) return
                if (homeRole.isDefault()) {
                    if (page != "home") navigate("home")
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.catalog.collect { catalog = it; displayBooks = it.books; renderList() } }
                if (v4.settings.enabled) launch { model.repository.dao.observeChanges().debounce(500).collect { renderList() } }
                if (v4.settings.enabled) launch { spirit.state.collect { if (page == "spirit") spiritPanel.render(it) } }
                launch { librarian.state.collect { if (page == "librarian") librarianPanel.render(it, librarian.preferences.config()) } }
                launch { combine(model.progress, model.message, model.sources) { p, m, s -> Triple(p, m, s) }.collect { updateScanStatus() } }
            }
        }
        if (page == "spirit") selectedSpiritId?.let { spirit.select(it) }
    }
    private fun columns(): Int {
        val width = resources.configuration.screenWidthDp / resources.configuration.fontScale.coerceAtLeast(1f)
        return when {
            width >= 720 -> 5
            width >= 520 -> 4
            width >= 390 -> 3
            else -> 2
        }
    }
    private fun navigate(destination: String, recent: Boolean = false, query: String = "", category: String? = null, author: String? = null) {
        if (page == "spirit" && destination != "spirit") { spirit.cancel(); importJob?.cancel() }
        if (page == "librarian" && destination != "librarian") librarian.leavePage()
        page = destination; recentOnly = recent; searchText = query
        if (destination !in setOf("library", "categories", "authors")) choosingSpirit = false
        catalogOffset = 0
        selectedCategory = category; selectedAuthor = author
        render(); list.scrollToPosition(0)
    }
    private fun goBackInCatalog(): Boolean = when {
        page == "library" && selectedAuthor != null -> { navigate("authors"); true }
        page == "authors" || (page == "library" && selectedCategory != null) -> { navigate("categories"); true }
        page == "categories" -> { navigate("home"); true }
        else -> false
    }
    override fun onResume() { super.onResume(); updateStatus(); model.refresh() }
    override fun onStop() {
        if (!isChangingConfigurations && page == "spirit") spirit.cancel()
        if (!isChangingConfigurations && page == "librarian") librarian.leavePage()
        super.onStop()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) navigate("home")
    }
    protected open fun handleContentKey(event: KeyEvent): Boolean {
        val standard = event.keyCode == KeyEvent.KEYCODE_PAGE_UP || event.keyCode == KeyEvent.KEYCODE_PAGE_DOWN
        val volume = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (page in listOf("home", "library", "categories", "authors", "librarian", "spirit") && (standard || (volumePaging && volume)) && ::list.isInitialized) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                var direction = if (event.keyCode == KeyEvent.KEYCODE_PAGE_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1
                if (!standard && reversePaging) direction = -direction
                if (page == "spirit") spiritPanel.scrollBy(0, direction * (spiritPanel.height * .9f).toInt())
                else if (page == "librarian") {
                    if (librarian.state.value.stage == DialogueStage.CARDS && librarian.state.value.card != null)
                        librarianAction(if (direction > 0) DialogueAction.NEXT else DialogueAction.PREVIOUS, "")
                    else librarianPanel.scrollBy(0, direction * (librarianPanel.height * .9f).toInt())
                } else list.scrollBy(0, direction * (list.height * .9f).toInt())
            }
            return true
        }
        return false
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("spiritBook", selectedSpiritId); outState.putString("packBook", pendingPackBook)
        outState.putBoolean("choosingSpirit", choosingSpirit)
        outState.putInt("catalogOffset", catalogOffset)
        outState.putString("page", page); outState.putBoolean("choosingTree", choosingTree)
        outState.putBoolean("recentOnly", recentOnly); outState.putString("searchText", searchText)
        outState.putString("selectedCategory", selectedCategory); outState.putString("selectedAuthor", selectedAuthor)
        outState.putString("authorQuery", authorQuery)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { v4.unbindConsent(this); bridge.close(); super.onDestroy() }
    private fun updateStatus() {
        // Snapshot on resume only: no periodic clock or refresh work on E Ink.
        clock.text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else getString(R.string.unknown)
        batteryStatus.text = percent; batteryStatus.contentDescription = getString(R.string.battery_label, percent)
        val network = getSystemService(ConnectivityManager::class.java)
        val wifi = network.getNetworkCapabilities(network.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        wifiButton.contentDescription = getString(R.string.wifi_settings) + " · " + getString(if (wifi) R.string.connected else R.string.disconnected)
        wifiButton.imageAlpha = if (wifi) 255 else 130
    }
    private fun updateScanStatus() {
        val p = model.progress.value
        val message = model.message.value
        val sources = model.sources.value
        progress.text = message?.let { getString(it) } ?: if (p.running) {
            getString(R.string.scanning, p.books, p.failures)
        } else {
            getString(R.string.scan_done, p.books)
        }
        if (p.limited) progress.append("\n" + getString(R.string.scan_limit))
        val lost = sources.count { it.status == "reauthorize" }
        val scanError = sources.any { it.status == "scan_error" }
        sourceStatus.text = listOfNotNull(
            getString(R.string.sources, sources.size, lost).takeIf { lost > 0 },
            getString(R.string.source_scan_error).takeIf { scanError }
        ).joinToString("\n")
        progress.visibility = if (message != null || p.running || (page == "library" && (p.books > 0 || p.failures > 0))) View.VISIBLE else View.GONE
        sourceStatus.visibility = if (page == "library" && (lost > 0 || scanError)) View.VISIBLE else View.GONE
        libraryActions.forEach { it.isEnabled = !p.running || it.tag == "cancel"; if (it.tag == "cancel") it.visibility = if (p.running) View.VISIBLE else View.GONE }
        scanButton.isEnabled = !p.running; scanButton.imageAlpha = if (p.running) 100 else 255
    }
    private fun render() {
        list.visibility = if (page in setOf("librarian", "spirit")) View.GONE else View.VISIBLE
        librarianPanel.visibility = if (page == "librarian") View.VISIBLE else View.GONE
        spiritPanel.visibility = if (page == "spirit") View.VISIBLE else View.GONE
        grid.spanCount = if (page == "categories") if (resources.configuration.screenWidthDp / resources.configuration.fontScale >= 360) 2 else 1 else columns()
        actions.removeAllViews(); libraryActions.clear()
        val inCatalog = page in listOf("categories", "authors") || selectedCategory != null || selectedAuthor != null
        actions.visibility = if (page == "library" || inCatalog) View.VISIBLE else View.GONE
        if (inCatalog) actions.addView(button(getString(R.string.back_to_parent)) { goBackInCatalog() })
        if (page == "apps") {
            actions.visibility = View.VISIBLE
            actions.addView(button(getString(R.string.back_to_parent)) { navigate("settings") })
        }
        if (page == "library") {
            libraryActions += button(getString(R.string.add_folder)) { pick(true) }
            libraryActions += button(getString(R.string.add_files)) { pick(false) }
            libraryActions += button(getString(R.string.cancel)) { model.cancel() }.apply { tag = "cancel" }
            libraryActions.forEach { actions.addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }) }
        }
        navigation.forEach { (key, tab) ->
            val selected = key == page || (key == "librarian" && page == "spirit") || (key == "library" && page in listOf("categories", "authors")) || (key == "settings" && page == "apps")
            tab.isSelected = selected; tab.setTypeface(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
            tab.setCompoundDrawablesWithIntrinsicBounds(null, ReaderIcon(tab.tag as ReaderIcon.Kind, resources.displayMetrics.density, selected), null, null)
            tab.background = null
        }
        updateScanStatus(); renderList()
    }
    private fun showSearch() {
        val authors = page == "authors"
        val input = EditText(this).apply { id = R.id.search_input; setSingleLine(true); hint = getString(if (authors) R.string.search_authors else R.string.search_hint); setText(if (authors) authorQuery else searchText) }
        AlertDialog.Builder(this).setTitle(if (authors) R.string.search_authors else R.string.search_books).setView(input)
            .setPositiveButton(R.string.search_action) { _, _ ->
                if (authors) { authorQuery = input.text.toString().trim(); navigate("authors") }
                else navigate("library", query = input.text.toString().trim(), category = selectedCategory, author = selectedAuthor)
            }
            .setNeutralButton(R.string.show_all) { _, _ -> if (authors) { authorQuery = ""; navigate("authors") } else navigate("library") }
            .setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun pick(tree: Boolean) {
        choosingTree = tree
        val intent = if (tree) Intent(Intent.ACTION_OPEN_DOCUMENT_TREE) else Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/epub+zip", "application/pdf")); putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        try { picker.launch(intent) } catch (_: ActivityNotFoundException) { toast(R.string.source_error) }
    }
    private fun renderList() {
        if (page == "spirit") { spiritPanel.render(spirit.state.value); return }
        if (page == "librarian") { librarianPanel.render(librarian.state.value, librarian.preferences.config()); return }
        if (v4.settings.enabled && page in setOf("home", "library", "categories", "authors")) { renderPagedCatalog(); return }
        if (page == "categories") {
            val labels = resources.getStringArray(R.array.book_category_labels)
            list.adapter = TextAdapter(listOf(
                getString(R.string.book_categories) to null,
                getString(R.string.by_author) to { authorQuery = ""; navigate("authors") }
            ) + BookFilename.categories.mapIndexed { i, category ->
                (labels[i] + "  ·  " + getString(R.string.book_count, catalog.categoryCount(category))) to { navigate("library", category = category) }
            } + listOf(
                getString(R.string.show_all) to { navigate("library") },
                (getString(R.string.uncategorized) + "  ·  " + getString(R.string.book_count, catalog.categoryCount(""))) to { navigate("library", category = "") }
            ))
        } else if (page == "authors") {
            val authors = catalog.authors.filter { it.first.contains(authorQuery, true) || (it.first.isEmpty() && getString(R.string.unknown_author).contains(authorQuery, true)) }
            list.adapter = TextAdapter(listOf(getString(R.string.by_author) to null) + authors.map { (name, count) ->
                (name.ifEmpty { getString(R.string.unknown_author) } + "  ·  " + getString(R.string.book_count, count)) to { navigate("library", author = name) }
            } + if (authors.isEmpty()) listOf(getString(R.string.no_authors) to null) else emptyList())
        } else if (page == "apps") {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = packageManager.queryIntentActivities(intent, 0).filter { it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled }.sortedBy { it.loadLabel(packageManager).toString() }
            list.adapter = TextAdapter(listOf(getString(R.string.visible_apps) to null) + apps.map { info ->
                info.loadLabel(packageManager).toString() to { safeStart(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName(info.activityInfo.packageName, info.activityInfo.name))) }
            })
        } else if (page == "settings") {
            list.adapter = TextAdapter(listOf(
                getString(R.string.room_settings) to null,
                getString(R.string.apps) to { navigate("apps") },
                getString(R.string.librarian_settings) to { showLibrarianSettings() },
                "書靈與費用設定" to { SpiritDialogs.settings(this, v4, librarian.preferences.config().model) {
                    librarian.clearSessionAndCache()
                    // Factories choose V3 or V4 data flows at creation; do not retain the old choice.
                    viewModelStore.clear(); recreate()
                } },
                "AI 用量紀錄" to { SpiritDialogs.usage(this, v4) },
                getString(R.string.home_recovery) to { showHomeRecovery() },
                getString(if (volumePaging) R.string.volume_paging_on else R.string.volume_paging_off) to {
                    volumePaging = !volumePaging; controlPreferences().edit().putBoolean("volume_paging", volumePaging).apply(); renderList()
                },
                getString(if (reversePaging) R.string.page_direction_reversed else R.string.page_direction_normal) to {
                    reversePaging = !reversePaging; controlPreferences().edit().putBoolean("reverse_paging", reversePaging).apply(); renderList()
                },
                getString(R.string.system_settings) to { safeStart(Intent(Settings.ACTION_SETTINGS)) },
                getString(R.string.wifi_settings) to { safeStart(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                getString(R.string.page_keys_note) to null,
                getString(R.string.settings_note) to null
            ))
        } else {
            if (list.adapter !== bookAdapter) list.adapter = bookAdapter
            val rows = mutableListOf<ShelfRow>()
            if (page == "home") {
                if (displayBooks.isEmpty()) rows += ShelfRow("empty", "empty", getString(R.string.empty))
                else rows += ShelfRow("featured", "featured", books = displayBooks.take(4))
                rows += ShelfRow("collections", "collections", books = displayBooks)
                rows += ShelfRow("shortcuts", "shortcuts")
            } else {
                val filtered = catalog.select(selectedCategory, selectedAuthor, recentOnly, searchText)
                val groupTitle = when {
                    selectedCategory != null -> if (selectedCategory!!.isEmpty()) getString(R.string.uncategorized) else resources.getStringArray(R.array.book_category_labels)[BookFilename.categories.indexOf(selectedCategory).coerceAtLeast(0)]
                    selectedAuthor != null -> selectedAuthor!!.ifEmpty { getString(R.string.unknown_author) }
                    recentOnly -> getString(R.string.recent)
                    else -> getString(R.string.my_books)
                }
                val title = if (searchText.isNotBlank()) groupTitle + " · " + getString(R.string.search_results, searchText) else groupTitle
                rows += ShelfRow("shelf-heading", "heading", title + "  ·  " + getString(R.string.book_count, filtered.size))
                if (filtered.isEmpty()) rows += ShelfRow("empty", "empty", getString(if (displayBooks.isEmpty()) R.string.empty else R.string.no_matches))
                else rows.addAll(filtered.map { ShelfRow("shelf-${it.identity}", book = it) })
            }
            bookAdapter.submitList(rows)
        }
    }
    private fun renderPagedCatalog() {
        catalogJob?.cancel()
        val requestedPage = page
        catalogJob = lifecycleScope.launch {
            val loading = launch {
                delay(500)
                if (page == requestedPage) list.adapter = TextAdapter(listOf("正在準備書目索引，首次使用可能需要稍候……" to null))
            }
            try {
                val data = withContext(Dispatchers.IO) {
                    pagedCatalog.sync()
                    when (requestedPage) {
                        "categories" -> pagedCatalog.categories()
                        "authors" -> pagedCatalog.authors(authorQuery, catalogOffset)
                        else -> pagedCatalog.page(if (requestedPage == "home") CatalogFilter() else CatalogFilter(searchText, selectedCategory, selectedAuthor, recentOnly), catalogOffset)
                    }
                }
                loading.cancel()
                if (page != requestedPage) return@launch
                if (data is BookPage) {
                    displayBooks = data.books; catalogTotal = data.count; catalogOffset = data.offset
                    if (page == "home") catalogRecent = withContext(Dispatchers.IO) { pagedCatalog.page(CatalogFilter(recent = true)).count }
                    val rows = mutableListOf<ShelfRow>()
                    if (page == "home") {
                        if (data.count == 0) rows += ShelfRow("empty", "empty", getString(R.string.empty))
                        else rows += ShelfRow("featured", "featured", books = data.books.take(4))
                        rows += ShelfRow("collections", "collections", books = data.books)
                        rows += ShelfRow("shortcuts", "shortcuts")
                    } else {
                        rows += ShelfRow("heading", "heading", "${if (choosingSpirit) "選一本書召喚書靈" else selectedCategory ?: selectedAuthor ?: getString(R.string.my_books)} · ${data.count} 本 · ${data.offset / 50 + 1} 頁")
                        rows += data.books.map { ShelfRow(it.identity, book = it) }
                        if (data.books.isEmpty()) rows += ShelfRow("empty", "empty", getString(R.string.no_matches))
                        rows += ShelfRow("pager", "pager")
                    }
                    list.adapter = bookAdapter; bookAdapter.submitList(rows)
                } else {
                    @Suppress("UNCHECKED_CAST") val groups = data as List<CatalogGroup>
                    val rows = mutableListOf<Pair<String, (() -> Unit)?>>()
                    if (page == "categories") {
                        rows += getString(R.string.book_categories) to null
                        rows += getString(R.string.by_author) to { navigate("authors") }
                        (BookFilename.categories + "").forEach { name -> rows += "${name.ifEmpty { getString(R.string.uncategorized) }} · ${groups.firstOrNull { it.name == name }?.count ?: 0} 本" to { navigate("library", category = name) } }
                    } else {
                        rows += "按作者分類 · ${catalogOffset / 50 + 1} 頁" to null
                        groups.forEach { group -> rows += "${group.name.ifEmpty { getString(R.string.unknown_author) }} · ${group.count} 本" to { navigate("library", author = group.name) } }
                        if (catalogOffset > 0) rows += "上一頁" to { catalogOffset = maxOf(0, catalogOffset - 50); renderList() }
                        if (groups.size == 50) rows += "下一頁" to { catalogOffset += 50; renderList() }
                    }
                    list.adapter = TextAdapter(rows)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { list.adapter = TextAdapter(listOf("暫時無法讀取分頁索引；原書仍保留。" to null, "重試" to { renderList() })) }
            finally { loading.cancel() }
        }
    }
    private fun librarianAction(action: DialogueAction, value: String) {
        when (val effect = librarian.dispatch(action, value)) {
            DialogueEffect.Library -> navigate("library")
            DialogueEffect.TextEntry -> LibrarianDialogs.textEntry(this) { librarianAction(DialogueAction.QUERY, it) }
            is DialogueEffect.Open -> lifecycleScope.launch {
                val book = model.repository.dao.book(effect.id)
                if (book == null) { toast(R.string.source_error); return@launch }
                open(book, effect.chooser) { page == "librarian" && librarian.state.value.stage == DialogueStage.CONFIRM && librarian.state.value.card?.book?.identity == effect.id }
            }
            else -> Unit
        }
    }
    private fun showLibrarianSettings() = LibrarianDialogs.settings(this, librarian,
        changed = { if (page == "librarian") librarianPanel.render(librarian.state.value, librarian.preferences.config()) },
        export = { try { diagnosticPicker.launch("kobostyle-ai-diagnostics.json") } catch (_: ActivityNotFoundException) { toast(R.string.source_error) } })
    private fun open(book: Book, chooser: Boolean, stillRequested: () -> Boolean = { true }) {
        lifecycleScope.launch {
            val accessible = withContext(Dispatchers.IO) {
                try { contentResolver.openFileDescriptor(Uri.parse(book.uri), "r")?.use { true } ?: false } catch (_: Exception) { false }
            }
            if (!stillRequested()) return@launch
            if (!accessible) { model.repository.dao.bookStatus(book.identity, "unavailable"); toast(R.string.source_error); return@launch }
            bridge.open(Uri.parse(book.uri), book.mime, chooser) { model.opened(book) }?.let { toast(it) }
        }
    }
    private fun bookMenu(book: Book) {
        val choices = listOf(getString(R.string.open_book), getString(R.string.choose_reader)) + if (v4.settings.enabled) listOf("召喚書靈／問這本書") else emptyList()
        AlertDialog.Builder(this).setTitle(book.title).setItems(choices.toTypedArray()) { _, which -> if (which == 2) showSpirit(book.identity) else open(book, which == 1) }.show()
    }
    private fun showSpirit(id: String) { selectedSpiritId = id; navigate("spirit"); spirit.select(id) }
    private fun spiritAction(action: String) {
        val book = spirit.state.value.book
        fun ask(question: String, refine: Boolean = false) { spirit.ask(question, refine) { SpiritDialogs.confirm(this, it) } }
        when (action) {
            "back" -> navigate("librarian")
            "library" -> navigate("library")
            "summon" -> spirit.summon()
            "cancel" -> spirit.cancel()
            "open" -> if (book != null) open(book, false) { page == "spirit" && spirit.state.value.book?.identity == book.identity }
            "import" -> if (book != null && importJob?.isActive != true) { pendingPackBook = book.identity; try { packPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) } catch (_: ActivityNotFoundException) { pendingPackBook = null; toast(R.string.source_error) } }
            "summary" -> ask("請整理已允許章節的重點。")
            "explain" -> ask("請只依已允許的片段解釋，不透露其他章節。")
            "quiz" -> ask("請根據已允許的片段提出一個思考問題。")
            "question" -> LibrarianDialogs.textEntry(this) { ask(it) }
            "refine" -> ask("請依書名和書目精修書靈的歡迎語，坦承未讀全書。", true)
            "delete", "remove_pack" -> AlertDialog.Builder(this).setTitle("確認移除本機書靈資料")
                .setMessage("只移除這本書的${if (action == "delete") "書靈與知識包" else "知識包"}，不刪除原始電子書。")
                .setNegativeButton("取消", null).setPositiveButton("移除") { _, _ -> if (action == "delete") spirit.delete() else spirit.removePack() }.show()
            "boundary" -> {
                val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
                body.addView(label("手動已讀到第幾章？留空代表未知。這不是 NeoReader 自動進度。", 17f))
                val input = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(spirit.state.value.boundary?.chapter?.toString().orEmpty()) }; body.addView(input)
                val spoilers = CheckBox(this).apply { text = "允許使用所有章節（可能劇透）"; isChecked = spirit.state.value.boundary?.spoilers == true }; body.addView(spoilers)
                val dialog = AlertDialog.Builder(this).setTitle("閱讀邊界").setView(body).setNegativeButton("取消", null).setPositiveButton("儲存", null).create()
                dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val number = input.text.toString().toIntOrNull()
                    if (input.text.isNotBlank() && (number == null || number !in 0..100_000)) input.error = "請輸入 0–100000 或留空"
                    else { spirit.boundary(number, spoilers.isChecked); dialog.dismiss() }
                } }; dialog.show()
            }
        }
    }
    private fun importKnowledge(id: String, uri: Uri) {
        if (uri.scheme != "content" || importJob?.isActive == true) return
        importJob = lifecycleScope.launch {
            try {
                val book = model.repository.dao.book(id) ?: return@launch
                val importer = KnowledgeImporter(v4.database)
                val preview = withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.use { importer.inspect(it) } ?: error("No stream") }
                if (page != "spirit" || selectedSpiritId != id) return@launch
                val confirmed = suspendCancellableCoroutine<Boolean> { continuation ->
                    fun done(value: Boolean) { if (continuation.isActive) continuation.resumeWith(Result.success(value)) }
                    val dialog = AlertDialog.Builder(this@MainActivity).setTitle("確認知識包對應書籍")
                        .setMessage("選定原書：${book.title}\n\n包內書名：${preview.title}\n作者：${preview.author}\n片段：${preview.count}\n來源：${preview.source}\n\n請確認兩者是同一本書。匯入不收費、不上傳；之後只有你確認 AI 問答時，才傳送已允許的少量片段。校驗碼只檢查完整性，不保證內容正確。")
                        .setNegativeButton("取消") { _, _ -> done(false) }.setPositiveButton("是這本書，匯入") { _, _ -> done(true) }.setOnCancelListener { done(false) }.create()
                    dialog.setOnDismissListener { done(false) }; continuation.invokeOnCancellation { runOnUiThread { dialog.dismiss() } }; dialog.show()
                }
                if (!confirmed || page != "spirit" || selectedSpiritId != id) return@launch
                withContext(Dispatchers.IO) {
                    check(bookVersion(model.repository.dao.book(id) ?: error("Missing book")) == bookVersion(book))
                    contentResolver.openInputStream(uri)?.use { importer.import(book, preview, it) } ?: error("No stream")
                }
                spirit.imported(); Toast.makeText(this@MainActivity, "知識包已匯入；請設定手動已讀章節。", Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { Toast.makeText(this@MainActivity, "無法匯入：請檢查版本、格式、大小、校驗碼與對應書籍。既有知識包保持完整。", Toast.LENGTH_LONG).show() }
        }
    }
    private fun showHomeRecovery() {
        homeRole.rememberOriginal()
        AlertDialog.Builder(this).setTitle(if (homeRole.isDefault()) R.string.home_is_default else R.string.home_is_app)
            .setItems(arrayOf(getString(R.string.open_original_home), getString(R.string.home_settings), getString(R.string.use_as_home))) { _, which ->
                when (which) {
                    0 -> homeRole.originalIntent()?.let(::safeStart) ?: toast(R.string.home_original_missing)
                    1 -> homeRole.settingsIntent()?.let(::safeStart) ?: toast(R.string.home_settings_missing)
                    2 -> AlertDialog.Builder(this).setTitle(R.string.use_as_home).setMessage(R.string.home_recovery_confirm)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.home_recovery_ready) { _, _ ->
                            try {
                                if (homeRole.enableCandidate()) homeRole.settingsIntent()?.let(::safeStart)
                                else toast(R.string.home_original_missing)
                            } catch (_: SecurityException) { toast(R.string.home_settings_missing) }
                        }.show()
                }
            }.show()
    }
    private fun safeStart(intent: Intent) { try { startActivity(intent) } catch (_: ActivityNotFoundException) { toast(R.string.open_failed) } catch (_: SecurityException) { toast(R.string.open_failed) } }
    private fun toast(id: Int) { Toast.makeText(this, id, Toast.LENGTH_LONG).show() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun border(fill: Int, stroke: Int = 0xFFAAAAAA.toInt(), width: Int = 1) = GradientDrawable().apply { setColor(fill); setStroke(dp(width).coerceAtLeast(1), stroke) }
    private fun rule() = View(this).apply { setBackgroundColor(0xFFB4B4B4.toInt()); layoutParams = LinearLayout.LayoutParams(-1, dp(1).coerceAtLeast(1)) }
    private fun label(text: String, size: Float = 16f, serif: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.BLACK); includeFontPadding = false
        typeface = if (serif) Typeface.create("serif", Typeface.NORMAL) else Typeface.DEFAULT
        setPadding(0, dp(4), 0, dp(4))
    }
    private fun button(text: String, outlined: Boolean = true, action: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 16f; isAllCaps = false; minHeight = dp(48); minimumWidth = dp(48); setTextColor(Color.BLACK)
        setPadding(dp(8), dp(6), dp(8), dp(6)); background = if (outlined) border(Color.WHITE) else null
        stateListAnimator = null; setOnClickListener { action() }
    }
    private fun iconButton(icon: ReaderIcon.Kind, description: Int, action: () -> Unit) = ImageButton(this).apply {
        setImageDrawable(ReaderIcon(icon, resources.displayMetrics.density)); contentDescription = getString(description)
        background = null; setPadding(dp(12), dp(12), dp(12), dp(12)); layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        stateListAnimator = null; setOnClickListener { action() }
    }
    private fun availableWidth() = ((if (list.width > 0) list.width else resources.displayMetrics.widthPixels) - dp(48)).coerceAtLeast(dp(240))
    private fun bookCover(book: Book?, height: Int, holder: BookHolder): FrameLayout {
        val frame = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(-1, height); background = border(Color.WHITE, 0xFF505050.toInt()); contentDescription = book?.title ?: getString(R.string.fallback_cover) }
        val paper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(10), dp(8), dp(10), dp(8)); background = border(Color.WHITE, 0xFFAAAAAA.toInt()) }
        paper.addView(label(book?.title ?: getString(R.string.my_books), 16f, true).apply {
            gravity = Gravity.CENTER; maxLines = 5; ellipsize = TextUtils.TruncateAt.END
            setAutoSizeTextTypeUniformWithConfiguration(10, 16, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        paper.addView(label(book?.mime?.substringAfterLast('/')?.substringBefore('+')?.uppercase() ?: "EPUB / PDF", 11f).apply { gravity = Gravity.CENTER })
        frame.addView(paper, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) })
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        frame.addView(image, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
        if (book?.coverPath != null) holder.jobs += lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(book.coverPath) }
            if (bitmap != null) { paper.visibility = View.GONE; image.setImageBitmap(bitmap) }
        }
        return frame
    }
    private fun bookTile(book: Book, height: Int, holder: BookHolder, menu: Boolean): LinearLayout {
        val tile = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), 0, dp(6), dp(12)) }
        val cover = bookCover(book, height, holder).apply {
            isFocusable = true; setOnClickListener { if (choosingSpirit) showSpirit(book.identity) else open(book, false) }; setOnLongClickListener { bookMenu(book); true }
            layoutParams = LinearLayout.LayoutParams(minOf((height * .70f).toInt(), availableWidth() / (if (menu) grid.spanCount else if (resources.configuration.screenWidthDp < 480) 2 else 4) - dp(12)), height)
        }
        tile.addView(cover)
        val title = label(book.title, 16f, true).apply { maxLines = 2; minLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(8), 0, dp(2)); setOnClickListener { if (choosingSpirit) showSpirit(book.identity) else open(book, false) } }
        if (menu) {
            val titleRow = LinearLayout(this).apply { gravity = Gravity.TOP }
            titleRow.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            titleRow.addView(button("⋯", outlined = false) { bookMenu(book) }.apply {
                textSize = 22f; setPadding(0, 0, 0, 0); contentDescription = book.title + " · " + getString(R.string.book_options)
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            tile.addView(titleRow)
            tile.addView(label(catalog.author(book).ifBlank { getString(R.string.unknown_author) }, 14f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        } else tile.addView(title)
        val note = when { book.scanStatus == "unavailable" -> getString(R.string.unavailable); book.scanStatus != "ok" -> getString(R.string.metadata_error); book.lastOpenedAt > 0 -> getString(R.string.opened_on, java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault()).format(Date(book.lastOpenedAt))); else -> getString(R.string.never_opened) }
        tile.addView(label(note, 14f).apply { maxLines = 2; minLines = if (menu) 2 else 1; ellipsize = TextUtils.TruncateAt.END; contentDescription = note })
        return tile
    }
    private fun collection(title: String, books: List<Book>, count: Int, holder: BookHolder, action: () -> Unit): LinearLayout {
        val width = availableWidth() / 2 - dp(18)
        val height = minOf((width * .78f).toInt(), (list.height * .23f).toInt()).coerceIn(dp(100), dp(210))
        val tile = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(8), dp(6), dp(12)); isFocusable = true; setOnClickListener { action() }; contentDescription = title + " · " + getString(R.string.book_count, count) }
        val stack = FrameLayout(this)
        val coverWidth = minOf((width * .63f).toInt(), (height * .70f).toInt())
        for (i in books.size.coerceIn(1, 3) - 1 downTo 0) {
            val cover = bookCover(books.getOrNull(i), height - dp(i * 10), holder)
            stack.addView(cover, FrameLayout.LayoutParams(coverWidth, height - dp(i * 10)).apply { leftMargin = (coverWidth * .25f * i).toInt(); gravity = Gravity.BOTTOM })
        }
        tile.addView(stack, LinearLayout.LayoutParams(-1, height))
        tile.addView(label(title, 23f, true).apply { setPadding(0, dp(12), 0, dp(2)) })
        tile.addView(label(getString(R.string.book_count, count), 14f))
        return tile
    }
    private inner class TextAdapter(val rows: List<Pair<String, (() -> Unit)?>>) : RecyclerView.Adapter<TextHolder>() {
        override fun getItemCount() = rows.size
        override fun onCreateViewHolder(parent: ViewGroup, type: Int) = TextHolder(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; layoutParams = RecyclerView.LayoutParams(-1, -2) })
        override fun onBindViewHolder(holder: TextHolder, position: Int) {
            holder.container.removeAllViews()
            val (text, action) = rows[position]
            holder.container.setPadding(0, 0, if (page == "categories") dp(8) else 0, if (page == "categories") dp(8) else 0)
            holder.container.addView(if (action == null) label(text, if (position == 0) 24f else 16f, position == 0).apply { setPadding(0, dp(12), 0, dp(12)) } else button(text, outlined = page == "categories", action = action).apply { gravity = Gravity.START; setPadding(dp(8), dp(16), dp(8), dp(16)) })
            if (action != null && page != "categories") holder.container.addView(rule())
        }
    }
    private class TextHolder(val container: LinearLayout) : RecyclerView.ViewHolder(container)
    private inner class BookAdapter : androidx.recyclerview.widget.ListAdapter<ShelfRow, BookHolder>(object : androidx.recyclerview.widget.DiffUtil.ItemCallback<ShelfRow>() {
        override fun areItemsTheSame(old: ShelfRow, new: ShelfRow) = old.key == new.key
        override fun areContentsTheSame(old: ShelfRow, new: ShelfRow) = old == new
    }) {
        override fun onCreateViewHolder(parent: ViewGroup, type: Int) = BookHolder(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; layoutParams = RecyclerView.LayoutParams(-1, -2) })
        override fun onBindViewHolder(holder: BookHolder, position: Int) {
            holder.jobs.forEach { it.cancel() }; holder.jobs.clear(); holder.container.removeAllViews()
            val row = getItem(position)
            when (row.kind) {
                "book" -> holder.container.addView(bookTile(row.book!!, ((availableWidth() / grid.spanCount - dp(12)) * 1.45f).toInt().coerceIn(dp(110), dp(240)), holder, true))
                "heading" -> holder.container.addView(label(row.text, 23f, true).apply { setPadding(dp(6), dp(4), 0, dp(18)) })
                "empty" -> {
                    holder.container.addView(label(row.text, 22f, true).apply { setPadding(0, dp(28), 0, dp(24)) })
                    holder.container.addView(button(getString(if (displayBooks.isEmpty()) R.string.add_folder else R.string.show_all)) { if (displayBooks.isEmpty()) pick(true) else navigate("library") })
                }
                "featured" -> {
                    val rail = LinearLayout(this@MainActivity)
                    val number = if (resources.configuration.screenWidthDp < 480) 2 else 4
                    val height = minOf(((availableWidth() / number - dp(12)) * 1.5f).toInt(), (list.height * .28f).toInt()).coerceIn(dp(120), dp(260))
                    row.books.take(number).forEach { rail.addView(bookTile(it, height, holder, false), LinearLayout.LayoutParams(0, -2, 1f)) }
                    repeat((number - row.books.size).coerceAtLeast(0)) { rail.addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f)) }
                    holder.container.addView(rail)
                }
                "collections" -> {
                    val tiles = LinearLayout(this@MainActivity).apply { setPadding(0, dp(12), 0, dp(8)) }
                    val recent = row.books.filter { it.lastOpenedAt > 0 }
                    tiles.addView(collection(getString(R.string.book_categories), row.books, if (v4.settings.enabled) catalogTotal else row.books.size, holder) { navigate("categories") }, LinearLayout.LayoutParams(0, -2, 1f))
                    tiles.addView(collection(getString(R.string.recent), recent, if (v4.settings.enabled) catalogRecent else recent.size, holder) { navigate("library", recent = true) }, LinearLayout.LayoutParams(0, -2, 1f))
                    holder.container.addView(tiles)
                }
                "pager" -> {
                    holder.container.addView(button("上一頁") { catalogOffset = maxOf(0, catalogOffset - 50); renderList(); list.scrollToPosition(0) }.apply { isEnabled = catalogOffset > 0 })
                    holder.container.addView(button("下一頁") { catalogOffset += 50; renderList(); list.scrollToPosition(0) }.apply { isEnabled = catalogOffset + 50 < catalogTotal })
                }
                "shortcuts" -> {
                    val pair = LinearLayout(this@MainActivity)
                    listOf(Triple(R.string.invite_books, R.string.add_folder, { pick(true) }), Triple(R.string.make_it_yours, R.string.room_settings, { navigate("settings") })).forEach { (title, subtitle, action) ->
                        val item = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), 0, dp(10), dp(18)); minimumHeight = dp(96); isFocusable = true; setOnClickListener { action() } }
                        item.addView(rule()); item.addView(label(getString(title), 20f, true).apply { setPadding(0, dp(12), 0, dp(6)) }); item.addView(label(getString(subtitle), 14f))
                        pair.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
                    }
                    holder.container.addView(pair)
                }
            }
        }
        override fun onViewRecycled(holder: BookHolder) { holder.jobs.forEach { it.cancel() }; holder.jobs.clear(); holder.container.removeAllViews() }
    }
    private class BookHolder(val container: LinearLayout, val jobs: MutableList<Job> = mutableListOf()) : RecyclerView.ViewHolder(container)
}
