package org.kobostyle.launcher.librarian

import kotlinx.coroutines.*
import org.kobostyle.launcher.data.db.Book
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

data class WireResponse(val code: Int, val body: String = "", val retryAfterSeconds: Int = 1, val errorCode: String = "unknown")
fun interface CuratorTransport { suspend fun post(key: String, body: String): WireResponse }

/** Fixed HTTPS destination, no redirects, no tools, no logging of requests or error bodies. */
class OpenAiTransport(private val connectionFactory: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }) : CuratorTransport {
    override suspend fun post(key: String, body: String): WireResponse = coroutineScope {
        val connection = connectionFactory(URL("https://api.openai.com/v1/responses"))
        val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { connection.disconnect() }
        }
        try {
            withContext(Dispatchers.IO) {
                connection.requestMethod = "POST"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000; connection.readTimeout = 20_000
                connection.setRequestProperty("Authorization", "Bearer $key")
                connection.setRequestProperty("Content-Type", "application/json")
                val bytes = body.toByteArray(Charsets.UTF_8)
                require(bytes.size <= 40_000)
                connection.doOutput = true; connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
                ensureActive()
                val code = connection.responseCode
                if (code !in 200..299) WireResponse(code,
                    retryAfterSeconds = connection.getHeaderField("Retry-After")?.toIntOrNull()?.coerceIn(1, 3) ?: 1,
                    errorCode = if (code in 400..599) readErrorCode(connection) else "unknown")
                else {
                    val output = java.io.ByteArrayOutputStream()
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (output.size() + read > 262_144) throw CuratorFailure("invalid_response")
                            output.write(buffer, 0, read)
                        }
                    }
                    WireResponse(code, output.toString("UTF-8"))
                }
            }
        } finally { try { watcher.cancelAndJoin() } finally { connection.disconnect() } }
    }

    private suspend fun readErrorCode(connection: HttpsURLConnection): String = try {
        connection.errorStream?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (output.size() + read > 8192) return@use "unknown"
                output.write(buffer, 0, read)
            }
            ApiErrorCode.parse(output.toString("UTF-8"))
        } ?: "unknown"
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { "unknown" }
}

class CuratorService(
    private val preferences: AiPreferences,
    private val vault: KeyVault,
    private val transport: CuratorTransport,
    private val connected: () -> Boolean,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val timeoutMillis: Long = 45_000,
    private val waitRetry: suspend (Long) -> Unit = { delay(it) },
    private val retryTransient: Boolean = true
) {
    private data class Entry(val time: Long, val result: CuratorResult)
    private val cache = linkedMapOf<String, Entry>()
    private var cacheEpoch = 0L
    @Synchronized fun clearCache() { cacheEpoch++; cache.clear() }
    @Synchronized private fun cached(key: String) = cache[key]?.takeIf { clock() - it.time in 0..600_000 }?.result
    @Synchronized private fun save(key: String, result: CuratorResult, epoch: Long) {
        if (cacheEpoch != epoch) return
        cache[key] = Entry(clock(), result)
        while (cache.size > 8) cache.remove(cache.keys.first())
    }
    suspend fun curate(candidates: List<Book>, choice: Choice, online: Boolean): CuratorResult {
        val started = clock()
        fun fallback(reason: String): CuratorResult {
            preferences.outcome(reason, clock() - started)
            return LocalCurator.offline(candidates, choice, reason)
        }
        if (candidates.isEmpty()) return fallback("empty")
        val config = preferences.config()
        if (!online || !config.enabled) return fallback("disabled")
        if (!vault.exists()) return fallback("no_key")
        if (!connected()) return fallback("offline")
        val request = ResponsesContract.request(candidates, choice, config.model)
        val binding = candidates.joinToString("|") { "${it.identity}:${it.lastModified}:${it.size}:${it.lastOpenedAt}:${it.scanStatus}" }
        val cacheKey = MessageDigest.getInstance("SHA-256").digest((request + binding).toByteArray()).joinToString("") { "%02x".format(it) }
        cached(cacheKey)?.let { preferences.outcome("cache", clock() - started); return it.copy(source = "cache") }
        val epoch = synchronized(this) { cacheEpoch }
        return try {
            val result = withTimeout(timeoutMillis) {
                var response: WireResponse? = null
                repeat(2) { attempt ->
                    if (response?.code in 200..299) return@repeat
                    ensureActive()
                    if (!preferences.reserve()) throw CuratorFailure("limit")
                    val key = try { vault.read() ?: throw CuratorFailure("no_key") }
                        catch (_: Exception) { throw CuratorFailure("key_storage") }
                    preferences.beginHttpAttempt()
                    response = transport.post(key, request)
                    val code = response!!.code
                    preferences.httpResponse(code, response!!.errorCode)
                    if (code in 200..299) return@repeat
                    val kind = when (code) { 401, 403 -> "authentication"; 429 -> "rate_limit"; in 500..599 -> "server"; else -> "http" }
                    if (retryTransient && attempt == 0 && (code == 429 || code in 500..599)) waitRetry(response!!.retryAfterSeconds.coerceIn(1, 3) * 1000L)
                    else throw CuratorFailure(kind)
                }
                ResponsesContract.parse(checkNotNull(response).body, candidates, choice)
            }
            currentCoroutineContext().ensureActive()
            preferences.usage(result.inputTokens, result.outputTokens)
            preferences.outcome("ok", clock() - started)
            save(cacheKey, result, epoch)
            result
        } catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); fallback("timeout") }
        catch (e: CancellationException) { throw e }
        catch (e: CuratorFailure) { fallback(e.kind) }
        catch (e: org.kobostyle.launcher.spirits.GateFailure) { fallback(e.reason) }
        catch (_: java.net.SocketTimeoutException) { fallback("timeout") }
        catch (_: Exception) { fallback("network") }
    }
}
