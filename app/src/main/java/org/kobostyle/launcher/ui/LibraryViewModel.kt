package org.kobostyle.launcher.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.kobostyle.launcher.R
import org.kobostyle.launcher.data.books.*
import org.kobostyle.launcher.data.db.*

class LibraryViewModel @JvmOverloads constructor(
    application: Application,
    val repository: LibraryRepository = LibraryRepository(application, LibraryDatabase.get(application).library())
) : AndroidViewModel(application) {
    private val scalable = application.getSharedPreferences("spirits_v4", 0).getBoolean("feature.meow_spirits_v4", false)
    val books = (if (scalable) flowOf(emptyList<Book>()) else repository.dao.observeBooks()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val catalog = books.map { withContext(Dispatchers.Default) { BookCatalog(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookCatalog(emptyList()))
    val sources = repository.dao.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val progress = MutableStateFlow(ScanProgress())
    val message = MutableStateFlow<Int?>(null)
    private var work: Job? = null
    fun refresh() { viewModelScope.launch { repository.refreshAccess() } }
    fun add(uris: List<Uri>, tree: Boolean, flags: Int) {
        if (work?.isActive == true) return
        work = viewModelScope.launch {
            progress.value = ScanProgress(running = true)
            try {
                uris.distinct().take(100).forEach { uri ->
                    try { repository.addSource(uri, tree, flags) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { message.value = R.string.temporary_grant }
                }
                repository.scan { progress.value = it }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = R.string.source_error }
            finally { progress.value = progress.value.copy(running = false) }
        }
    }
    fun scan() {
        if (work?.isActive == true) return
        work = viewModelScope.launch {
            message.value = null
            progress.value = ScanProgress(running = true)
            try { repository.scan { progress.value = it } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = R.string.source_error }
            finally { progress.value = progress.value.copy(running = false) }
        }
    }
    fun cancel() { work?.cancel(); message.value = R.string.scan_cancelled }
    fun opened(book: Book) { viewModelScope.launch { repository.dao.opened(book.identity, System.currentTimeMillis()) } }
    override fun onCleared() { work?.cancel(); /* Room lives for this app process; do not close under an IO query. */ }
}
