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
import org.kobostyle.launcher.R
import org.kobostyle.launcher.data.db.Book
import org.kobostyle.launcher.reader.NeoReaderBridge
import org.kobostyle.launcher.platform.HomeRoleHelper
import java.text.DateFormat
import java.util.Date

private data class ShelfRow(val key: String, val kind: String = "book", val text: String = "", val book: Book? = null, val books: List<Book> = emptyList())

open class MainActivity : ComponentActivity() {
    protected open fun libraryModelFactory(): androidx.lifecycle.ViewModelProvider.Factory = defaultViewModelProviderFactory
    protected open fun controlPreferences(): SharedPreferences = getSharedPreferences("reading_controls", MODE_PRIVATE)
    private var volumePaging = false
    private var reversePaging = false
    private val model: LibraryViewModel by viewModels { libraryModelFactory() }
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
    private var displayBooks: List<Book> = emptyList()
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.let { data ->
            val uris = data.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } } ?: listOfNotNull(data.data)
            model.add(uris, choosingTree, data.flags)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = savedInstanceState?.getString("page") ?: "home"
        choosingTree = savedInstanceState?.getBoolean("choosingTree") ?: false
        recentOnly = savedInstanceState?.getBoolean("recentOnly") ?: false
        searchText = savedInstanceState?.getString("searchText").orEmpty()
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
            override fun getSpanSize(position: Int): Int = if (page == "library" && list.adapter === bookAdapter && bookAdapter.currentList.getOrNull(position)?.kind == "book") 1 else grid.spanCount
        }
        content.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(rule())
        val bottom = LinearLayout(this).apply { setPadding(dp(8), dp(4), dp(8), dp(2)) }
        val tabs = listOf(Triple("home", R.string.home, ReaderIcon.Kind.HOME), Triple("library", R.string.library, ReaderIcon.Kind.BOOKS), Triple("apps", R.string.apps, ReaderIcon.Kind.APPS), Triple("settings", R.string.settings, ReaderIcon.Kind.MORE))
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
                launch { model.books.collect { displayBooks = it; renderList() } }
                launch { combine(model.progress, model.message, model.sources) { p, m, s -> Triple(p, m, s) }.collect { updateScanStatus() } }
            }
        }
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
    private fun navigate(destination: String, recent: Boolean = false, query: String = "") {
        page = destination; recentOnly = recent; searchText = query
        render(); list.scrollToPosition(0)
    }
    override fun onResume() { super.onResume(); updateStatus(); model.refresh() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) navigate("home")
    }
    protected open fun handleContentKey(event: KeyEvent): Boolean {
        val standard = event.keyCode == KeyEvent.KEYCODE_PAGE_UP || event.keyCode == KeyEvent.KEYCODE_PAGE_DOWN
        val volume = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if ((page == "home" || page == "library") && (standard || (volumePaging && volume)) && ::list.isInitialized) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                var direction = if (event.keyCode == KeyEvent.KEYCODE_PAGE_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1
                if (!standard && reversePaging) direction = -direction
                list.scrollBy(0, direction * (list.height * .9f).toInt())
            }
            return true
        }
        return false
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page); outState.putBoolean("choosingTree", choosingTree)
        outState.putBoolean("recentOnly", recentOnly); outState.putString("searchText", searchText)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { bridge.close(); super.onDestroy() }
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
        actions.removeAllViews(); libraryActions.clear()
        actions.visibility = if (page == "library") View.VISIBLE else View.GONE
        if (page == "library") {
            libraryActions += button(getString(R.string.add_folder)) { pick(true) }
            libraryActions += button(getString(R.string.add_files)) { pick(false) }
            libraryActions += button(getString(R.string.cancel)) { model.cancel() }.apply { tag = "cancel" }
            libraryActions.forEach { actions.addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }) }
        }
        navigation.forEach { (key, tab) ->
            val selected = key == page
            tab.isSelected = selected; tab.setTypeface(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
            tab.setCompoundDrawablesWithIntrinsicBounds(null, ReaderIcon(tab.tag as ReaderIcon.Kind, resources.displayMetrics.density, selected), null, null)
            tab.background = null
        }
        updateScanStatus(); renderList()
    }
    private fun showSearch() {
        val input = EditText(this).apply { id = R.id.search_input; setSingleLine(true); hint = getString(R.string.search_hint); setText(searchText) }
        AlertDialog.Builder(this).setTitle(R.string.search_books).setView(input)
            .setPositiveButton(R.string.search_action) { _, _ -> navigate("library", query = input.text.toString().trim()) }
            .setNeutralButton(R.string.show_all) { _, _ -> navigate("library") }
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
        if (page == "apps") {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = packageManager.queryIntentActivities(intent, 0).filter { it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled }.sortedBy { it.loadLabel(packageManager).toString() }
            list.adapter = TextAdapter(listOf(getString(R.string.visible_apps) to null) + apps.map { info ->
                info.loadLabel(packageManager).toString() to { safeStart(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName(info.activityInfo.packageName, info.activityInfo.name))) }
            })
        } else if (page == "settings") {
            list.adapter = TextAdapter(listOf(
                getString(R.string.room_settings) to null,
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
                val filtered = displayBooks.filter { (!recentOnly || it.lastOpenedAt > 0) && (searchText.isBlank() || it.title.contains(searchText, true) || it.author.contains(searchText, true)) }
                val title = when { searchText.isNotBlank() -> getString(R.string.search_results, searchText); recentOnly -> getString(R.string.recent); else -> getString(R.string.my_books) }
                rows += ShelfRow("shelf-heading", "heading", title + "  ·  " + getString(R.string.book_count, filtered.size))
                if (filtered.isEmpty()) rows += ShelfRow("empty", "empty", getString(if (displayBooks.isEmpty()) R.string.empty else R.string.no_matches))
                else rows.addAll(filtered.map { ShelfRow("shelf-${it.identity}", book = it) })
            }
            bookAdapter.submitList(rows)
        }
    }
    private fun open(book: Book, chooser: Boolean) {
        lifecycleScope.launch {
            val accessible = withContext(Dispatchers.IO) {
                try { contentResolver.openFileDescriptor(Uri.parse(book.uri), "r")?.use { true } ?: false } catch (_: Exception) { false }
            }
            if (!accessible) { model.repository.dao.bookStatus(book.identity, "unavailable"); toast(R.string.source_error); return@launch }
            bridge.open(Uri.parse(book.uri), book.mime, chooser) { model.opened(book) }?.let { toast(it) }
        }
    }
    private fun bookMenu(book: Book) {
        AlertDialog.Builder(this).setTitle(book.title).setItems(arrayOf(getString(R.string.open_book), getString(R.string.choose_reader))) { _, which -> open(book, which == 1) }.show()
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
            isFocusable = true; setOnClickListener { open(book, false) }; setOnLongClickListener { bookMenu(book); true }
            layoutParams = LinearLayout.LayoutParams(minOf((height * .70f).toInt(), availableWidth() / (if (menu) grid.spanCount else if (resources.configuration.screenWidthDp < 480) 2 else 4) - dp(12)), height)
        }
        tile.addView(cover)
        val title = label(book.title, 16f, true).apply { maxLines = 2; minLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(8), 0, dp(2)); setOnClickListener { open(book, false) } }
        if (menu) {
            val titleRow = LinearLayout(this).apply { gravity = Gravity.TOP }
            titleRow.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            titleRow.addView(button("⋯", outlined = false) { bookMenu(book) }.apply {
                textSize = 22f; setPadding(0, 0, 0, 0); contentDescription = book.title + " · " + getString(R.string.book_options)
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            tile.addView(titleRow)
            tile.addView(label(book.author.ifBlank { getString(R.string.unknown_author) }, 14f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
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
            holder.container.addView(if (action == null) label(text, if (position == 0) 24f else 16f, position == 0).apply { setPadding(0, dp(12), 0, dp(12)) } else button(text, outlined = false, action = action).apply { gravity = Gravity.START; setPadding(0, dp(12), 0, dp(12)) })
            if (action != null) holder.container.addView(rule())
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
                    tiles.addView(collection(getString(R.string.my_books), row.books, row.books.size, holder) { navigate("library") }, LinearLayout.LayoutParams(0, -2, 1f))
                    tiles.addView(collection(getString(R.string.recent), recent, recent.size, holder) { navigate("library", recent = true) }, LinearLayout.LayoutParams(0, -2, 1f))
                    holder.container.addView(tiles)
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
