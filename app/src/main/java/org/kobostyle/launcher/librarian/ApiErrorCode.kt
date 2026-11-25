package org.kobostyle.launcher.librarian

/** Only these fixed labels may leave the error parser. Never retain the server's message. */
internal object ApiErrorCode {
    private val allowed = setOf(
        "invalid_api_key", "insufficient_permissions", "unsupported_country_region_territory",
        "model_not_found", "insufficient_quota", "credit_balance_exhausted",
        "rate_limit_exceeded", "organization_usage_limit_exceeded", "server_error"
    )
    fun safe(value: String?) = value?.takeIf { it in allowed } ?: "unknown"
    fun parse(body: String): String = try {
        safe(StrictJson.obj(body).optJSONObject("error")?.optString("code"))
    } catch (_: Exception) { "unknown" }
}
