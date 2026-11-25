package org.kobostyle.launcher.spirits

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale
import kotlin.coroutines.resume

object SpiritDialogs {
    fun usage(activity: ComponentActivity, runtime: SpiritRuntime) {
        activity.lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    val day = LocalDate.now().toString(); val dao = runtime.database.dao()
                    "今日已記錄／保留：${usd(dao.dayCost(day))}\n本月已記錄／保留：${usd(dao.monthCost(day.take(7)))}\n\n" +
                        dao.latestUsage().joinToString("\n\n") { "${it.day} · ${it.action} · ${it.model}\n${it.status} · ${usd(it.chargedMicros)}\n輸入 ${it.inputTokens}／輸出 ${it.outputTokens} tokens" }
                } catch (_: Exception) { "暫時無法讀取用量紀錄。" }
            }
            val label = TextView(activity).apply { this.text = "$text\n\nUNKNOWN_BILLING 表示結果不明，保守保留估計額度。這是 V4 本機紀錄，不是供應商帳單，也不包含其他裝置或舊版請求。"; textSize = 17f; setPadding(24, 16, 24, 16) }
            AlertDialog.Builder(activity).setTitle("AI 用量紀錄").setView(ScrollView(activity).apply { addView(label) }).setPositiveButton("關閉", null).show()
        }
    }
    fun usd(micros: Long) = String.format(Locale.US, "USD %.6f", micros / 1_000_000.0)
    suspend fun confirm(activity: ComponentActivity, preview: CostPreview): Boolean = withContext(Dispatchers.Main) {
        if (activity.isFinishing || activity.isDestroyed) return@withContext false
        suspendCancellableCoroutine { continuation ->
            fun done(value: Boolean) { if (continuation.isActive) continuation.resume(value) }
            val dialog = AlertDialog.Builder(activity).setTitle("確認這次 AI 請求")
                .setMessage("${preview.model}\n本機估計上限：${usd(preview.estimatedMicros)}\n輸入採保守估算 ${preview.estimatedInput} tokens；輸出最多 ${preview.maxOutput} tokens。\n單價確認日：${preview.priceDate}\n\n推薦只傳候選書目；書靈只傳這本書的已允許片段及問題。費用是估計，不是帳單；逾時也可能已扣款。")
                .setNegativeButton("取消") { _, _ -> done(false) }
                .setPositiveButton("同意，送出一次") { _, _ -> done(true) }
                .setOnCancelListener { done(false) }.create()
            dialog.setOnDismissListener { done(false) }
            continuation.invokeOnCancellation { activity.runOnUiThread { dialog.dismiss() } }
            dialog.show()
        }
    }
    fun settings(activity: ComponentActivity, runtime: SpiritRuntime, model: String, changed: () -> Unit) {
        val prefs = runtime.settings
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 16, 28, 16) }
        fun label(text: String) { body.addView(TextView(activity).apply { this.text = text; textSize = 17f }) }
        fun check(text: String, value: Boolean) = CheckBox(activity).apply { this.text = text; textSize = 17f; minHeight = (48 * resources.displayMetrics.density).toInt(); isChecked = value; body.addView(this) }
        fun input(text: String, value: String): EditText {
            label(text)
            return EditText(activity).apply { setSingleLine(true); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setText(value); body.addView(this) }
        }
        val enabled = check("啟用 V4 書靈與分頁書庫", prefs.enabled)
        val paid = check("允許書靈付費問答／人格精修（每次另行確認）", prefs.paidSpirits)
        val stopped = check("緊急停止所有 AI（包含原有推薦）", prefs.stopped)
        stopped.setOnCheckedChangeListener { _, value -> if (value) runtime.gateway.stop() }
        label("免費 Lite 與匯入不呼叫 AI。付費功能還需要在喵館員 AI 設定啟用 OpenAI 並保存 Key。啟動、翻頁、掃描不會孵化或轉換任何書籍。停用 V4 會保留資料並回到原功能。")
        val budget = prefs.budget(); val price = prefs.price(model)
        fun money(v: Long) = BigDecimal.valueOf(v, 6).toPlainString()
        val day = input("每日預算 USD", money(budget.daily)); val month = input("每月預算 USD", money(budget.monthly)); val single = input("單次估計上限 USD", money(budget.single))
        label("模型：$model。請自行核對供應商單價；留空時費用未知，V4 付費請求將被拒絕。沒有背景價格更新。")
        val incoming = input("每百萬輸入 tokens 的 USD 單價", price?.let { money(it.input) }.orEmpty())
        val outgoing = input("每百萬輸出 tokens 的 USD 單價", price?.let { money(it.output) }.orEmpty())
        label("本機預算不是供應商帳務硬限制。可於 OpenAI Project Limits 設定平台提供的限制；清除 App 資料會清除本機帳本。已送出而結果不明的請求保留估計額度，不自動重試。")
        val dialog = AlertDialog.Builder(activity).setTitle("書靈與費用設定").setView(ScrollView(activity).apply { addView(body) })
            .setNegativeButton("關閉", null).setPositiveButton("儲存", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                fun amount(text: EditText) = BigDecimal(text.text.toString()).movePointRight(6).longValueExact()
                val value = Budget(amount(day), amount(month), amount(single))
                require(value.daily in 1..100_000_000 && value.monthly in 1..1_000_000_000 && value.single in 1..value.daily)
                val p = if (incoming.text.isNotBlank() || outgoing.text.isNotBlank()) Price(amount(incoming), amount(outgoing), LocalDate.now().toString()) else null
                if (p != null) require(p.input in 0..100_000_000 && p.output in 0..100_000_000)
                prefs.saveBudget(value); if (p != null) prefs.savePrice(model, p) else prefs.clearPrice()
                prefs.enabled = enabled.isChecked; prefs.paidSpirits = paid.isChecked; prefs.stopped = stopped.isChecked
                changed(); dialog.dismiss()
            } catch (_: Exception) { Toast.makeText(activity, "請輸入有效且在範圍內的 USD 金額（最多六位小數）。", Toast.LENGTH_LONG).show() }
        } }
        dialog.show()
    }
    fun failure(reason: String): String = when (reason) {
        "stopped" -> "所有 AI 已緊急停止；仍可離線閱讀。"
        "unknown_price" -> "費用未知，未送出。請先在書靈與費用設定確認此模型單價。"
        "budget" -> "已達本機次數或估計費用上限，未送出。"
        "busy", "duplicate" -> "已有相同或其他 AI 請求處理中，未重複送出。"
        "clock_changed" -> "裝置日期早於既有用量紀錄，暫停付費請求。"
        "cancelled", "settings_changed" -> "已取消，或設定已變更。"
        "disabled" -> "書靈 AI 未啟用，仍可使用離線資料。"
        "no_key" -> "尚未設定 API Key。"
        "offline" -> "目前沒有網際網路連線；未送出 AI 請求。"
        "input_limit" -> "內容超過本次限制，請縮短問題或片段。"
        else -> "暫時無法完成，請稍後手動重試；已送出的請求可能已計費。"
    }
}
