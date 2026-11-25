package org.kobostyle.launcher.librarian

import android.app.AlertDialog
import android.graphics.Color
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.kobostyle.launcher.R

object LibrarianDialogs {
    private fun box(activity: ComponentActivity) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (20 * resources.displayMetrics.density).toInt(); setPadding(padding, padding, padding, padding)
    }
    private fun label(activity: ComponentActivity, text: String) = TextView(activity).apply { this.text = text; textSize = 16f; setTextColor(Color.BLACK); setPadding(0, 8, 0, 8) }
    fun textEntry(activity: ComponentActivity, submit: (String) -> Unit) {
        val body = box(activity)
        val input = EditText(activity).apply {
            id = R.id.librarian_query; hint = "例如：想找輕鬆的小說，今晚讀十五分鐘"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4; isSaveEnabled = false; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        val count = label(activity, "0 / 200 字")
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, countValue: Int) { val text = s.toString(); count.text = "${text.codePointCount(0, text.length)} / 200 字" }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        body.addView(label(activity, "只描述想讀什麼，請勿貼上書籍全文或個人機密。未啟用 AI 時，只用書名、作者與分類關鍵字比對。"))
        body.addView(input); body.addView(count)
        val dialog = AlertDialog.Builder(activity).setTitle("跟墨喵說說").setView(body)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton("開始找書", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = input.text.toString().trim()
            if (value.isBlank() || value.codePointCount(0, value.length) > 200) input.error = "請輸入 1 到 200 字"
            else { submit(value); dialog.dismiss() }
        } }
        dialog.setOnDismissListener { input.text.clear() }
        dialog.show()
    }
    fun settings(activity: ComponentActivity, model: LibrarianViewModel, changed: () -> Unit, export: () -> Unit) {
        val body = box(activity)
        val config = model.preferences.config()
        val enable = CheckBox(activity).apply { text = "啟用 OpenAI 選書"; isChecked = config.enabled }
        body.addView(enable)
        body.addView(label(activity, "AI 預設關閉。啟用後，只在你要求推薦時，把最多 20 本候選書名、作者、類別及當次需求傳給 OpenAI；不傳書籍全文、檔案路徑或閱讀筆記。"))
        val credentials = box(activity)
        val key = EditText(activity).apply {
            id = R.id.librarian_api_key
            hint = if (model.hasKey()) "已保存 Key；留空保留" else "輸入 OpenAI Project API Key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true); isSaveEnabled = false; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
        credentials.addView(label(activity, "金鑰僅存於本機，以 Android Keystore 加密。App 執行時仍需解密，無法防止裝置遭入侵後的擷取；建議使用獨立 Project Key，並在 OpenAI 管理用量及撤銷。"))
        credentials.addView(key)
        credentials.visibility = if (enable.isChecked) View.VISIBLE else View.GONE
        enable.setOnCheckedChangeListener { _, checked -> credentials.visibility = if (checked) View.VISIBLE else View.GONE }
        body.addView(credentials)
        body.addView(label(activity, "模型 ID（需支援 Responses API 與 Structured Outputs）"))
        val modelInput = EditText(activity).apply { setSingleLine(true); setText(config.model) }; body.addView(modelInput)
        body.addView(label(activity, "每日請求上限 1–100（包含失敗及重試；目前 ${model.preferences.count()} 次）"))
        val limit = EditText(activity).apply { inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine(true); setText(config.dailyLimit.toString()) }; body.addView(limit)
        body.addView(label(activity, "API 另行計費；這只是本機軟限制，不是 OpenAI 帳務硬上限。store:false 不等於零保留，仍適用 OpenAI 資料處理政策。頁面翻頁不發送請求。"))
        body.addView(label(activity, "對話最多保留六個操作回合，暫存在記憶體；App 程序結束後清除。AI 卡片快取也僅在記憶體，最多八組、十分鐘。"))
        body.addView(Button(activity).apply { text = "清空會話與 AI 快取"; setOnClickListener { model.clearSessionAndCache(); changed(); Toast.makeText(activity, "已清空", Toast.LENGTH_SHORT).show() } })
        body.addView(Button(activity).apply { text = "匯出不含書目與金鑰的診斷報告"; setOnClickListener { export() } })
        val dialog = AlertDialog.Builder(activity).setTitle("喵館員 AI 設定").setView(ScrollView(activity).apply { addView(body) })
            .setPositiveButton("儲存", null).setNegativeButton("關閉", null).setNeutralButton("刪除金鑰", null).create()
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val daily = limit.text.toString().toIntOrNull()
                val name = modelInput.text.toString().trim()
                if (daily !in 1..100) { limit.error = "請輸入 1–100"; return@setOnClickListener }
                if (!name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}"))) { modelInput.error = "請輸入有效的模型 ID"; return@setOnClickListener }
                val value = if (enable.isChecked) key.text.toString().trim() else ""
                if (enable.isChecked && value.isBlank() && !model.hasKey()) { key.error = "啟用前請先輸入 Key"; return@setOnClickListener }
                fun save() {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    activity.lifecycleScope.launch {
                        try { model.saveSettings(AiConfig(enable.isChecked, name, daily!!), value); key.text.clear(); changed(); dialog.dismiss() }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { Toast.makeText(activity, "無法安全儲存設定，請確認金鑰與裝置金鑰庫。", Toast.LENGTH_LONG).show(); dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true }
                    }
                }
                if (enable.isChecked && !config.enabled) AlertDialog.Builder(activity).setTitle("啟用 AI 選書")
                    .setMessage("同意把候選書目及當次需求傳送至 OpenAI，並理解 API 計費及裝置端金鑰風險？儲存設定不會立即發出請求。")
                    .setNegativeButton("先不要", null).setPositiveButton("同意並儲存") { _, _ -> save() }.show()
                else save()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                activity.lifecycleScope.launch {
                    try { model.deleteKey(); key.text.clear(); enable.isChecked = false; changed(); dialog.dismiss() }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { Toast.makeText(activity, "刪除金鑰未完成；AI 已停用。請稍後重試。", Toast.LENGTH_LONG).show() }
                }
            }
        }
        dialog.setOnDismissListener { key.text.clear() }
        dialog.show()
    }
}
