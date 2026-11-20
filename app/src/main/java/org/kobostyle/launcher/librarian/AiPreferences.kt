package org.kobostyle.launcher.librarian

import android.content.SharedPreferences
import java.time.LocalDate

data class AiConfig(val enabled: Boolean = false, val model: String = "gpt-4.1-mini", val dailyLimit: Int = 20)

/** Counts attempts, including retries and failed HTTP requests. Not a billing hard cap. */
class AiPreferences(private val prefs: SharedPreferences, private val date: () -> LocalDate = { LocalDate.now() }) {
    fun config() = AiConfig(prefs.getBoolean("enabled", false), prefs.getString("model", "gpt-4.1-mini")!!, prefs.getInt("limit", 20).coerceIn(1, 100))
    @Synchronized fun save(config: AiConfig) {
        require(config.model.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}")))
        require(config.dailyLimit in 1..100)
        check(prefs.edit().putBoolean("enabled", config.enabled).putString("model", config.model).putInt("limit", config.dailyLimit).commit())
    }
    @Synchronized fun count(): Int = if (date().toEpochDay() > prefs.getLong("day", Long.MIN_VALUE)) 0 else prefs.getInt("attempts", 0)
    @Synchronized fun reserve(): Boolean {
        val config = config()
        if (!config.enabled) return false
        val used = count()
        if (used >= config.dailyLimit) return false
        val day = maxOf(date().toEpochDay(), prefs.getLong("day", Long.MIN_VALUE))
        return prefs.edit().putLong("day", day).putInt("attempts", used + 1).commit()
    }
    @Synchronized fun usage(input: Int, output: Int) {
        prefs.edit().putInt("last_input_tokens", input.coerceAtLeast(0)).putInt("last_output_tokens", output.coerceAtLeast(0)).apply()
    }
    @Synchronized fun outcome(kind: String, durationMillis: Long) {
        val safeKind = kind.takeIf { it in setOf("ok", "cache", "empty", "disabled", "no_key", "offline", "key_storage", "limit", "timeout", "authentication", "rate_limit", "server", "http", "invalid_response", "network") } ?: "invalid_response"
        prefs.edit().putString("last_outcome", safeKind).putLong("last_duration_ms", durationMillis.coerceAtLeast(0)).apply()
    }
    fun diagnostics() = org.json.JSONObject().put("ai_enabled", config().enabled).put("daily_limit", config().dailyLimit)
        .put("attempts_today", count()).put("last_input_tokens", prefs.getInt("last_input_tokens", 0))
        .put("last_output_tokens", prefs.getInt("last_output_tokens", 0))
        .put("operation", "recommend").put("last_outcome", prefs.getString("last_outcome", "none"))
        .put("last_duration_ms", prefs.getLong("last_duration_ms", 0))
        .put("app", "KoboStyle").put("report_version", 1)
}
