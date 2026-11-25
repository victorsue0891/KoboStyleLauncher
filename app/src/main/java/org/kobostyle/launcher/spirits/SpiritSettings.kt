package org.kobostyle.launcher.spirits

import android.content.SharedPreferences
import java.time.LocalDate

/** No model discovery or remotely refreshed prices. All amounts are integer micro-USD. */
class SpiritSettings(private val prefs: SharedPreferences) {
    var enabled: Boolean
        get() = prefs.getBoolean("feature.meow_spirits_v4", false)
        set(value) { check(prefs.edit().putBoolean("feature.meow_spirits_v4", value).commit()) }
    var stopped: Boolean
        get() = prefs.getBoolean("stop_all_ai", false)
        set(value) { check(prefs.edit().putBoolean("stop_all_ai", value).commit()) }
    var paidSpirits: Boolean
        get() = prefs.getBoolean("paid_spirits", false)
        set(value) { check(prefs.edit().putBoolean("paid_spirits", value).commit()) }
    fun budget() = Budget(prefs.getLong("daily", 1_000_000), prefs.getLong("monthly", 5_000_000), prefs.getLong("single", 50_000))
    fun saveBudget(value: Budget) {
        require(value.daily in 1..100_000_000 && value.monthly in 1..1_000_000_000 && value.single in 1..value.daily)
        check(prefs.edit().putLong("daily", value.daily).putLong("monthly", value.monthly).putLong("single", value.single).commit())
    }
    fun price(model: String): Price? {
        if (prefs.getString("price_model", null) != model) return null
        val input = prefs.getLong("price_input", -1); val output = prefs.getLong("price_output", -1)
        val date = prefs.getString("price_date", "").orEmpty()
        return if (input in 0..100_000_000 && output in 0..100_000_000 && date.isNotEmpty()) Price(input, output, date) else null
    }
    fun savePrice(model: String, price: Price) {
        require(model.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}")))
        require(price.input in 0..100_000_000 && price.output in 0..100_000_000)
        LocalDate.parse(price.date)
        check(prefs.edit().putString("price_model", model).putLong("price_input", price.input)
            .putLong("price_output", price.output).putString("price_date", price.date).commit())
    }
    fun clearPrice() {
        check(prefs.edit().remove("price_model").remove("price_input").remove("price_output").remove("price_date").commit())
    }
}
data class Budget(val daily: Long = 1_000_000, val monthly: Long = 5_000_000, val single: Long = 50_000)
// micro-USD per 1M tokens, not a live supplier quote.
data class Price(val input: Long, val output: Long, val date: String) {
    fun cost(inputTokens: Int, outputTokens: Int): Long =
        Math.addExact(Math.multiplyExact(inputTokens.toLong(), input), Math.multiplyExact(outputTokens.toLong(), output))
            .let { (it + 999_999) / 1_000_000 }
}
