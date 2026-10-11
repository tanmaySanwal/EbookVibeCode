package com.ebookvibe.tvreader.ui.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ebookvibe.tvreader.data.FileEntry
import com.ebookvibe.tvreader.data.Storage
import com.ebookvibe.tvreader.data.StorageRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BrowserState(
    val hasAccess: Boolean = false,
    val roots: List<StorageRoot> = emptyList(),
    val root: StorageRoot? = null,
    val dir: File? = null,
    val entries: List<FileEntry> = emptyList(),
    val loading: Boolean = false,
    /** Entry to focus after a listing loads (the folder we just came back out of). */
    val focusName: String? = null,
)

class BrowserViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()
    private var listJob: Job? = null

    init {
        refresh()
    }

    /** Re-checks permission and mounted volumes (call on resume and when a USB drive is plugged in). */
    fun refresh() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val access = Storage.hasAccess(ctx)
            val roots = withContext(Dispatchers.IO) { if (access) Storage.roots(ctx) else emptyList() }
            val current = _state.value
            _state.update { it.copy(hasAccess = access, roots = roots) }
            when {
                !access -> Unit
                current.dir != null && current.dir.isDirectory -> load(current.dir, current.focusName)
                roots.isNotEmpty() -> openRoot(roots.first())
                else -> _state.update { it.copy(root = null, dir = null, entries = emptyList()) }
            }
        }
    }

    fun openRoot(root: StorageRoot) {
        _state.update { it.copy(root = root) }
        load(root.dir, null)
    }

    fun openDir(dir: File) = load(dir, null)

    /** Returns false when already at the top of a root, so the caller can leave the browser. */
    fun goUp(): Boolean {
        val s = _state.value
        val dir = s.dir ?: return false
        val root = s.root?.dir ?: return false
        if (dir.absolutePath == root.absolutePath) return false
        val parent = dir.parentFile ?: return false
        load(parent, dir.name)
        return true
    }

    private fun load(dir: File, focusName: String?) {
        listJob?.cancel()
        _state.update { it.copy(dir = dir, loading = true, focusName = focusName) }
        listJob = viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { Storage.list(dir) }
            _state.update { it.copy(entries = entries, loading = false) }
        }
    }
}
