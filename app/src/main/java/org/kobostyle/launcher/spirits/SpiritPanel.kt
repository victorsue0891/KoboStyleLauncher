package org.kobostyle.launcher.spirits

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.*
import org.kobostyle.launcher.R

class SpiritPanel(context: Context, private val action: (String) -> Unit) : ScrollView(context) {
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(16)) }
    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
    init { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER; addView(body) }
    fun render(state: SpiritState) {
        body.removeAllViews()
        fun label(value: String, size: Float = 18f) { body.addView(TextView(context).apply { text = value; textSize = size; setTextColor(Color.BLACK); setPadding(0, dp(8), 0, dp(8)) }) }
        fun button(value: String, key: String, enabled: Boolean = !state.loading) {
            body.addView(Button(context).apply {
                text = value; textSize = 17f; isAllCaps = false; minHeight = dp(48); setTextColor(Color.BLACK); isEnabled = enabled
                stateListAnimator = null; background = GradientDrawable().apply { setColor(Color.WHITE); setStroke(dp(1).coerceAtLeast(1), Color.BLACK) }
                setOnClickListener { action(key) }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        button("返回喵館員", "back", true)
        val expression = if (state.loading) R.drawable.cat_thinking else if (state.spirit == null) R.drawable.cat_curious else R.drawable.cat_happy
        body.addView(ImageView(context).apply { setImageResource(expression); contentDescription = "墨喵陪你探索書靈" }, LinearLayout.LayoutParams(dp(120), dp(120)))
        val book = state.book
        if (book == null) { label(if (state.loading) "正在讀取本機書目……" else "請從書庫選一本書，開啟選單中的「召喚書靈」。"); button("前往書庫選書", "library"); return }
        label(book.title, 22f)
        label(book.author.ifBlank { "作者未提供" }, 16f)
        label(if (state.pack == null) "Lite｜僅根據書名與書目，未讀全文" else "Deep｜已匯入知識包，僅依允許的片段", 16f)
        state.spirit?.let { label("${it.name} · ${it.tone}", 20f); label(it.opening); if (it.refined) label("歡迎語經 AI 精修，不代表書籍事實。", 14f) }
        if (state.message.isNotBlank()) label(state.message)
        if (state.loading) { label("處理中……"); button("停止／取消", "cancel", true); return }
        button("開始閱讀原書", "open")
        if (state.spirit == null) button("免費建立 Lite 書靈", "summon")
        button("匯入 PC 知識包（Deep）", "import")
        state.pack?.let { label("已匯入：${it.title}；來源：${it.source}", 15f) }
        label(if (state.boundary?.spoilers == true) "本次允許劇透（可隨時關閉）" else "防劇透：${state.boundary?.chapter?.let { "手動已讀到第 $it 章" } ?: "尚未指定已讀章節，不提供章節內容"}", 16f)
        button("設定已讀章節／劇透範圍", "boundary")
        if (state.spirit != null) {
            button("這章重點", "summary"); button("不劇透地解釋", "explain")
            button("提出一個問題", "quiz"); button("我要自己提問", "question")
            button("使用 AI 精修歡迎語（可能收費）", "refine")
        }
        state.answer?.let { answer ->
            label(answer.text)
            label("依書目或匯入片段作答，並非全書摘要。", 14f)
            answer.citations.forEach { section ->
                label("出處：第 ${section.chapter} 章 · ${section.title} [${section.sectionId}]", 16f)
                label(section.content, 16f)
                if (section.locator.isNotBlank()) label("包內位置：${section.locator}（非閱讀器頁碼）", 14f)
            }
            button("查看原書", "open")
        }
        if (state.pack != null) button("移除知識包（保留原書）", "remove_pack")
        if (state.spirit != null) button("刪除書靈與知識包（保留原書）", "delete")
    }
}
