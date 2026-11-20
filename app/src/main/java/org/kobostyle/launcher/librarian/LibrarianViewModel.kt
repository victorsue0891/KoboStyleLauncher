package org.kobostyle.launcher.librarian

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.kobostyle.launcher.data.books.LibraryRepository
import org.kobostyle.launcher.data.db.Book

class LibrarianViewModel(
    private val repository: LibraryRepository,
    val preferences: AiPreferences,
    private val vault: KeyVault,
    private val service: CuratorService,
    private val includeAcceptanceBooks: Boolean = false,
    private val readable: suspend (Book) -> Boolean
) : ViewModel() {
    private val mutableState = MutableStateFlow(DialogueState())
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var work: Job? = null
    fun hasKey() = vault.exists()
    fun dispatch(action: DialogueAction, value: String = ""): DialogueEffect? {
        val transition = DialogueMachine.reduce(state.value, action, value)
        mutableState.value = transition.state
        when (val effect = transition.effect) {
            is DialogueEffect.Recommend -> recommend(effect.online)
            DialogueEffect.Cancel -> cancelWork()
            else -> return effect
        }
        return null
    }
    private fun cancelWork() { generation++; work?.cancel(); work = null }
    private fun recommend(online: Boolean) {
        cancelWork()
        val requestGeneration = generation
        val choice = state.value.choice
        work = viewModelScope.launch {
            try {
                val books = repository.dao.observeBooks().first()
                val ranked = withContext(Dispatchers.Default) { LocalCurator.rank(books, choice, includeAcceptanceBooks) }
                val candidates = withContext(Dispatchers.IO) {
                    val available = mutableListOf<Book>()
                    for (book in ranked.take(60)) {
                        ensureActive()
                        val ok = try { readable(book) } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
                        if (ok) available += book
                        if (available.size == 20) break
                    }
                    available
                }
                val result = withContext(Dispatchers.IO) { service.curate(candidates, choice, online) }
                    .copy(indexedBooks = ranked.size, checkedCandidates = candidates.size)
                if (generation == requestGeneration) mutableState.value = state.value.copy(stage = DialogueStage.CARDS, result = result, index = 0)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (generation == requestGeneration) mutableState.value = state.value.copy(stage = DialogueStage.CARDS,
                    result = LocalCurator.offline(emptyList(), choice, "library"), index = 0)
            }
        }
    }
    fun leavePage() { if (state.value.stage == DialogueStage.LOADING) dispatch(DialogueAction.CANCEL) }
    fun clearSessionAndCache() { dispatch(DialogueAction.RESET); service.clearCache() }
    suspend fun saveSettings(config: AiConfig, newKey: String) {
        leavePage()
        withContext(Dispatchers.IO) {
            if (newKey.isNotBlank()) vault.save(newKey.trim())
            require(!config.enabled || vault.exists())
            preferences.save(config)
            service.clearCache()
        }
    }
    suspend fun deleteKey() {
        leavePage()
        withContext(Dispatchers.IO) {
            preferences.save(preferences.config().copy(enabled = false))
            service.clearCache()
            vault.delete()
        }
    }
    override fun onCleared() { cancelWork(); service.clearCache() }
    companion object {
        fun create(application: Application, repository: LibraryRepository): LibrarianViewModel {
            val prefs = AiPreferences(application.getSharedPreferences("librarian_ai", 0))
            val vault = AndroidKeyVault(application)
            val service = CuratorService(prefs, vault, OpenAiTransport(), {
                val network = application.getSystemService(ConnectivityManager::class.java)
                network.getNetworkCapabilities(network.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            })
            return LibrarianViewModel(repository, prefs, vault, service) { book ->
                application.contentResolver.openFileDescriptor(Uri.parse(book.uri), "r")?.use { true } == true
            }
        }
    }
}
