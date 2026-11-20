package com.tobi.app.core.runtime

import com.tobi.app.core.protocol.Fault
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

class ChallengeCoordinator {
    private val _pending = MutableStateFlow<Fault?>(null)
    val pending: StateFlow<Fault?> = _pending
    private var deferred: CompletableDeferred<Boolean>? = null

    suspend fun solve(fault: Fault): Boolean {
        val completer = CompletableDeferred<Boolean>()
        deferred = completer
        _pending.value = fault
        val result = try {
            withTimeoutOrNull(180_000) { completer.await() } ?: false
        } finally {
            _pending.value = null
            deferred = null
        }
        return result
    }

    fun complete(solved: Boolean) {
        deferred?.complete(solved)
    }

    fun dismiss() {
        deferred?.complete(false)
    }
}
