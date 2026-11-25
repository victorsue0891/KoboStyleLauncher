package org.kobostyle.launcher.spirits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kobostyle.launcher.data.db.Book
import org.kobostyle.launcher.librarian.*

data class SpiritState(val book: Book? = null, val spirit: Spirit? = null, val pack: KnowledgePack? = null,
    val boundary: ReadingBoundary? = null, val answer: SpiritAnswer? = null, val loading: Boolean = false, val message: String = "")

class SpiritViewModel(val repository: SpiritRepository, private val settings: SpiritSettings, private val prefs: AiPreferences,
    private val vault: KeyVault, private val gateway: OpenAiGateway, private val connected: () -> Boolean) : ViewModel() {
    private val mutable = MutableStateFlow(SpiritState()); val state = mutable.asStateFlow()
    private var work: Job? = null; private var generation = 0
    private val memory = ArrayDeque<String>()
    private fun task(block: suspend () -> Unit) {
        if (work?.isActive == true) return
        val started = generation
        work = viewModelScope.launch {
            mutable.value = state.value.copy(loading = true, message = "")
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: CancellationException) { throw e }
            catch (e: GateFailure) { mutable.value = state.value.copy(message = SpiritDialogs.failure(e.reason)) }
            catch (_: Exception) { mutable.value = state.value.copy(message = "無法完成；原書與既有知識包仍保留。請檢查資料或稍後手動重試。") }
            finally { if (generation == started) mutable.value = state.value.copy(loading = false) }
        }
    }
    fun select(id: String) {
        cancel(); memory.clear(); mutable.value = SpiritState()
        task { load(id) }
    }
    private suspend fun load(id: String) {
        val book = repository.book(id) ?: throw GateFailure("missing")
        val next = SpiritState(book, repository.db.dao().spirit(id)?.takeIf { it.version == bookVersion(book) }, repository.validPack(book), repository.boundary(id), loading = state.value.loading)
        currentCoroutineContext().ensureActive(); mutable.value = next
    }
    fun summon() { val book = state.value.book ?: return; task { repository.summon(book.identity); load(book.identity) } }
    fun delete() { val book = state.value.book ?: return; task { repository.delete(book.identity); memory.clear(); load(book.identity) } }
    fun removePack() { val book = state.value.book ?: return; task { repository.removePack(book.identity); memory.clear(); load(book.identity) } }
    fun boundary(chapter: Int?, spoilers: Boolean) {
        val book = state.value.book ?: return; cancel(); memory.clear()
        task { repository.setBoundary(ReadingBoundary(book.identity, chapter, spoilers)); load(book.identity) }
    }
    fun imported() { val id = state.value.book?.identity ?: return; memory.clear(); task { load(id) } }
    fun ask(question: String, refine: Boolean = false, confirm: suspend (CostPreview) -> Boolean) {
        val book = state.value.book ?: return
        if (question.isBlank() || question.codePointCount(0, question.length) > 200 || work?.isActive == true) return
        val currentGeneration = generation
        task {
            val latest = repository.book(book.identity) ?: throw GateFailure("missing")
            require(bookVersion(latest) == bookVersion(book))
            val spirit = repository.summon(book.identity)
            val evidence = if (refine) emptyList() else repository.evidence(book, question)
            if (!refine && evidence.isEmpty()) {
                mutable.value = state.value.copy(spirit = spirit, answer = SpiritAnswer("目前書庫資料不足。我只知道書名與書目；請匯入知識包並設定已讀章節，或直接開書閱讀。", emptyList()))
                return@task
            }
            // Imported, permitted excerpts remain usable without a key or paid-feature opt-in.
            if (!refine && (!settings.paidSpirits || !prefs.config().enabled || settings.stopped || !vault.exists() || !connected())) {
                mutable.value = state.value.copy(spirit = spirit, answer = SpiritAnswer("本機模式：可查看下方已允許的匯入片段；沒有產生新的 AI 回答。", evidence))
                return@task
            }
            if (!settings.enabled || !settings.paidSpirits || !prefs.config().enabled) throw GateFailure("disabled")
            if (!vault.exists()) throw GateFailure("no_key")
            if (!connected()) throw GateFailure("offline")
            val body = SpiritAnswers.request(book, spirit, question, evidence, prefs.config().model, memory.toList(), refine)
            val response = gateway.send(if (refine) "refine" else "ask", body, { vault.read() }, confirm)
            prefs.httpResponse(response.code, response.errorCode)
            if (response.code !in 200..299) throw GateFailure(if (response.code == 401) "authentication" else "http")
            val answer = SpiritAnswers.parse(response.body, evidence)
            if (generation != currentGeneration || bookVersion(repository.book(book.identity) ?: return@task) != bookVersion(book)) return@task
            if (refine) {
                repository.db.dao().spirit(spirit.copy(opening = answer.text.take(240), refined = true)); load(book.identity)
            } else {
                memory.addLast("${question.take(70)}：${answer.text.take(80)}"); while (memory.size > 6) memory.removeFirst()
                mutable.value = state.value.copy(spirit = spirit, answer = answer)
            }
        }
    }
    fun cancel() { generation++; work?.cancel(); work = null; mutable.value = state.value.copy(loading = false) }
    override fun onCleared() { cancel(); memory.clear() }
}
