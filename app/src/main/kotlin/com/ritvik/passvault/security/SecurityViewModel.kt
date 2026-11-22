package com.ritvik.passvault.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritvik.passvault.crypto.AuditEntry
import com.ritvik.passvault.crypto.BreachCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

enum class BreachStatus { IDLE, RUNNING, DONE, ERROR }

data class BreachUiState(
    val status: BreachStatus = BreachStatus.IDLE,
    val checked: Int = 0,
    val total: Int = 0,
    /** Item id to the number of times its password appears in known breaches. Only breached items are listed. */
    val breached: Map<String, Long> = emptyMap(),
    val error: String? = null,
)

class SecurityViewModel : ViewModel() {
    private val client = BreachClient()
    private val _state = MutableStateFlow(BreachUiState())
    val state: StateFlow<BreachUiState> = _state.asStateFlow()
    private var job: Job? = null

    /**
     * One request per distinct 5-character hash prefix (items whose hashes share a prefix share a
     * request). The hash comparison happens here on the phone.
     */
    fun check(entries: List<AuditEntry>) {
        if (_state.value.status == BreachStatus.RUNNING) return

        data class Candidate(val id: String, val suffix: String)

        val byPrefix = HashMap<String, MutableList<Candidate>>()
        for (e in entries) {
            if (e.password.isEmpty()) continue
            val hash = BreachCheck.sha1Hex(e.password)
            byPrefix.getOrPut(BreachCheck.prefix(hash)) { mutableListOf() }
                .add(Candidate(e.id, BreachCheck.suffix(hash)))
        }
        if (byPrefix.isEmpty()) return

        _state.value = BreachUiState(status = BreachStatus.RUNNING, total = byPrefix.size)
        job = viewModelScope.launch(Dispatchers.IO) {
            val found = HashMap<String, Long>()
            try {
                var done = 0
                for ((prefix, candidates) in byPrefix) {
                    val body = client.fetchRange(prefix)
                    for (c in candidates) {
                        val count = BreachCheck.countInRange(body, c.suffix)
                        if (count > 0) found[c.id] = count
                    }
                    done++
                    _state.update { it.copy(checked = done, breached = HashMap(found)) }
                }
                _state.update { it.copy(status = BreachStatus.DONE, breached = HashMap(found)) }
            } catch (e: IOException) {
                _state.update {
                    it.copy(
                        status = BreachStatus.ERROR,
                        error = "Could not reach the breach service. Check your internet connection and try again.",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(status = BreachStatus.ERROR, error = "The breach check failed. Please try again.") }
            }
        }
    }

    /** Forget all results, for example when leaving the screen or when the vault locks. */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = BreachUiState()
    }
}
