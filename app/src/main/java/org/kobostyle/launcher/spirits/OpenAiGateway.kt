package org.kobostyle.launcher.spirits

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import org.kobostyle.launcher.librarian.*
import java.time.LocalDate
import java.util.UUID

data class CostPreview(val action: String, val model: String, val estimatedInput: Int, val maxOutput: Int,
    val estimatedMicros: Long, val priceDate: String)
class GateFailure(val reason: String) : Exception(reason)

/** Single process-wide paid lane, shared by legacy recommendations and spirit requests. */
class OpenAiGateway(private val db: SpiritDatabase, private val settings: SpiritSettings,
    private val transport: CuratorTransport, private val today: () -> LocalDate = { LocalDate.now() },
    private val now: () -> Long = { System.currentTimeMillis() }) {
    companion object { private val lane = Mutex() }
    private var recovered = false
    private var active: Job? = null
    fun stop() { settings.stopped = true; active?.cancel() }
    suspend fun send(action: String, body: String, key: () -> String?, confirm: suspend (CostPreview) -> Boolean,
        requestId: String = UUID.randomUUID().toString()): WireResponse {
        require(action in setOf("recommend", "ask", "refine"))
        if (settings.stopped) throw GateFailure("stopped")
        if (!lane.tryLock()) throw GateFailure("busy")
        var reserved: UsageEvent? = null
        try {
            active = currentCoroutineContext()[Job]
            if (settings.stopped) throw GateFailure("stopped")
            // Turning V4 off preserves V3 opt-in. The emergency stop and lane still apply.
            if (!settings.enabled && action == "recommend") return transport.post(key() ?: throw GateFailure("no_key"), body)
            if (!settings.enabled || (action != "recommend" && !settings.paidSpirits)) throw GateFailure("disabled")
            if (!recovered) { db.dao().recoverReservations(); recovered = true }
            val request = JSONObject(body)
            val model = request.getString("model")
            val price = settings.price(model) ?: throw GateFailure("unknown_price")
            val bytes = body.toByteArray(Charsets.UTF_8).size
            val maxOutput = request.getInt("max_output_tokens")
            if (bytes !in 1..40_000 || maxOutput !in 1..1800 || (action != "recommend" && maxOutput > 300)) throw GateFailure("input_limit")
            // Conservative byte estimate including JSON overhead, not an exact tokenizer or bill.
            val estimate = CostPreview(action, model, bytes, maxOutput, price.cost(bytes, maxOutput), price.date)
            fun limits() = settings.budget()
            suspend fun checkBudget() {
                val day = today().toString(); val month = day.take(7); val budget = limits()
                if ((db.dao().latestDay() ?: day) > day) throw GateFailure("clock_changed")
                if (db.dao().event(requestId) != null) throw GateFailure("duplicate")
                if (db.dao().dayRequests(day) >= 20 || estimate.estimatedMicros > budget.single ||
                    db.dao().dayCost(day) + estimate.estimatedMicros > budget.daily ||
                    db.dao().monthCost(month) + estimate.estimatedMicros > budget.monthly) throw GateFailure("budget")
            }
            checkBudget()
            if (!confirm(estimate)) throw GateFailure("cancelled")
            currentCoroutineContext().ensureActive()
            if (settings.stopped || !settings.enabled || (action != "recommend" && !settings.paidSpirits) || settings.price(model) != price) throw GateFailure("settings_changed")
            val credential = key() ?: throw GateFailure("no_key")
            db.transaction {
                checkBudget()
                val day = today().toString()
                val entry = UsageEvent(requestId, action, model, day, day.take(7), now(), estimate.estimatedMicros,
                    estimate.estimatedMicros, "IN_FLIGHT")
                db.dao().reserve(entry); reserved = entry
            }
            currentCoroutineContext().ensureActive()
            val response = withTimeout(45_000) { transport.post(credential, body) }
            // A successful HTTP with bad/missing usage remains conservatively reserved.
            val usage = if (response.code in 200..299) try { StrictJson.obj(response.body).optJSONObject("usage") } catch (_: Exception) { null } else null
            val input = usage?.opt("input_tokens") as? Number
            val output = usage?.opt("output_tokens") as? Number
            val valid = input != null && output != null && input.toDouble() == input.toInt().toDouble() && output.toDouble() == output.toInt().toDouble() && input.toInt() in 0..1_000_000 && output.toInt() in 0..100_000
            val original = checkNotNull(reserved)
            db.dao().updateUsage(if (valid) original.copy(status = "SUCCESS", inputTokens = input!!.toInt(), outputTokens = output!!.toInt(),
                chargedMicros = price.cost(input.toInt(), output.toInt())) else original.copy(status = "UNKNOWN_BILLING"))
            reserved = null
            return response
        } finally {
            val pending = reserved
            if (pending != null) withContext(NonCancellable) {
                try { db.dao().updateUsage(pending.copy(status = "UNKNOWN_BILLING")) } catch (_: Exception) { /* IN_FLIGHT remains reserved on disk. */ }
            }
            active = null; lane.unlock()
        }
    }
}
