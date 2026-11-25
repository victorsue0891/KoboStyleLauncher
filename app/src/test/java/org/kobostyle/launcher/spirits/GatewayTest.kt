package org.kobostyle.launcher.spirits

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.kobostyle.launcher.librarian.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class GatewayTest {
    private lateinit var db: SpiritDatabase
    private lateinit var settings: SpiritSettings
    private val request = """{"model":"test-model","max_output_tokens":300,"input":"synthetic"}"""
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SpiritDatabase::class.java).setDriver(BundledSQLiteDriver()).addCallback(SpiritDatabase.SEARCH).build()
        settings = SpiritSettings(context.getSharedPreferences(UUID.randomUUID().toString(), 0)).apply {
            enabled = true; paidSpirits = true; savePrice("test-model", Price(1_000_000, 2_000_000, "2026-11-25"))
        }
    }
    @After fun close() { db.close() }
    private suspend fun refused(expected: String, block: suspend () -> Unit) {
        try { block(); fail("Expected refusal") } catch (e: GateFailure) { assertEquals(expected, e.reason) }
    }
    @Test fun noImplicitCallsAndUnknownPriceDisabledBudgetAndConsentRefuse() = runBlocking {
        var calls = 0
        val gateway = OpenAiGateway(db, settings, CuratorTransport { _, _ -> calls++; WireResponse(200) })
        assertEquals(0, calls)
        settings.stopped = true
        refused("stopped") { gateway.send("ask", request, { "fake-key" }, { true }) }
        settings.stopped = false; settings.paidSpirits = false
        refused("disabled") { gateway.send("ask", request, { "fake-key" }, { true }) }
        settings.paidSpirits = true
        refused("unknown_price") { gateway.send("ask", request.replace("test-model", "unknown"), { "fake-key" }, { true }) }
        refused("cancelled") { gateway.send("ask", request, { "fake-key" }, { false }) }
        settings.clearPrice()
        refused("unknown_price") { gateway.send("ask", request, { "fake-key" }, { true }) }
        settings.savePrice("test-model", Price(1_000_000, 2_000_000, "2026-11-25"))
        settings.saveBudget(Budget(1, 1, 1))
        refused("budget") { gateway.send("ask", request, { "fake-key" }, { true }) }
        assertEquals(0, calls)
    }
    @Test fun tenDuplicateClicksAndIndependentRequestsShareOnePaidLane() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>(); var calls = 0
        val gateway = OpenAiGateway(db, settings, CuratorTransport { _, _ -> calls++; entered.complete(Unit); finish.await(); WireResponse(200) })
        val first = async { gateway.send("ask", request, { "fake-key" }, { true }, "same") }
        entered.await()
        repeat(10) { refused("busy") { gateway.send("ask", request, { "fake-key" }, { true }, if (it < 9) "same" else "other") } }
        finish.complete(Unit); first.await()
        refused("duplicate") { gateway.send("ask", request, { "fake-key" }, { true }, "same") }
        assertEquals(1, calls)
    }
    @Test fun cancellationAndProcessRecoveryKeepReservationsWithoutRetry() = runBlocking {
        val entered = CompletableDeferred<Unit>(); var calls = 0
        val gateway = OpenAiGateway(db, settings, CuratorTransport { _, _ -> calls++; entered.complete(Unit); awaitCancellation() })
        val job = launch { gateway.send("ask", request, { "fake-key" }, { true }, "cancelled-request") }
        entered.await(); job.cancelAndJoin()
        assertEquals("UNKNOWN_BILLING", db.dao().event("cancelled-request")!!.status)
        assertTrue(db.dao().event("cancelled-request")!!.chargedMicros > 0)
        db.dao().reserve(UsageEvent("crashed", "ask", "test-model", "2026-11-25", "2026-11", 0, 100, 100, "IN_FLIGHT"))
        val restored = OpenAiGateway(db, settings, CuratorTransport { _, _ -> error("No retry") }, today = { LocalDate.of(2026, 11, 25) })
        refused("cancelled") { restored.send("ask", request, { "fake-key" }, { false }) }
        assertEquals("UNKNOWN_BILLING", db.dao().event("crashed")!!.status); assertEquals(1, calls)
    }
    @Test fun errorsNeverAutomaticallyRetryAndMissingUsageStaysReserved() = runBlocking {
        var calls = 0
        val gateway = OpenAiGateway(db, settings, CuratorTransport { _, _ -> calls++; WireResponse(429) })
        assertEquals(429, gateway.send("ask", request, { "fake-key" }, { true }, "rate").code)
        assertEquals(1, calls); assertEquals("UNKNOWN_BILLING", db.dao().event("rate")!!.status)
    }
    @Test fun actualUsageAndDayMonthRolloverRemainAccounted() = runBlocking {
        var date = LocalDate.of(2026, 11, 30)
        val gateway = OpenAiGateway(db, settings, CuratorTransport { _, _ -> WireResponse(200, """{"usage":{"input_tokens":10,"output_tokens":20}}""") }, today = { date })
        gateway.send("ask", request, { "fake-key" }, { true }, "first")
        assertEquals(50L, db.dao().dayCost(date.toString()))
        date = date.minusDays(1)
        refused("clock_changed") { gateway.send("ask", request, { "fake-key" }, { true }) }
        date = LocalDate.of(2026, 12, 1)
        gateway.send("ask", request, { "fake-key" }, { true }, "second")
        assertEquals(50L, db.dao().monthCost("2026-11")); assertEquals(50L, db.dao().monthCost("2026-12"))
    }
    @Test fun killSwitchCancelsExistingRequestAndLegacyCannotBypassStop() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gateway = OpenAiGateway(db, settings, CuratorTransport { _, _ -> entered.complete(Unit); awaitCancellation() })
        val job = launch { gateway.send("ask", request, { "fake-key" }, { true }, "stop") }
        entered.await(); gateway.stop(); job.join()
        settings.enabled = false
        refused("stopped") { gateway.send("recommend", request, { "fake-key" }, { true }) }
        assertEquals("UNKNOWN_BILLING", db.dao().event("stop")!!.status)
    }
    @Test fun persistenceFailureAndOversizedRequestNeverReachTransport() = runBlocking {
        var calls = 0
        val gateway = OpenAiGateway(db, settings, CuratorTransport { _, _ -> calls++; WireResponse(200) })
        refused("input_limit") { gateway.send("ask", request.replace("synthetic", "x".repeat(40_001)), { "fake-key" }, { true }) }
        db.close()
        try { gateway.send("ask", request, { "fake-key" }, { true }); fail() } catch (_: IllegalStateException) { }
        assertEquals(0, calls)
    }
}
