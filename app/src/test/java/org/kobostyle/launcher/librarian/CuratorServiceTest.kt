package org.kobostyle.launcher.librarian

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class CuratorServiceTest {
    private val books = (0..2).map(::sampleBook)
    private fun prefs(date: () -> LocalDate = { LocalDate.of(2026, 11, 20) }) = AiPreferences(
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("test-${UUID.randomUUID()}", 0), date)
    @Test fun disabledMissingKeyAndNoNetworkNeverSendOrDecrypt() = runBlocking {
        for (mode in listOf("disabled", "no_key", "offline")) {
            val prefs = prefs(); if (mode != "disabled") prefs.save(AiConfig(true))
            val vault = FakeVault(if (mode == "no_key") null else "test-key-not-real")
            val service = CuratorService(prefs, vault, CuratorTransport { _, _ -> error("Must not send") }, { mode != "offline" })
            val result = service.curate(books, Choice(), true)
            assertEquals("offline", result.source); assertEquals(mode, result.notice); assertEquals(0, vault.reads); assertEquals(0, prefs.count())
        }
    }
    @Test fun successfulResponseIsCachedUntilTtlAndBoundToActualIds() = runBlocking {
        val prefs = prefs().apply { save(AiConfig(true)) }; var calls = 0; var now = 0L
        val service = CuratorService(prefs, FakeVault(), CuratorTransport { _, _ -> calls++; WireResponse(200, envelope().toString()) }, { true }, { now })
        assertEquals("openai", service.curate(books, Choice(), true).source)
        assertEquals("cache", service.curate(books, Choice(), true).source)
        assertEquals(1, calls)
        val changed = books.map { it.copy(identity = "different-${it.identity}") }
        assertEquals(changed, service.curate(changed, Choice(), true).cards.map { it.book })
        assertEquals(2, calls)
        now = 600_001
        assertEquals("openai", service.curate(books, Choice(), true).source)
        assertEquals(3, calls)
        assertEquals(200, prefs.diagnostics().getInt("last_input_tokens"))
    }
    @Test fun httpFailuresRetryOnlyTransientCodesAndFallBack() = runBlocking {
        for ((code, expected) in mapOf(401 to "authentication", 403 to "authentication", 429 to "rate_limit", 500 to "server", 503 to "server", 302 to "http", 400 to "http")) {
            val prefs = prefs().apply { save(AiConfig(true)) }; var calls = 0; var waits = 0
            val service = CuratorService(prefs, FakeVault(), CuratorTransport { _, _ -> calls++; WireResponse(code, "sensitive error must not be shown", 900) }, { true }, waitRetry = { delay -> assertTrue(delay <= 3000); waits++ })
            val result = service.curate(books, Choice(), true)
            assertEquals(expected, result.notice); assertEquals("offline", result.source)
            assertEquals(if (code == 429 || code >= 500) 2 else 1, calls)
            assertEquals(calls, prefs.count()); assertEquals(calls - 1, waits)
            assertFalse(result.toString().contains("sensitive error"))
            assertEquals(code, prefs.diagnostics().getInt("last_http_status"))
            assertFalse(prefs.diagnostics().toString().contains("sensitive error"))
        }
    }
    @Test fun diagnosticsSanitizeErrorCodesAndResetBeforeNewAttempt() {
        val prefs = prefs()
        prefs.httpResponse(401, "invalid_api_key")
        assertEquals("invalid_api_key", prefs.diagnostics().getString("last_error_code"))
        prefs.httpResponse(403, "SECRET fake credential")
        assertEquals("unknown", prefs.diagnostics().getString("last_error_code"))
        assertFalse(prefs.diagnostics().toString().contains("SECRET"))
        prefs.beginHttpAttempt()
        assertEquals(0, prefs.diagnostics().getInt("last_http_status"))
        assertEquals("none", prefs.diagnostics().getString("last_error_code"))
        prefs.httpResponse(200, "invalid_api_key")
        assertEquals("none", prefs.diagnostics().getString("last_error_code"))
    }
    @Test fun retrySuccessAndEveryAttemptUsesBudget() = runBlocking {
        val prefs = prefs().apply { save(AiConfig(true)) }; var calls = 0
        val service = CuratorService(prefs, FakeVault(), CuratorTransport { _, _ -> calls++; if (calls == 1) WireResponse(429) else WireResponse(200, envelope().toString()) }, { true }, waitRetry = {})
        assertEquals("openai", service.curate(books, Choice(), true).source)
        assertEquals(2, prefs.count())
        val limited = prefs().apply { save(AiConfig(true, dailyLimit = 1)) }; calls = 0
        val service2 = CuratorService(limited, FakeVault(), CuratorTransport { _, _ -> calls++; WireResponse(500) }, { true }, waitRetry = {})
        assertEquals("limit", service2.curate(books, Choice(), true).notice); assertEquals(1, calls)
    }
    @Test fun timeoutFallsBackButUserCancellationPropagates() = runBlocking {
        val prefs = prefs().apply { save(AiConfig(true)) }
        val service = CuratorService(prefs, FakeVault(), CuratorTransport { _, _ -> awaitCancellation() }, { true }, timeoutMillis = 30)
        assertEquals("timeout", service.curate(books, Choice(), true).notice)
        val entered = CompletableDeferred<Unit>()
        val cancellable = CuratorService(prefs, FakeVault(), CuratorTransport { _, _ -> entered.complete(Unit); awaitCancellation() }, { true })
        var completed = false
        val job = launch { cancellable.curate(books, Choice(), true); completed = true }
        entered.await(); job.cancelAndJoin()
        assertFalse(completed)
    }
    @Test fun invalidBodyAndRefusalUseLocalCards() = runBlocking {
        val prefs = prefs().apply { save(AiConfig(true)) }
        val service = CuratorService(prefs, FakeVault(), CuratorTransport { _, _ -> WireResponse(200, "{") }, { true })
        val result = service.curate(books, Choice(), true)
        assertEquals("invalid_response", result.notice); assertEquals(books, result.cards.map { it.book })
    }
    @Test fun clearingDuringInFlightRequestDoesNotRepopulateCache() = runBlocking {
        val prefs = prefs().apply { save(AiConfig(true)) }; var calls = 0
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val service = CuratorService(prefs, FakeVault(), CuratorTransport { _, _ -> calls++; if (calls == 1) { entered.complete(Unit); release.await() }; WireResponse(200, envelope().toString()) }, { true })
        val running = async { service.curate(books, Choice(), true) }
        entered.await(); service.clearCache(); release.complete(Unit); running.await()
        assertEquals("openai", service.curate(books, Choice(), true).source); assertEquals(2, calls)
    }
    @Test fun dailyLimitIsAtomicAndClockRollbackDoesNotResetAllowance() = runBlocking {
        var date = LocalDate.of(2026, 11, 20)
        val prefs = prefs { date }.apply { save(AiConfig(true, dailyLimit = 20)) }
        val results = (0 until 50).map { async(Dispatchers.IO) { prefs.reserve() } }.awaitAll()
        assertEquals(20, results.count { it }); assertEquals(20, prefs.count())
        date = date.minusDays(1); assertFalse(prefs.reserve())
        date = date.plusDays(2); assertTrue(prefs.reserve()); assertEquals(1, prefs.count())
        prefs.save(AiConfig(false)); assertFalse(prefs.reserve())
    }
}
