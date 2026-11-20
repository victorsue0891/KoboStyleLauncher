package org.kobostyle.launcher.librarian

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import org.kobostyle.launcher.R
import org.kobostyle.launcher.data.books.BookFilename

/** One stable page, one static cat, at most four conversational choices. No timed redraws. */
class LibrarianPanel(context: Context, private val action: (DialogueAction, String) -> Unit, private val settings: () -> Unit) : ScrollView(context) {
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var lastState: DialogueState? = null
    private var lastConfig: AiConfig? = null
    private var resetScroll = false
    init { id = R.id.librarian_panel; isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER; addView(body) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 18f) = TextView(context).apply {
        text = value; textSize = size; setTextColor(Color.BLACK); setPadding(0, dp(8), 0, dp(8)); setTextIsSelectable(false)
    }
    private fun button(label: String, onClick: () -> Unit) = Button(context).apply {
        text = label; textSize = 17f; isAllCaps = false; setTextColor(Color.BLACK); minimumHeight = dp(48)
        setPadding(dp(12), dp(8), dp(12), dp(8)); stateListAnimator = null
        background = GradientDrawable().apply { setColor(Color.WHITE); setStroke(dp(1).coerceAtLeast(1), Color.DKGRAY) }
        setOnClickListener { onClick() }
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (resetScroll) { scrollTo(0, 0); resetScroll = false }
    }
    fun render(state: DialogueState, config: AiConfig) {
        if (state == lastState && config == lastConfig) return
        lastState = state; lastConfig = config
        resetScroll = true
        body.removeAllViews(); body.setPadding(dp(24), dp(8), dp(24), dp(16))
        val tools = LinearLayout(context)
        tools.addView(button("返回") { action(DialogueAction.BACK, "") }.apply { isEnabled = state.stage != DialogueStage.WELCOME }, LinearLayout.LayoutParams(0, -2, 1f))
        tools.addView(button("重新開始") { action(DialogueAction.RESET, "") }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        tools.addView(button("AI 設定", settings), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        body.addView(tools)
        body.addView(text("墨喵 · 你的書房館員", 24f).apply { typeface = Typeface.DEFAULT_BOLD })
        val portrait = resources.configuration.screenHeightDp >= resources.configuration.screenWidthDp
        val character = ImageView(context).apply {
            id = R.id.librarian_character
            setImageResource(expressionResource(state.expression)); scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = expressionDescription(state.expression)
        }
        val heading = when (state.stage) {
            DialogueStage.WELCOME -> "歡迎回來！今天想探索哪一個書架？"
            DialogueStage.MOOD -> "今天想用什麼心情讀書？"
            DialogueStage.DURATION -> "想留多少時間給自己？"
            DialogueStage.PREFERENCE -> "要先看看還沒從書房開啟的書嗎？這不代表真正的未讀狀態。"
            DialogueStage.THEMES -> "想逛哪一場小書展？每次一頁一本，自己決定翻頁的速度。"
            DialogueStage.CANDIDATES -> "準備好了。我會先確認館藏可讀，再挑最多三本。" + if (state.choice.query.isNotBlank()) "\n這次想找：${state.choice.query}" else "\n這次留 ${state.choice.minutes} 分鐘閱讀。"
            DialogueStage.LOADING -> "正在整理書架上的線索……你可以隨時取消。"
            DialogueStage.CARDS -> state.result?.dialogue.orEmpty()
            DialogueStage.DETAIL -> "這本有什麼線索值得看看？我只根據書目資料介紹，不假裝讀過全文。"
            DialogueStage.CONFIRM -> "找到想看的書了！按下「開始閱讀」，我會把它交給裝置上的閱讀器。"
        }
        val introduction = LinearLayout(context).apply { orientation = if (portrait && state.stage == DialogueStage.WELCOME) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val imageSize = if (portrait && state.stage == DialogueStage.WELCOME) 220 else if (portrait) 120 else 96
        introduction.addView(character, LinearLayout.LayoutParams(dp(imageSize), dp(imageSize)).apply { if (introduction.orientation == LinearLayout.VERTICAL) gravity = Gravity.CENTER_HORIZONTAL })
        introduction.addView(text(heading), if (introduction.orientation == LinearLayout.VERTICAL) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        body.addView(introduction)
        val mode = when (state.result?.source.takeIf { state.stage in setOf(DialogueStage.CARDS, DialogueStage.DETAIL, DialogueStage.CONFIRM) }) {
            "openai" -> "OpenAI 推薦 · 只使用候選書目"
            "cache" -> "AI 快取 · 翻頁不再發送請求"
            "offline" -> "離線選書 · 依書目與開啟紀錄"
            else -> if (config.enabled) "AI 已啟用 · 只在你要求推薦時傳送候選書目" else "離線模式 · 不需 API Key，也能選書"
        }
        body.addView(text(mode, 14f))
        if (state.stage in setOf(DialogueStage.CARDS, DialogueStage.DETAIL, DialogueStage.CONFIRM)) {
            notice(state.result?.notice.orEmpty()).takeIf { it.isNotEmpty() }?.let { body.addView(text(it, 16f)) }
            state.card?.let { card ->
                body.addView(text("${state.index + 1} / ${state.result!!.cards.size}  ·  ${card.book.title}", 22f).apply { typeface = Typeface.DEFAULT_BOLD })
                val name = BookFilename.parse(BookFilename.name(card.book))
                val author = (name.author ?: card.book.author).ifBlank { "作者未提供" }
                body.addView(text("$author · ${name.category ?: "未分類"}", 16f))
                body.addView(text(if (card.origin == "openai") "AI 建議 · 依書目資料推測" else "本機選書 · 依館藏資料", 14f))
                body.addView(text(card.reason, 17f)); body.addView(text(card.tip, 16f))
            }
        }
        val choices = LinearLayout(context).apply { id = R.id.librarian_choices; orientation = LinearLayout.VERTICAL }
        fun option(label: String, kind: DialogueAction, value: String = "", enabled: Boolean = true) {
            choices.addView(button(label) { action(kind, value) }.apply { isEnabled = enabled }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        when (state.stage) {
            DialogueStage.WELCOME -> { option("陪我選一本", DialogueAction.CHOOSE); option("我想自己說", DialogueAction.TEXT); option("逛逛主題書展", DialogueAction.THEMES); option("回到書庫", DialogueAction.LIBRARY) }
            DialogueStage.MOOD -> { option("輕鬆一下", DialogueAction.MOOD, "relax"); option("學點新知", DialogueAction.MOOD, "learn"); option("深入想想", DialogueAction.MOOD, "deep"); option("給我驚喜", DialogueAction.MOOD, "surprise") }
            DialogueStage.DURATION -> listOf(15, 30, 60).forEach { option("$it 分鐘", DialogueAction.DURATION, it.toString()) }
            DialogueStage.PREFERENCE -> { option("優先尚未從書房開啟", DialogueAction.PREFERENCE, "yes"); option("都可以，也想重溫好書", DialogueAction.PREFERENCE, "no") }
            DialogueStage.THEMES -> { option("輕鬆故事", DialogueAction.THEME, "relax"); option("探索新知", DialogueAction.THEME, "learn"); option("藏書尋寶", DialogueAction.THEME, "surprise"); option("自訂主題", DialogueAction.TEXT) }
            DialogueStage.CANDIDATES -> { option(if (config.enabled) "請墨喵用 AI 推薦" else "請墨喵幫我挑選", DialogueAction.REQUEST); option("這次只用離線選書", DialogueAction.OFFLINE) }
            DialogueStage.LOADING -> option("取消，返回選書", DialogueAction.CANCEL)
            DialogueStage.CARDS -> if (state.card == null) { option("回到書庫", DialogueAction.LIBRARY); option("重新開始", DialogueAction.RESET) } else {
                option("上一本", DialogueAction.PREVIOUS, enabled = state.index > 0)
                option("看看這本", DialogueAction.VIEW, state.card!!.book.identity)
                option("下一本", DialogueAction.NEXT, enabled = state.index + 1 < state.result!!.cards.size)
                option("再挑一批", DialogueAction.MORE)
            }
            DialogueStage.DETAIL -> { option("就讀這本", DialogueAction.CONFIRM); option("回到推薦", DialogueAction.BACK) }
            DialogueStage.CONFIRM -> { option("開始閱讀", DialogueAction.OPEN, state.card?.book?.identity.orEmpty()); option("使用其他閱讀器", DialogueAction.OTHER_READER, state.card?.book?.identity.orEmpty()); option("再看看推薦", DialogueAction.BACK) }
        }
        body.addView(choices)
    }
    companion object {
        fun expressionResource(expression: CatExpression) = when (expression) {
            CatExpression.WELCOME -> R.drawable.cat_welcome
            CatExpression.THINKING -> R.drawable.cat_thinking
            CatExpression.RECOMMEND -> R.drawable.cat_recommend
            CatExpression.CURIOUS -> R.drawable.cat_curious
            CatExpression.HAPPY -> R.drawable.cat_happy
            CatExpression.READING -> R.drawable.cat_reading
        }
        fun expressionDescription(expression: CatExpression) = when (expression) {
            CatExpression.WELCOME -> "墨喵向你招手"
            CatExpression.THINKING -> "墨喵正在思考"
            CatExpression.RECOMMEND -> "墨喵展示推薦書籍"
            CatExpression.CURIOUS -> "墨喵好奇地提問"
            CatExpression.HAPPY -> "墨喵開心地確認選書"
            CatExpression.READING -> "墨喵正在閱讀"
        }
        fun notice(kind: String): String = when (kind) {
            "", "disabled", "empty" -> ""
            "no_key" -> "尚未設定 API Key，先用離線方式挑選。"
            "key_storage" -> "無法解密金鑰，請到 AI 設定重新輸入；先顯示離線選書。"
            "offline", "network" -> "網路暫時無法使用，先顯示離線選書。"
            "timeout" -> "AI 等候逾時，先顯示離線選書。"
            "limit" -> "已達本機每日請求上限，先顯示離線選書。"
            "authentication" -> "API Key 或專案權限無法使用，請檢查 AI 設定；先顯示離線選書。"
            "rate_limit" -> "API 配額不足或暫時限流，先顯示離線選書。"
            "server", "http" -> "AI 服務暫時無法完成請求，請檢查模型設定；先顯示離線選書。"
            "library" -> "書庫暫時無法讀取，請回到書庫確認來源。"
            else -> "AI 未提供完整且有效的館藏建議，先顯示離線選書。"
        }
    }
}
